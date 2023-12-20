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

    public List<String> toLines(AbstractInsnNode[] array) {
        List<String> resultList = new ArrayList<>();
        if (array == null || array.length < 1) {
            return resultList;
        }

        for (AbstractInsnNode node : array) {
            List<String> lines = toLines(node);
            resultList.addAll(lines);
        }
        return resultList;
    }

    public List<String> toLines(AbstractInsnNode node) {
        List<String> resultList = new ArrayList<>();
        node.accept(new TraceMethodVisitor(printer));
        for(Object o: printer.getText()) {
            String s = o.toString().trim();
            if(s.length() > PRINT_LENGTH_LIMIT) {
                s = s.substring(0, PRINT_LENGTH_LIMIT - 3) + "...";
            }
            resultList.add(s);
        }
        printer.getText().clear();
        return resultList;
    }

}