package ppt4j.feature;

import ppt4j.annotation.Property;
import ppt4j.util.StringUtils;
import lombok.Getter;
import lombok.NonNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Getter
public class Features implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum SourceType {
        JAVA, BYTECODE
    }

    @SuppressWarnings("SpellCheckingInspection")
    public enum InstType {
        THROW, MONITOR, SWITCH, INSTANCEOF, RETURN, LOOP,
        BRLT, BRLE, BRGT, BRGE, SHL, SHR, USHR
    }

    @Property("ppt4j.features.similarity.threshold")
    private static double SIM_THRESHOLD;

    @Property("ppt4j.features.similarity.algorithm")
    private static String SIM_ALGORITHM;

    protected final SourceType sourceType;

    protected String className;
    protected int lineNo;

    protected final Set<Object>      Constants           = new HashSet<>();
    protected final Set<String>      MethodInvocations   = new HashSet<>();
    protected final Set<String>      FieldAccesses       = new HashSet<>();
    protected final Set<String>      ObjCreations        = new HashSet<>();
    protected final Set<InstType>    Instructions        = new HashSet<>();
    protected final Set<String>      Misc                = new HashSet<>();

    protected Features(@NonNull SourceType sourceType,
                    @NonNull String className, int lineNo) {
        this.sourceType = sourceType;
        this.className = className;
        this.lineNo = lineNo;
    }

    /**
     * Checks if all the collections (Constants, MethodInvocations, FieldAccesses, ObjCreations,
     * Instructions, and Misc) are empty.
     * 
     * @return true if all collections are empty, false otherwise
     */
    public boolean isEmpty() {
            // Check if all collections are empty
            return Constants.isEmpty() &&
                   MethodInvocations.isEmpty() &&
                   FieldAccesses.isEmpty() &&
                   ObjCreations.isEmpty() &&
                   Instructions.isEmpty() &&
                   Misc.isEmpty();
    }

    /**
     * This method calculates the total size of various components including Constants, MethodInvocations, FieldAccesses,
     * ObjCreations, Instructions, and Misc. It then returns the sum of all of these sizes.
     */
    public int size() {
        // Return the total size by summing up the sizes of different components
        return Constants.size() +
               MethodInvocations.size() +
               FieldAccesses.size() +
               ObjCreations.size() +
               Instructions.size() +
               Misc.size();
    }

    /**
     * Returns a string representation of the object, including the sets of Constants, Method Invocations, 
     * Field Accesses, Object Creations, Instructions, and Misc. Each set is formatted using StringUtils.printSet
     * method and concatenated together in the final string.
     */
    @Override
    public String toString() {
        // Format each set of data using StringUtils.printSet method
        String constants = String.format("Constants: %s\n",
                StringUtils.printSet(Constants, true, true));
        String methodInvocations = String.format("Method Invocations: %s\n",
                StringUtils.printSet(MethodInvocations));
        String fieldAccesses = String.format("Field Accesses: %s\n",
                StringUtils.printSet(FieldAccesses));
        String objCreations = String.format("Object Creations: %s\n",
                StringUtils.printSet(ObjCreations));
        String instructions = String.format("Instructions: %s\n",
                StringUtils.printSet(Instructions));
        String misc = String.format("Misc: %s\n",
                StringUtils.printSet(Misc));
        
        // Concatenate all the formatted strings and return the result
        return constants + methodInvocations + fieldAccesses
                + objCreations + instructions + misc;
    }

    /**
     * Checks if this Features object is equal to another object.
     * 
     * @param rhs the object to compare this Features object to
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object rhs) {
        // Check if rhs is null
        if(rhs == null) return false;
        
        // Check if rhs is an instance of Features
        if(!(rhs instanceof Features _rhs)) return false;
        
        // Compare this Features object with _rhs using a FeatureMatcher and a similarity threshold
        return FeatureMatcher.get(SIM_ALGORITHM).isMatch(this, _rhs, SIM_THRESHOLD);
    }

}
