package ppt4j.feature;

@SuppressWarnings("unused")
public interface FeatureMatcher {

    /**
     * Returns the algorithm used by the object.
     * 
     * @return the algorithm used by the object
     */
    String getAlgorithm();

    /**
     * Returns a FeatureMatcher based on the input algorithm.
     * 
     * @param algorithm the algorithm to determine which FeatureMatcher to return
     * @return a FeatureMatcher based on the specified algorithm
     * @throws IllegalArgumentException if the algorithm is unknown
     */
    @SuppressWarnings({"SwitchStatementWithTooFewBranches", "EnhancedSwitchMigration"})
    static FeatureMatcher get(String algorithm) {
        // Switch statement to determine which FeatureMatcher to return based on the algorithm
        switch (algorithm) {
            case "jaccard":
                return new JaccardMatcher();
            default:
                throw new IllegalArgumentException(
                        "Unknown algorithm: " + algorithm);
        }
    }

    /**
     * This method returns a FeatureMatcher object with the default similarity measure set to "jaccard".
     * 
     * @return a FeatureMatcher object with the default similarity measure set to "jaccard"
     */
    static FeatureMatcher get() {
        // Calls the overloaded get method with default similarity measure "jaccard"
        return get("jaccard");
    }

    /**
     * Calculates the similarity between two feature sets.
     * 
     * @param f1 the first set of features
     * @param f2 the second set of features
     * @return a double value representing the similarity between f1 and f2
     */
    double match(Features f1, Features f2);

    /**
     * Checks if the similarity score between two Features objects is greater than or equal to a specified threshold.
     * 
     * @param f1 the first Features object to compare
     * @param f2 the second Features object to compare
     * @param threshold the threshold value for the match to be considered successful
     * @return true if the similarity score is greater than or equal to the threshold, false otherwise
     */
    default boolean isMatch(Features f1, Features f2, double threshold) {
        return match(f1, f2) >= threshold;
    }

}
