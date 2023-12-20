package ppt4j.feature.bytecode;

import ppt4j.analysis.bytecode.ArgTypeAnalysis;
import ppt4j.analysis.bytecode.LoopAnalysis;
import ppt4j.annotation.Property;
import ppt4j.feature.Extractor;
import ppt4j.feature.Features;
import ppt4j.util.AsmUtils;
import ppt4j.util.StringUtils;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unused")
@Log4j
public final class BytecodeExtractor implements Extractor {

    @Serial
    private static final long serialVersionUID = 1L;

    @Property("org.objectweb.asm.api")
    private static int ASM_API;

    private transient final ClassNode root;

    @Getter
    private final Map<String, BytecodeExtractor>
            innerClasses = new HashMap<>();

    @Getter
    private final Map<Integer, Features> featuresMap = new TreeMap<>();

    private transient final Map<Integer, List<AbstractInsnNode>>
            aggInstMap = new TreeMap<>();

    @Getter
    private transient final Map<MethodInsnNode, String>
            methodDescMap = new HashMap<>();

    @Getter
    private transient final Set<AbstractInsnNode>
            backwardBranches = new HashSet<>();

    private boolean isParsed = false;

    @Getter
    private final String className;

    public BytecodeExtractor(
            @NonNull InputStream inputStream) throws IOException {
        this(new ClassReader(inputStream));
    }

    public BytecodeExtractor(@NonNull String classFile) throws IOException {
        this(new FileInputStream(StringUtils.resolvePath(classFile)));
    }

    public BytecodeExtractor(@NonNull ClassReader classReader) {
        root = new ClassNode(ASM_API);
        classReader.accept(root, ClassReader.EXPAND_FRAMES);
        className = root.name;
    }

    private BytecodeExtractor() {
        this.root = null;
        className = "fake";
    }

    public static BytecodeExtractor nil() {
        return new BytecodeExtractor();
    }

    @Override
    public void parse() {
        if (isParsed) {
            return;
        }
        root.methods.forEach(m -> {
            if (AsmUtils.isNative(m.access) || AsmUtils.isAbstract(m.access)) {
                log.debug("Skipping native or abstract method: " + m.name);
            } else {
                if(!AsmUtils.hasLineNumberInfo(m)) {
                    log.warn("Method " + m.name + " does not have line number info, skipping");
                } else {
                    new ArgTypeAnalysis(className, m, methodDescMap).analyze();
                    new LoopAnalysis(className, m, backwardBranches).analyze();
                    cluster(m.instructions, aggInstMap);
                }
            }
        });
        MethodNode clinit = null;
        for (MethodNode method : root.methods) {
            if(method.name.equals("<clinit>")) {
                clinit = method;
                break;
            }
        }
        Map<Integer, List<AbstractInsnNode>> clinitMap = new TreeMap<>();
        if(clinit != null) {
            cluster(clinit.instructions, clinitMap);
        }
        AtomicInteger idx = new AtomicInteger(0);
        aggInstMap.forEach((line, insts) -> {
            Features features = new BytecodeFeatures(
                    className, insts, line, idx.get(), this);
            if(clinitMap.containsKey(line)) {
                features.getInstructions().remove(Features.InstType.RETURN);
            }
            featuresMap.put(idx.get(), features);
            idx.incrementAndGet();
        });
        isParsed = true;
    }

    public void putInnerClass(@NonNull BytecodeExtractor ex) {
        ex.parse();
        innerClasses.put(ex.getClassName(), ex);
    }

    public BytecodeExtractor getInnerClass(@NonNull String className) {
        return innerClasses.get(className);
    }

    @Override
    public Features.SourceType getSourceType() {
        return Features.SourceType.BYTECODE;
    }

    @Override
    public void print() {
        if(root == null) {
            System.out.println("nil extractor");
        } else {
            getFeaturesMap().values().forEach(System.out::println);
            innerClasses.values().forEach(Extractor::print);
        }
    }

    private void cluster(InsnList insts,
                         Map<Integer, List<AbstractInsnNode>> map) {
        Iterator<AbstractInsnNode> it = insts.iterator();
        List<AbstractInsnNode> insnList = null;
        while (it.hasNext()) {
            AbstractInsnNode node = it.next();
            if(node instanceof LabelNode || node instanceof FrameNode) {
                continue;
            }
            if(node instanceof LineNumberNode lnn) {
                int line = lnn.line;
                if(map.containsKey(line)) {
                    insnList = map.get(line);
                } else {
                    insnList = new ArrayList<>();
                    map.put(line, insnList);
                }
            } else {
                if(insnList == null) {
                    throw new IllegalStateException(
                            "No line number node found");
                }
                insnList.add(node);
            }
        }
    }

}
