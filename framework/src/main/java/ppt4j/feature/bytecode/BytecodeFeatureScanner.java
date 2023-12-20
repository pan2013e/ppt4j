package ppt4j.feature.bytecode;

import ppt4j.annotation.Property;
import ppt4j.feature.Features;
import ppt4j.util.AsmUtils;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@SuppressWarnings("UnnecessaryReturnStatement")
final class BytecodeFeatureScanner extends MethodVisitor {

    @Property("org.objectweb.asm.api")
    private static int ASM_API;

    private final Features features;

    BytecodeFeatureScanner(Features features) {
        super(ASM_API);
        this.features = features;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        if(name.equals("toString") || name.equals("valueOf")
                || name.equals("append") || name.equals("longValue") ||
                name.matches(".*\\$\\d+")) {
            return;
        }
        if(name.equals("<init>")) {
            if (owner.equals("java/lang/Object") ||
                    owner.equals("java/lang/StringBuilder")) {
                return;
            }
        }
        String signature = owner + "." + name + ":" + descriptor;
        signature = signature.substring(0, signature.lastIndexOf(")") + 1);
        features.getMethodInvocations().add(signature);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        super.visitFieldInsn(opcode, owner, name, descriptor);
        if(name.startsWith("$SwitchMap$")) {
            features.getFieldAccesses().add(name + ":" + descriptor);
            return;
        }
        // Access java/lang/Void.class will actually access java/lang/Void.TYPE
        // first. Here we ignore this to be consistent with Spoon
        if(owner.equals("java/lang/Void")) {
            return;
        }
        if(name.matches(".*\\$\\d+") || owner.contains("$")) {
            return;
        }
        features.getFieldAccesses().add(owner + "." + name + ":" + descriptor);
    }

    @Override
    public void visitLdcInsn(Object value) {
        super.visitLdcInsn(value);
        if(value.getClass().getSimpleName().equals("Type")) {
            return;
        }
        features.getConstants().add(value);
    }

    @Override
    public void visitInsn(int opcode) {
        super.visitInsn(opcode);
        if(AsmUtils.isReturnOp(opcode)) {
            features.getInstructions().add(Features.InstType.RETURN);
            return;
        }
        if (AsmUtils.isConstOp(opcode)) {
            features.getConstants().add(AsmUtils.getConst(opcode));
            return;
        }
        if (opcode == Opcodes.MONITORENTER) {
            features.getInstructions().add(Features.InstType.MONITOR);
            return;
        }
        if (opcode == Opcodes.ATHROW) {
            features.getInstructions().add(Features.InstType.THROW);
            return;
        }
        if (opcode == Opcodes.ARRAYLENGTH) {
            features.getFieldAccesses().add("int.length:I");
            return;
        }
        if (AsmUtils.isShiftOp(opcode)) {
            features.getInstructions().add(AsmUtils.getShiftType(opcode));
            return;
        }
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        super.visitIntInsn(opcode, operand);
        if(opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
            features.getConstants().add(operand);
            return;
        }

        if(opcode == Opcodes.NEWARRAY) {
            features.getObjCreations().add(AsmUtils.getArrayType(operand));
            return;
        }
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        super.visitTypeInsn(opcode, type);
        if (opcode == Opcodes.ANEWARRAY) {
            features.getObjCreations().add(type + "[]");
        }
        if (opcode == Opcodes.INSTANCEOF) {
            features.getInstructions().add(Features.InstType.INSTANCEOF);
            return;
        }
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        super.visitLookupSwitchInsn(dflt, keys, labels);
        features.getInstructions().add(Features.InstType.SWITCH);
        int count = keys.length;
        if(dflt != null) {
            count++;
        }
        features.getMisc().add("CASE" + count);
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        super.visitTableSwitchInsn(min, max, dflt, labels);
        int count = 0;
        for(int i = min; i <= max; i++) {
            // check if label is a fake case label, i.e., equals to default label
            if(labels[i - min] == dflt) {
                continue;
            }
            count++;
        }
        if(dflt != null) {
            count++;
        }
        features.getMisc().add("CASE" + count);
        features.getInstructions().add(Features.InstType.SWITCH);
    }

    @Override
    public void visitIincInsn(int varIndex, int increment) {
        super.visitIincInsn(varIndex, increment);
        features.getConstants().add(increment);
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        super.visitJumpInsn(opcode, label);
        switch (opcode) {
            case Opcodes.IF_ICMPLT -> features.getInstructions().add(Features.InstType.BRLT);
            case Opcodes.IF_ICMPLE -> features.getInstructions().add(Features.InstType.BRLE);
            case Opcodes.IF_ICMPGT -> features.getInstructions().add(Features.InstType.BRGT);
            case Opcodes.IF_ICMPGE -> features.getInstructions().add(Features.InstType.BRGE);
        }
    }

}
