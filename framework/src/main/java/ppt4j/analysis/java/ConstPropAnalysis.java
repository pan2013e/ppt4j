package ppt4j.analysis.java;

import ppt4j.analysis.AbstractAnalysis;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.ModifierKind;

import java.util.Set;

public class ConstPropAnalysis<R extends CtElement>
        implements AbstractAnalysis {

    private final R element;

    private R result;

    public ConstPropAnalysis(R element) {
        this.element = element;
    }

    /**
     * Checks if the given CtElement represents a platform-specific field by
     * verifying if it is an instance of CtFieldRead and if the declaring type
     * of the variable starts with "java.io".
     *
     * @param e the CtElement to check
     * @return true if the CtElement is a platform-specific field, false otherwise
     */
    private boolean isPlatformSpecificField(CtElement e) {
            // Check if the CtElement is an instance of CtFieldRead
            // and if the declaring type starts with "java.io"
            return e instanceof CtFieldRead<?> field &&
                    field.getVariable().getDeclaringType()
                            .getQualifiedName().startsWith("java.io");
    }

    /**
     * Checks if the given field is a Java library field by examining its declaring type's qualified name.
     * A field is considered a Java library field if its declaring type's qualified name starts with "java." or "javax.".
     * 
     * @param field the field to be checked
     * @return true if the field is a Java library field, false otherwise
     */
    private boolean isJavaLibField(CtFieldRead<?> field) {
        // Check if the declaring type's qualified name starts with "java."
        // or "javax." to determine if the field is a Java library field
        return field.getVariable().getDeclaringType()
                .getQualifiedName().startsWith("java.") ||
                field.getVariable().getDeclaringType()
                        .getQualifiedName().startsWith("javax.");
    }

    /**
     * This method performs constant propagation analysis on the given element. It evaluates the element using a ConstEvaluator
     * and checks various conditions based on the type of element (CtFieldRead) and its modifiers. If the element is a CtFieldRead
     * representing a specific key ("int#length") or has certain modifiers (static without final or non-Java library field with no modifiers),
     * the element itself is marked as the result. If the key corresponds to a value in a predefined library, a new literal element
     * is created with that value and set as the result. If the element represents a platform-specific field, it is also marked as the result.
     * Otherwise, the element is evaluated using the ConstEvaluator and the result is set accordingly. The method returns an instance
     * of ConstPropAnalysis representing the result of the analysis.
     */
    @Override
    @SuppressWarnings("unchecked")
    public ConstPropAnalysis<R> analyze() {
        ConstEvaluator eval = new ConstEvaluator();
        if (element instanceof CtFieldRead<?> field) {
            String key = field.getVariable().getQualifiedName();
            if (key.equals("int#length")) {
                result = element;
                return this;
            }
            Set<ModifierKind> modifiers = field.getVariable().getModifiers();
            if (modifiers.contains(ModifierKind.STATIC) &&
                    !modifiers.contains(ModifierKind.FINAL)) {
                result = element;
                return this;
            }
            if (!isJavaLibField(field) && modifiers.isEmpty()) {
                result = element;
                return this;
            }
            CtClass<?> refClazz = element.getFactory().Class().get(field.getVariable().getDeclaringType().getQualifiedName());
            Object val = LibraryConstants.get(refClazz, key);
            if (val != null) {
                CtElement parent = field.getParent();
                result = (R) field.getFactory().Code().createLiteral(val);
                result.setParent(parent);
                return this;
            }
        }
        // Some fields in Java standard library, especially in java.io,
        // are platform-specific. javac does not propagate them, but
        // Spoon does. We change the behavior of Spoon to be consistent
        // with javac here.
        if(isPlatformSpecificField(element)) {
            result = element;
            return this;
        }
        result = eval.evaluate(element);
        return this;
    }

    /**
     * Checks if the result is a literal value that is not null.
     *
     * @return true if the result is a literal value that is not null, false otherwise
     */
    public boolean isLiteral() {
        // Check if the result is an instance of CtLiteral and its value is not null
        return result instanceof CtLiteral && ((CtLiteral<?>) result).getValue() != null;
    }

    /**
     * Retrieves the CtLiteral object from the result.
     * 
     * @return the CtLiteral object contained in the result
     */
    public CtLiteral<?> getLiteral() {
        // Casting the result object to CtLiteral and returning it
        return (CtLiteral<?>) result;
    }

}
