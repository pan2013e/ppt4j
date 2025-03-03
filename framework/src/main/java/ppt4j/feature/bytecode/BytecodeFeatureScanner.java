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

    /**
     * Visits a method instruction and adds the method invocation signature to the features.
     *
     * @param opcode the opcode of the instruction
     * @param owner the internal name of the class containing the method
     * @param name the name of the method
     * @param descriptor the descriptor of the method
     * @param isInterface true if the owner is an interface
     */
    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        
        // Skip certain method invocations
        if(name.equals("toString") || name.equals("valueOf")
                || name.equals("append") || name.equals("longValue") ||
                name.matches(".*\\$\\d+")) {
            return;
        }
        
        // Skip constructor calls for specific classes
        if(name.equals("<init>")) {
            if (owner.equals("java/lang/Object") ||
                    owner.equals("java/lang/StringBuilder")) {
                return;
            }
        }
        
        // Construct the method invocation signature and add it to the features
        String signature = owner + "." + name + ":" + descriptor;
        signature = signature.substring(0, signature.lastIndexOf(")") + 1);
        features.getMethodInvocations().add(signature);
    }

    /**
     * Visits a field instruction in the bytecode and extracts information about the field access.
     * If the field name starts with "$SwitchMap$", it is added to the list of field accesses in the features.
     * If the owner is "java/lang/Void", the method returns without adding the field access to maintain consistency with Spoon.
     * If the field name matches a specific pattern or the owner contains a "$", the method also returns without adding the field access.
     * Otherwise, the owner, name, and descriptor of the field access are combined and added to the list of field accesses in the features.
     * 
     * @param opcode the opcode of the field instruction
     * @param owner the internal name of the field's owner class
     * @param name the name of the field
     * @param descriptor the descriptor of the field
     */
    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        super.visitFieldInsn(opcode, owner, name, descriptor);
        
        // Check if the field name starts with "$SwitchMap$"
        if(name.startsWith("$SwitchMap$")) {
            features.getFieldAccesses().add(name + ":" + descriptor);
            return;
        }
        
        // Ignore java/lang/Void.class access to maintain consistency
        if(owner.equals("java/lang/Void")) {
            return;
        }
        
        // Ignore fields with specific patterns or containing "$"
        if(name.matches(".*\\$\\d+") || owner.contains("$")) {
            return;
        }
        
        // Add the field access to the list of field accesses in the features
        features.getFieldAccesses().add(owner + "." + name + ":" + descriptor);
    }

    /**
     * Visits a LDC instruction in the bytecode and adds the constant value to the list of constants in the features object.
     *
     * @param value the constant value being visited
     */
    @Override
    public void visitLdcInsn(Object value) {
        // Call the superclass method to visit the LDC instruction
        super.visitLdcInsn(value);
        
        // Check if the value is an instance of Type class
        if (value.getClass().getSimpleName().equals("Type")) {
            // If the value is an instance of Type class, do not add it to the list of constants
            return;
        }
        
        // Add the constant value to the list of constants in the features object
        features.getConstants().add(value);
    }

    /**
     * Visits a zero operand instruction and adds relevant features to the Features object based on the opcode.
     * Features added include instruction type (RETURN, MONITOR, THROW), constants, field accesses, and shift type.
     *
     * @param opcode the opcode of the instruction to visit
     */
    @Override
    public void visitInsn(int opcode) {
        super.visitInsn(opcode); // Calls the super class method with the given opcode
        if(AsmUtils.isReturnOp(opcode)) { // Check if the opcode represents a return instruction
            features.getInstructions().add(Features.InstType.RETURN); // Add RETURN instruction type feature
            return; // Exit method
        }
        if (AsmUtils.isConstOp(opcode)) { // Check if the opcode represents a constant instruction
            features.getConstants().add(AsmUtils.getConst(opcode)); // Add constant to the Features object
            return; // Exit method
        }
        if (opcode == Opcodes.MONITORENTER) { // Check if the opcode represents a MONITORENTER instruction
            features.getInstructions().add(Features.InstType.MONITOR); // Add MONITOR instruction type feature
            return; // Exit method
        }
        if (opcode == Opcodes.ATHROW) { // Check if the opcode represents an ATHROW instruction
            features.getInstructions().add(Features.InstType.THROW); // Add THROW instruction type feature
            return; // Exit method
        }
        if (opcode == Opcodes.ARRAYLENGTH) { // Check if the opcode represents an ARRAYLENGTH instruction
            features.getFieldAccesses().add("int.length:I"); // Add field access feature for array length
            return; // Exit method
        }
        if (AsmUtils.isShiftOp(opcode)) { // Check if the opcode represents a shift instruction
            features.getInstructions().add(AsmUtils.getShiftType(opcode)); // Add shift type feature
            return; // Exit method
        }
    }

    /**
     * Visits an instruction with an integer operand. If the opcode is BIPUSH or SIPUSH, adds the operand to the constants list in the features object.
     * If the opcode is NEWARRAY, adds the type of array creation to the objCreations list in the features object.
     * 
     * @param opcode the opcode of the instruction
     * @param operand the integer operand of the instruction
     */
    @Override
    public void visitIntInsn(int opcode, int operand) {
        super.visitIntInsn(opcode, operand);
        
        // If opcode is BIPUSH or SIPUSH, add operand to constants list
        if(opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
            features.getConstants().add(operand);
            return;
        }
        
        // If opcode is NEWARRAY, add array type to objCreations list
        if(opcode == Opcodes.NEWARRAY) {
            features.getObjCreations().add(AsmUtils.getArrayType(operand));
            return;
        }
    }

    /**
     * Visits a type instruction in the bytecode and adds relevant information to the features.
     * If the opcode is ANEWARRAY, it adds the type followed by "[]" to the objCreations list in the features.
     * If the opcode is INSTANCEOF, it adds the INSTANCEOF instruction type to the instructions list in the features.
     * 
     * @param opcode the type instruction opcode
     * @param type the type of the instruction
     */
    @Override
    public void visitTypeInsn(int opcode, String type) {
        super.visitTypeInsn(opcode, type); // Call superclass method
    
        // Check if the opcode is ANEWARRAY
        if (opcode == Opcodes.ANEWARRAY) {
            // Add the type followed by "[]" to the objCreations list
            features.getObjCreations().add(type + "[]");
        }
    
        // Check if the opcode is INSTANCEOF
        if (opcode == Opcodes.INSTANCEOF) {
            // Add the INSTANCEOF instruction type to the instructions list
            features.getInstructions().add(Features.InstType.INSTANCEOF);
            return; // Exit the method
        }
    }

    /**
     * Visits a LOOKUPSWITCH instruction. This method adds the instruction type SWITCH to the features list.
     * It also adds a miscellaneous item with the count of cases in the switch statement.
     *
     * @param dflt the default label
     * @param keys the array of switch case keys
     * @param labels the array of labels to jump to for each case
     */
    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        super.visitLookupSwitchInsn(dflt, keys, labels);
        // Add instruction type SWITCH to features list
        features.getInstructions().add(Features.InstType.SWITCH);
        int count = keys.length;
        // If there is a default label, increment count
        if(dflt != null) {
            count++;
        }
        // Add a miscellaneous item with the count of cases in the switch statement
        features.getMisc().add("CASE" + count);
    }

    /**
     * Visits a TABLESWITCH instruction. This method calculates the number of case labels
     * in the switch statement excluding the default label, and adds the relevant features
     * to the instruction set.
     *
     * @param min the minimum key value
     * @param max the maximum key value
     * @param dflt the default label
     * @param labels the case labels
     */
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

    /**
     * Visits an IINC instruction. This method calls the super class's visitIincInsn method
     * to handle the instruction, and then adds the increment value to the constants list
     * in the features object.
     *
     * @param varIndex the index of the local variable to increment
     * @param increment the value by which to increment the local variable
     */
    @Override
    public void visitIincInsn(int varIndex, int increment) {
        // Call super class method to handle the instruction
        super.visitIincInsn(varIndex, increment);
        
        // Add the increment value to the constants list
        features.getConstants().add(increment);
    }

    /**
     * Visits a jump instruction and adds the corresponding instruction type to the features list based on the opcode.
     * 
     * @param opcode the opcode of the jump instruction
     * @param label the label to jump to
     */
    @Override
    public void visitJumpInsn(int opcode, Label label) {
        super.visitJumpInsn(opcode, label); // calls the superclass method
    
        // switch statement to determine the instruction type based on the opcode
        switch (opcode) {
            case Opcodes.IF_ICMPLT -> features.getInstructions().add(Features.InstType.BRLT); // add BRGT instruction type
            case Opcodes.IF_ICMPLE -> features.getInstructions().add(Features.InstType.BRLE); // add BRLE instruction type
            case Opcodes.IF_ICMPGT -> features.getInstructions().add(Features.InstType.BRGT); // add BRGT instruction type
            case Opcodes.IF_ICMPGE -> features.getInstructions().add(Features.InstType.BRGE); // add BRGE instruction type
        }
    }

}
