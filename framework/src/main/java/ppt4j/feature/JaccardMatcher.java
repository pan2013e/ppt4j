package ppt4j.feature;

import ppt4j.util.SetUtils;
import lombok.Getter;

import java.util.Set;

final class JaccardMatcher implements FeatureMatcher {

    @Getter
    private final String algorithm = "jaccard";

    /**
     * Calculates the Jaccard similarity between two sets of features based on their constants, method invocations, field accesses, object creations, miscellaneous features, and instructions.
     * 
     * @param f1 the first set of features
     * @param f2 the second set of features
     * @return the Jaccard similarity between the two sets of features
     */
    @Override
    public double match(Features f1, Features f2) {
        Set<Object> f1C, f2C;
        Set<String> f1M, f2M, f1F, f2F, f1O, f2O, f1S, f2S;
        Set<Features.InstType> f1I, f2I;
        f1C = f1.getConstants();
        f2C = f2.getConstants();
        f1M = f1.getMethodInvocations();
        f2M = f2.getMethodInvocations();
        f1F = f1.getFieldAccesses();
        f2F = f2.getFieldAccesses();
        f1O = f1.getObjCreations();
        f2O = f2.getObjCreations();
        f1S = f1.getMisc();
        f2S = f2.getMisc();
        f1I = f1.getInstructions();
        f2I = f2.getInstructions();
        double jaccard;
        int is = 0, us = 0;
        is += SetUtils.intersection(f1C, f2C).size();
        us += SetUtils.union(f1C, f2C).size();
        is += SetUtils.intersection(f1M, f2M).size();
        us += SetUtils.union(f1M, f2M).size();
        is += SetUtils.intersection(f1F, f2F).size();
        us += SetUtils.union(f1F, f2F).size();
        is += SetUtils.intersection(f1O, f2O).size();
        us += SetUtils.union(f1O, f2O).size();
        is += SetUtils.intersection(f1S, f2S).size();
        us += SetUtils.union(f1S, f2S).size();
        is += SetUtils.intersection(f1I, f2I).size();
        us += SetUtils.union(f1I, f2I).size();
        if(is == 0 && us == 0) {
            jaccard = 1.0;
        } else {
            if(us == 0) {
                throw new IllegalStateException("Union is empty");
            }
            jaccard = (double) is / (double) us;
        }
        return jaccard;
    }

}