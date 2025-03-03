package ppt4j.analysis.bytecode;

import ppt4j.analysis.bytecode.graph.InsnBlock;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.Value;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BasicBlockControlFlowAnalyzer<V extends Value>
        extends InstControlFlowAnalyzer<V> {

    public BasicBlockControlFlowAnalyzer(Interpreter<V> interpreter) {
        super(interpreter);
    }

    private final Set<InsnBlock> delimiters = new HashSet<>();

    public boolean isDelimiter(InsnBlock block) {
        return delimiters.contains(block);
    }

    public InsnBlock[] getOriginalBlocks() {
        return super.getBlocks();
    }

    @Override
    public InsnBlock[] getBlocks() {
        InsnBlock[] blocks = super.getBlocks();

        if (blocks == null || blocks.length < 1) {
            return blocks;
        }

        Set<InsnBlock> newBlockSet = new HashSet<>();
        int length = blocks.length;
        for (int i = 0; i < length; i++) {
            InsnBlock currentBlock = blocks[i];
            List<InsnBlock> nextBlockList = currentBlock.nextBlockList;
            List<InsnBlock> jumpBlockList = currentBlock.jumpBlockList;

            boolean hasNext = false;
            boolean hasJump = false;

            if (nextBlockList.size() > 0) {
                hasNext = true;
            }

            if (jumpBlockList.size() > 0) {
                hasJump = true;
            }

            if (!hasNext && (i + 1) < length) {
                newBlockSet.add(blocks[i + 1]);
            }

            if (hasJump) {
                newBlockSet.addAll(jumpBlockList);

                if (hasNext) {
                    newBlockSet.add(blocks[i + 1]);
                }
            }
        }

        List<InsnBlock> resultList = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            InsnBlock currentBlock = blocks[i];

            if (i == 0) {
                resultList.add(currentBlock);
                delimiters.add(currentBlock);
            } else if (newBlockSet.contains(currentBlock)) {
                resultList.add(currentBlock);
                delimiters.add(currentBlock);
            } else {
                int size = resultList.size();
                InsnBlock lastBlock = resultList.get(size - 1);
                lastBlock.lines.addAll(currentBlock.lines);
                lastBlock.jumpBlockList.clear();
                lastBlock.jumpBlockList.addAll(currentBlock.jumpBlockList);
                lastBlock.nextBlockList.clear();
                lastBlock.nextBlockList.addAll(currentBlock.nextBlockList);
            }
        }

        return resultList.toArray(new InsnBlock[0]);
    }
}