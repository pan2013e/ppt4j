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

        public void register() {
            Runtime.getRuntime().addShutdownHook(this);
        }

        @Override
        @SuppressWarnings("ResultOfMethodCallIgnored")
        public void run() {
            if (!diff.downloadIntegrityCheck) {
                log.error("Download incomplete");
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

    public int getNumOfDiffs() {
        return diffs.size();
    }

    public String getFileName(int diffIndex, boolean removePrefix) {
        String file = diffs.get(diffIndex).getFromFileName();
        if(file.equals("/dev/null")) {
            file = diffs.get(diffIndex).getToFileName();
        }
        if(removePrefix) {
            file = file.substring(file.indexOf('/') + 1);
        }
        return file;
    }

    public FileDiff getFileDiff(int diffIndex) {
        return fileDiffs.get(diffIndex);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fileDiffs.size(); i++) {
            FileDiff fileDiff = fileDiffs.get(i);
            sb.append(getFileName(i, true)).append("\n");
            sb.append(fileDiff.toString()).append("\n");
        }
        return sb.toString();
    }

    private void buildDiffLines() {
        diffs.forEach(diff -> {
            List<Pair<Integer, Line>> lines = new ArrayList<>();
            diff.getHunks().forEach(hunk -> {
                int fromLine = hunk.getFromFileRange().getLineStart() - 1;
                int toLine = hunk.getToFileRange().getLineStart() - 1;
                List<Line> hunkLines = hunk.getLines();
                for (Line line : hunkLines) {
                    if (line.getLineType() == Line.LineType.FROM) {
                        fromLine++;
                        lines.add(Pair.of(fromLine, line));
                    } else if (line.getLineType() == Line.LineType.TO) {
                        toLine++;
                        lines.add(Pair.of(toLine, line));
                    } else {
                        fromLine++;
                        toLine++;
                    }
                }
            });
            diffLines.add(lines);
        });
    }

    // insert '\n' before the pattern "diff --git"
    private byte[] preprocess(byte[] buf) {
        String content = new String(buf);
        String[] lines = content.split("\n");
        List<String> newLines = new ArrayList<>();
        for(int i = 0;i < lines.length;i++) {
            String line = lines[i];
            if(line.startsWith("diff --git") && i > 0) {
                newLines.add("");
            }
            newLines.add(line);
        }
        return StringUtils.toBytes(newLines);
    }

}
