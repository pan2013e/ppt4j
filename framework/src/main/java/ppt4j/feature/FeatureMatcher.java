package ppt4j.feature;

@SuppressWarnings("unused")
public interface FeatureMatcher {

    String getAlgorithm();

    @SuppressWarnings({"SwitchStatementWithTooFewBranches", "EnhancedSwitchMigration"})
    static FeatureMatcher get(String algorithm) {
        switch (algorithm) {
            case "jaccard":
                return new JaccardMatcher();
            default:
                throw new IllegalArgumentException(
                        "Unknown algorithm: " + algorithm);
        }
    }

    static FeatureMatcher get() {
        return get("jaccard");
    }

    double match(Features f1, Features f2);

    default boolean isMatch(Features f1, Features f2, double threshold) {
        return match(f1, f2) >= threshold;
    }

}
