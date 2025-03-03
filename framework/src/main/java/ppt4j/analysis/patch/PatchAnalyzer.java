package ppt4j.analysis.patch;

import ppt4j.annotation.MethodProfiler;
import ppt4j.annotation.Property;
import ppt4j.database.DatabaseType;
import ppt4j.database.Vulnerability;
import ppt4j.diff.BlockDiff;
import ppt4j.diff.DiffParser;
import ppt4j.diff.FileDiff;
import ppt4j.factory.DatabaseFactory;
import ppt4j.factory.ExtractorFactory;
import ppt4j.feature.FeatureMatcher;
import ppt4j.feature.Features;
import ppt4j.feature.java.JavaExtractor;
import ppt4j.feature.java.JavaFeatures;
import ppt4j.util.StringUtils;
import lombok.extern.log4j.Log4j;

import java.io.IOException;
import java.util.*;

@Log4j
public class PatchAnalyzer {

    @Property("ppt4j.analysis.patch.presence_threshold")
    private static double PATCH_PRESENCE_THRESHOLD;

    @Property("ppt4j.features.similarity.threshold")
    private static double SIM_THRESHOLD;

    @Property("ppt4j.features.similarity.algorithm")
    private static String SIM_ALGORITHM;

    private final DiffParser diffParser;
    private final ExtractorFactory factory;
    private final Vulnerability cve;

    private final List<String> filterMatch = new ArrayList<>();
    private final List<String> filterNotMatch = new ArrayList<>();

    private int total = 0, found = 0;

    private final Set<Integer> preUsedLines = new HashSet<>();
    private final Set<Integer> postUsedLines = new HashSet<>();

    public PatchAnalyzer(Vulnerability cve, ExtractorFactory factory)
            throws IOException {
        this.cve = cve;
        this.diffParser = new DiffParser(cve.getDiffUrl());
        this.factory = factory;
        this.filterIfMatch(cve.getRequiredFilePatterns())
                .filterIfNotMatch(cve.getIgnoredFilePatterns());
    }

    public PatchAnalyzer(Vulnerability cve, DatabaseType type)
            throws IOException {
        this(cve, ExtractorFactory.get(cve, type));
        log.info("Ground truth binary type in dataset: " + type);
    }

