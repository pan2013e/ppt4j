package ppt4j.diff;

import io.reflectoring.diffparser.api.model.Line;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public class FileDiff {

    @Getter
    private final List<BlockDiff> blocks = new ArrayList<>();

    public FileDiff(List<Pair<Integer, Line>> lines) {
        int offset = 0;
        int idx = 0;
        while (idx < lines.size()) {
            int start = idx;
            int end = idx;
            if (lines.get(start).getRight().getLineType() == Line.LineType.TO) {
                while (end < lines.size() - 1 &&
                        lines.get(end + 1).getLeft() == lines.get(end).getLeft() + 1 &&
                        lines.get(end + 1).getRight().getLineType() != Line.LineType.FROM) {
                    end++;
                }
                offset += end - start + 1;
            } else {
                while (end < lines.size() - 1 &&
                        lines.get(end + 1).getLeft() == lines.get(end).getLeft() + 1 &&
                        lines.get(end + 1).getRight().getLineType() != Line.LineType.TO) {
                    end++;
                }
                offset -= end - start + 1;
                if (end < lines.size() - 1 &&
                        lines.get(end + 1).getRight().getLineType() == Line.LineType.TO &&
                        lines.get(end + 1).getLeft() - offset - 1 <= lines.get(end).getLeft()) {
                    end++;
                    while (end < lines.size() - 1 &&
                            lines.get(end + 1).getLeft() == lines.get(end).getLeft() + 1) {
                        end++;
                    }
                    offset += end - start + 1;
                }
            }
            blocks.add(new BlockDiff(lines.subList(start, end + 1)));
            idx = end + 1;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (BlockDiff block : blocks) {
            sb.append(block.toString());
            sb.append("\n");
        }
        return sb.toString();
    }

}
