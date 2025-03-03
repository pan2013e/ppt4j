package ppt4j.analysis.bytecode;

import ppt4j.analysis.AbstractAnalysis;
import ppt4j.util.StringUtils;
import lombok.extern.log4j.Log4j;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SimpleVerifier;

import java.util.Map;

@SuppressWarnings("ClassCanBeRecord")
@Log4j
public class ArgTypeAnalysis implements AbstractAnalysis {

    private final String className;

    private final Map<MethodInsnNode, String> methodDescMap;

    private final MethodNode methodNode;

    public ArgTypeAnalysis(String className,
                           MethodNode methodNode,
                           Map<MethodInsnNode, String> methodDescMap) {
        this.className = className;
        this.methodNode = methodNode;
        this.methodDescMap = methodDescMap;
    }

    /**
     * Analyzes the given method by using a SimpleVerifier and populates a map with the method descriptions.
     * The method iterates over the instructions of the method and retrieves the frames from the analyzer to get the arguments of method invocations.
     * It then builds a method signature using the method instruction and the arguments, and stores it in a map.
     * If any exception occurs during the analysis, a log warning is generated.
     * 
     * @return this ArgTypeAnalysis object
     */
    @Override
    public ArgTypeAnalysis analyze() {
        // Create a new Analyzer with SimpleVerifier
        Analyzer<BasicValue> analyzer = new Analyzer<>(new SimpleVerifier());
        try {
            // Analyze the method using the analyzer
            analyzer.analyze(className, methodNode);
            Frame<BasicValue>[] frames = analyzer.getFrames();
            AbstractInsnNode[] insts = methodNode.instructions.toArray();
            for(int i = 0;i < insts.length;i++) {
                AbstractInsnNode inst = insts[i];
                Frame<BasicValue> frame = frames[i];
                if(frame == null) { // Skip if frame is null
                    continue;
                }
                if(inst instanceof MethodInsnNode minsn) {
                    // Get arguments of method invocation
                    BasicValue[] args = getArguments(minsn, frame);
                    // Build method signature
                    String sig = StringUtils.buildMethodSignature(minsn, args);
                    // Store method description in map
                    methodDescMap.put(minsn, sig);
                }
            }
        } catch (NoClassDefFoundError e) { // Catch missing binaries exception
            log.warn("Missing binaries when analyzing: " + className);
            log.trace(e);
        } catch (Exception e) { // Catch any other exceptions
            log.warn(e.getMessage());
        }
        return this;
    }

    /**
     * Retrieves the arguments of a method call from the given MethodInsnNode and Frame.
     *
     * @param insn the MethodInsnNode representing the method call
     * @param f the Frame containing the current operand stack values
     * @return an array of BasicValue objects representing the arguments of the method call
     */
    private BasicValue[] getArguments(MethodInsnNode insn, Frame<BasicValue> f) {
        // Get the method descriptor
        String desc = insn.desc;
        
        // Get the argument types of the method
        Type[] args = Type.getArgumentTypes(desc);
        
        // Create an array to store the argument values
        BasicValue[] values = new BasicValue[args.length];
        
        // Iterate through the argument types and retrieve the corresponding values from the operand stack
        for (int i = 0; i < args.length; i++) {
            // Arguments are stored in reverse order on the operand stack, so we need to adjust the index
            values[i] = getStackValue(f, args.length - i - 1);
        }
        
        return values;
    }

    /**
     * Retrieves the value at the specified index in the stack of the given frame.
     * 
     * @param f the frame containing the stack
     * @param index the index of the value to retrieve
     * @return the value at the specified index in the stack, or null if the index is out of bounds
     */
    private BasicValue getStackValue(Frame<BasicValue> f, int index) {
        int top = f.getStackSize() - 1; // get the top index of the stack
        return index <= top ? f.getStack(top - index) : null; // return the value at the calculated index, or null if index is out of bounds
    }

}
