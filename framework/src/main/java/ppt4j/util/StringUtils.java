package ppt4j.util;

import lombok.NonNull;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import ppt4j.Main;
import ppt4j.annotation.Property;
import ppt4j.database.Vulnerability;
import ppt4j.database.VulnerabilityInfo;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class StringUtils {

    @Property("user.home")
    private static String USER_HOME;

    @Property("ppt4j.database.root")
    private static String DB_ROOT;

    @Property("ppt4j.database.prepatch.name")
    private static String PREPATCH_NAME;

    @Property("ppt4j.database.postpatch.name")
    private static String POSTPATCH_NAME;

    @Property("ppt4j.classpath")
    private static String CLASSPATH;

    public static String printSet(Set<?> set) {
        return printSet(set, false, false);
    }

    public static String printSet(Set<?> set, boolean emphasizeString, boolean printType) {
        if(set.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[ ");
        Object[] arr = set.toArray();
        for (int i = 0; i < set.size(); i++) {
            Object s = arr[i];
            if(emphasizeString && s instanceof String) {
                sb.append(String.format("\"%s\"", s));
            } else {
                sb.append(String.format("%s", s));
            }
            if(printType) {
                sb.append(String.format(":%s", s.getClass().getSimpleName()));
            }
            if(i != set.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(" ]");
        return sb.toString();
    }

    public static String convertToDescriptor(String type) {
        type = type.replaceAll("<.*>", "");
        type = type.trim().replace(".", "/");
        return switch (type) {
            case "" -> "";
            case "<unknown>", "void" -> "V";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            default -> {
                if(type.endsWith("[]")) {
                    String baseType = type.substring(0, type.length() - 2);
                    yield String.format("[%s", convertToDescriptor(baseType));
                } else {
                    yield String.format("L%s;", type);
                }
            }
        };
    }

    public static boolean isPrimitive(String type) {
        if(type.startsWith("java.lang.")) {
            type = type.substring("java.lang.".length());
        }
        return switch (type) {
            case "Boolean", "Byte", "Character", "Short",
                    "Integer", "Long", "Float", "Double",
                    "boolean", "byte", "char", "short", "int", "long", "float",
                    "double", "Z", "B", "C", "S", "I", "J", "F", "D" -> true;
            default -> false;
        };
    }

    public static String convertMethodSignature(
            String nameAndArgs, String returnType) {
        String name = nameAndArgs.substring(0, nameAndArgs.indexOf("("));
        String[] args = nameAndArgs.substring(
                    nameAndArgs.indexOf("(") + 1, nameAndArgs.indexOf(")")
                ).split(",");
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append(":(");
        for (String arg : args) {
            sb.append(convertToDescriptor(arg));
        }
        sb.append(")");
        sb.append(convertToDescriptor(returnType));
        return sb.toString();
    }

    public static String convertQualifiedName(String name, String className) {
        return convertQualifiedName(name, className, true);
    }

    public static String convertQualifiedName(String name,
                                              String className,
                                              boolean retainClassName) {
        String _className = name.split("#")[0];
        if((_className.equals(className) || isPrimitive(_className)) && !retainClassName) {
            return name.split("#")[1];
        } else {
            return _className.replace(".", "/")
                    + "." + name.split("#")[1];
        }
    }

    public static boolean isJavaComment(String line) {
        String trimmed = line.trim();
        return trimmed.matches("//.*") || trimmed.matches("/\\*.*\\*/");
    }

    public static boolean isJavaCode(String line) {
        if(isJavaComment(line)) {
            return false;
        }
        String trimmed = line.trim();
        if(trimmed.isEmpty()) {
            return false;
        }
        String[] patterns = {
                "\\{",
                "\\}",
                "\\};",
                "else",
                "\\}\\s*else\\s*\\{",
                "else\\s*\\{",
                "\\}\\s*else",
        };
        for (String pattern : patterns) {
            if(trimmed.matches(pattern)) {
                return false;
            }
        }
        return true;
    }

    public static String trimJavaCodeLine(String codeLine) {
        codeLine = codeLine.replaceAll("//.*", "");
        codeLine = codeLine.replaceAll("/\\*.*\\*/", "");
        codeLine = codeLine.replaceAll("\\s+$", "");
        codeLine = codeLine.replaceAll("^\\s+", "");
        if (codeLine.endsWith("{")) {
            codeLine = codeLine.substring(0, codeLine.length() - 1);
        }
        return codeLine.trim();
    }

    public static @NonNull Main.Command matchPrefix(String prefix) {
        Main.Command matched = null;
        for (Main.Command command : Main.Command.values()) {
            if(command.name.startsWith(prefix.toLowerCase())) {
                if(matched != null) {
                    System.err.println("Ambiguous command: " + prefix);
                    System.err.println("Possible commands:");
                    for (Main.Command c : Main.Command.values()) {
                        if(c.name.startsWith(prefix)) {
                            System.err.println(c.name);
                        }
                    }
                    System.exit(1);
                }
                matched = command;
            }
        }
        if(matched == null) {
            System.err.println("Unknown command: " + prefix);
            System.exit(1);
        }
        return matched;
    }

    public static byte[] toBytes(List<String> lines) {
        return String.join("\n", lines).getBytes();
    }

    @SuppressWarnings("HttpUrlsUsage")
    public static URL toURL(String path) {
        try {
            if(path.startsWith("http://") || path.startsWith("https://")) {
                return new URL(path);
            } else {
                return new File(path).toURI().toURL();
            }
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String extractClassName(String filePath,
                                          String topLevelPrefix) {
        if(topLevelPrefix.endsWith("/")) {
            topLevelPrefix =
                    topLevelPrefix.substring(0, topLevelPrefix.length() - 1);
        }
        String className = filePath.substring(
                topLevelPrefix.length() + 1, filePath.length() - 5
        );
        return className.replace("/", ".");
    }

    public static String buildMethodSignature(MethodInsnNode minsn, BasicValue[] args) {
        String returnType = minsn.desc.split("\\)")[1];
        StringBuilder argTypes = new StringBuilder();
        for(BasicValue arg : args) {
            argTypes.append(arg.getType().getDescriptor());
        }
        return "(" + argTypes + ")" + returnType;
    }

    public static String substringBetween(String str, char c1, char c2) {
        int start = str.indexOf(c1);
        int end = str.lastIndexOf(c2);
        if(start == -1 || end == -1) {
            return null;
        }
        return str.substring(start + 1, end);
    }

    public static String toWrapperType(String type) {
        return switch (type) {
            case "boolean" -> "java/lang/Boolean";
            case "byte" -> "java/lang/Byte";
            case "char" -> "java/lang/Character";
            case "short" -> "java/lang/Short";
            case "int" -> "java/lang/Integer";
            case "long" -> "java/lang/Long";
            case "float" -> "java/lang/Float";
            case "double" -> "java/lang/Double";
            default -> type;
        };
    }

    public static String resolvePath(String path) {
        // if the path begins with ~, replace it with the home directory
        if(path.startsWith("~")) {
            path = USER_HOME + path.substring(1);
        }
        return path;
    }

    public static String[] splitArgDesc(String desc) {
        String argDescs = substringBetween(desc, '(', ')');
        if(argDescs == null || argDescs.isEmpty()) {
            return new String[0];
        }
        List<String> args = new ArrayList<>();
        for(int i = 0; i < argDescs.length(); i++) {
            char c = argDescs.charAt(i);
            if(c == 'L') {
                int end = argDescs.indexOf(';', i);
                args.add(argDescs.substring(i, end + 1));
                i = end;
            } else if(c == '[') {
                int end = i;
                while(argDescs.charAt(end) == '[') {
                    end++;
                }
                if(argDescs.charAt(end) == 'L') {
                    end = argDescs.indexOf(';', end);
                }
                args.add(argDescs.substring(i, end + 1));
                i = end;
            } else {
                args.add(String.valueOf(c));
            }
        }
        return args.toArray(String[]::new);
    }

    public static String joinArgDesc(List<String> descList) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for(String desc : descList) {
            sb.append(desc);
        }
        sb.append(")");
        return sb.toString();
    }

    public static String getDatabasePrepatchSrcPath(Vulnerability vuln) {
        int id = vuln.getDatabaseId();
        String innerPath =  "/" + vuln.getJavaSrcTopLevelDir();
        return String.format("%s/%s/%d%s",
                DB_ROOT, PREPATCH_NAME, id, innerPath);
    }

    public static String getDatabasePostpatchSrcPath(Vulnerability vuln) {
        int id = vuln.getDatabaseId();
        String innerPath = "/" + vuln.getJavaSrcTopLevelDir();
        return String.format("%s/%s/%d%s",
                DB_ROOT, POSTPATCH_NAME, id, innerPath);
    }

    public static String getDatabasePrepatchClassPath(Vulnerability vuln) {
        int id = vuln.getDatabaseId();
        return resolvePath(String.format("%s/%s/%d/%s",
                DB_ROOT, PREPATCH_NAME, id, vuln.getClassesTopLevelDir()));
    }

    public static String getDatabasePostpatchClassPath(Vulnerability vuln) {
        int id = vuln.getDatabaseId();
        return resolvePath(String.format("%s/%s/%d/%s",
                DB_ROOT, POSTPATCH_NAME, id, vuln.getClassesTopLevelDir()));
    }

    public static String[] getThirdPartySrcDirsFromPrepatch(
            Vulnerability vuln) {
        int id = vuln.getDatabaseId();
        String[] thirdParties = vuln.getThirdPartySrcDirs().clone();
        for(int i = 0;i < thirdParties.length;i++) {
            thirdParties[i] = String.format("%s/%s/%d/%s",
                DB_ROOT, PREPATCH_NAME, id, thirdParties[i]);
            thirdParties[i] = resolvePath(thirdParties[i]);
        }
        return thirdParties;
    }

    public static String[] getThirdPartyLibDirsFromPrepatch(
            Vulnerability vuln) {
        int id = vuln.getDatabaseId();
        String[] thirdParties = vuln.getThirdPartyLibDirs().clone();
        for(int i = 0;i < thirdParties.length;i++) {
            thirdParties[i] = String.format("%s/%s/%d/%s",
                DB_ROOT, PREPATCH_NAME, id, thirdParties[i]);
            thirdParties[i] = resolvePath(thirdParties[i]);
        }
        return thirdParties;
    }

    public static int extractDatabaseId(String name) {
        // pattern: VUL4J-%d
        // extract the number and return it
        if(name.matches("VUL4J-\\d+")) {
            return Integer.parseInt(name.substring(6));
        } else {
            throw new IllegalStateException("Invalid database name: " + name);
        }
    }

    public static String getJavaSrcTopLevelDir(VulnerabilityInfo info) {
        if(info.src_top_level_dir != null) {
            return info.src_top_level_dir;
        }
        if(info.build_system.equals("Maven")) {
            String targetDir = info.src_classes_dir;
            if(targetDir.equals("target/classes")) {
                return "src/main/java";
            }
            int index = targetDir.indexOf("/target/classes");
            if(index == -1) {
                throw new IllegalStateException("Invalid target directory: " + targetDir);
            }
            String base = targetDir.substring(0, index);
            return base + "/src/main/java";
        }
        if(info.build_system.equals("Gradle")) {
            String targetDir = info.src_classes_dir;
            if(targetDir.equals("build/classes/java/main") ||
                    targetDir.equals("build/classes/main")) {
                return "src/main/java";
            }
            int index = targetDir.indexOf("/build/classes/java/main");
            if(index == -1) {
                index = targetDir.indexOf("/build/classes/main");
            }
            if(index == -1) {
                throw new IllegalStateException("Invalid target directory: " + targetDir);
            }
            String base = targetDir.substring(0, index);
            return base + "/src/main/java";
        }
        throw new IllegalStateException("Must specify src_top_level_dir with custom build system");
    }

    public static String buildNewStringArrayCode(String[] arr) {
        if(arr == null || arr.length == 0) {
            return "new String[0]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("new String[] {");
        for(String e : arr) {
            sb.append("\"").append(e).append("\",");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append("}");
        return sb.toString();
    }

    public static String getThirdPartySrcDirsString(VulnerabilityInfo info) {
        return buildNewStringArrayCode(info.third_party_src_dirs);
    }

    public static String getClassPathToLoad(Vulnerability vuln) {

        return String.join(File.pathSeparator,
                CLASSPATH,
                getDatabasePrepatchClassPath(vuln),
                getDatabasePostpatchClassPath(vuln),
                String.join(File.pathSeparator,
                        getThirdPartyLibDirsFromPrepatch(vuln)));
    }

    public static String getThirdPartyLibDirsString(VulnerabilityInfo info) {
        return buildNewStringArrayCode(info.third_party_lib_dirs);
    }
}