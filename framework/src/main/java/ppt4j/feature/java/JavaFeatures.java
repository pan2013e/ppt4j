package ppt4j.feature.java;

import ppt4j.feature.Extractor;
import ppt4j.feature.Features;
import ppt4j.util.StringUtils;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j;
import spoon.reflect.code.CtStatement;

import java.util.ArrayList;
import java.util.List;

@Log4j
@Getter
@SuppressWarnings("unused")
public final class JavaFeatures extends Features {

    private final String text;

    private final List<Integer> splitLines = new ArrayList<>();

    JavaFeatures(@NonNull String className,
                 @NonNull CtStatement line) {
        super(SourceType.JAVA, className, line.getPosition().getLine());
        text = parseLine(line);
        try {
            JavaFeatureScanner scanner = new JavaFeatureScanner(this);
            line.accept(scanner);
        } catch (Exception e) {
            log.warn(text);
            log.warn(e);
        }
    }

    private JavaFeatures() {
        super(SourceType.JAVA, "", 0);
        text = "";
    }

    /**
     * This method creates and returns a new instance of JavaFeatures with no initial data.
     * 
     * @return a new instance of JavaFeatures with no initial data
     */
    public static JavaFeatures empty() {
        // Create a new instance of JavaFeatures
        return new JavaFeatures();
    }

    /**
     * Merges the Java features from the provided JavaFeatures object into this JavaFeatures object.
     * 
     * @param other the JavaFeatures object to merge with this JavaFeatures object
     */
    public void merge(@NonNull JavaFeatures other) {
        // Merge constants from other JavaFeatures object
        getConstants().addAll(other.getConstants());
        // Merge field accesses from other JavaFeatures object
        getFieldAccesses().addAll(other.getFieldAccesses());
        // Merge method invocations from other JavaFeatures object
        getMethodInvocations().addAll(other.getMethodInvocations());
        // Merge object creations from other JavaFeatures object
        getObjCreations().addAll(other.getObjCreations());
        // Merge instructions from other JavaFeatures object
        getInstructions().addAll(other.getInstructions());
        // Merge miscellaneous Java features from other JavaFeatures object
        getMisc().addAll(other.getMisc());
    }

    /**
     * Returns a formatted string representation of the current object, including the line number and text content, followed by the default string representation.
     * 
     * @return The formatted string representation of the object.
     */
    @Override
    public String toString() {
        // Format the string to include line number and text content
        String formattedString = String.format("Line %4d: %s\n", lineNo, text);
        
        // Append the default string representation
        return formattedString + super.toString();
    }

    /**
     * Parses a given CtStatement line to extract the actual code line without any comments.
     *
     * @param line the CtStatement line to parse
     * @return the parsed code line without comments
     */
    private String parseLine(@NonNull CtStatement line) {
        String[] codeLines = line.toString().lines().toArray(String[]::new);
        int base = line.getPosition().getLine();
        int i = 0;
        
        // Skip over any comments at the beginning of the code lines
        for (; i < codeLines.length; i++) {
            if(!StringUtils.isJavaComment(codeLines[i])) {
                break;
            } else {
                base--;
            }
        }
        
        List<Integer> splitLines = new ArrayList<>();
        
        // Identify the line(s) with actual Java code
        for (; i < codeLines.length; i++) {
            if(StringUtils.isJavaCode(codeLines[i])) {
                splitLines.add(base + i);
            }
        }
        
        // Validate the parsed code lines
        if(splitLines.isEmpty()) {
            throw new IllegalStateException();
        }
        
        String codeLine = StringUtils.trimJavaCodeLine(codeLines[splitLines.get(0) - base]);
        
        // Validate the trimmed code line
        if (codeLine.isEmpty()) {
            throw new IllegalStateException();
        }
        
        return codeLine;
    }

}
