package ppt4j.feature.bytecode;

import ppt4j.feature.Features;
import ppt4j.util.AsmUtils;
import ppt4j.util.StringUtils;
import lombok.Getter;
import lombok.NonNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.Serial;
import java.util.*;
import java.util.stream.Stream;

public final class BytecodeFeatures extends Features {

    @Serial
    private static final long serialVersionUID = 1L;

    @Getter
    private final List<String> insts = new ArrayList<>();

    @Getter
    private final int index;

    private final transient Map<MethodInsnNode, String> methodDescMap;

    private final transient Set<AbstractInsnNode> backwardBranches;

    private BytecodeFeatures() {
        super(SourceType.BYTECODE, "", 0);
        this.index = 0;
        this.methodDescMap = null;
        this.backwardBranches = null;
    }

    BytecodeFeatures(@NonNull String className,
                            @NonNull List<AbstractInsnNode> insts,
                            int line, int index,
                            BytecodeExtractor extractor) {
        super(SourceType.BYTECODE, className, line);
        this.index = index;
        this.methodDescMap = extractor.getMethodDescMap();
        this.backwardBranches = extractor.getBackwardBranches();
        insts.forEach(this::visitInst);
        for(int i = 0;i < insts.size() - 1;i++) {
            AbstractInsnNode inst = insts.get(i);
            AbstractInsnNode next = insts.get(i + 1);
            if(AsmUtils.isConstOp(inst.getOpcode()) &&
                    (next.getOpcode() == Opcodes.ANEWARRAY || next.getOpcode() == Opcodes.NEWARRAY)) {
                int length = (int) AsmUtils.getConst(inst.getOpcode());
                for(int j = 0; j <= length; j++) {
                    getConstants().remove(j);
                }
            }
            if(inst.getOpcode() == Opcodes.ARRAYLENGTH &&
                    (next.getOpcode() == Opcodes.IFNE ||
                            next.getOpcode() == Opcodes.IFEQ)) {
                getConstants().add(0);
            }
        }
        Printer printer = new Textifier();
        insts.forEach(inst -> inst.accept(new TraceMethodVisitor(printer)));
        for(Object o: printer.getText()) {
            this.insts.add(o.toString());
        }
    }

    public static BytecodeFeatures merge(@NonNull BytecodeFeatures f1,
                                         @NonNull BytecodeFeatures f2) {
        BytecodeFeatures merged = new BytecodeFeatures();
        Stream.of(f1.getConstants(), f2.getConstants())
                .flatMap(Collection::stream)
                .forEach(merged.getConstants()::add);
        Stream.of(f1.getMethodInvocations(), f2.getMethodInvocations())
                .flatMap(Collection::stream)
                .forEach(merged.getMethodInvocations()::add);
        Stream.of(f1.getFieldAccesses(), f2.getFieldAccesses())
                .flatMap(Collection::stream)
                .forEach(merged.getFieldAccesses()::add);
        Stream.of(f1.getObjCreations(), f2.getObjCreations())
                .flatMap(Collection::stream)
                .forEach(merged.getObjCreations()::add);
        Stream.of(f1.getInstructions(), f2.getInstructions())
                .flatMap(Collection::stream)
                .forEach(merged.getInstructions()::add);
        Stream.of(f1.getMisc(), f2.getMisc())
                .flatMap(Collection::stream)
                .forEach(merged.getMisc()::add);
        return merged;
    }

    public static BytecodeFeatures empty() {
        return new BytecodeFeatures();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(
                String.format("Index: %d, Source Line: %d\n", index, lineNo));
        for(String s: insts) {
            sb.append(s);
        }
        return sb + super.toString();
    }

    private void visitInst(AbstractInsnNode inst) {
        if (inst instanceof JumpInsnNode jump) {
            visitJumpInsn(jump);
        } else if (inst instanceof MethodInsnNode minsn) {
            visitMethodInsn(minsn);
        } else {
            inst.accept(new BytecodeFeatureScanner(this));
        }
    }

    private String handleInnerClassConstructorDesc(String owner, String name, String[] descs) {
        if(name.equals("<init>") && owner.contains("$")) {
            if(descs.length > 0) {
                String[] skipFirst = new String[descs.length - 1];
                System.arraycopy(descs, 1, skipFirst, 0, descs.length - 1);
                return StringUtils.joinArgDesc(Arrays.stream(skipFirst).toList());
            }
        }
        return null;
    }

    private void visitJumpInsn(JumpInsnNode jump) {
        BytecodeFeatureScanner scanner = new BytecodeFeatureScanner(this);
        if(backwardBranches.contains(jump)) {
            getInstructions().add(InstType.LOOP);
        } else {
            scanner.visitJumpInsn(jump.getOpcode(), jump.label.getLabel());
        }
    }

    private void visitMethodInsn(MethodInsnNode minsn) {
        BytecodeFeatureScanner scanner = new BytecodeFeatureScanner(this);
        String desc = methodDescMap.get(minsn);
        String[] origDescs = StringUtils.splitArgDesc(minsn.desc);
        String newDesc;
        if(desc != null) {
            String[] newDescs = StringUtils.splitArgDesc(desc);
            List<String> descList = new ArrayList<>();
            if(minsn.name.equals("<init>") && minsn.owner.contains("$")) {
                if(origDescs.length > 0) {
                    descList.add(origDescs[0]);
                }
                for(int i = 1; i < origDescs.length; i++) {
                    if(StringUtils.isPrimitive(origDescs[i])) {
                        descList.add(origDescs[i]);
                    } else {
                        descList.add(newDescs[i]);
                    }
                }
            } else {
                for(int i = 0; i < origDescs.length; i++) {
                    if(StringUtils.isPrimitive(origDescs[i])) {
                        descList.add(origDescs[i]);
                    } else {
                        descList.add(newDescs[i]);
                    }
                }
            }
            newDesc = handleInnerClassConstructorDesc(
                    minsn.owner, minsn.name, descList.toArray(String[]::new));
            if(newDesc == null) {
                newDesc = StringUtils.joinArgDesc(descList);
            }
        } else {
            newDesc = handleInnerClassConstructorDesc(
                    minsn.owner, minsn.name, origDescs);
            if(newDesc == null) {
                newDesc = minsn.desc;
            }
        }
        scanner.visitMethodInsn(minsn.getOpcode(),
                minsn.owner, minsn.name, newDesc, minsn.itf);
    }

}