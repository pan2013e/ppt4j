package ppt4j.analysis.bytecode;

import ppt4j.analysis.bytecode.graph.InsnBlock;
import ppt4j.analysis.bytecode.graph.InsnText;
import lombok.Getter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstControlFlowAnalyzer<V extends Value> extends Analyzer<V> {

    @Getter
    private AbstractInsnNode[] nodeArray;

    private InsnBlock[] blocks;

    public final Map<AbstractInsnNode, InsnBlock>
            nodeToBlockMap = new HashMap<>();

    public final Map<AbstractInsnNode, InsnBlock>
            nodeToJumpBlockMap = new HashMap<>();

    public final Map<InsnBlock, Integer>
            blockToIndexMap = new HashMap<>();

    public InstControlFlowAnalyzer(Interpreter<V> interpreter) {
        super(interpreter);
    }

    /**
     * Analyzes a given method by creating an array of instruction blocks, mapping each instruction node to a block,
     * and storing the index of each block. It then retrieves the lines of text for each instruction node and adds them
     * to the corresponding block. Finally, it delegates the analysis to the superclass implementation.
     *
     * @param owner the owner of the method
     * @param method the method node to be analyzed
     * @return an array of frames containing the analysis results
     * @throws AnalyzerException if an error occurs during the analysis
     */
    @Override
    public Frame<V>[] analyze(String owner, MethodNode method)
            throws AnalyzerException {
        nodeArray = method.instructions.toArray();
        int length = nodeArray.length;
        blocks = new InsnBlock[length];
        InsnText insnText = new InsnText();
        for (int i = 0; i < length; i++) {
            blocks[i] = getBlock(i);
            AbstractInsnNode node = nodeArray[i];
            nodeToBlockMap.put(node, blocks[i]);
            blockToIndexMap.put(blocks[i], i);
            List<String> lines = insnText.toLines(node);
            blocks[i].addLines(lines);
        }
    
        return super.analyze(owner, method);
    }

    /**
     * Creates a new control flow edge between the instruction at the specified index and its successor index.
     * This method determines the type of the instruction at the specified index and adds the appropriate control flow edge.
     * If the instruction is a jump instruction, it adds a next edge if the successor index is the next instruction index,
     * otherwise it adds a jump edge. If the instruction is a lookupswitch, tableswitch, or ret instruction, it adds a jump edge.
     * If the instruction is a throw instruction or a return instruction, it throws an IllegalStateException.
     * Otherwise, it adds a next edge.
     * 
     * @param insnIndex the index of the instruction
     * @param successorIndex the index of the successor instruction
     */
    @Override
    protected void newControlFlowEdge(int insnIndex, int successorIndex) {
        AbstractInsnNode insnNode = nodeArray[insnIndex];
        int insnOpcode = insnNode.getOpcode();
        int insnType = insnNode.getType();
    
        if (insnType == AbstractInsnNode.JUMP_INSN) {
            if ((insnIndex + 1) == successorIndex) {
                addNext(insnIndex, successorIndex); // add a next edge
            }
            else {
                addJump(insnIndex, successorIndex); // add a jump edge
            }
        }
        else if (insnOpcode == LOOKUPSWITCH || insnOpcode == TABLESWITCH || insnOpcode == RET) {
            addJump(insnIndex, successorIndex); // add a jump edge
        }
        else if (insnOpcode == ATHROW || (insnOpcode >= IRETURN && insnOpcode <= RETURN)) {
            throw new IllegalStateException("Unexpected opcode: " + insnOpcode);
        }
        else {
            addNext(insnIndex, successorIndex); // add a next edge
        }
    
        super.newControlFlowEdge(insnIndex, successorIndex);
    }

    /**
     * Connects the InsnBlock at the specified fromIndex to the InsnBlock at the specified toIndex
     * by adding the InsnBlock at toIndex as a successor of the InsnBlock at fromIndex.
     * 
     * @param fromIndex the index of the InsnBlock to add the next block from
     * @param toIndex the index of the InsnBlock to add as the next block
     */
    private void addNext(int fromIndex, int toIndex) {
        // Get the InsnBlock at the specified fromIndex
        InsnBlock currentBlock = getBlock(fromIndex);
        // Get the InsnBlock at the specified toIndex
        InsnBlock nextBlock = getBlock(toIndex);
        
        // Add the nextBlock as a successor of the currentBlock
        currentBlock.addNext(nextBlock);
    }

    /**
     * Adds a jump from the block at the specified 'fromIndex' to the block at the specified 'toIndex'.
     * 
     * @param fromIndex the index of the block from which the jump will be added
     * @param toIndex the index of the block to which the jump will be added
     */
    private void addJump(int fromIndex, int toIndex) {
        // Get the current block and the next block based on the provided indices
        InsnBlock currentBlock = getBlock(fromIndex);
        InsnBlock nextBlock = getBlock(toIndex);
        
        // Add a jump from the current block to the next block
        currentBlock.addJump(nextBlock);
        
        // Get the instruction node at the 'fromIndex'
        AbstractInsnNode insnNode = nodeArray[fromIndex];
        
        // Map the instruction node to the next block
        nodeToJumpBlockMap.put(insnNode, nextBlock);
        
        // Map the next block to its index
        blockToIndexMap.put(nextBlock, toIndex);
    }

    /**
     * Retrieves an InsnBlock at the specified index. If the block does not exist at the index,
     * a new InsnBlock is created and stored at that index.
     *
     * @param insnIndex the index of the InsnBlock to retrieve
     * @return the InsnBlock at the specified index
     */
    private InsnBlock getBlock(int insnIndex) {
        InsnBlock block = blocks[insnIndex]; // Retrieve the InsnBlock at the specified index
        if (block == null){
            block = new InsnBlock(); // Create a new InsnBlock if it doesn't exist at the index
            blocks[insnIndex] = block; // Store the new InsnBlock at the specified index
        }
        return block; // Return the InsnBlock
    }

    /**
     * Returns an array of InsnBlock objects representing the blocks of instructions.
     *
     * @return an array of InsnBlock objects
     */
    public InsnBlock[] getBlocks() {
        // Return the array of InsnBlock objects
        return blocks;
    }

}