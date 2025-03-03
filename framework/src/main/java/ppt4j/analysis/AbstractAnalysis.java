package ppt4j.analysis;

@SuppressWarnings("UnusedReturnValue")
@FunctionalInterface
public interface AbstractAnalysis {

    /**
     * This method is responsible for performing an analysis on some data and returning the result.
     * The specific analysis to be performed is dictated by the concrete implementation of this method in subclasses.
     *
     * @return The result of the analysis.
     */
    AbstractAnalysis analyze();

}
