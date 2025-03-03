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

    @Override
    public ArgTypeAnalysis analyze() {
        Analyzer<BasicValue> analyzer = new Analyzer<>(new SimpleVerifier());
        try {
            analyzer.analyze(className, methodNode);
            Frame<BasicValue>[] frames = analyzer.getFrames();
            AbstractInsnNode[] insts = methodNode.instructions.toArray();
            for(int i = 0;i < insts.length;i++) {
                AbstractInsnNode inst = insts[i];
                Frame<BasicValue> frame = frames[i];
                if(frame == null) {
                    continue;
                }
                if(inst instanceof MethodInsnNode minsn) {
                    BasicValue[] args = getArguments(minsn, frame);
                    String sig = StringUtils.buildMethodSignature(minsn, args);
                    methodDescMap.put(minsn, sig);
                }
            }
        } catch (NoClassDefFoundError e) {
            log.warn("Missing binaries when analyzing: " + className);
            log.trace(e);
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
        return this;
    }

    private BasicValue[] getArguments(MethodInsnNode insn, Frame<BasicValue> f) {
        String desc = insn.desc;
        Type[] args = Type.getArgumentTypes(desc);
        BasicValue[] values = new BasicValue[args.length];
        for (int i = 0; i < args.length; i++) {
            values[i] = getStackValue(f, args.length - i - 1);
        }
        return values;
    }

    private BasicValue getStackValue(Frame<BasicValue> f, int index) {
        int top = f.getStackSize() - 1;
        return index <= top ? f.getStack(top - index) : null;
    }

}
