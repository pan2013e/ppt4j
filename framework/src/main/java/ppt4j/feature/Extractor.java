package ppt4j.feature;

import java.io.Serializable;
import java.util.Map;

@SuppressWarnings("unused")
public interface Extractor extends Serializable {

    void parse();

    /**
     * This method prints out all the values in the features map.
     */
    default void print() {
        // Retrieve the values from the features map and iterate through each value
        getFeaturesMap().values().forEach(System.out::println);
    }

    /**
     * Prints the features associated with the specified line number.
     *
     * @param line the line number to print features for
     */
    default void print(int line) {
        // Get the key set of the features map, stream it, filter based on the line number,
        // map the features corresponding to the line number, and print each feature
        getFeaturesMap().keySet().stream()
                .filter(l -> l == line)
                .map(getFeaturesMap()::get)
                .forEach(System.out::println);
    }

    /**
     * Returns a map containing integer keys and Features values.
     * 
     * @return a map with integer keys corresponding to Features objects
     */
    Map<Integer, Features> getFeaturesMap();

    /**
     * This method returns the name of the class to which the object belongs.
     *
     * @return the name of the class
     */
    String getClassName();

    /**
     * This method retrieves the type of source for a particular feature.
     * 
     * @return the source type of the feature
     */
    Features.SourceType getSourceType();

}
