package ppt4j.feature.java;

import ppt4j.analysis.java.ConstPropAnalysis;
import ppt4j.feature.Features;
import ppt4j.util.StringUtils;
import lombok.NonNull;
import lombok.extern.log4j.Log4j;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;

import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Log4j
@SuppressWarnings({"rawtypes", "RedundantCast", "unchecked"})
final class JavaFeatureScanner
        extends CtScanner implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Features features;

    JavaFeatureScanner(@NonNull Features features) {
        this.features = features;
    }

    @Override
    public <R> void visitCtReturn(CtReturn<R> returnStatement) {
        features.getInstructions().add(Features.InstType.RETURN);
        super.visitCtReturn(returnStatement);
    }

    @Override
    public <S> void visitCtSwitch(CtSwitch<S> switchStatement) {
        features.getInstructions().add(Features.InstType.SWITCH);
        this.enter(switchStatement);
        this.scan(CtRole.ANNOTATION, (Collection)switchStatement.getAnnotations());
        this.scan(CtRole.EXPRESSION, (CtElement)switchStatement.getSelector());
        this.exit(switchStatement);
        CtExpression<?> selector = switchStatement.getSelector();
        String selectorType = selector.getType().getQualifiedName()
                .replace('.', '/');
        if(selectorType.equals("java/lang/String")) {
            features.getMethodInvocations().add(
                    "java/lang/String.hashCode:()");
            features.getMethodInvocations().add(
                    "java/lang/String.equals:(Ljava/lang/String;)");
        } else if(!StringUtils.isPrimitive(selectorType)) {
            String switchMapField = "$SwitchMap$" +
                    selectorType.replace('/', '$');
            features.getFieldAccesses().add(
                    switchMapField + ":[I");
            features.getMethodInvocations().add(
                    selectorType + ".ordinal:()");
        }
        List<CtCase<? super S>> cases = switchStatement.getCases();
        features.getMisc().add("CASE" + cases.size());
    }

    @Override
    public void visitCtThrow(CtThrow throwStatement) {
        features.getInstructions().add(Features.InstType.THROW);
        super.visitCtThrow(throwStatement);
    }

    @Override
    public void visitCtSynchronized(CtSynchronized synchro) {
        features.getInstructions().add(Features.InstType.MONITOR);
        this.scan(CtRole.EXPRESSION, (CtElement) synchro.getExpression());
    }

    @Override
    public <T> void visitCtLambda(CtLambda<T> lambda) {
        features.getInstructions().add(Features.InstType.RETURN);
        super.visitCtLambda(lambda);
    }

    @Override
    public <T> void visitCtInvocation(CtInvocation<T> invocation) {
        super.visitCtInvocation(invocation);
        String name = invocation.getExecutable().getSimpleName();
        if(name.equals("toString") || name.equals("valueOf")
                || name.equals("append") || name.equals("longValue")) {
            return;
        }
        String nameAndArgs = invocation.getExecutable().getSignature();
        CtTypeReference<?> retTy = invocation.getType();
        CtExpression<?> target = invocation.getTarget();
        CtTypeReference<?> declTy;
        if(target == null || target.getType() == null || target.getType().toString().equals("void")) {
            declTy = invocation.getExecutable().getDeclaringType();
            // When calling methods not overriden, but inherited from super class,
            // Spoon will use the actual class name of the special method,
            // while javac just calls the virtual method in current class.
            // Here we fix the behaviour of Spoon to be consistent with javac
            CtClass<?> current = invocation.getFactory().Class().get(features.getClassName());
            boolean existInCurrent = current.getMethods().stream().anyMatch(m -> m.getSimpleName().equals(name));
            CtTypeReference<?> superTy = current.getSuperclass();
            if(superTy != null) {
                CtClass<?> _super = current.getFactory().Class().get(superTy.getQualifiedName());
                if(_super != null) {
                    boolean existInSuper = _super.getMethods().stream().anyMatch(m -> m.getSimpleName().equals(name));
                    if(!existInCurrent && existInSuper) {
                        declTy = current.getReference();
                    }
                }
            }
        } else {
            if(target.getTypeCasts() != null && !target.getTypeCasts().isEmpty()) {
                declTy = target.getTypeCasts().get(0);
            } else {
                declTy = target.getType();
            }
        }
        if(retTy == null || declTy == null) {
            throw new IllegalStateException("Unknown return type or declaring class");
        } else {
            String className = declTy.getQualifiedName().replace(".", "/");
            String returnType = retTy.getQualifiedName();
            if(returnType.equals("?")) {
                returnType = "java/lang/Object";
            }
            List<CtExpression<?>> args = invocation.getArguments();
            if(className.contains("$")) {
                for (CtExpression<?> arg : args) {
                    if(arg instanceof CtThisAccess<?> _this) {
                        String _thisType = _this.getType().getQualifiedName().replace(".", "/");
                        if(!_thisType.equals(className)) {
                            // the inner class method access outer class THIS
                            // javac will generate a synthetic method here
                            // we do not add access$xxx method to methodInvocations
                            return;
                        }
                    }
                }
            }
            StringBuilder sb = new StringBuilder();
            sb.append(name).append('(');
            String[] origArgs = Objects.requireNonNull(StringUtils.substringBetween(nameAndArgs, '(', ')'))
                    .split(",");
            int origArgsCount = origArgs.length;
            if(origArgs[0].equals("")) {
                origArgsCount = 0;
            }
            int argsCount = args.size();
            if(argsCount != origArgsCount) {
                assert origArgsCount > 0;
                features.getObjCreations().add(
                        origArgs[origArgsCount - 1].replace(".", "/")
                );
            }
            int length = Math.min(origArgsCount, argsCount);
            for(int i = 0; i < length; i++) {
                CtExpression<?> arg = args.get(i);
                List<CtTypeReference<?>> casts = arg.getTypeCasts();
                String type;
                if(i == length - 1 && argsCount > origArgsCount) {
                    type = origArgs[i].replace(".", "/");
                } else if(arg.getType().toString().equals("<nulltype>")) {
                    type = "null";
                } else {
                    if(casts.size() > 0) {
                        type = casts.get(0).getQualifiedName().replace(".", "/");
                    } else {
                        type = arg.getType().getQualifiedName().replace(".", "/");
                    }
                    if(StringUtils.isPrimitive(type)) {
                        if(StringUtils.isPrimitive(origArgs[i])) {
                            type = origArgs[i];
                        }
                        if(origArgs[i].equals("java.lang.Object")) {
                            type = StringUtils.toWrapperType(type);
                        }
                    }
                }
                sb.append(type);
                if(i != length - 1) {
                    sb.append(',');
                }
            }
            if(argsCount < origArgsCount) {
                sb.append(",").append(origArgs[origArgsCount - 1].replace(".", "/"));
            }
            sb.append(')');
            String signature = className + "." +
                    StringUtils.convertMethodSignature(sb.toString(), returnType);
            signature = signature.substring(0, signature.lastIndexOf(")") + 1);
            if(className.equals("java/lang/String") && name.equals("format")) {
                signature = "java/lang/String.format:(Ljava/lang/String;[Ljava/lang/Object;)";
                features.getObjCreations().add("java/lang/Object[]");
            }
            if(className.equals("java/lang/Class") && name.equals("equals")) {
                signature = "java/lang/Object.equals:(Ljava/lang/Class;)";
            }
            if (className.equals("org/springframework/util/Assert") && name.equals("isTrue")) {
                features.getConstants().add(0);
                features.getConstants().add(1);
            }
            features.getMethodInvocations().add(signature);
        }
    }

    @Override
    public <T> void visitCtNewArray(CtNewArray<T> newArray) {
        super.visitCtNewArray(newArray);
        features.getObjCreations()
                .add(newArray.getType()
                        .getQualifiedName().replace(".","/"));
    }

    @Override
    public <T> void visitCtFieldRead(CtFieldRead<T> fieldRead) {
        super.visitCtFieldRead(fieldRead);
        if(fieldRead.getVariable().getSimpleName().equals("class")) {
            if(fieldRead.getType().getQualifiedName().startsWith("java.lang")) {
                return;
            }
            features.getFieldAccesses().add(customizeQualifiedName(
                    fieldRead.getType(),
                    fieldRead.getVariable())
            );
            return;
        }
        if(fieldRead.getVariable().getType().getSimpleName().equals("length")) {
            if(fieldRead.getTarget().getType().toString().endsWith("[]")) {
                return;
            }
        }
        ConstPropAnalysis analysis = new ConstPropAnalysis(fieldRead);
        analysis.analyze();
        if(analysis.isLiteral()) {
            addLiteral(analysis.getLiteral());
        } else {
            if(fieldRead.getVariable().getQualifiedName().contains("$")) {
                // inner class
                return;
            }
            features.getFieldAccesses().add(customizeQualifiedName(
                    fieldRead.getType(),
                    fieldRead.getVariable())
            );
        }
    }

    @Override
    public <T> void visitCtFieldWrite(CtFieldWrite<T> fieldWrite) {
        super.visitCtFieldWrite(fieldWrite);
        if(fieldWrite.getVariable().getQualifiedName().contains("$")) {
            // inner class
            return;
        }
        features.getFieldAccesses().add(customizeQualifiedName(
                fieldWrite.getType(),
                fieldWrite.getVariable())
        );
    }

    public <T> Object castLiteral(Object o, CtTypeReference<T> type) {
        if(o == null || o instanceof String || o instanceof Character || o instanceof Boolean) {
            return o;
        }
        Class<?> clazz = type.getActualClass();
        try {
            String cls = clazz.getSimpleName();
            if(StringUtils.isPrimitive(cls) && !cls.equals("char")
                    && !cls.equals("Character") && !cls.equals("boolean") && !cls.equals("Boolean")) {
                Class<?> originalClazz = o.getClass();
                if (originalClazz != clazz) {
                    String name = clazz.getSimpleName().toLowerCase() + "Value";
                    Method cast = originalClazz.getMethod(name);
                    return cast.invoke(o);
                }
            }
        } catch (Exception e) {
            log.warn(e);
        }
        return o;
    }

    private <T> void addLiteral(CtLiteral<T> literal) {
        Object o = literal.getValue();
        CtElement parent = literal.getParent();
        while (parent != null) {
            if (parent instanceof CtConditional) {
                parent = parent.getParent();
            } else {
                break;
            }
        }
        if (parent instanceof CtLocalVariable local) {
            o = castLiteral(o, local.getType());
        } else if(parent instanceof CtAssignment assignment) {
            o = castLiteral(o, assignment.getAssigned().getType());
        }
        if(literal.getValue() == null) {
            return;
        }
        if(literal.getType() == null) {
            features.getConstants().add(o);

        } else if(literal.getType().toString().equals("char")) {
            features.getConstants().add((int) ((char) o));
        } else if(literal.getType().toString().equals("boolean")) {
            if((boolean) o) {
                features.getConstants().add(1);
            } else {
                features.getConstants().add(0);
            }
        } else {
            features.getConstants().add(o);
        }
    }

    @Override
    public <T> void visitCtLiteral(CtLiteral<T> literal) {
        super.visitCtLiteral(literal);
        addLiteral(literal);
    }

    @Override
    public void visitCtIf(CtIf ifElement) {
        this.enter(ifElement);
        this.scan(CtRole.ANNOTATION, (Collection)ifElement.getAnnotations());
        this.scan(CtRole.CONDITION, (CtElement)ifElement.getCondition());
        this.exit(ifElement);
        CtExpression<Boolean> condition = ifElement.getCondition();
        ConstPropAnalysis analysis = new ConstPropAnalysis(condition);
        analysis.analyze();
        if(!analysis.isLiteral() && condition instanceof CtBinaryOperator op) {
            switch (op.getKind()) {
                case LT -> features.getInstructions().add(Features.InstType.BRGE);
                case LE -> features.getInstructions().add(Features.InstType.BRGT);
                case GT -> features.getInstructions().add(Features.InstType.BRLE);
                case GE -> features.getInstructions().add(Features.InstType.BRLT);
            }
        }
    }

    @Override
    public void visitCtFor(CtFor forLoop) {
        features.getInstructions().add(Features.InstType.LOOP);
        this.enter(forLoop);
        this.scan(CtRole.ANNOTATION, (Collection)forLoop.getAnnotations());
        this.scan(CtRole.FOR_INIT, (Collection)forLoop.getForInit());
        this.scan(CtRole.EXPRESSION, (CtElement)forLoop.getExpression());
        this.scan(CtRole.FOR_UPDATE, (Collection)forLoop.getForUpdate());
        this.exit(forLoop);
    }

    @Override
    public void visitCtForEach(CtForEach foreach) {
        features.getInstructions().add(Features.InstType.LOOP);
        this.enter(foreach);
        this.scan(CtRole.ANNOTATION, (Collection)foreach.getAnnotations());
        this.scan(CtRole.FOREACH_VARIABLE, (CtElement)foreach.getVariable());
        this.scan(CtRole.EXPRESSION, (CtElement)foreach.getExpression());
        this.exit(foreach);
    }

    @Override
    public void visitCtWhile(CtWhile whileLoop) {
        features.getInstructions().add(Features.InstType.LOOP);
        this.enter(whileLoop);
        this.scan(CtRole.ANNOTATION, (Collection)whileLoop.getAnnotations());
        this.scan(CtRole.EXPRESSION,
                (CtElement)whileLoop.getLoopingExpression());
        this.exit(whileLoop);
    }

    @Override
    public <T> void visitCtBinaryOperator(final CtBinaryOperator<T> operator) {
        if (operator.getKind() == BinaryOperatorKind.INSTANCEOF) {
            features.getInstructions().add(Features.InstType.INSTANCEOF);
        }
        if (operator.getLeftHandOperand() instanceof CtLiteral<?> literal &&
                operator.getRightHandOperand() instanceof CtFieldRead<?> field) {
            ConstPropAnalysis _analysis = new ConstPropAnalysis(field);
            _analysis.analyze();
            if (literal.getValue() instanceof String str && _analysis.isLiteral()) {
                CtLiteral<?> _lit = operator.getFactory().Code()
                        .createLiteral(str + _analysis.getLiteral().getValue());
                _lit.setParent(operator.getParent());
                addLiteral(_lit);
                return;
            }
        }
        ConstPropAnalysis analysis = new ConstPropAnalysis(operator);
        analysis.analyze();
        if (analysis.isLiteral()) {
            addLiteral(analysis.getLiteral());
        } else {
            switch (operator.getKind()) {
                case SL -> features.getInstructions().add(Features.InstType.SHL);
                case SR -> features.getInstructions().add(Features.InstType.SHR);
                case USR -> features.getInstructions().add(Features.InstType.USHR);
            }
            super.visitCtBinaryOperator(operator);
        }
    }

    @Override
    public <T> void visitCtUnaryOperator(final CtUnaryOperator<T> operator) {
        ConstPropAnalysis analysis = new ConstPropAnalysis(operator);
        analysis.analyze();
        if(analysis.isLiteral()) {
            addLiteral(analysis.getLiteral());
        } else {
            if(operator.getKind() == UnaryOperatorKind.POSTDEC
                    || operator.getKind() == UnaryOperatorKind.POSTINC
                    || operator.getKind() == UnaryOperatorKind.PREDEC
                    || operator.getKind() == UnaryOperatorKind.PREINC) {
                features.getConstants().add(castLiteral(1, operator.getType()));
            }
            super.visitCtUnaryOperator(operator);
        }
    }

    @Override
    public <T> void visitCtConstructorCall(
            final CtConstructorCall<T> ctConstructorCall) {
        super.visitCtConstructorCall(ctConstructorCall);
        String className = ctConstructorCall.getType().getQualifiedName();
        className = className.replace(".", "/");
        List<CtExpression<?>> args = ctConstructorCall.getArguments();
        StringBuilder sb = new StringBuilder();
        sb.append("<init>").append('(');
        for(int i = 0; i < args.size(); i++) {
            CtExpression<?> arg = args.get(i);
            List<CtTypeReference<?>> casts = arg.getTypeCasts();
            if(casts.size() > 0) {
                sb.append(casts.get(0).getQualifiedName().replace(".", "/"));
            } else {
                sb.append(arg.getType().getQualifiedName().replace(".", "/"));
            }
            if(i < args.size() - 1) {
                sb.append(',');
            }
        }
        sb.append(')');
        String signature = className + "." +
                StringUtils.convertMethodSignature(sb.toString(), "void");
        signature = signature.substring(0, signature.lastIndexOf(")") + 1);
        features.getMethodInvocations()
                .add(signature);
    }

    private String customizeQualifiedName(CtTypeReference<?> fieldTypeRef,
                                          CtFieldReference<?> fieldRef) {
        String typeDesc = StringUtils.convertToDescriptor(
                fieldTypeRef.getQualifiedName());
        String fieldName = StringUtils.convertQualifiedName(
                fieldRef.getQualifiedName(), features.getClassName());
        return fieldName + ":" + typeDesc;
    }

}
