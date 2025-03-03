package ppt4j.feature.java;

import lombok.Getter;
import lombok.extern.log4j.Log4j;
import ppt4j.analysis.java.ConstPropAnalysis;
import ppt4j.analysis.java.LibraryConstants;
import ppt4j.feature.Extractor;
import ppt4j.feature.Features;
import ppt4j.util.StringUtils;
import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtStatement;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtField;
import spoon.reflect.factory.ClassFactory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.LineFilter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.util.*;


@SuppressWarnings("unused")
@Log4j
public final class JavaExtractor implements Extractor {

    @Serial
    private static final long serialVersionUID = 1L;

    private transient final CtClass<?> root;

    @Getter
    private final Map<String, JavaExtractor>
            innerClasses = new HashMap<>();

    @Getter
    private final Map<Integer, Features> featuresMap = new TreeMap<>();

    private boolean isParsed = false;

    @Getter
    private final String className;

    @Getter
    private final String superClassName;

    private final Set<Integer> validLines = new HashSet<>();

    private final Map<Integer, Integer> splitLinesToLogical = new TreeMap<>();

    public JavaExtractor(InputStream inputStream)
            throws IOException {
        this(Launcher.parseClass(new String(inputStream.readAllBytes())));
    }

    public JavaExtractor(String path)
            throws IOException {
        this(new FileInputStream(path));
    }

    public JavaExtractor(CtClass<?> clazz) {
        String superClassName1;
        this.root = clazz;
        this.className = clazz.getQualifiedName().replace('.', '/');
        CtTypeReference<?> superClass = clazz.getSuperclass();
        if(superClass != null) {
            superClassName1 = superClass.getQualifiedName().replace('.', '/');
        } else {
            superClassName1 = null;
        }
        this.superClassName = superClassName1;
    }

    public JavaExtractor(ClassFactory factory, String className) {
        this(factory.get(className));
    }

    private JavaExtractor() {
        root = null;
        className = "fake";
        superClassName = "fake";
    }

    public static JavaExtractor nil() {
        return new JavaExtractor();
    }

    @Override
    public Features.SourceType getSourceType() {
        return Features.SourceType.JAVA;
    }

    @Override
    public void parse() {
        if (isParsed) {
            return;
        }
        root.getFields().forEach(this::parseField);
        root.getElements(new LineFilter()).forEach(this::parseLine);
        root.getNestedTypes().forEach(ty -> {
            if (ty instanceof CtClass _class) {

                JavaExtractor ex = new JavaExtractor(_class);
                innerClasses.put(ex.getClassName(), ex);
            }
        });
        root.getElements(new AnonymousClassFilter()).forEach(ty -> {
            CtClass<?> _class = ty.getAnonymousClass();
            JavaExtractor ex = new JavaExtractor(_class);
            innerClasses.put(ex.getClassName(), ex);
        });
        innerClasses.values().forEach(JavaExtractor::parse);
        isParsed = true;
    }

    public Collection<JavaExtractor> getInnerClass() {
        return innerClasses.values();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isValidLine(int line) {
        return validLines.contains(line);
    }

    public int getLogicalLine(int line) {
        if(!splitLinesToLogical.containsKey(line)) {
            return -1;
        }
        return splitLinesToLogical.get(line);
    }

    private <T> void parseField(CtField<T> field) {
        if(field.getPosition().equals(SourcePosition.NOPOSITION)) {
            return;
        }
        CtFieldReference<T> fieldRef = field.getReference();
        CtTypeReference<T> fieldTypeRef = fieldRef.getType();
        CtExpression<T> assignment = field.getAssignment();
        ConstPropAnalysis<?> analysis = new ConstPropAnalysis<>(assignment);
        analysis.analyze();
        if(!analysis.isLiteral()) {
            String fieldAccess =
                    StringUtils.convertQualifiedName(fieldRef.getQualifiedName(), root.getQualifiedName())
                    + ":" + StringUtils.convertToDescriptor(fieldTypeRef.getQualifiedName());
            JavaFeatures features;
            if(assignment instanceof CtStatement stmt) {
                features = new JavaFeatures(root.getQualifiedName(), stmt);
                features.getFieldAccesses().add(fieldAccess);
                putSplitLines(features);
                validLines.add(stmt.getPosition().getLine());
                putFeatures(stmt.getPosition().getLine(), features);
            }
        } else {
            LibraryConstants.put(fieldRef.getQualifiedName(), analysis.getLiteral().getValue());
        }
    }

    private void parseLine(CtStatement stmt) {
        if(stmt.getPosition().equals(SourcePosition.NOPOSITION)) {
            return;
        }
        try {
            JavaFeatures features = new JavaFeatures(root.getQualifiedName(), stmt);
            String text = features.getText();
            if(text.equals("do") || text.equals("else") || text.equals("try")) {
                return;
            }
            putSplitLines(features);
            validLines.add(stmt.getPosition().getLine());
            putFeatures(stmt.getPosition().getLine(), features);
        } catch (IllegalStateException e) {
            // comment in a single line
        }
    }

    private void putSplitLines(JavaFeatures features) {
        List<Integer> splitLines = features.getSplitLines();
        int baseLine = splitLines.get(0);
        for (Integer line : splitLines) {
            splitLinesToLogical.put(line, baseLine);
        }
    }

    private void putFeatures(int line, JavaFeatures features) {
        if(featuresMap.containsKey(line)) {
            JavaFeatures old = (JavaFeatures) featuresMap.get(line);
            old.merge(features);
        } else {
            featuresMap.put(line, features);
        }
    }

}
