package ppt4j.analysis.patch;

import ppt4j.annotation.Property;
import ppt4j.feature.FeatureMatcher;
import ppt4j.feature.Features;
import ppt4j.feature.bytecode.BytecodeExtractor;
import ppt4j.feature.bytecode.BytecodeFeatures;
import ppt4j.feature.java.JavaExtractor;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.IntStream;

final class J2BCMatcher implements CrossMatcher {

    @Property("ppt4j.features.similarity.threshold")
    private static double SIM_THRESHOLD;

    @Property("ppt4j.features.similarity.algorithm")
    private static String SIM_ALGORITHM;

    @Property("ppt4j.analysis.matcher.max_window_size")
    private static int MAX_WINDOW_SIZE;

    private final JavaExtractor e1;

    private final BytecodeExtractor e2;

    @Getter
    private final Map<Integer, Features>
            featuresMap = new TreeMap<>();

    private final Map<Integer, Pair<Integer, Integer>>
            matchedRanges = new TreeMap<>();

    private final int maxLine;

    private final int maxBcIndex;

    @Getter
    private final boolean[] srcMatched;

    @Getter
    private final double[] score;

    private final int[][] lcs;

    J2BCMatcher(JavaExtractor e1, BytecodeExtractor e2, boolean diffType) {
        this.e1 = e1;
        this.e2 = e2;
        Set<Integer> lineSet = e1.getFeaturesMap().keySet();
        maxLine = e1.getFeaturesMap().isEmpty() ? 0 : Collections.max(lineSet);
        Set<Integer> bcLineSet = e2.getFeaturesMap().keySet();
        maxBcIndex = e2.getFeaturesMap().isEmpty() ? 0 : Collections.max(bcLineSet);
        lcs = new int[maxLine + 10][maxBcIndex + 10];
        for (int i = 0; i < maxLine + 10; i++) {
            for (int j = 0; j < maxBcIndex + 10; j++) {
                lcs[i][j] = -1;
            }
        }
        srcMatched = new boolean[maxLine + 10];
        score = new double[maxLine + 10];
        LCSMatch(diffType);
    }

    /**
     * Returns the source type for Java features.
     * 
     * @return The source type for Java features.
     */
    @Override
    public Features.SourceType getKeyType() {
        // Return the source type for Java features
        return Features.SourceType.JAVA;
    }

    /**
     * Returns the source type of the features as BYTECODE.
     * 
     * @return the source type of the features as BYTECODE
     */
    @Override
    public Features.SourceType getValueType() {
        // Return the source type as BYTECODE
        return Features.SourceType.BYTECODE;
    }

    /**
     * Checks if the specified index is within the range of the srcMatched array and returns the boolean value at that index.
     * 
     * @param index the index to check
     * @return true if the index is within range and the value at that index is true, false otherwise
     */
    @Override
    public boolean isMatched(int index) {
        // Check if the index is within the bounds of the srcMatched array
        if(index < 0 || index >= srcMatched.length) {
            return false;
        }
        // Return the boolean value at the specified index
        return srcMatched[index];
    }

    /**
     * This method retrieves the score at the specified index in the score array.
     * If the index is out of bounds, it returns -1.
     *
     * @param index the index of the score to retrieve
     * @return the score at the specified index, or -1 if the index is out of bounds
     */
    @Override
    public double getScore(int index) {
        // Check if the index is out of bounds
        if(index < 0 || index >= score.length) {
            return -1;
        }
        // Return the score at the specified index
        return score[index];
    }

    /**
     * Retrieves the features corresponding to the given index from the featuresMap.
     * If the index is matched, the corresponding features object is returned.
     * If the index is not matched, null is returned.
     *
     * @param index the index of the features to query
     * @return the features object corresponding to the index, or null if index is not matched
     */
    @Override
    public Features query(int index) {
        // Check if the index is matched
        if(isMatched(index)) {
            // Return the features object corresponding to the index
            return featuresMap.get(index);
        } else {
            // Return null if index is not matched
            return null;
        }
    }

