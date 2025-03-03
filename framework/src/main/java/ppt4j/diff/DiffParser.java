package ppt4j.diff;

import ppt4j.util.FileUtils;
import ppt4j.util.StringUtils;
import io.reflectoring.diffparser.api.UnifiedDiffParser;
import io.reflectoring.diffparser.api.model.Diff;
import io.reflectoring.diffparser.api.model.Line;
import lombok.extern.log4j.Log4j;
import org.apache.commons.lang3.tuple.Pair;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

@Log4j
public class DiffParser {

    private final List<Diff> diffs;

    private final List<List<Pair<Integer, Line>>>
            diffLines = new ArrayList<>();

    private final List<FileDiff> fileDiffs = new ArrayList<>();

    private boolean downloadIntegrityCheck = false;

    @Log4j
    static class SigintHandler extends Thread {

        private final DiffParser diff;
        private final File f;

        public SigintHandler(DiffParser diff, File f) {
            this.diff = diff;
            this.f = f;
        }

        /**
         * Registers the current object as a shutdown hook with the runtime. 
         * This allows the object to perform cleanup operations before the JVM shuts down.
         */
        public void register() {
            // Add the current object as a shutdown hook to the runtime
            Runtime.getRuntime().addShutdownHook(this);
        }

        /**
         * This method checks if the download integrity check has been completed. If the check has not been completed, an error message is logged and the file is deleted.
         */
        @Override
        @SuppressWarnings("ResultOfMethodCallIgnored")
        public void run() {
            // Check if the download integrity check has been completed
            if (!diff.downloadIntegrityCheck) {
                // Log an error message if the download is incomplete
                log.error("Download incomplete");
                
                // Delete the file
                f.delete();
            }
        }

    }

    public DiffParser(URL url) throws IOException {
        FileUtils.makeLocalDirectory(".temp");
        boolean web = url.getProtocol().equals("http")
                || url.getProtocol().equals("https");
        if (web) {
            log.trace("Downloading diff file");
            log.trace("URL: " + url);
        }
        byte[] buf = download(url);
        if(web) {
            log.trace("Download complete");
        }
        UnifiedDiffParser parser = new UnifiedDiffParser();
        diffs = parser.parse(preprocess(buf));
        buildDiffLines();
        for (List<Pair<Integer, Line>> diffLine : diffLines) {
            fileDiffs.add(new FileDiff(diffLine));
        }
    }

    /**
     * Downloads a file from the provided URL. If the file already exists locally, it reads its contents
     * into a byte array. If the file does not exist, it creates a new file, downloads the contents from
     * the URL, writes the contents to the file, and performs an integrity check. If an IOException occurs
     * during the download process, the error is logged and the system exits with status code 1.
     *
     * @param url the URL pointing to the file to download
     * @return a byte array containing the downloaded file contents
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public byte[] download(URL url) throws IOException {
        byte[] buf = null;
        File f = new File(".temp/" + url.toString().hashCode());
        if(f.exists()) {
            FileInputStream fis = new FileInputStream(f);
            buf = fis.readAllBytes();
            fis.close();
        } else {
            f.createNewFile();
            new SigintHandler(this, f).register();
            try {
                URLConnection socketConn = url.openConnection();
                socketConn.setConnectTimeout(10000);
                socketConn.setReadTimeout(20000);
                BufferedInputStream bis = new BufferedInputStream(socketConn.getInputStream());
                FileOutputStream fos = new FileOutputStream(f);
                buf = bis.readAllBytes();
                fos.write(buf);
                fos.close();
                bis.close();
                downloadIntegrityCheck = true;
            } catch (IOException e) {
                log.error(e);
                System.exit(1);
            }
        }
        return buf;
    }

    public DiffParser(String patchPath) throws IOException {
        this(StringUtils.toURL(patchPath));
    }

    /**
     * Returns the number of differences in the list.
     *
     * @return the number of differences in the list
     */
    public int getNumOfDiffs() {
        // Return the size of the list containing differences
        return diffs.size();
    }

