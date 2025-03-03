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

    /**
     * Adds the given patterns to the list of filters to be applied during matching analysis.
     * 
     * @param patterns the patterns to add to the filter list
     * @return the PatchAnalyzer object to allow method chaining
     */
    @SuppressWarnings("UnusedReturnValue")
    public PatchAnalyzer filterIfMatch(String... patterns) {
        // Add the given patterns to the list of filters
        filterMatch.addAll(Arrays.asList(patterns));
        // Return the PatchAnalyzer object to allow method chaining
        return this;
    }

    /**
     * Adds the specified patterns to the list of patterns to filter if they do not match.
     * 
     * @param patterns the patterns to be added to the filterNotMatch list
     * @return the PatchAnalyzer object with the updated filterNotMatch list
     */
    @SuppressWarnings("UnusedReturnValue")
    public PatchAnalyzer filterIfNotMatch(String... patterns) {
        // Add the specified patterns to the filterNotMatch list
        filterNotMatch.addAll(Arrays.asList(patterns));
        // Return the PatchAnalyzer object with the updated filterNotMatch list
        return this;
    }

    /**
     * Analyzes the patch for a specific CVE (Common Vulnerabilities and Exposures) entry.
     * It processes the differences between the patched files and the original files,
     * identifying pure additions, pure deletions, and modifications. It calculates a
     * similarity score between the additions and deletions, and determines if the patch
     * is present based on a threshold.
     *
     * @return true if the patch is present, false otherwise
     * @throws IOException if an I/O error occurs during analysis
     */
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

    /**
     * Filters the deletionLines list by removing any lines that are not valid in the given preJavaClass.
     *
     * @param className the name of the preJavaClass
     * @param deletionLines the list of deletion lines to be filtered
     */
    private void filterDeletion(String className, List<Integer> deletionLines) {
        JavaExtractor preEx;
        
        // Try to get the preJavaClass with the given className
        try {
            preEx = factory.getPreJavaClass(className);
        } catch (RuntimeException e) {
            return; // If an exception is caught, return from the method
        }
        
        // Remove any lines from deletionLines that are not valid in the preJavaClass
        deletionLines.removeIf(line -> !preEx.isValidLine(line));
    }

    /**
     * Filters out invalid lines from the List of additionLines based on the given className.
     * It retrieves a JavaExtractor object for the given className using the factory, 
     * and then removes lines from the additionLines List that are not valid according to the postEx object.
     *
     * @param className the name of the Java class to extract
     * @param additionLines the List of integers representing lines to filter
     */
    private void filterAddition(String className, List<Integer> additionLines) {
        JavaExtractor postEx;
        try {
            postEx = factory.getPostJavaClass(className); // Retrieve JavaExtractor object for the given className
        } catch (RuntimeException e) {
            log.warn(e); // Log warning if exception is caught
            return; // Return if an exception is caught
        }
        additionLines.removeIf(line -> !postEx.isValidLine(line)); // Remove lines that are not valid according to postEx
    }

    /**
     * Merges features from a Java class based on the specified class name, window of lines, and character.
     * 
     * @param className the name of the Java class
     * @param window a list of line numbers representing a window of lines
     * @param c a character indicating whether to extract features from the post or pre Java class
     * @return a JavaFeatures object containing the merged features
     */
    private JavaFeatures mergeFeatures(String className, List<Integer> window, char c) {
        JavaExtractor ex;
        JavaFeatures f = JavaFeatures.empty();
        try {
            if(c == '+') {
                ex = factory.getPostJavaClass(className); // extract features from post Java class
            } else if(c == '-') {
                ex = factory.getPreJavaClass(className); // extract features from pre Java class
            } else {
                throw new IllegalStateException(); // throw exception if character is invalid
            }
        } catch (RuntimeException e) {
            log.warn(e); // log warning if exception occurs
            return f; // return empty features if exception occurs
        }
        for (Integer line : window) {
            if(ex.getFeaturesMap().containsKey(line)) {
                f.merge((JavaFeatures) ex.getFeaturesMap().get(line)); // merge features if line exists in features map
            }
        }
        return f; // return merged features
    }

    /**
     * Analyzes an addition in a Java class file at the specified line number.
     * Retrieves the post-extracted Java class using a factory, checks if the line number is valid,
     * checks if the line number has been used before, calculates and updates total and found features count,
     * and adds the line number to the set of used lines.
     *
     * @param className the name of the Java class
     * @param lineNum the line number where the addition occurred
     * @throws IOException if an I/O error occurs
     */
    private void analyzeAddition(String className, int lineNum)
                throws IOException {
            JavaExtractor postEx;
            try {
                postEx = factory.getPostJavaClass(className); // Retrieve the post-extracted Java class
            } catch (RuntimeException e) {
                log.warn(e);
                return;
            }
            CrossMatcher post2class = factory.getPost2Class(className); // Get cross matcher for post and class
            lineNum = postEx.getLogicalLine(lineNum); // Get the logical line number
            if(postUsedLines.contains(lineNum) || // Check if line number has been used before
                !postEx.isValidLine(lineNum) || !postEx.getFeaturesMap().containsKey(lineNum)) { // Check if line number is valid and features map contains the line number
                return;
            }
            log.debug("Detected addition: " + className + ":" + lineNum); // Log the detected addition
            int features = postEx.getFeaturesMap().get(lineNum).size(); // Get the number of features for the line
            total += features; // Update total features count
            if (post2class.isMatched(lineNum)) { // Check if line number is matched
                found += features; // Update found features count
            }
            postUsedLines.add(lineNum); // Add line number to used lines set
        }

    /**
     * Analyzes a deletion in a Java class by checking if the specified line number is a valid deletion point.
     * If the line number is a valid deletion point, the method logs the deletion and updates the total and found features counts.
     * 
     * @param className the name of the Java class being analyzed
     * @param lineNum the line number of the deletion
     * @throws IOException if an I/O error occurs while analyzing the deletion
     */
    private void analyzeDeletion(String className, int lineNum)
                throws IOException {
            JavaExtractor preEx;
            try {
                preEx = factory.getPreJavaClass(className);
            } catch (RuntimeException e) {
                return;
            }
            CrossMatcher pre2class = factory.getPre2Class(className);
            // Get the logical line number for the deletion
            lineNum = preEx.getLogicalLine(lineNum);
            // Check if the line number is already processed, not a valid line, or does not contain features
            if(preUsedLines.contains(lineNum) ||
                    !preEx.isValidLine(lineNum) || !preEx.getFeaturesMap().containsKey(lineNum)) {
                return;
            }
            // Log the detected deletion and get the number of features for the line
            log.debug("Detected deletion: " + className + ":" + lineNum);
            int features = preEx.getFeaturesMap().get(lineNum).size();
            total += features; // Update the total features count
            // Check if the deletion is not matched in the post-class and update the found features count
            if (!pre2class.isMatched(lineNum)) {
                found += features;
            }
            preUsedLines.add(lineNum); // Add the line number to the processed list
        }

    /**
     * Analyzes the modification between two lines of code in a class.
     * If a significant modification is detected based on certain similarity threshold,
     * the method logs the modification details and updates the total and found modification counters.
     *
     * @param className the name of the class being analyzed
     * @param curLine the line number in the original code
     * @param nextLine the line number in the modified code
     * @throws IOException if an IO error occurs during the analysis process
     */
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
                return; // Skip if lines have already been used
            }
            
            if(!preEx.isValidLine(curLine) || !postEx.isValidLine(nextLine)) {
                return; // Skip if lines are not valid
            }
            
            Features curFeatures = preEx.getFeaturesMap().get(curLine);
            Features nextFeatures = postEx.getFeaturesMap().get(nextLine);
            
            double sim = FeatureMatcher.get(SIM_ALGORITHM)
                    .match(curFeatures, nextFeatures);
            
            if(sim == 1.0) {
                preUsedLines.add(curLine);
                postUsedLines.add(nextLine);
                return; // Skip if lines are identical
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

    /**
     * This method is the entry point of the PatchAnalyzer program. It takes in two command line arguments: dataset id and database type (PREPATCH or POSTPATCH). It then creates a new PatchAnalyzer object and calls the analyze method to perform analysis on the specified dataset with the given database type.
     *
     * @param args The command line arguments containing dataset id and database type
     * @throws IOException If an I/O error occurs
     */
    public static void main(String[] args) throws IOException {
            if(args.length < 2) {
                log.error("Usage: PatchAnalyzer.main <dataset id> <PREPATCH | POSTPATCH>");
                System.exit(1); // Exit the program if not enough arguments are provided
            }
            int id = Integer.parseInt(args[0]); // Parse the dataset id from the first argument
            DatabaseType type = DatabaseType.valueOf(args[1].toUpperCase()); // Parse the database type from the second argument and convert to uppercase
            new PatchAnalyzer(DatabaseFactory.getByDatabaseId(id), type).analyze(); // Create a new PatchAnalyzer object with the specified dataset id and database type, then call the analyze method
        }

}
