package ppt4j.analysis.patch;

import ppt4j.feature.Extractor;
import ppt4j.feature.Features;
import ppt4j.feature.bytecode.BytecodeExtractor;
import ppt4j.feature.java.JavaExtractor;
import org.apache.commons.lang3.tuple.Pair;

import static ppt4j.feature.Features.SourceType;

@SuppressWarnings("unused")
public interface CrossMatcher {

    /**
     * Returns the type of key used in the data source.
     *
     * @return the type of key
     */
    SourceType getKeyType();

    /**
     * Returns the source type of the value.
     * This method is responsible for retrieving the source type of the value.
     * 
     * @return the source type of the value
     */
    public SourceType getValueType();

    /**
     * Checks if the given index is matched with a specific condition.
     *
     * @param index the index to be checked
     * @return true if the index is matched, false otherwise
     */
    boolean isMatched(int index);

    /**
     * This method retrieves the score at the specified index in the list of scores.
     *
     * @param index the index of the score to retrieve
     * @return the score at the specified index
     */
    double getScore(int index);

    /**
     * This method returns a Pair containing the starting and ending index of a matched range
     * based on the given index in a data structure.
     *
     * @param index the index to search for a matched range
     * @return a Pair containing the starting and ending index of the matched range
     */
    Pair<Integer, Integer> getMatchedRange(int index);

    /**
     * Retrieves the features at the specified index in the query results.
     *
     * @param index the index of the features to retrieve
     * @return the features at the specified index
     */
    public Features query(int index);

    /**
     * Returns a CrossMatcher based on the JavaExtractor and Extractor provided, with the option to specify whether to consider different types.
     *
     * @param k the JavaExtractor to use
     * @param v the Extractor to use
     * @param diffType a boolean indicating whether to consider different types
     * @return a CrossMatcher based on the JavaExtractor and Extractor provided
     * @throws IllegalArgumentException if the source type of Extractor v is not SourceType.BYTECODE
     */
    static CrossMatcher get(JavaExtractor k, Extractor v, boolean diffType) {
            // Check if the source type of Extractor v is SourceType.BYTECODE
            if (v.getSourceType() == SourceType.BYTECODE) {
                // Return a new J2BCMatcher with the provided JavaExtractor, BytecodeExtractor, and diffType
                return new J2BCMatcher(k, (BytecodeExtractor) v, diffType);
            } else {
                // Throw an exception if the source types do not match
                throw new IllegalArgumentException("Invalid source types");
            }
        }

}
