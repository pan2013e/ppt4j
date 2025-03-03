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

    /**
     * Merges two sets of BytecodeFeatures by combining their constants, method invocations, field accesses, object creations,
     * instructions, and miscellaneous features into a single BytecodeFeatures object.
     *
     * @param f1 the first set of BytecodeFeatures
     * @param f2 the second set of BytecodeFeatures
     * @return a new BytecodeFeatures object containing the merged features from f1 and f2
     */
    public static BytecodeFeatures merge(@NonNull BytecodeFeatures f1,
                                         @NonNull BytecodeFeatures f2) {
        BytecodeFeatures merged = new BytecodeFeatures();
        
        // Merge constants
        Stream.of(f1.getConstants(), f2.getConstants())
                .flatMap(Collection::stream)
                .forEach(merged.getConstants()::add);
        
        // Merge method invocations
        Stream.of(f1.getMethodInvocations(), f2.getMethodInvocations())
                .flatMap(Collection::stream)
                .forEach(merged.getMethodInvocations()::add);
        
        // Merge field accesses
        Stream.of(f1.getFieldAccesses(), f2.getFieldAccesses())
                .flatMap(Collection::stream)
                .forEach(merged.getFieldAccesses()::add);
        
        // Merge object creations
        Stream.of(f1.getObjCreations(), f2.getObjCreations())
                .flatMap(Collection::stream)
                .forEach(merged.getObjCreations()::add);
        
        // Merge instructions
        Stream.of(f1.getInstructions(), f2.getInstructions())
                .flatMap(Collection::stream)
                .forEach(merged.getInstructions()::add);
        
        // Merge miscellaneous features
        Stream.of(f1.getMisc(), f2.getMisc())
                .flatMap(Collection::stream)
                .forEach(merged.getMisc()::add);
        
        return merged;
    }

    /**
     * Creates and returns a new instance of BytecodeFeatures with no features set.
     * 
     * @return a new instance of BytecodeFeatures with no features set
     */
    public static BytecodeFeatures empty() {
        // Create a new instance of BytecodeFeatures
        return new BytecodeFeatures();
    }

    /**
     * Returns a string representation of the object. 
     * The string includes the index, source line number,
     * and all instructions in the instance.
     * 
     * @return a string representation of the object
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(
                String.format("Index: %d, Source Line: %d\n", index, lineNo)); // Initialize StringBuilder with index and line number
        for(String s: insts) {
            sb.append(s); // Append each instruction to the StringBuilder
        }
        return sb + super.toString(); // Return the StringBuilder appended with the superclass's toString method result
    }

    /**
     * Visits the given instruction node in the bytecode. 
     * If the instruction is a JumpInsnNode, calls visitJumpInsn method.
     * If the instruction is a MethodInsnNode, calls visitMethodInsn method.
     * Otherwise, accepts a new BytecodeFeatureScanner to process the instruction.
     * 
     * @param inst the instruction node to visit
     */
    private void visitInst(AbstractInsnNode inst) {
        // Check if the instruction is a JumpInsnNode
        if (inst instanceof JumpInsnNode jump) {
            visitJumpInsn(jump);
        } else if (inst instanceof MethodInsnNode minsn) {
            // Check if the instruction is a MethodInsnNode
            visitMethodInsn(minsn);
        } else {
            // If not a JumpInsnNode or MethodInsnNode, accept a new BytecodeFeatureScanner to process the instruction
            inst.accept(new BytecodeFeatureScanner(this));
        }
    }

    /**
     * Handles the constructor description of inner classes by skipping the first parameter description
     * if the method name is "<init>" and the owner contains a "$".
     * 
     * @param owner the owner of the constructor
     * @param name the name of the constructor
     * @param descs an array of parameter descriptions
     * @return the concatenated parameter descriptions excluding the first parameter, or null if conditions are not met
     */
    private String handleInnerClassConstructorDesc(String owner, String name, String[] descs) {
        if(name.equals("<init>") && owner.contains("$")) { // check if method name is "<init>" and owner contains "$"
            if(descs.length > 0) { // check if descs array has elements
                String[] skipFirst = new String[descs.length - 1]; // create a new array excluding the first element
                System.arraycopy(descs, 1, skipFirst, 0, descs.length - 1); // copy elements to the new array
                return StringUtils.joinArgDesc(Arrays.stream(skipFirst).toList()); // join the descriptions and return
            }
        }
        return null; // return null if conditions are not met
    }

    /**
     * Visits a JumpInsnNode and adds a bytecode feature to the list of instructions based on whether the jump is a backward branch or not.
     * If the JumpInsnNode is a backward branch, adds InstType.LOOP to the list of instructions.
     * If the JumpInsnNode is not a backward branch, delegates the visit to a BytecodeFeatureScanner to extract additional features.
     * 
     * @param jump the JumpInsnNode to visit
     */
    private void visitJumpInsn(JumpInsnNode jump) {
        BytecodeFeatureScanner scanner = new BytecodeFeatureScanner(this);
        
        if(backwardBranches.contains(jump)) { // check if the jump is a backward branch
            getInstructions().add(InstType.LOOP); // add LOOP instruction if it is a backward branch
        } else {
            scanner.visitJumpInsn(jump.getOpcode(), jump.label.getLabel()); // delegate to BytecodeFeatureScanner for further analysis
        }
    }

    /**
     * Visits a method instruction node and updates the method description based on the information stored in the method description map.
     * If a new method description is found in the map, it updates the argument descriptions accordingly.
     * If no new method description is found, it checks if it's an inner class constructor and updates the argument descriptions accordingly.
     * If no new description is found for an inner class constructor, it falls back to the original method description.
     * Finally, it calls the BytecodeFeatureScanner to process the method instruction node.
     *
     * @param minsn the MethodInsnNode to visit
     */
    private void visitMethodInsn(MethodInsnNode minsn) {
        BytecodeFeatureScanner scanner = new BytecodeFeatureScanner(this);
        String desc = methodDescMap.get(minsn);
        String[] origDescs = StringUtils.splitArgDesc(minsn.desc);
        String newDesc;
        if (desc != null) {
            String[] newDescs = StringUtils.splitArgDesc(desc);
            List<String> descList = new ArrayList<>();
            if (minsn.name.equals("<init>") && minsn.owner.contains("$")) {
                if (origDescs.length > 0) {
                    descList.add(origDescs[0]);
                }
                for (int i = 1; i < origDescs.length; i++) {
                    if (StringUtils.isPrimitive(origDescs[i])) {
                        descList.add(origDescs[i]);
                    } else {
                        descList.add(newDescs[i]);
                    }
                }
            } else {
                for (int i = 0; i < origDescs.length; i++) {
                    if (StringUtils.isPrimitive(origDescs[i])) {
                        descList.add(origDescs[i]);
                    } else {
                        descList.add(newDescs[i]);
                    }
                }
            }
            newDesc = handleInnerClassConstructorDesc(
                    minsn.owner, minsn.name, descList.toArray(String[]::new));
            if (newDesc == null) {
                newDesc = StringUtils.joinArgDesc(descList);
            }
        } else {
            newDesc = handleInnerClassConstructorDesc(
                    minsn.owner, minsn.name, origDescs);
            if (newDesc == null) {
                newDesc = minsn.desc;
            }
        }
        scanner.visitMethodInsn(minsn.getOpcode(),
                minsn.owner, minsn.name, newDesc, minsn.itf);
    }

}