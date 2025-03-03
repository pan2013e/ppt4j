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

    /**
     * Returns a list of line numbers that represent additions in a document.
     * An addition line is identified by having a LineType of TO.
     *
     * @return A list of integers representing line numbers of additions
     */
    public List<Integer> getAdditionLines() {
        List<Integer> additionLines = new ArrayList<>();
    
        // Iterate through each Pair<Integer, Line> in the lines list
        for(Pair<Integer, Line> p : lines) {
            // Check if the LineType of the Line object is TO
            if(p.getRight().getLineType() == Line.LineType.TO) {
                // Add the line number to the additionLines list
                additionLines.add(p.getLeft());
            }
        }
    
        return additionLines;
    }

    /**
     * This method retrieves a list of line numbers corresponding to deletion lines in a document.
     * Deletion lines are identified by having a LineType of FROM.
     *
     * @return a list of integers representing line numbers of deletion lines
     */
    public List<Integer> getDeletionLines() {
        List<Integer> deletionLines = new ArrayList<>();
        // Iterate through each Pair<Integer, Line> in the lines list
        for(Pair<Integer, Line> p : lines) {
            // Check if the LineType of the current Line is FROM
            if(p.getRight().getLineType() == Line.LineType.FROM) {
                // Add the line number to the deletionLines list
                deletionLines.add(p.getLeft());
            }
        }
        return deletionLines;
    }

    /**
     * Checks if the method only involves addition and no deletion.
     * Returns true if there are no deletion lines present, false otherwise.
     * 
     * @return true if there are no deletion lines, false otherwise
     */
    public boolean isPureAddition() {
        // Check if there are any deletion lines present
        return getDeletionLines().isEmpty(); // Return true if there are no deletion lines
    }

    /**
     * This method checks if the current changeset is a pure deletion, meaning that there are no lines added.
     * @return true if the changeset is a pure deletion, false otherwise
     */
    public boolean isPureDeletion() {
        // Check if there are no addition lines
        return getAdditionLines().isEmpty(); // Return true if no addition lines are present
    }

    /**
     * Returns a string representation of the object by iterating through each line in the 'lines' list.
     * For each line, appends a prefix '-' if the line type is FROM, '+' if the line type is TO.
     * Throws an IllegalStateException if the line type is neither FROM nor TO.
     * Appends the content of the line followed by a newline character to the StringBuilder.
     * Finally, returns the resulting string.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Pair<Integer, Line> pair : lines) {
            Line line = pair.getRight();
            if(line.getLineType() == Line.LineType.FROM) {
                sb.append("- "); // Prefix for FROM line
            } else if(line.getLineType() == Line.LineType.TO) {
                sb.append("+ "); // Prefix for TO line
            } else {
                throw new IllegalStateException(); // Throw exception for invalid line type
            }
            sb.append(line.getContent()).append("\n"); // Append line content with newline
        }
        return sb.toString();
    }

}
