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

    private boolean isPlatformSpecificField(CtElement e) {
        return e instanceof CtFieldRead<?> field &&
                field.getVariable().getDeclaringType()
                        .getQualifiedName().startsWith("java.io");
    }

    private boolean isJavaLibField(CtFieldRead<?> field) {
        return field.getVariable().getDeclaringType()
                .getQualifiedName().startsWith("java.") ||
                field.getVariable().getDeclaringType()
                        .getQualifiedName().startsWith("javax.");
    }

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

    public boolean isLiteral() {
        return result instanceof CtLiteral && ((CtLiteral<?>) result).getValue() != null;
    }

    public CtLiteral<?> getLiteral() {
        return (CtLiteral<?>) result;
    }

}