    /**
     * Returns the file name at the specified index in the list of differences. If the file name is "/dev/null",
     * then it returns the file name at the "to" side of the difference instead. If removePrefix is true, it removes
     * any directory path prefixes from the file name before returning.
     * 
     * @param diffIndex the index of the difference
     * @param removePrefix whether to remove directory path prefixes from the file name
     * @return the file name at the specified index in the list of differences
     */
    public String getFileName(int diffIndex, boolean removePrefix) {
        String file = diffs.get(diffIndex).getFromFileName(); // Get the file name at the specified index
    
        if(file.equals("/dev/null")) { // If the file name is "/dev/null", get the "to" file name instead
            file = diffs.get(diffIndex).getToFileName();
        }
    
        if(removePrefix) { // If removePrefix is true, remove any directory path prefixes from the file name
            file = file.substring(file.indexOf('/') + 1);
        }
    
        return file; // Return the file name
    }

    /**
     * Returns the FileDiff object at the specified index in the list of file differences.
     * 
     * @param diffIndex the index of the FileDiff object to retrieve
     * @return the FileDiff object at the specified index
     */
    public FileDiff getFileDiff(int diffIndex) {
        // Get the FileDiff object at the specified index from the list
        return fileDiffs.get(diffIndex);
    }

    /**
     * Returns a string representation of this object by iterating through
     * the list of FileDiffs and appending the file name and the string
     * representation of each FileDiff to a StringBuilder.
     *
     * @return a string representation of this object
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fileDiffs.size(); i++) {
            FileDiff fileDiff = fileDiffs.get(i);
            sb.append(getFileName(i, true)).append("\n"); // Append file name with index
            sb.append(fileDiff.toString()).append("\n"); // Append FileDiff's string representation
        }
        return sb.toString(); // Return the final string representation
    }

    /**
     * Builds a list of lines for each diff in the diffs list. 
     * For each diff, iterates through the hunks and extracts the lines, assigning line numbers based on the LineType.
     */
    private void buildDiffLines() {
        diffs.forEach(diff -> {
            List<Pair<Integer, Line>> lines = new ArrayList<>();
            diff.getHunks().forEach(hunk -> {
                int fromLine = hunk.getFromFileRange().getLineStart() - 1;
                int toLine = hunk.getToFileRange().getLineStart() - 1;
                List<Line> hunkLines = hunk.getLines();
                for (Line line : hunkLines) {
                    if (line.getLineType() == Line.LineType.FROM) {
                        fromLine++; // Increment "from" line number for "FROM" type
                        lines.add(Pair.of(fromLine, line));
                    } else if (line.getLineType() == Line.LineType.TO) {
                        toLine++; // Increment "to" line number for "TO" type
                        lines.add(Pair.of(toLine, line));
                    } else {
                        fromLine++; // Increment line numbers for other types
                        toLine++;
                    }
                }
            });
            diffLines.add(lines); // Add the lines for the current diff to diffLines list
        });
    }

    // insert '\n' before the pattern "diff --git"
    /**
     * Preprocesses the input byte array by splitting it into lines and adding an empty line before lines that start with "diff --git" if they are not the first line.
     * 
     * @param buf the input byte array to preprocess
     * @return the preprocessed byte array
     */
    private byte[] preprocess(byte[] buf) {
        // Convert byte array to string
        String content = new String(buf);
        
        // Split content into lines
        String[] lines = content.split("\n");
        
        // Create a new list to store preprocessed lines
        List<String> newLines = new ArrayList<>();
        
        // Iterate through each line
        for(int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // Add an empty line before lines starting with "diff --git" if they are not the first line
            if(line.startsWith("diff --git") && i > 0) {
                newLines.add("");
            }
            
            // Add the line to the newLines list
            newLines.add(line);
        }
        
        // Convert preprocessed lines back to byte array and return
        return StringUtils.toBytes(newLines);
    }

}
