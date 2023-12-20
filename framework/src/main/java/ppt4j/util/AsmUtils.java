package ppt4j.util;

import ppt4j.feature.Features;
import lombok.extern.log4j.Log4j;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

@Log4j
public class AsmUtils {

    public static boolean hasLineNumberInfo(MethodNode m) {
        for (AbstractInsnNode node : m.instructions) {
            if (node instanceof LineNumberNode) {
                return true;
            }
        }
        return false;
    }

    public static boolean isReturnOp(int opcode) {
        return opcode == Opcodes.IRETURN || opcode == Opcodes.LRETURN ||
                opcode == Opcodes.FRETURN || opcode == Opcodes.DRETURN ||
                opcode == Opcodes.ARETURN || opcode == Opcodes.RETURN;
    }

    public static boolean isConstOp(int opcode) {
        return opcode == Opcodes.ICONST_0 || opcode == Opcodes.ICONST_1 ||
                opcode == Opcodes.ICONST_2 || opcode == Opcodes.ICONST_3 ||
                opcode == Opcodes.ICONST_4 || opcode == Opcodes.ICONST_5 ||
                opcode == Opcodes.ICONST_M1 || opcode == Opcodes.LCONST_0 ||
                opcode == Opcodes.LCONST_1 || opcode == Opcodes.FCONST_0 ||
                opcode == Opcodes.FCONST_1 || opcode == Opcodes.FCONST_2 ||
                opcode == Opcodes.DCONST_0 || opcode == Opcodes.DCONST_1;
    }

    public static Object getConst(int opcode) {
        return switch (opcode) {
            case Opcodes.ICONST_0 -> 0;
            case Opcodes.ICONST_1 -> 1;
            case Opcodes.ICONST_2 -> 2;
            case Opcodes.ICONST_3 -> 3;
            case Opcodes.ICONST_4 -> 4;
            case Opcodes.ICONST_5 -> 5;
            case Opcodes.ICONST_M1 -> -1;
            case Opcodes.LCONST_0 -> 0L;
            case Opcodes.LCONST_1 -> 1L;
            case Opcodes.FCONST_0 -> 0.0f;
            case Opcodes.FCONST_1 -> 1.0f;
            case Opcodes.FCONST_2 -> 2.0f;
            case Opcodes.DCONST_0 -> 0.0;
            case Opcodes.DCONST_1 -> 1.0;
            default -> throw new IllegalArgumentException("Not a const opcode: " + opcode);
        };
    }

    public static String getArrayType(int operand) {
        return switch (operand) {
            case Opcodes.T_BOOLEAN -> "boolean[]";
            case Opcodes.T_CHAR -> "char[]";
            case Opcodes.T_FLOAT -> "float[]";
            case Opcodes.T_DOUBLE -> "double[]";
            case Opcodes.T_BYTE -> "byte[]";
            case Opcodes.T_SHORT -> "short[]";
            case Opcodes.T_INT -> "int[]";
            case Opcodes.T_LONG -> "long[]";
            default -> throw new IllegalArgumentException("Not an array type: " + operand);
        };
    }

    public static boolean isNative(int access) {
        return (access & Opcodes.ACC_NATIVE) != 0;
    }

    public static boolean isAbstract(int access) {
        return (access & Opcodes.ACC_ABSTRACT) != 0;
    }

    public static boolean isShiftOp(int opcode) {
        return opcode == Opcodes.ISHL || opcode == Opcodes.ISHR ||
                opcode == Opcodes.IUSHR || opcode == Opcodes.LSHL ||
                opcode == Opcodes.LSHR || opcode == Opcodes.LUSHR;
    }

    public static Features.InstType getShiftType(int opcode) {
        return switch (opcode) {
            case Opcodes.ISHL, Opcodes.LSHL -> Features.InstType.SHL;
            case Opcodes.ISHR, Opcodes.LSHR -> Features.InstType.SHR;
            case Opcodes.IUSHR, Opcodes.LUSHR -> Features.InstType.USHR;
            default -> throw new IllegalArgumentException("Not a shift opcode: " + opcode);
        };
    }
}
