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

    @Override
    public Features.SourceType getKeyType() {
        return Features.SourceType.JAVA;
    }

    @Override
    public Features.SourceType getValueType() {
        return Features.SourceType.BYTECODE;
    }

    @Override
    public boolean isMatched(int index) {
        if(index < 0 || index >= srcMatched.length) {
            return false;
        }
        return srcMatched[index];
    }

    @Override
    public double getScore(int index) {
        if(index < 0 || index >= score.length) {
            return -1;
        }
        return score[index];
    }

    @Override
    public Features query(int index) {
        if(isMatched(index)) {
            return featuresMap.get(index);
        } else {
            return null;
        }
    }

    @Override
    public Pair<Integer, Integer> getMatchedRange(int index) {
        if(isMatched(index)) {
            return matchedRanges.get(index);
        } else {
            return null;
        }
    }

    private double score(int i, int j) {
        if(!e1.getFeaturesMap().containsKey(i)) {
            return -1;
        }
        if(!e2.getFeaturesMap().containsKey(j)) {
            return -1;
        }
        Features f1 = e1.getFeaturesMap().get(i);
        Features f2 = e2.getFeaturesMap().get(j);
        return FeatureMatcher.get(SIM_ALGORITHM).match(f1, f2);
    }


    private boolean matches(int i, int j) {
        return score(i, j) >= SIM_THRESHOLD;
    }

    // j has been added 1 before calling this method
    private int getLCSLength(int i, int j) {
        if(i == 0 || j == 0) {
            return 0;
        }
        if(lcs[i][j] != -1) {
            return lcs[i][j];
        }
        if(matches(i, j - 1)) {
            lcs[i][j] = getLCSLength(i - 1, j - 1) + 1;
        } else {
            lcs[i][j] = Math.max(getLCSLength(i - 1, j), getLCSLength(i, j - 1));
        }
        return lcs[i][j];
    }

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

    private void merge(J2BCMatcher rhs) {
        featuresMap.putAll(rhs.getFeaturesMap());
        boolean[] rhsMatched = rhs.getSrcMatched();
        for(int i = 0;i < rhsMatched.length;i++) {
            srcMatched[i] |= rhsMatched[i];
        }
        double[] rhsScore = rhs.getScore();
        for(int i = 0;i < rhsScore.length;i++) {
            score[i] = Math.max(score[i], rhsScore[i]);
        }
    }

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
