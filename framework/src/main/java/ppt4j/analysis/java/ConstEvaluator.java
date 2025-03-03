package ppt4j.analysis.java;

import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.support.reflect.eval.VisitorPartialEvaluator;

public class ConstEvaluator extends VisitorPartialEvaluator {

    CtElement result;

    /**
     * Sets the result of a computation to the given CtElement.
     *
     * @param element the CtElement to set as the result
     */
    void setResult(CtElement element) {
        // Set the result to the provided CtElement
        this.result = element;
    }

    /**
     * This method is called when visiting a field read operation in a CtFieldRead object.
     * It calls the visitFieldAccess method with the CtFieldRead object passed as a parameter.
     * 
     * @param fieldRead the CtFieldRead object representing the field read operation
     */
    @Override
    public <T> void visitCtFieldRead(CtFieldRead<T> fieldRead) {
        // Call the visitFieldAccess method with the CtFieldRead object
        this.visitFieldAccess(fieldRead);
    }

    /**
     * This method overrides the visitCtFieldWrite method in the CtScanner class. It visits a CtFieldWrite node
     * and invokes the visitFieldAccess method with the same CtFieldWrite node.
     * 
     * @param fieldWrite the CtFieldWrite node to visit
     */
    @Override
    public <T> void visitCtFieldWrite(CtFieldWrite<T> fieldWrite) {
        // Visit the CtFieldWrite node by calling the visitFieldAccess method
        this.visitFieldAccess(fieldWrite);
    }

    /**
     * Visits a field access expression and evaluates it if necessary.
     * If the field access is from the Java library or is a class literal, the method clones the field access and returns.
     * If the field access is for an array length, the method returns the size of the array as a literal.
     * If the field access is for a final field from a non-enum type, the method evaluates the default expression of the field.
     * Otherwise, the method clones the field access.
     *
     * @param fieldAccess the field access expression to visit
     */
    private <T> void visitFieldAccess(CtFieldAccess<T> fieldAccess) {
            if (fieldAccess.getVariable().getQualifiedName().startsWith("java.")) {
                // Don't evaluate fields from the Java library
                setResult(fieldAccess.clone());
                return;
            }
            if ("class".equals(fieldAccess.getVariable().getSimpleName())) {
                // Don't evaluate class literals
                setResult(fieldAccess.clone());
                return;
            }
    
            if ("length".equals(fieldAccess.getVariable().getSimpleName())) {
                CtExpression<?> target = fieldAccess.getTarget();
                if (target instanceof CtNewArray<?> newArr) {
                    CtLiteral<Number> literal = fieldAccess.getFactory().createLiteral(newArr.getElements().size());
                    this.setResult(literal);
                    return;
                }
            }
    
            String fieldName = fieldAccess.getVariable().getSimpleName();
            CtType<?> typeDeclaration = fieldAccess.getVariable().getDeclaringType().getTypeDeclaration();
            CtField<?> f;
            if (typeDeclaration != null) {
                f = typeDeclaration.getField(fieldName);
            } else {
                f = fieldAccess.getVariable().getFieldDeclaration();
            }
    
            if (f != null && f.getModifiers().contains(ModifierKind.FINAL) && !fieldAccess.getVariable().getDeclaringType().isSubtypeOf(fieldAccess.getFactory().Type().ENUM)) {
                this.setResult(this.evaluate(f.getDefaultExpression()));
            } else {
                this.setResult(fieldAccess.clone());
            }
        }
}
