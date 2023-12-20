package ppt4j.analysis.java;

import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.support.reflect.eval.VisitorPartialEvaluator;

public class ConstEvaluator extends VisitorPartialEvaluator {

    CtElement result;

    void setResult(CtElement element) {
        this.result = element;
    }

    @Override
    public <T> void visitCtFieldRead(CtFieldRead<T> fieldRead) {
        this.visitFieldAccess(fieldRead);
    }

    @Override
    public <T> void visitCtFieldWrite(CtFieldWrite<T> fieldWrite) {
        this.visitFieldAccess(fieldWrite);
    }

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
