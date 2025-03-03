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

    /**
     * Visits a return statement in a CtElement and adds the instruction type "RETURN" to the features list.
     * This method overrides the visitCtReturn method in the parent class.
     * 
     * @param returnStatement the CtReturn statement to be visited
     */
    @Override
    public <R> void visitCtReturn(CtReturn<R> returnStatement) {
        // Add the instruction type "RETURN" to the features list
        features.getInstructions().add(Features.InstType.RETURN);
        
        // Call the superclass method to continue visiting the CtReturn statement
        super.visitCtReturn(returnStatement);
    }

    /**
     * Visits a CtSwitch element and adds features to a Features object based on the switch statement.
     * Adds instruction type SWITCH to the features list.
     * Scans annotations, expression, and exits the switch statement.
     * Checks the type of the selector and adds method invocations and field accesses accordingly.
     * Adds a miscellaneous feature with the number of cases in the switch statement.
     * 
     * @param switchStatement the CtSwitch element to visit
     */
    @Override
    public <S> void visitCtSwitch(CtSwitch<S> switchStatement) {
        features.getInstructions().add(Features.InstType.SWITCH); // Add SWITH instruction type
        this.enter(switchStatement);
        this.scan(CtRole.ANNOTATION, (Collection)switchStatement.getAnnotations()); // Scan annotations
        this.scan(CtRole.EXPRESSION, (CtElement)switchStatement.getSelector()); // Scan expression
        this.exit(switchStatement); // Exit switch statement
        CtExpression<?> selector = switchStatement.getSelector();
        String selectorType = selector.getType().getQualifiedName()
                .replace('.', '/');
        if(selectorType.equals("java/lang/String")) { // Check if selector type is String
            features.getMethodInvocations().add(
                    "java/lang/String.hashCode:()");
            features.getMethodInvocations().add(
                    "java/lang/String.equals:(Ljava/lang/String;)");
        } else if(!StringUtils.isPrimitive(selectorType)) { // Check if selector type is not a primitive
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

    /**
     * Visits a throw statement in the AST and adds the instruction type to the features list.
     * 
     * @param throwStatement the throw statement to visit
     */
    @Override
    public void visitCtThrow(CtThrow throwStatement) {
        // Add the instruction type THROW to the features list
        features.getInstructions().add(Features.InstType.THROW);
        
        // Call the super method to continue visiting the AST
        super.visitCtThrow(throwStatement);
    }

    /**
     * Visits a CtSynchronized element, adds MONITOR feature to the list of instructions,
     * and scans the expression within the synchronized block.
     * 
     * @param synchro the CtSynchronized element to visit
     */
    @Override
    public void visitCtSynchronized(CtSynchronized synchro) {
        // Add MONITOR feature to the list of instructions
        features.getInstructions().add(Features.InstType.MONITOR);
        
        // Scan the expression within the synchronized block
        this.scan(CtRole.EXPRESSION, (CtElement) synchro.getExpression());
    }

    /**
     * Visits a CtLambda element and adds a RETURN instruction to the features.
     * 
     * @param lambda the CtLambda element to visit
     */
    @Override
    public <T> void visitCtLambda(CtLambda<T> lambda) {
        // Add a RETURN instruction to the features
        features.getInstructions().add(Features.InstType.RETURN);
        
        // Continue visiting the CtLambda element
        super.visitCtLambda(lambda);
    }

    /**
     * Visits a CtInvocation element in the AST and extracts information about the method invocation.
     * If the method being invoked is one of the predefined methods (toString, valueOf, append, longValue),
     * it skips processing. It then retrieves the name of the method, its signature, return type, target,
     * declaring class type, and arguments. It checks if the method is called on a superclass and adjusts
     * the declaring class type accordingly. It then constructs a signature for the method invocation and
     * adds it to the features list. Special handling is applied for certain predefined method invocations.
     * Additionally, it keeps track of object creations, constants, and method invocations for feature extraction.
     *
     * @param invocation the CtInvocation element to visit
     */
    @Override
    public <T> void visitCtInvocation(CtInvocation<T> invocation) {
        super.visitCtInvocation(invocation);
        String name = invocation.getExecutable().getSimpleName();
        
        // Skip predefined methods
        if(name.equals("toString") || name.equals("valueOf")
                || name.equals("append") || name.equals("longValue")) {
            return;
        }
        
        String nameAndArgs = invocation.getExecutable().getSignature();
        CtTypeReference<?> retTy = invocation.getType();
        CtExpression<?> target = invocation.getTarget();
        CtTypeReference<?> declTy;
        
        // Handle superclass method calls
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
        
        // Handle return type and declaring class type
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

    /**
     * Visits a CtNewArray element and adds the qualified name of the type of the array
     * to the list of object creations in the features object.
     * 
     * @param newArray The CtNewArray element to visit
     */
    @Override
    public <T> void visitCtNewArray(CtNewArray<T> newArray) {
        super.visitCtNewArray(newArray); // Call super method to visit CtNewArray element
        
        // Add the qualified name of the type of the array to the list of object creations
        features.getObjCreations()
                .add(newArray.getType()
                        .getQualifiedName().replace(".", "/"));
    }

    /**
     * Visits a CtFieldRead node and customizes the field access based on certain conditions.
     * If the field being read is "class" and its type starts with "java.lang", it is ignored.
     * Otherwise, the field access is added to the features.
     * If the field being read is "length" of an array type, it is ignored.
     * Otherwise, a constant propagation analysis is performed to determine if the field access is a literal.
     * If it is a literal, the literal value is added to the features.
     * If the field access belongs to an inner class, it is ignored.
     * Otherwise, the field access is added to the features.
     * 
     * @param fieldRead the CtFieldRead node to visit
     */
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

    /**
     * Visits a field write operation in a class. If the field being written to is an inner class field (contains '$' in its qualified name), it does nothing. Otherwise, it adds the customized qualified name of the field access to the list of field accesses in the features object.
     * 
     * @param fieldWrite the field write operation being visited
     */
    @Override
    public <T> void visitCtFieldWrite(CtFieldWrite<T> fieldWrite) {
        super.visitCtFieldWrite(fieldWrite);
        
        // Check if the field is an inner class
        if(fieldWrite.getVariable().getQualifiedName().contains("$")) {
            // inner class, do nothing
            return;
        }
        
        // Add customized qualified name of field access to list of field accesses
        features.getFieldAccesses().add(customizeQualifiedName(
            fieldWrite.getType(),
            fieldWrite.getVariable())
        );
    }

    /**
     * Casts the given object to the specified type if possible.
     *
     * @param o the object to be cast
     * @param type the type reference to which the object should be cast
     * @return the casted object or the original object if casting is not possible
     */
    public <T> Object castLiteral(Object o, CtTypeReference<T> type) {
        // If the object is null or already of type String, Character, or Boolean, return the object as is
        if(o == null || o instanceof String || o instanceof Character || o instanceof Boolean) {
            return o;
        }
        
        // Get the actual class of the specified type
        Class<?> clazz = type.getActualClass();
        try {
            // Check if the class is a primitive type and not char, Character, boolean, or Boolean
            String cls = clazz.getSimpleName();
            if(StringUtils.isPrimitive(cls) && !cls.equals("char")
                    && !cls.equals("Character") && !cls.equals("boolean") && !cls.equals("Boolean")) {
                // Get the original class of the object
                Class<?> originalClazz = o.getClass();
                // If the original class is different from the specified class, attempt to cast the object
                if (originalClazz != clazz) {
                    String name = clazz.getSimpleName().toLowerCase() + "Value";
                    // Get the method for casting based on the class name
                    Method cast = originalClazz.getMethod(name);
                    // Invoke the casting method and return the result
                    return cast.invoke(o);
                }
            }
        } catch (Exception e) {
            // Log a warning if an exception occurs during casting
            log.warn(e);
        }
        
        // Return the original object if casting is not successful
        return o;
    }


    /**
     * This method adds a literal value to the list of constants in the features object.
     * It first retrieves the value of the literal and its parent element. 
     * Then it iterates through the parent elements until it finds a non-conditional parent.
     * If the parent is a local variable or an assignment, it casts the literal value to the appropriate type.
     * Finally, it adds the casted value to the list of constants based on the type of the literal.
     */
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
                o = castLiteral(o, local.getType()); // cast the literal value based on the local variable's type
            } else if(parent instanceof CtAssignment assignment) {
                o = castLiteral(o, assignment.getAssigned().getType()); // cast the literal value based on the assignment's type
            }
            if(literal.getValue() == null) {
                return;
            }
            if(literal.getType() == null) {
                features.getConstants().add(o);
    
            } else if(literal.getType().toString().equals("char")) {
                features.getConstants().add((int) ((char) o)); // cast the char value to int and add to constants
            } else if(literal.getType().toString().equals("boolean")) {
                if((boolean) o) {
                    features.getConstants().add(1); // add 1 for true boolean value
                } else {
                    features.getConstants().add(0); // add 0 for false boolean value
                }
            } else {
                features.getConstants().add(o); // add the value to constants for other types
            }
        }

    /**
     * This method overrides the visitCtLiteral method from the parent class.
     * It first calls the super method to visit the CtLiteral node, then adds the CtLiteral node to a collection of literals.
     *
     * @param literal the CtLiteral node to visit
     */
    @Override
    public <T> void visitCtLiteral(CtLiteral<T> literal) {
        // Call the super method to visit the CtLiteral node
        super.visitCtLiteral(literal);
        
        // Add the CtLiteral node to a collection of literals
        addLiteral(literal);
    }

    /**
     * Visits a CtIf element, scans annotations, condition, and analyzes the condition for constant propagation.
     * If the condition is a non-literal binary operator, adds corresponding instructions to the features.
     *
     * @param ifElement the CtIf element to visit
     */
    @Override
    public void visitCtIf(CtIf ifElement) {
        // Enter the if element
        this.enter(ifElement);
        
        // Scan annotations of the if element
        this.scan(CtRole.ANNOTATION, (Collection)ifElement.getAnnotations());
        
        // Scan the condition of the if element
        this.scan(CtRole.CONDITION, (CtElement)ifElement.getCondition());
        
        // Exit the if element
        this.exit(ifElement);
        
        // Get the condition of the if element
        CtExpression<Boolean> condition = ifElement.getCondition();
        
        // Perform constant propagation analysis on the condition
        ConstPropAnalysis analysis = new ConstPropAnalysis(condition);
        analysis.analyze();
        
        // Check if the condition is a non-literal binary operator
        if(!analysis.isLiteral() && condition instanceof CtBinaryOperator op) {
            // Add corresponding instructions based on the operator kind
            switch (op.getKind()) {
                case LT -> features.getInstructions().add(Features.InstType.BRGE);
                case LE -> features.getInstructions().add(Features.InstType.BRGT);
                case GT -> features.getInstructions().add(Features.InstType.BRLE);
                case GE -> features.getInstructions().add(Features.InstType.BRLT);
            }
        }
    }

    /**
     * Visits a CtFor element, adds the type of instruction as LOOP to the features, 
     * enters the element, scans its annotations, for initialization, expression, and updates,
     * then exits the element.
     */
    @Override
    public void visitCtFor(CtFor forLoop) {
        features.getInstructions().add(Features.InstType.LOOP); // Add LOOP type of instruction to features
        this.enter(forLoop); // Enter the for loop element
        this.scan(CtRole.ANNOTATION, (Collection)forLoop.getAnnotations()); // Scan annotations of the for loop
        this.scan(CtRole.FOR_INIT, (Collection)forLoop.getForInit()); // Scan initialization of the for loop
        this.scan(CtRole.EXPRESSION, (CtElement)forLoop.getExpression()); // Scan expression of the for loop
        this.scan(CtRole.FOR_UPDATE, (Collection)forLoop.getForUpdate()); // Scan updates of the for loop
        this.exit(forLoop); // Exit the for loop element
    }

    /**
     * Visits a CtForEach element, adding a LOOP instruction to the features list,
     * then scans the annotations, foreach variable, and expression of the CtForEach element.
     * This method is called when visiting a CtForEach element during the AST scanning process.
     *
     * @param foreach the CtForEach element to visit
     */
    @Override
    public void visitCtForEach(CtForEach foreach) {
        // Add LOOP instruction to features list
        features.getInstructions().add(Features.InstType.LOOP);
        
        // Enter the CtForEach element
        this.enter(foreach);
        
        // Scan annotations of CtForEach element
        this.scan(CtRole.ANNOTATION, (Collection)foreach.getAnnotations());
        
        // Scan foreach variable of CtForEach element
        this.scan(CtRole.FOREACH_VARIABLE, (CtElement)foreach.getVariable());
        
        // Scan expression of CtForEach element
        this.scan(CtRole.EXPRESSION, (CtElement)foreach.getExpression());
        
        // Exit the CtForEach element
        this.exit(foreach);
    }

    /**
     * Visits a CtWhile element in the AST and performs the following actions:
     * - Adds the Features.InstType.LOOP instruction to the list of instructions in features
     * - Enters the whileLoop element
     * - Scans the annotations of the whileLoop element
     * - Scans the looping expression of the whileLoop element
     * - Exits the whileLoop element
     */
    @Override
    public void visitCtWhile(CtWhile whileLoop) {
        features.getInstructions().add(Features.InstType.LOOP); // Add LOOP instruction to features
        this.enter(whileLoop); // Enter the whileLoop element
        this.scan(CtRole.ANNOTATION, (Collection)whileLoop.getAnnotations()); // Scan annotations of whileLoop
        this.scan(CtRole.EXPRESSION, (CtElement)whileLoop.getLoopingExpression()); // Scan looping expression of whileLoop
        this.exit(whileLoop); // Exit the whileLoop element
    }

    /**
     * Visits a CtBinaryOperator node and performs analysis on the operands.
     * If the operator is an INSTANCEOF operator, adds Features.InstType.INSTANCEOF to the list of instructions.
     * If the left operand is a CtLiteral and the right operand is a CtFieldRead, performs constant propagation analysis.
     * If the left operand is a String literal and the analysis results in a literal, creates a new CtLiteral with concatenated values and adds it to the list of literals.
     * If the operator is not a literal, analyzes it using ConstPropAnalysis and adds the resulting literal to the list of literals if applicable.
     * Otherwise, adds the appropriate instruction type to the list of instructions based on the operator kind.
     * Calls the superclass method to continue visiting the CtBinaryOperator node.
     */
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

    /**
     * Visits a CtUnaryOperator and performs constant propagation analysis.
     * If the operator is a literal, adds the literal value to the features.
     * If the operator is a pre/post increment or decrement, adds the corresponding constant value to the features.
     * Otherwise, delegates the visit to the superclass.
     *
     * @param operator the CtUnaryOperator to visit
     */
    @Override
    public <T> void visitCtUnaryOperator(final CtUnaryOperator<T> operator) {
        // Perform constant propagation analysis
        ConstPropAnalysis analysis = new ConstPropAnalysis(operator);
        analysis.analyze();
        
        // If the operator is a literal, add the literal value to the features
        if(analysis.isLiteral()) {
            addLiteral(analysis.getLiteral());
        } else {
            // If the operator is a pre/post increment or decrement, add the corresponding constant value to the features
            if(operator.getKind() == UnaryOperatorKind.POSTDEC
                    || operator.getKind() == UnaryOperatorKind.POSTINC
                    || operator.getKind() == UnaryOperatorKind.PREDEC
                    || operator.getKind() == UnaryOperatorKind.PREINC) {
                features.getConstants().add(castLiteral(1, operator.getType()));
            }
            // Delegate the visit to the superclass
            super.visitCtUnaryOperator(operator);
        }
    }

    /**
     * Visits a constructor call in the AST and extracts the method signature for the constructor.
     * The method first gets the class name of the constructor call, replaces '.' with '/' in the class name,
     * gets the arguments of the constructor call, and constructs a method signature using the class name 
     * and argument types. The method signature is added to the list of method invocations in the features.
     * 
     * @param ctConstructorCall the constructor call to visit
     */
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

    /**
     * Customizes the qualified name of a field by converting the field type and field name to descriptors
     * and concatenating them with a colon separator.
     * 
     * @param fieldTypeRef The reference to the type of the field
     * @param fieldRef The reference to the field itself
     * @return The customized qualified name of the field in the format "fieldName:typeDescriptor"
     */
    private String customizeQualifiedName(CtTypeReference<?> fieldTypeRef, CtFieldReference<?> fieldRef) {
        // Convert the field type's qualified name to a descriptor
        String typeDesc = StringUtils.convertToDescriptor(fieldTypeRef.getQualifiedName());
        
        // Convert the field's qualified name to a custom name using the class name as a feature
        String fieldName = StringUtils.convertQualifiedName(fieldRef.getQualifiedName(), features.getClassName());
        
        // Concatenate the customized field name and type descriptor with a colon separator
        return fieldName + ":" + typeDesc;
    }

}
