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

    /**
     * Checks if the provided InsnBlock is a delimiter.
     *
     * @param block the InsnBlock to check
     * @return true if the InsnBlock is a delimiter, false otherwise
     */
    public boolean isDelimiter(InsnBlock block) {
        // Check if the delimiters set contains the provided block
        return delimiters.contains(block);
    }

    /**
     * Returns the original instruction blocks retrieved from the superclass.
     * These blocks are used for comparison with modified blocks later on.
     *
     * @return an array of instruction blocks
     */
    public InsnBlock[] getOriginalBlocks() {
        // Retrieve the original blocks from the superclass
        return super.getBlocks();
    }

    /**
     * Retrieves a list of instruction blocks from the superclass, processes the blocks to merge adjacent and connected blocks,
     * and returns the modified list of instruction blocks.
     */
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