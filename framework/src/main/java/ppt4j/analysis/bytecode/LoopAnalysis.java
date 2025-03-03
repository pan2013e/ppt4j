package ppt4j.analysis.bytecode;

import ppt4j.analysis.AbstractAnalysis;
import ppt4j.analysis.bytecode.graph.InsnBlock;
import lombok.extern.log4j.Log4j;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.BasicInterpreter;

import java.util.Set;

@SuppressWarnings("ClassCanBeRecord")
@Log4j
public class LoopAnalysis implements AbstractAnalysis {

    private final String className;

    private final Set<AbstractInsnNode> backwardBranches;

    private final MethodNode methodNode;

    public LoopAnalysis(String className,
                        MethodNode methodNode,
                        Set<AbstractInsnNode> backwardBranches) {
        this.className = className;
        this.methodNode = methodNode;
        this.backwardBranches = backwardBranches;
    }

    /**
     * This method analyzes the control flow of the given method by using a BasicBlockControlFlowAnalyzer. 
     * It iterates through the instructions of the method, identifies jump instructions, and checks for backward branches.
     * If a backward branch is found, it adds the jump instruction to the backwardBranches list.
     * 
     * @return The current instance of the class.
     */
    @Override
    public LoopAnalysis analyze() {
        BasicBlockControlFlowAnalyzer<?> analyzer = new BasicBlockControlFlowAnalyzer<>(new BasicInterpreter());
        try {
            analyzer.analyze(className, methodNode);
            analyzer.getBlocks();
            for(AbstractInsnNode node : methodNode.instructions) {
                if(node instanceof JumpInsnNode) {
                    InsnBlock block1 = analyzer.nodeToBlockMap.get(node);
                    InsnBlock block2 = analyzer.nodeToJumpBlockMap.get(node);
                    int idx1 = analyzer.blockToIndexMap.get(block1);
                    int idx2 = analyzer.blockToIndexMap.get(block2);
                    if(idx2 <= idx1) {
                        AbstractInsnNode[] insts = analyzer.getNodeArray();
                        for(int i = idx2; i < insts.length; i++) {
                            AbstractInsnNode inst = insts[i];
                            if(i > idx2 &&
                                    analyzer.isDelimiter(
                                        analyzer.getOriginalBlocks()[i])) {
                                break;
                            }
                            if(inst instanceof JumpInsnNode) {
                                backwardBranches.add(inst);
                            }
                        }
                    }
                }
            }
        } catch (NoClassDefFoundError e) {
            log.warn("Missing binaries when analyzing: " + className);
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
        return this;
    }

}
