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

    public boolean isEmpty() {
        return Constants.isEmpty() &&
               MethodInvocations.isEmpty() &&
               FieldAccesses.isEmpty() &&
               ObjCreations.isEmpty() &&
               Instructions.isEmpty() &&
               Misc.isEmpty();
    }

    public int size() {
        return Constants.size() +
               MethodInvocations.size() +
               FieldAccesses.size() +
               ObjCreations.size() +
               Instructions.size() +
               Misc.size();
    }

    @Override
    public String toString() {
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
        return constants + methodInvocations + fieldAccesses
                + objCreations + instructions + misc;
    }

    @Override
    public boolean equals(Object rhs) {
        if(rhs == null) return false;
        if(!(rhs instanceof Features _rhs)) return false;
        return FeatureMatcher.get(SIM_ALGORITHM).isMatch(this, _rhs, SIM_THRESHOLD);
    }

}
