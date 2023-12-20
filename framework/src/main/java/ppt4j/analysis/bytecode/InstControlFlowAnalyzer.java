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

    @Override
    protected void newControlFlowEdge(int insnIndex, int successorIndex) {
        AbstractInsnNode insnNode = nodeArray[insnIndex];
        int insnOpcode = insnNode.getOpcode();
        int insnType = insnNode.getType();

        if (insnType == AbstractInsnNode.JUMP_INSN) {
            if ((insnIndex + 1) == successorIndex) {
                addNext(insnIndex, successorIndex);
            }
            else {
                addJump(insnIndex, successorIndex);
            }
        }
        else if (insnOpcode == LOOKUPSWITCH) {
            addJump(insnIndex, successorIndex);
        }
        else if (insnOpcode == TABLESWITCH) {
            addJump(insnIndex, successorIndex);
        }
        else if (insnOpcode == RET) {
            addJump(insnIndex, successorIndex);
        }
        else if (insnOpcode == ATHROW ||
                (insnOpcode >= IRETURN && insnOpcode <= RETURN)) {
            throw new IllegalStateException("Unexpected opcode: " + insnOpcode);
        }
        else {
            addNext(insnIndex, successorIndex);
        }

        super.newControlFlowEdge(insnIndex, successorIndex);
    }

    private void addNext(int fromIndex, int toIndex) {
        InsnBlock currentBlock = getBlock(fromIndex);
        InsnBlock nextBlock = getBlock(toIndex);
        currentBlock.addNext(nextBlock);
    }

    private void addJump(int fromIndex, int toIndex) {
        InsnBlock currentBlock = getBlock(fromIndex);
        InsnBlock nextBlock = getBlock(toIndex);
        currentBlock.addJump(nextBlock);
        AbstractInsnNode insnNode = nodeArray[fromIndex];
        nodeToJumpBlockMap.put(insnNode, nextBlock);
        blockToIndexMap.put(nextBlock, toIndex);
    }

    private InsnBlock getBlock(int insnIndex) {
        InsnBlock block = blocks[insnIndex];
        if (block == null){
            block = new InsnBlock();
            blocks[insnIndex] = block;
        }
        return block;
    }

    public InsnBlock[] getBlocks() {
        return blocks;
    }

}