    /**
     * Returns the matched range at the given index. If the index is matched,
     * returns the Pair representing the start and end of the matched range,
     * otherwise returns null.
     *
     * @param index the index of the matched range to retrieve
     * @return the Pair representing the start and end of the matched range, or null if index is not matched
     */
    @Override
    public Pair<Integer, Integer> getMatchedRange(int index) {
        if(isMatched(index)) { // check if index is matched
            return matchedRanges.get(index); // return matched range if index is matched
        } else {
            return null; // return null if index is not matched
        }
    }

    /**
     * This method calculates the similarity score between two features based on the specified similarity algorithm. 
     * It first checks if the features with the given indices exist in the feature maps of e1 and e2. If either of them does not exist, 
     * it returns -1 indicating that the score cannot be calculated. If both features exist, it retrieves the Features objects 
     * associated with the indices i and j from the feature maps of e1 and e2 respectively. It then calculates the similarity score 
     * between the two Features objects using the specified SIM_ALGORITHM and returns the result.
     */
    private double score(int i, int j) {
        // Check if feature i exists in e1's feature map
        if(!e1.getFeaturesMap().containsKey(i)) {
            return -1;
        }
        // Check if feature j exists in e2's feature map
        if(!e2.getFeaturesMap().containsKey(j)) {
            return -1;
        }
        // Retrieve the Features object associated with feature i from e1's feature map
        Features f1 = e1.getFeaturesMap().get(i);
        // Retrieve the Features object associated with feature j from e2's feature map
        Features f2 = e2.getFeaturesMap().get(j);
        // Calculate and return the similarity score between f1 and f2 using the specified similarity algorithm
        return FeatureMatcher.get(SIM_ALGORITHM).match(f1, f2);
    }


    /**
     * Checks if the similarity score between two integers i and j is greater than or equal to the predefined threshold.
     * 
     * @param i the first integer
     * @param j the second integer
     * @return true if the similarity score is greater than or equal to the threshold, false otherwise
     */
    private boolean matches(int i, int j) {
        // Calculate the similarity score between the two integers i and j
        // using the score method
        return score(i, j) >= SIM_THRESHOLD;
    }

    // j has been added 1 before calling this method
    /**
     * This method calculates the length of the Longest Common Subsequence (LCS) between two strings 
     * represented by indices i and j in a 2D array lcs. It uses dynamic programming to store and reuse 
     * previously calculated values to optimize the process.
     */
    private int getLCSLength(int i, int j) {
            if(i == 0 || j == 0) { // Base case: if either i or j is 0, return 0
                return 0;
            }
            if(lcs[i][j] != -1) { // If value is already calculated, return it
                return lcs[i][j];
            }
            if(matches(i, j - 1)) { // If characters match, update value based on diagonal element
                lcs[i][j] = getLCSLength(i - 1, j - 1) + 1;
            } else { // If characters don't match, update value based on max of upper and left elements
                lcs[i][j] = Math.max(getLCSLength(i - 1, j), getLCSLength(i, j - 1));
            }
            return lcs[i][j]; // Return calculated LCS length
        }

    /**
     * This method performs the Longest Common Subsequence (LCS) matching algorithm to find the similarities between two entities (e1 and e2). 
     * It iterates through the features of both entities, marking the matching features and updating the scores accordingly. 
     * If a match is not found, it determines the next step based on the LCS length of adjacent features or the 'diffAddition' flag. 
     * After matching the features, it identifies the unmatched source lines and performs a second round of matching if needed. 
     * It also matches inner classes of the entities using a J2BCMatcher and merges the results.
     */
    private void LCSMatch(boolean diffAddition) {
            int i = maxLine;
            int j = maxBcIndex;
            while (i > 0 && j >= 0) {
                if (!e1.getFeaturesMap().containsKey(i)) {
                    i--;
                    continue;
                }
                if (!e2.getFeaturesMap().containsKey(j)) {
                    j--;
                    continue;
                }
                if(matches(i, j)) {
                    srcMatched[i] = true;
                    score[i] = score(i, j);
                    matchedRanges.put(i, Pair.of(j, j));
                    i--;
                    j--;
                } else {
                    if (getLCSLength(i - 1, j + 1) > getLCSLength(i, j)) {
                        i--;
                    } else if (getLCSLength(i - 1, j + 1) < getLCSLength(i, j)) {
                        j--;
                    } else {
                        if (diffAddition) {
                            i--;
                        } else {
                            j--;
                        }
                    }
                }
            }
            List<Integer> notMatchedSrc = new ArrayList<>();
            for(int ii = 1;ii <= maxLine; ii++) {
                if(e1.getFeaturesMap().containsKey(ii) && !srcMatched[ii]) {
                    notMatchedSrc.add(ii);
                }
            }
            notMatchedSrc.forEach(line -> secondRoundMatch(line, e1, e2));
            e1.getInnerClass().forEach(inner -> {
                String name = inner.getClassName();
                BytecodeExtractor bcInner = e2.getInnerClass(name);
                try {
                    if(bcInner != null) {
                        J2BCMatcher matcher = new J2BCMatcher(inner, bcInner, true);
                        merge(matcher);
                    }
                } catch (Exception e) {
                    // ignore
                }
            });
        }

