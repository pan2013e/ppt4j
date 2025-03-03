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

    public static JavaFeatures empty() {
        return new JavaFeatures();
    }

    public void merge(@NonNull JavaFeatures other) {
        getConstants().addAll(other.getConstants());
        getFieldAccesses().addAll(other.getFieldAccesses());
        getMethodInvocations().addAll(other.getMethodInvocations());
        getObjCreations().addAll(other.getObjCreations());
        getInstructions().addAll(other.getInstructions());
        getMisc().addAll(other.getMisc());
    }

    @Override
    public String toString() {
        return String.format("Line %4d: %s\n", lineNo, text) + super.toString();
    }

    private String parseLine(@NonNull CtStatement line) {
        String[] codeLines = line.toString().lines().toArray(String[]::new);
        int base = line.getPosition().getLine();
        int i = 0;
        for (; i < codeLines.length; i++) {
            if(!StringUtils.isJavaComment(codeLines[i])) {
                break;
            } else {
                base--;
            }
        }
        for (; i < codeLines.length; i++) {
            if(StringUtils.isJavaCode(codeLines[i])) {
                splitLines.add(base + i);
            }
        }
        if(splitLines.isEmpty()) {
            throw new IllegalStateException();
        }
        String codeLine = StringUtils.trimJavaCodeLine(codeLines[splitLines.get(0) - base]);
        if (codeLine.isEmpty()) {
            throw new IllegalStateException();
        }
        return codeLine;
    }

}
