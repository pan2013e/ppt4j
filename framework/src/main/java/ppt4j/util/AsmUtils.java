package ppt4j.util;

import ppt4j.feature.Features;
import lombok.extern.log4j.Log4j;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

@Log4j
public class AsmUtils {

    /**
     * Checks if the given MethodNode contains any LineNumberNode instructions, which
     * provide information about the source code line numbers associated with the instructions.
     * 
     * @param m the MethodNode to check for LineNumberNode instructions
     * @return true if the MethodNode contains at least one LineNumberNode instruction, false otherwise
     */
    public static boolean hasLineNumberInfo(MethodNode m) {
        // Iterate over all instructions in the MethodNode
        for (AbstractInsnNode node : m.instructions) {
            // Check if the current instruction is a LineNumberNode
            if (node instanceof LineNumberNode) {
                return true;
            }
        }
        // No LineNumberNode instructions found
        return false;
    }

    /**
     * Checks if the given opcode corresponds to a return instruction in bytecode.
     * 
     * @param opcode the opcode to check
     * @return true if the opcode is a return instruction, false otherwise
     */
    public static boolean isReturnOp(int opcode) {
        // Check if the opcode corresponds to any of the return instructions
        return opcode == Opcodes.IRETURN || opcode == Opcodes.LRETURN ||
                opcode == Opcodes.FRETURN || opcode == Opcodes.DRETURN ||
                opcode == Opcodes.ARETURN || opcode == Opcodes.RETURN;
    }

    /**
     * Checks if the given opcode represents a constant operation in bytecode instructions.
     * Constant operations include loading the constants 0, 1, 2, 3, 4, 5, -1 for integers,
     * 0, 1 for longs, 0, 1, 2 for floats, and 0, 1 for doubles.
     *
     * @param opcode the opcode value to check
     * @return true if the opcode represents a constant operation, false otherwise
     */
    public static boolean isConstOp(int opcode) {
        return opcode == Opcodes.ICONST_0 || opcode == Opcodes.ICONST_1 ||
                opcode == Opcodes.ICONST_2 || opcode == Opcodes.ICONST_3 ||
                opcode == Opcodes.ICONST_4 || opcode == Opcodes.ICONST_5 ||
                opcode == Opcodes.ICONST_M1 || opcode == Opcodes.LCONST_0 ||
                opcode == Opcodes.LCONST_1 || opcode == Opcodes.FCONST_0 ||
                opcode == Opcodes.FCONST_1 || opcode == Opcodes.FCONST_2 ||
                opcode == Opcodes.DCONST_0 || opcode == Opcodes.DCONST_1;
    }

    /**
     * Returns the constant value associated with the given opcode.
     * 
     * @param opcode the opcode value
     * @return the constant value based on the opcode
     * @throws IllegalArgumentException if the opcode does not correspond to a known constant
     */
    public static Object getConst(int opcode) {
        return switch (opcode) {
            case Opcodes.ICONST_0 -> 0; // return 0 for ICONST_0 opcode
            case Opcodes.ICONST_1 -> 1; // return 1 for ICONST_1 opcode
            case Opcodes.ICONST_2 -> 2; // return 2 for ICONST_2 opcode
            case Opcodes.ICONST_3 -> 3; // return 3 for ICONST_3 opcode
            case Opcodes.ICONST_4 -> 4; // return 4 for ICONST_4 opcode
            case Opcodes.ICONST_5 -> 5; // return 5 for ICONST_5 opcode
            case Opcodes.ICONST_M1 -> -1; // return -1 for ICONST_M1 opcode
            case Opcodes.LCONST_0 -> 0L; // return 0L for LCONST_0 opcode
            case Opcodes.LCONST_1 -> 1L; // return 1L for LCONST_1 opcode
            case Opcodes.FCONST_0 -> 0.0f; // return 0.0f for FCONST_0 opcode
            case Opcodes.FCONST_1 -> 1.0f; // return 1.0f for FCONST_1 opcode
            case Opcodes.FCONST_2 -> 2.0f; // return 2.0f for FCONST_2 opcode
            case Opcodes.DCONST_0 -> 0.0; // return 0.0 for DCONST_0 opcode
            case Opcodes.DCONST_1 -> 1.0; // return 1.0 for DCONST_1 opcode
            default -> throw new IllegalArgumentException("Not a const opcode: " + opcode);
        };
    }

    /**
     * Returns the string representation of an array type based on the given operand.
     * 
     * @param operand the operand representing the array type
     * @return the string representation of the array type
     * @throws IllegalArgumentException if the operand does not represent an array type
     */
    public static String getArrayType(int operand) {
            return switch (operand) {
                case Opcodes.T_BOOLEAN -> "boolean[]"; // return boolean array type
                case Opcodes.T_CHAR -> "char[]"; // return char array type
                case Opcodes.T_FLOAT -> "float[]"; // return float array type
                case Opcodes.T_DOUBLE -> "double[]"; // return double array type
                case Opcodes.T_BYTE -> "byte[]"; // return byte array type
                case Opcodes.T_SHORT -> "short[]"; // return short array type
                case Opcodes.T_INT -> "int[]"; // return int array type
                case Opcodes.T_LONG -> "long[]"; // return long array type
                default -> throw new IllegalArgumentException("Not an array type: " + operand); // throw exception for invalid operand
            };
        }

    /**
     * This method checks if the given access flags include the ACC_NATIVE flag, which indicates that a method is native.
     * @param access the access flags to check
     * @return true if the ACC_NATIVE flag is present in the access flags, false otherwise
     */
    public static boolean isNative(int access) {
        // Bitwise AND operation to check if the ACC_NATIVE flag is present in the access flags
        return (access & Opcodes.ACC_NATIVE) != 0;
    }

    /**
     * Checks if the provided access modifier integer represents an abstract type.
     * 
     * @param access the access modifier integer to check
     * @return true if the access modifier represents an abstract type, false otherwise
     */
    public static boolean isAbstract(int access) {
        // Bitwise AND operation to check if the ACC_ABSTRACT flag is set in the access modifier
        return (access & Opcodes.ACC_ABSTRACT) != 0;
    }

    /**
     * Checks if the given opcode represents a shift operation.
     *
     * @param opcode the opcode to check
     * @return true if the opcode is a shift operation, false otherwise
     */
    public static boolean isShiftOp(int opcode) {
        // Check if the opcode matches any of the shift operation opcodes
        return opcode == Opcodes.ISHL || opcode == Opcodes.ISHR ||
                opcode == Opcodes.IUSHR || opcode == Opcodes.LSHL ||
                opcode == Opcodes.LSHR || opcode == Opcodes.LUSHR;
    }

    /**
     * This method takes an opcode as input and returns the corresponding shift type from the Features.InstType enum.
     * 
     * @param opcode the opcode representing the type of shift operation
     * @return the shift type corresponding to the given opcode
     * @throws IllegalArgumentException if the opcode is not a valid shift opcode
     */
    public static Features.InstType getShiftType(int opcode) {
        return switch (opcode) {
            // Check if the opcode is for left shift operation and return SHL
            case Opcodes.ISHL, Opcodes.LSHL -> Features.InstType.SHL;
            // Check if the opcode is for right arithmetic shift operation and return SHR
            case Opcodes.ISHR, Opcodes.LSHR -> Features.InstType.SHR;
            // Check if the opcode is for right logical shift operation and return USHR
            case Opcodes.IUSHR, Opcodes.LUSHR -> Features.InstType.USHR;
            // Throw an exception if the opcode does not match any valid shift opcode
            default -> throw new IllegalArgumentException("Not a shift opcode: " + opcode);
        };
    }
}
