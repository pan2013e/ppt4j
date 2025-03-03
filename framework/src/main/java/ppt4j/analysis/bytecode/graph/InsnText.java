package ppt4j.analysis.bytecode.graph;

import ppt4j.annotation.Property;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public final class InsnText {

    @Property("ppt4j.analysis.bytecode.print_length_limit")
    private static int PRINT_LENGTH_LIMIT;

    Printer printer = new Textifier();

    /**
     * Converts an array of AbstractInsnNode objects into a list of strings representing each node.
     *
     * @param array the array of AbstractInsnNode objects to convert
     * @return a list of strings representing each AbstractInsnNode in the input array
     */
    public List<String> toLines(AbstractInsnNode[] array) {
        List<String> resultList = new ArrayList<>();
        
        // Check if the input array is null or empty, return an empty list if true
        if (array == null || array.length < 1) {
            return resultList;
        }
    
        // Iterate through each AbstractInsnNode in the input array
        for (AbstractInsnNode node : array) {
            // Convert the current node to a list of strings
            List<String> lines = toLines(node);
            // Add all the strings representing the current node to the result list
            resultList.addAll(lines);
        }
        
        return resultList;
    }

    /**
     * Converts the given AbstractInsnNode into a list of strings representing the lines of code.
     * The method uses a TraceMethodVisitor to visit the node and extract the text. Each line is trimmed
     * and checked against a PRINT_LENGTH_LIMIT, truncating it if necessary. The final list of lines is returned.
     *
     * @param node the AbstractInsnNode to convert to lines
     * @return a list of strings representing the lines of code extracted from the node
     */
    public List<String> toLines(AbstractInsnNode node) {
        List<String> resultList = new ArrayList<>();
        // Use TraceMethodVisitor to visit the node and extract the text
        node.accept(new TraceMethodVisitor(printer));
        for(Object o: printer.getText()) {
            String s = o.toString().trim();
            // Check if the line exceeds the PRINT_LENGTH_LIMIT and truncate if necessary
            if(s.length() > PRINT_LENGTH_LIMIT) {
                s = s.substring(0, PRINT_LENGTH_LIMIT - 3) + "...";
            }
            resultList.add(s);
        }
        // Clear the text from the printer for the next usage
        printer.getText().clear();
        return resultList;
    }

}