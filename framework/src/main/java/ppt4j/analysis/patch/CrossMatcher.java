package ppt4j.analysis.patch;

import ppt4j.feature.Extractor;
import ppt4j.feature.Features;
import ppt4j.feature.bytecode.BytecodeExtractor;
import ppt4j.feature.java.JavaExtractor;
import org.apache.commons.lang3.tuple.Pair;

import static ppt4j.feature.Features.SourceType;

@SuppressWarnings("unused")
public interface CrossMatcher {

    SourceType getKeyType();

    SourceType getValueType();

    boolean isMatched(int index);

    double getScore(int index);

    Pair<Integer, Integer> getMatchedRange(int index);

    Features query(int index);

    static CrossMatcher get(JavaExtractor k, Extractor v, boolean diffType) {
        if (v.getSourceType() == SourceType.BYTECODE) {
            return new J2BCMatcher(k, (BytecodeExtractor) v, diffType);
        } else {
            throw new IllegalArgumentException("Invalid source types");
        }
    }

}