    @SuppressWarnings("UnusedReturnValue")
    public PatchAnalyzer filterIfMatch(String... patterns) {
        filterMatch.addAll(Arrays.asList(patterns));
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public PatchAnalyzer filterIfNotMatch(String... patterns) {
        filterNotMatch.addAll(Arrays.asList(patterns));
        return this;
    }

    @MethodProfiler
    public boolean analyze() throws IOException {
        log.info(String.format("Analyzing patch for %s, " +
                        "CVE ID: %s, Database ID: %d",
                cve.getProjectName(), cve.getCVEId(), cve.getDatabaseId()));
        total = 0;
        found = 0;
        for(int i = 0;i < diffParser.getNumOfDiffs();i++) {
            String fileName = diffParser.getFileName(i, true);
            if(filterNotMatch.stream().anyMatch(fileName::matches)) {
                continue;
            }
            if(!filterMatch.stream().allMatch(fileName::matches)) {
                continue;
            }
            if(!fileName.startsWith(cve.getJavaSrcTopLevelDir())) {
                continue;
            }
            String className = StringUtils.extractClassName(
                    fileName, cve.getJavaSrcTopLevelDir()
            );
            FileDiff fileDiff = diffParser.getFileDiff(i);
            for (BlockDiff block : fileDiff.getBlocks()) {
                if(block.isPureDeletion()) {
                    for (Integer line : block.getDeletionLines()) {
                        analyzeDeletion(className, line);
                    }
                } else if(block.isPureAddition()) {
                    for (Integer line : block.getAdditionLines()) {
                        analyzeAddition(className, line);
                    }
                } else {
                    List<Integer> additions = block.getAdditionLines();
                    filterAddition(className, additions);
                    List<Integer> deletions = block.getDeletionLines();
                    filterDeletion(className, deletions);
                    int windowSize = Math.min(additions.size(), deletions.size());
                    List<Integer> candidate, window;
                    Features windowFeatures;
                    char type;
                    if (additions.size() < deletions.size()) {
                        candidate = deletions;
                        window = additions;
                        type = '+';
                    } else {
                        candidate = additions;
                        window = deletions;
                        type = '-';
                    }
                    windowFeatures = mergeFeatures(className, window, type);
                    List<Integer> bestOverlap = null;
                    double bestScore = 0;
                    for(int j = 0; j < candidate.size() - windowSize + 1; j++) {
                        List<Integer> overlap = candidate.subList(j, j + windowSize);
                        Features overlapFeatures = mergeFeatures(className, overlap, type == '+' ? '-' : '+');
                        FeatureMatcher alg = FeatureMatcher.get(SIM_ALGORITHM);
                        double score = alg.match(windowFeatures, overlapFeatures);
                        if(score >= SIM_THRESHOLD && score > bestScore) {
                            bestScore = score;
                            bestOverlap = overlap;
                        }
                    }
                    if(bestScore == 1) {
                        candidate.removeAll(bestOverlap);
                        window.clear();
                    } else if(bestScore >= SIM_THRESHOLD) {
                        assert bestOverlap != null;
                        for (int k = 0;k < windowSize; k++) {
                            if(type == '+') {
                                analyzeModification(className, bestOverlap.get(k), window.get(k));
                            } else {
                                analyzeModification(className, window.get(k), bestOverlap.get(k));
                            }
                        }
                        candidate.removeAll(bestOverlap);
                        window.clear();
                    }
                    for(Integer line: deletions) {
                        analyzeDeletion(className, line);
                    }
                    for(Integer line: additions) {
                        analyzeAddition(className, line);
                    }
                }
            }
        }
        double ratio = (double) found / total;
        log.info("Result: " + ratio);
        return ratio >= PATCH_PRESENCE_THRESHOLD;
    }

    private void filterDeletion(String className, List<Integer> deletionLines) {
        JavaExtractor preEx;
        try {
            preEx = factory.getPreJavaClass(className);
        } catch (RuntimeException e) {
            return;
        }
        deletionLines.removeIf(line -> !preEx.isValidLine(line));
    }

    private void filterAddition(String className, List<Integer> additionLines) {
        JavaExtractor postEx;
        try {
            postEx = factory.getPostJavaClass(className);
        } catch (RuntimeException e) {
            log.warn(e);
            return;
        }
        additionLines.removeIf(line -> !postEx.isValidLine(line));
    }

    private JavaFeatures mergeFeatures(String className, List<Integer> window, char c) {
        JavaExtractor ex;
        JavaFeatures f = JavaFeatures.empty();
        try {
            if(c == '+') {
                ex = factory.getPostJavaClass(className);
            } else if(c == '-') {
                ex = factory.getPreJavaClass(className);
            } else {
                throw new IllegalStateException();
            }
        } catch (RuntimeException e) {
            log.warn(e);
            return f;
        }
        for (Integer line : window) {
            if(ex.getFeaturesMap().containsKey(line)) {
                f.merge((JavaFeatures) ex.getFeaturesMap().get(line));
            }
        }
        return f;
    }

    private void analyzeAddition(String className, int lineNum)
            throws IOException {
        JavaExtractor postEx;
        try {
            postEx = factory.getPostJavaClass(className);
        } catch (RuntimeException e) {
            log.warn(e);
            return;
        }
        CrossMatcher post2class = factory.getPost2Class(className);
        lineNum = postEx.getLogicalLine(lineNum);
        if(postUsedLines.contains(lineNum) ||
            !postEx.isValidLine(lineNum) || !postEx.getFeaturesMap().containsKey(lineNum)) {
            return;
        }
        log.debug("Detected addition: " + className + ":" + lineNum);
        int features = postEx.getFeaturesMap().get(lineNum).size();
        total += features;
        if (post2class.isMatched(lineNum)) {
            found += features;
        }
        postUsedLines.add(lineNum);
    }

    private void analyzeDeletion(String className, int lineNum)
            throws IOException {
        JavaExtractor preEx;
        try {
            preEx = factory.getPreJavaClass(className);
        } catch (RuntimeException e) {
            return;
        }
        CrossMatcher pre2class = factory.getPre2Class(className);
        lineNum = preEx.getLogicalLine(lineNum);
        if(preUsedLines.contains(lineNum) ||
                !preEx.isValidLine(lineNum) || !preEx.getFeaturesMap().containsKey(lineNum)) {
            return;
        }
        log.debug("Detected deletion: " + className + ":" + lineNum);
        int features = preEx.getFeaturesMap().get(lineNum).size();
        total += features;
        if (!pre2class.isMatched(lineNum)) {
            found += features;
        }
        preUsedLines.add(lineNum);
    }

    private void analyzeModification(String className,
                                     int curLine, int nextLine)
            throws IOException {
        JavaExtractor preEx, postEx;
        try {
            preEx = factory.getPreJavaClass(className);
            postEx = factory.getPostJavaClass(className);
        } catch (RuntimeException e) {
            return;
        }
        CrossMatcher pre2class = factory.getPre2Class(className);
        CrossMatcher post2class = factory.getPost2Class(className);
        curLine = preEx.getLogicalLine(curLine);
        nextLine = postEx.getLogicalLine(nextLine);
        if(preUsedLines.contains(curLine) || postUsedLines.contains(nextLine)) {
            return;
        }
        if(!preEx.isValidLine(curLine) || !postEx.isValidLine(nextLine)) {
            return;
        }
        Features curFeatures = preEx.getFeaturesMap().get(curLine);
        Features nextFeatures = postEx.getFeaturesMap().get(nextLine);
        double sim = FeatureMatcher.get(SIM_ALGORITHM)
                .match(curFeatures, nextFeatures);
        if(sim == 1.0) {
            preUsedLines.add(curLine);
            postUsedLines.add(nextLine);
            return;
        }
        if (sim >= SIM_THRESHOLD) {
            log.debug("Detected modification: " + className + ":" + curLine + " -> " + nextLine);
            int features = nextFeatures.size();
            total += features;
            double sim_pre = pre2class.getScore(curLine);
            double sim_post = post2class.getScore(nextLine);
            if (sim_post > sim_pre) {
                found += nextFeatures.size();
            }
            preUsedLines.add(curLine);
            postUsedLines.add(nextLine);
        }
    }

    public static void main(String[] args) throws IOException {
        if(args.length < 2) {
            log.error("Usage: PatchAnalyzer.main <dataset id> <PREPATCH | POSTPATCH>");
            System.exit(1);
        }
        int id = Integer.parseInt(args[0]);
        DatabaseType type = DatabaseType.valueOf(args[1].toUpperCase());
        new PatchAnalyzer(DatabaseFactory.getByDatabaseId(id), type).analyze();
    }

}
