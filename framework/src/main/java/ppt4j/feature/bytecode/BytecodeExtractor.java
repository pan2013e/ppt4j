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

    /**
     * This method creates and returns a new instance of BytecodeExtractor object with default values.
     * 
     * @return a new instance of BytecodeExtractor object
     */
    public static BytecodeExtractor nil() {
        // Create a new instance of BytecodeExtractor
        return new BytecodeExtractor();
    }

    /**
     * Parses the methods of the class by analyzing their bytecode instructions.
     * Skips native or abstract methods, methods without line number info, and <clinit> methods.
     * Performs ArgTypeAnalysis, LoopAnalysis, and clustering of instructions for each method.
     * Generates features for each instruction and removes RETURN instructions if present in <clinit> method.
     * Sets isParsed flag to true after parsing is complete.
     */
    @Override
    public void parse() {
        if (isParsed) {
            return;
        }
        // Parse each method in the class
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
        
        // Find and cluster <clinit> method if it exists
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
        
        // Generate features and remove RETURN instructions if present in <clinit> method
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
        
        // Set isParsed flag to true
        isParsed = true;
    }

    /**
     * This method takes a BytecodeExtractor object, parses it, and then stores it in a map called innerClasses
     * with the class name as the key.
     *
     * @param ex the BytecodeExtractor object to be stored in the innerClasses map
     */
    public void putInnerClass(@NonNull BytecodeExtractor ex) {
        // Parse the BytecodeExtractor object
        ex.parse();
        
        // Store the BytecodeExtractor object in the innerClasses map with the class name as the key
        innerClasses.put(ex.getClassName(), ex);
    }

    /**
     * Retrieves the BytecodeExtractor for the specified inner class by its class name.
     * 
     * @param className the name of the inner class to retrieve
     * @return the BytecodeExtractor for the specified inner class, or null if not found
     */
    public BytecodeExtractor getInnerClass(@NonNull String className) {
        // Return the BytecodeExtractor associated with the provided className from the innerClasses map
        return innerClasses.get(className);
    }

    /**
     * This method returns the source type of the features, which is determined to be bytecode.
     * @return The source type of the features, which is bytecode.
     */
    @Override
    public Features.SourceType getSourceType() {
        // Return the source type as bytecode
        return Features.SourceType.BYTECODE;
    }

    /**
     * Prints the features map and recursively prints the inner classes of the extractor.
     * If the root is null, it prints "nil extractor".
     */
    @Override
    public void print() {
        if(root == null) {
            System.out.println("nil extractor"); // Print message if root is null
        } else {
            getFeaturesMap().values().forEach(System.out::println); // Print values of features map
            innerClasses.values().forEach(Extractor::print); // Recursively print inner classes
        }
    }

    /**
     * Clusters instructions based on their line numbers.
     *
     * @param insts the list of instructions to cluster
     * @param map a map to store the clustered instructions based on line numbers
     */
    private void cluster(InsnList insts,
                         Map<Integer, List<AbstractInsnNode>> map) {
        Iterator<AbstractInsnNode> it = insts.iterator();
        List<AbstractInsnNode> insnList = null;
        while (it.hasNext()) {
            AbstractInsnNode node = it.next();
            // Skip LabelNode and FrameNode instructions
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
                // Handle instructions without line numbers
                if(insnList == null) {
                    throw new IllegalStateException(
                            "No line number node found");
                }
                insnList.add(node);
            }
        }
    }

}
