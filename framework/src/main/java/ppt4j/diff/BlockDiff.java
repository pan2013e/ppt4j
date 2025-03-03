package ppt4j.diff;

import io.reflectoring.diffparser.api.model.Line;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("ClassCanBeRecord")
public class BlockDiff {

    private final List<Pair<Integer, Line>> lines;

    public BlockDiff(List<Pair<Integer, Line>> lines) {
        this.lines = lines;
    }

    public List<Integer> getAdditionLines() {
        List<Integer> additionLines = new ArrayList<>();
        for(Pair<Integer, Line> p : lines) {
            if(p.getRight().getLineType() == Line.LineType.TO) {
                additionLines.add(p.getLeft());
            }
        }
        return additionLines;
    }

    public List<Integer> getDeletionLines() {
        List<Integer> deletionLines = new ArrayList<>();
        for(Pair<Integer, Line> p : lines) {
            if(p.getRight().getLineType() == Line.LineType.FROM) {
                deletionLines.add(p.getLeft());
            }
        }
        return deletionLines;
    }

    public boolean isPureAddition() {
        return getDeletionLines().isEmpty();
    }

    public boolean isPureDeletion() {
        return getAdditionLines().isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Pair<Integer, Line> pair : lines) {
            Line line = pair.getRight();
            if(line.getLineType() == Line.LineType.FROM) {
                sb.append("- ");
            } else if(line.getLineType() == Line.LineType.TO) {
                sb.append("+ ");
            } else {
                throw new IllegalStateException();
            }
            sb.append(line.getContent()).append("\n");
        }
        return sb.toString();
    }

}
