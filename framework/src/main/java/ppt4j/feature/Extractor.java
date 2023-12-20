package ppt4j.feature;

import java.io.Serializable;
import java.util.Map;

@SuppressWarnings("unused")
public interface Extractor extends Serializable {

    void parse();

    default void print() {
        getFeaturesMap().values().forEach(System.out::println);
    }

    default void print(int line) {
        getFeaturesMap().keySet().stream()
                .filter(l -> l == line)
                .map(getFeaturesMap()::get)
                .forEach(System.out::println);
    }

    Map<Integer, Features> getFeaturesMap();

    String getClassName();

    Features.SourceType getSourceType();

}
