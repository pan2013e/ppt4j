package ppt4j.util;

import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("unused")
public class SetUtils {

    /**
     * Returns a new Set containing the intersection of the elements in the two input Sets.
     * The intersection of two sets is the set of elements that are common to both sets.
     * 
     * @param <T> the type of elements in the Sets
     * @param set1 the first Set of elements
     * @param set2 the second Set of elements
     * @return a new Set containing the intersection of elements from set1 and set2
     */
    public static <T> Set<T> intersection(Set<T> set1, Set<T> set2) {
            // Create a new HashSet initialized with the elements of set1
            Set<T> intersection = new HashSet<>(set1);
            // Retain only the elements that are present in both set1 and set2
            intersection.retainAll(set2);
            // Return the resulting intersection Set
            return intersection;
        }

    /**
     * Returns the union of two sets, which is a set containing all the distinct elements from both input sets.
     * 
     * @param <T> the type of elements in the sets
     * @param set1 the first set
     * @param set2 the second set
     * @return a set containing all the distinct elements from both input sets
     */
    public static <T> Set<T> union(Set<T> set1, Set<T> set2) {
        // Create a new HashSet and initialize it with elements from set1
        Set<T> union = new HashSet<>(set1);
        
        // Add all elements from set2 to the union set
        union.addAll(set2);
        
        return union;
    }

    /**
     * Returns a new Set containing the elements that are present in set1 but not in set2.
     * 
     * @param <T> the type of elements in the sets
     * @param set1 the first set
     * @param set2 the second set
     * @return a Set containing the elements that are in set1 but not in set2
     */
    public static <T> Set<T> difference(Set<T> set1, Set<T> set2) {
            // Create a copy of set1
            Set<T> difference = new HashSet<>(set1);
            
            // Remove all elements from the copy that are present in set2
            difference.removeAll(set2);
            
            // Return the set containing the difference
            return difference;
        }

}