    /**
     * Merges the features, matched status, and scores from the given J2BCMatcher object (rhs) into this J2BCMatcher object.
     * 
     * @param rhs the J2BCMatcher object to merge with
     */
    private void merge(J2BCMatcher rhs) {
        // Merge the features map from rhs into this object's features map
        featuresMap.putAll(rhs.getFeaturesMap());
        
        // Merge the matched status from rhs into this object's matched status
        boolean[] rhsMatched = rhs.getSrcMatched();
        for(int i = 0; i < rhsMatched.length; i++) {
            srcMatched[i] |= rhsMatched[i];
        }
        
        // Merge the scores from rhs into this object's scores by taking the maximum for each index
        double[] rhsScore = rhs.getScore();
        for(int i = 0; i < rhsScore.length; i++) {
            score[i] = Math.max(score[i], rhsScore[i]);
        }
    }

    /**
     * Finds the best matching bytecode features for a given line of code by comparing it with surrounding bytecode features.
     *
     * @param line the line number of the code to match
     * @param e1 the JavaExtractor containing the features of the Java code
     * @param e2 the BytecodeExtractor containing the features of the bytecode
     */
    private void secondRoundMatch(int line, JavaExtractor e1, BytecodeExtractor e2) {
        Map<Integer, Features> fm1, fm2;
        int maxLine2;
        fm1 = e1.getFeaturesMap();
        fm2 = e2.getFeaturesMap();
        maxLine2 = e2.getFeaturesMap().isEmpty() ? 0 : Collections.max(fm2.keySet());
        int before = -1, after = -1;
        for(int i = line - 1; i >= 0; i--) {
            if(srcMatched[i]) {
                before = i;
                break;
            }
        }
        for(int i = line + 1; i < srcMatched.length; i++) {
            if(srcMatched[i]) {
                after = i;
                break;
            }
        }
        int bcStart = 0, bcEnd = maxLine2;
        if(before != -1) {
            bcStart = matchedRanges.get(before).getRight() + 1;
        }
        if(after != -1) {
            bcEnd = matchedRanges.get(after).getLeft();
        }
        int windowSize = 2;
        Pair<Integer, Integer> bestMatch = null;
        Features bestMatchFeatures = null;
        double bestScore = -1;
        while(windowSize <= bcEnd - bcStart && windowSize <= MAX_WINDOW_SIZE) {
            for(int i = bcStart; i < bcEnd - windowSize + 1; i++) {
                double score;
                BytecodeFeatures temp;
                temp = IntStream.range(i, i + windowSize)
                        .filter(fm2::containsKey)
                        .mapToObj(fm2::get)
                        .map(f -> (BytecodeFeatures) f)
                        .parallel().reduce(BytecodeFeatures::merge).orElse(BytecodeFeatures.empty());
                score = FeatureMatcher.get(SIM_ALGORITHM).match(fm1.get(line), temp);
                if(score > bestScore) {
                    bestScore = score;
                    bestMatch = Pair.of(i, i + windowSize - 1);
                    bestMatchFeatures = temp;
                }
            }
            if(bestScore >= SIM_THRESHOLD) {
                break;
            }
            windowSize++;
        }
        if(bestScore >= SIM_THRESHOLD) {
            featuresMap.put(line, bestMatchFeatures);
            srcMatched[line] = true;
            score[line] = bestScore;
            matchedRanges.put(line, bestMatch);
        }
    }
}
