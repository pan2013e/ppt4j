package ppt4j.analysis.bytecode.graph;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public final class InsnBlock {

    public final List<String> lines = new ArrayList<>();

    public final List<InsnBlock> nextBlockList = new ArrayList<>();
    public final List<InsnBlock> jumpBlockList = new ArrayList<>();

    public void addLines(List<String> list) {
        lines.addAll(list);
    }

    public void addNext(InsnBlock item) {
        nextBlockList.add(item);
    }

    public void addJump(InsnBlock item) {
        jumpBlockList.add(item);
    }

}
