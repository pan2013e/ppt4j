package ppt4j.analysis.bytecode.graph;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public final class InsnBlock {

    public final List<String> lines = new ArrayList<>();

    public final List<InsnBlock> nextBlockList = new ArrayList<>();
    public final List<InsnBlock> jumpBlockList = new ArrayList<>();

    /**
     * Adds a list of strings to the existing list of lines.
     *
     * @param list the list of strings to be added
     */
    public void addLines(List<String> list) {
        // Add all strings from the input list to the existing list
        lines.addAll(list);
    }

    /**
     * Adds the given InsnBlock item to the list of next blocks.
     * 
     * @param item the InsnBlock to be added
     */
    public void addNext(InsnBlock item) {
        // Add the item to the next block list
        nextBlockList.add(item);
    }

    /**
     * Adds the given InsnBlock item to the list of jump blocks.
     * 
     * @param item the InsnBlock to add to the list
     */
    public void addJump(InsnBlock item) {
        // Add the InsnBlock item to the jumpBlockList
        jumpBlockList.add(item);
    }

}
