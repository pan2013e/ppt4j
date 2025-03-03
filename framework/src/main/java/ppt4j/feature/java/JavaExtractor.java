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

    /**
     * Creates and returns a new instance of JavaExtractor with default values.
     * This method is used to create an instance of JavaExtractor when no specific parameters are needed.
     *
     * @return a new instance of JavaExtractor with default values
     */
    public static JavaExtractor nil() {
        // Return a new instance of JavaExtractor with default values
        return new JavaExtractor();
    }

    /**
     * This method returns the source type of the Features class, which is always JAVA.
     * @return The source type of the Features class (always JAVA).
     */
    @Override
    public Features.SourceType getSourceType() {
        // Return the source type as JAVA
        return Features.SourceType.JAVA;
    }

    /**
     * Parses the root element by iterating through its fields, elements, nested types, and inner classes.
     * For each nested type that is a class, it creates a JavaExtractor object and adds it to the innerClasses map.
     * For each anonymous inner class, it creates a JavaExtractor object and adds it to the innerClasses map.
     * Finally, it recursively calls parse method on all inner classes and marks the root element as parsed.
     */
    @Override
    public void parse() {
        if (isParsed) {
            return;
        }
        // Parse fields of the root element
        root.getFields().forEach(this::parseField);
        
        // Parse elements of the root element using LineFilter
        root.getElements(new LineFilter()).forEach(this::parseLine);
        
        // Parse nested types
        root.getNestedTypes().forEach(ty -> {
            if (ty instanceof CtClass _class) {

                JavaExtractor ex = new JavaExtractor(_class);
                innerClasses.put(ex.getClassName(), ex);
            }
        });
        
        // Parse anonymous inner classes
        root.getElements(new AnonymousClassFilter()).forEach(ty -> {
            CtClass<?> _class = ty.getAnonymousClass();
            JavaExtractor ex = new JavaExtractor(_class);
            innerClasses.put(ex.getClassName(), ex);
        });
        
        // Recursively parse inner classes
        innerClasses.values().forEach(JavaExtractor::parse);
        
        // Mark the root element as parsed
        isParsed = true;
    }

    /**
     * Returns a Collection of JavaExtractor instances representing the inner classes contained in this JavaExtractor.
     * 
     * @return a Collection of JavaExtractor instances representing the inner classes
     */
    public Collection<JavaExtractor> getInnerClass() {
        // Return the values of the innerClasses map, which holds the inner classes
        return innerClasses.values();
    }

    /**
     * Checks if the given line number is a valid line.
     * 
     * @param line the line number to be checked
     * @return true if the line number is valid, false otherwise
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isValidLine(int line) {
        // Check if the validLines set contains the given line number
        return validLines.contains(line);
    }

    /**
     * Retrieves the logical line number corresponding to the given physical line number.
     * If the mapping does not exist, returns -1.
     *
     * @param line the physical line number
     * @return the logical line number, or -1 if the mapping does not exist
     */
    public int getLogicalLine(int line) {
        // Check if the mapping for the physical line number exists
        if(!splitLinesToLogical.containsKey(line)) {
            return -1; // Return -1 if mapping does not exist
        }
        return splitLinesToLogical.get(line); // Return the logical line number
    }

    /**
     * Parses a given CtField and analyzes its assignment to extract Java features.
     * If the field has no position information, it is skipped. Otherwise, the method
     * analyzes the assignment expression using constant propagation analysis. If the
     * assignment is not a literal, it extracts the field access information and
     * adds it to the JavaFeatures object. If the assignment is a literal, the
     * field's fully qualified name and the literal value are stored in LibraryConstants.
     * 
     * @param field the CtField to be parsed
     */
    private <T> void parseField(CtField<T> field) {
            // Check if the field has position information
            if(field.getPosition().equals(SourcePosition.NOPOSITION)) {
                return;
            }
            CtFieldReference<T> fieldRef = field.getReference();
            CtTypeReference<T> fieldTypeRef = fieldRef.getType();
            CtExpression<T> assignment = field.getAssignment();
            
            // Perform constant propagation analysis on the assignment expression
            ConstPropAnalysis<?> analysis = new ConstPropAnalysis<>(assignment);
            analysis.analyze();
            
            if(!analysis.isLiteral()) {
                // Extract field access information
                String fieldAccess = StringUtils.convertQualifiedName(fieldRef.getQualifiedName(), root.getQualifiedName())
                        + ":" + StringUtils.convertToDescriptor(fieldTypeRef.getQualifiedName());
                JavaFeatures features;
                
                // If the assignment is a statement, add field access to JavaFeatures
                if(assignment instanceof CtStatement stmt) {
                    features = new JavaFeatures(root.getQualifiedName(), stmt);
                    features.getFieldAccesses().add(fieldAccess);
                    putSplitLines(features);
                    validLines.add(stmt.getPosition().getLine());
                    putFeatures(stmt.getPosition().getLine(), features);
                }
            } else {
                // Store the field's fully qualified name and the literal value in LibraryConstants
                LibraryConstants.put(fieldRef.getQualifiedName(), analysis.getLiteral().getValue());
            }
        }

    /**
     * Parses a given CtStatement to extract Java features and add them to the appropriate data structures.
     * If the position of the statement is NOPOSITION, the method returns immediately.
     * Otherwise, it creates a JavaFeatures object based on the qualified name of the root and the given statement.
     * It checks if the text of the features is "do", "else", or "try" and returns if true.
     * Otherwise, it adds the split lines of the features, the line number of the statement, and the features themselves to the respective data structures.
     * If an IllegalStateException is thrown during the process, it is caught and ignored.
     * 
     * @param stmt the CtStatement to be parsed
     */
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
            putSplitLines(features); // Add split lines of the features
            validLines.add(stmt.getPosition().getLine()); // Add line number of the statement to validLines
            putFeatures(stmt.getPosition().getLine(), features); // Add features to the data structure
        } catch (IllegalStateException e) {
            // comment in a single line
        }
    }

    /**
     * Takes a JavaFeatures object and puts split lines into a hashmap mapping split lines to a base line.
     *
     * @param features the JavaFeatures object containing split lines
     */
    private void putSplitLines(JavaFeatures features) {
        // Get the list of split lines from the JavaFeatures object
        List<Integer> splitLines = features.getSplitLines();
        
        // Get the base line from the first element in the split lines list
        int baseLine = splitLines.get(0);
        
        // Iterate through each split line and put it in the hashmap with the base line
        for (Integer line : splitLines) {
            splitLinesToLogical.put(line, baseLine);
        }
    }

    /**
     * Inserts or updates JavaFeatures for a specific line in the featuresMap.
     * If the line already exists in the map, it merges the new features with the existing ones.
     * If the line does not exist, it adds the new features to the map.
     *
     * @param line the line number where the features should be inserted or updated
     * @param features the JavaFeatures to be inserted or merged
     */
    private void putFeatures(int line, JavaFeatures features) {
        if(featuresMap.containsKey(line)) { // Check if the line already exists in the map
            JavaFeatures old = (JavaFeatures) featuresMap.get(line); // Get the existing features for the line
            old.merge(features); // Merge the new features with the existing ones
        } else {
            featuresMap.put(line, features); // Add the new features to the map since the line does not exist
        }
    }

}
