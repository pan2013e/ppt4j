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

    /**
     * This method takes a Set of unknown type elements and returns a String representation of the elements.
     * If the second parameter is set to true, the elements will be printed in a sorted order.
     * If the third parameter is set to true, the elements will be printed in a reversed order. 
     * 
     * @param set the Set of elements to be printed
     * @return a String representation of the elements in the Set
     */
    public static String printSet(Set<?> set) {
        // Call the overloaded method with the default values for sorting and reversing
        return printSet(set, false, false);
    }

    /**
     * This method takes a Set and options to emphasize strings and print types, and returns a formatted string
     * representation of the elements in the set. If set is empty, it returns "[]". If emphasizeString is true,
     * strings in the set are enclosed in double quotes. If printType is true, the class name of each element is
     * appended after the value.
     *
     * @param set the Set to be printed
     * @param emphasizeString whether to emphasize strings with double quotes
     * @param printType whether to print the class name of each element
     * @return a formatted string representation of the elements in the set
     */
    public static String printSet(Set<?> set, boolean emphasizeString, boolean printType) {
        if(set.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[ ");
        Object[] arr = set.toArray();
        for (int i = 0; i < set.size(); i++) {
            Object s = arr[i];
            if(emphasizeString && s instanceof String) { // Check if emphasizing strings is required
                sb.append(String.format("\"%s\"", s));
            } else {
                sb.append(String.format("%s", s));
            }
            if(printType) { // Check if printing the type is required
                sb.append(String.format(":%s", s.getClass().getSimpleName()));
            }
            if(i != set.size() - 1) { // Add a comma if not at the last element
                sb.append(", ");
            }
        }
        sb.append(" ]");
        return sb.toString();
    }

    /**
     * Converts a given type name to its corresponding descriptor as per the JVM specification.
     * The descriptor is used in bytecode representation of Java classes.
     * 
     * @param type the type name to convert
     * @return the descriptor corresponding to the given type name
     */
    public static String convertToDescriptor(String type) {
        // Remove generic type information
        type = type.replaceAll("<.*>", "");
        // Replace '.' with '/' and remove leading/trailing whitespaces
        type = type.trim().replace(".", "/");
        // Switch statement to determine descriptor based on type
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
                    // Handle arrays by recursively calling the method
                    String baseType = type.substring(0, type.length() - 2);
                    yield String.format("[%s", convertToDescriptor(baseType));
                } else {
                    // For objects, format as 'Lclassname;'
                    yield String.format("L%s;", type);
                }
            }
        };
    }

    /**
     * Determines if the given type is a primitive data type in Java.
     *
     * @param type the type to check
     * @return true if the type is a primitive data type, false otherwise
     */
    public static boolean isPrimitive(String type) {
            // Check if the type starts with "java.lang." and remove it if necessary
            if(type.startsWith("java.lang.")) {
                type = type.substring("java.lang.".length());
            }
            
            // Return true if the type is a primitive data type, false otherwise
            return switch (type) {
                case "Boolean", "Byte", "Character", "Short",
                        "Integer", "Long", "Float", "Double",
                        "boolean", "byte", "char", "short", "int", "long", "float",
                        "double", "Z", "B", "C", "S", "I", "J", "F", "D" -> true;
                default -> false;
            };
        }

    /**
     * Converts a method signature from a human-readable format to a JVM descriptor format.
     * 
     * @param nameAndArgs the method name and arguments in human-readable format
     * @param returnType the return type of the method in human-readable format
     * @return the method signature in JVM descriptor format
     */
    public static String convertMethodSignature(
            String nameAndArgs, String returnType) {
        // Extract the method name from the nameAndArgs parameter
        String name = nameAndArgs.substring(0, nameAndArgs.indexOf("("));
        // Extract the arguments from the nameAndArgs parameter
        String[] args = nameAndArgs.substring(
                    nameAndArgs.indexOf("(") + 1, nameAndArgs.indexOf(")")
                ).split(",");
        // Create a StringBuilder to build the method signature
        StringBuilder sb = new StringBuilder();
        // Append the method name to the StringBuilder
        sb.append(name);
        // Append the opening parenthesis for the arguments
        sb.append(":(");
        // Iterate through the arguments and convert each to JVM descriptor format
        for (String arg : args) {
            sb.append(convertToDescriptor(arg));
        }
        // Append the closing parenthesis for the arguments
        sb.append(")");
        // Convert the return type to JVM descriptor format and append it to the StringBuilder
        sb.append(convertToDescriptor(returnType));
        // Return the final method signature as a String
        return sb.toString();
    }

    /**
     * Converts the given name to a qualified name by appending the class name to it.
     * 
     * @param name the name to convert
     * @param className the class name to append
     * @return the qualified name with the class name appended
     */
    public static String convertQualifiedName(String name, String className) {
        // delegate to the overloaded method with includePackage set to true
        return convertQualifiedName(name, className, true);
    }

    /**
     * Converts a qualified name by separating the class name and the method name.
     * If the class name matches the specified className or is a primitive type, and retainClassName is false,
     * it returns only the method name. Otherwise, it returns the qualified name with the class name converted to bytecode format.
     * 
     * @param name the qualified name to convert
     * @param className the class name to compare with
     * @param retainClassName whether to retain the class name in the output
     * @return the converted qualified name
     */
    public static String convertQualifiedName(String name, String className, boolean retainClassName) {
        // Split the qualified name to separate class name and method name
        String _className = name.split("#")[0];
        
        // Check if the class name matches the specified className or is a primitive type, and retainClassName is false
        if((_className.equals(className) || isPrimitive(_className)) && !retainClassName) {
            // Return only the method name
            return name.split("#")[1];
        } else {
            // Return the qualified name with the class name converted to bytecode format
            return _className.replace(".", "/") + "." + name.split("#")[1];
        }
    }

    /**
     * Checks if a given line is a Java comment.
     * A Java comment can be either a single line comment starting with "//" or a multi-line comment enclosed in /* * /.
     * 
     * @param line the input line to check
     * @return true if the line is a Java comment, false otherwise
     */
    public static boolean isJavaComment(String line) {
            // Trim any leading or trailing whitespace from the input line
            String trimmed = line.trim();
            
            // Check if the trimmed line matches the pattern for a single line comment "//.*"
            // or a multi-line comment "/\*.*\*/"
            return trimmed.matches("//.*") || trimmed.matches("/\\*.*\\*/");
        }

    /**
     * Checks if the provided line of text is valid Java code by analyzing its content.
     * Returns true if the line contains valid Java code, and false otherwise.
     * 
     * @param line the line of text to check
     * @return true if the line contains valid Java code, false otherwise
     */
    public static boolean isJavaCode(String line) {
        // Check if the line is a Java comment, if so, return false
        if(isJavaComment(line)) {
            return false;
        }
        
        // Trim the line to remove leading and trailing whitespaces
        String trimmed = line.trim();
        
        // If the trimmed line is empty, return false
        if(trimmed.isEmpty()) {
            return false;
        }
        
        // Array of patterns to check for specific Java code elements
        String[] patterns = {
                "\\{",
                "\\}",
                "\\};",
                "else",
                "\\}\\s*else\\s*\\{",
                "else\\s*\\{",
                "\\}\\s*else",
        };
        
        // Check if the trimmed line matches any of the patterns, if so, return false
        for (String pattern : patterns) {
            if(trimmed.matches(pattern)) {
                return false;
            }
        }
        
        // If none of the above conditions are met, return true
        return true;
    }

    /**
     * Trims a line of Java code by removing comments, leading and trailing whitespace,
     * and removing the opening curly brace if it is the last character.
     *
     * @param codeLine the line of Java code to be trimmed
     * @return the trimmed version of the input code line
     */
    public static String trimJavaCodeLine(String codeLine) {
        // Remove single line comments
        codeLine = codeLine.replaceAll("//.*", "");
        // Remove multiline comments
        codeLine = codeLine.replaceAll("/\\*.*\\*/", "");
        // Remove trailing whitespace
        codeLine = codeLine.replaceAll("\\s+$", "");
        // Remove leading whitespace
        codeLine = codeLine.replaceAll("^\\s+", "");
        // Remove opening curly brace if it is the last character
        if (codeLine.endsWith("{")) {
            codeLine = codeLine.substring(0, codeLine.length() - 1);
        }
        return codeLine.trim();
    }

    /**
     * Matches a command enum based on the given prefix string.
     * If multiple commands are found with the same prefix, it prints an error message
     * and exits the program. If no commands are found with the prefix, it prints an
     * error message and exits the program.
     *
     * @param prefix the prefix to match the command enum
     * @return the matched command enum
     */
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

    /**
     * Converts a list of strings into a byte array. Each string in the list is joined with a newline character ('\n') and then converted into bytes.
     *
     * @param lines the list of strings to convert
     * @return a byte array representing the joined strings
     */
    public static byte[] toBytes(List<String> lines) {
        return String.join("\n", lines).getBytes();
    }

    /**
     * Converts a given file path or URL string to a URL object. If the path starts with "http://" or "https://",
     * it creates a URL object. Otherwise, it creates a URL object from a File object representing the path.
     *
     * @param path the file path or URL string to convert
     * @return a URL object representing the given path
     */
    @SuppressWarnings("HttpUrlsUsage")
    public static URL toURL(String path) {
        try {
            if(path.startsWith("http://") || path.startsWith("https://")) { // Check if the path is a URL
                return new URL(path);
            } else { // Convert file path to URL
                return new File(path).toURI().toURL();
            }
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Extracts the class name from a file path based on a specified top level prefix.
     * 
     * @param filePath the file path from which to extract the class name
     * @param topLevelPrefix the top level prefix to remove from the file path
     * @return the extracted class name with '/' replaced by '.'
     */
    public static String extractClassName(String filePath, String topLevelPrefix) {
            // Remove trailing '/' if present in the top level prefix
            if(topLevelPrefix.endsWith("/")) {
                topLevelPrefix = topLevelPrefix.substring(0, topLevelPrefix.length() - 1);
            }
            
            // Extract the class name from the file path by removing the top level prefix and file extension
            String className = filePath.substring(
                    topLevelPrefix.length() + 1, filePath.length() - 5
            );
            
            // Replace '/' with '.' in the class name
            return className.replace("/", ".");
        }

    /**
     * Builds a method signature based on the given MethodInsnNode and arguments.
     *
     * This method extracts the return type from the MethodInsnNode and concatenates it with
     * the descriptors of the argument types from the BasicValue array to form the method signature.
     *
     * @param minsn the MethodInsnNode representing the method
     * @param args an array of BasicValue representing the argument types
     * @return the method signature as a String
     */
    public static String buildMethodSignature(MethodInsnNode minsn, BasicValue[] args) {
        String returnType = minsn.desc.split("\\)")[1]; // Extracting the return type from the MethodInsnNode description
        StringBuilder argTypes = new StringBuilder();
        for(BasicValue arg : args) {
            argTypes.append(arg.getType().getDescriptor()); // Appending the descriptor of each argument type
        }
        return "(" + argTypes + ")" + returnType; // Concatenating argument types and return type to form the method signature
    }

    /**
     * Returns the substring of a given string that is between two specified characters.
     *
     * @param str the input string
     * @param c1 the starting character
     * @param c2 the ending character
     * @return the substring between the two specified characters, or null if either character is not found
     */
    public static String substringBetween(String str, char c1, char c2) {
        // Find the index of the starting character
        int start = str.indexOf(c1);
        // Find the index of the ending character
        int end = str.lastIndexOf(c2);
        
        // If either character is not found, return null
        if(start == -1 || end == -1) {
            return null;
        }
        
        // Return the substring between the two characters
        return str.substring(start + 1, end);
    }

    /**
     * Converts a primitive type to its corresponding wrapper class type.
     *
     * @param type the primitive type to convert
     * @return the wrapper class type
     */
    public static String toWrapperType(String type) {
            return switch (type) {
                case "boolean" -> "java/lang/Boolean"; // convert boolean to Boolean
                case "byte" -> "java/lang/Byte"; // convert byte to Byte
                case "char" -> "java/lang/Character"; // convert char to Character
                case "short" -> "java/lang/Short"; // convert short to Short
                case "int" -> "java/lang/Integer"; // convert int to Integer
                case "long" -> "java/lang/Long"; // convert long to Long
                case "float" -> "java/lang/Float"; // convert float to Float
                case "double" -> "java/lang/Double"; // convert double to Double
                default -> type; // return the type if not a primitive type
            };
        }

    /**
     * Resolves the given path by replacing the "~" symbol with the user's home directory.
     * 
     * @param path the path to be resolved
     * @return the resolved path with the "~" symbol replaced by the home directory
     */
    public static String resolvePath(String path) {
            // if the path begins with ~, replace it with the home directory
            if(path.startsWith("~")) {
                path = USER_HOME + path.substring(1);
            }
            return path;
        }

    /**
     * Splits the argument descriptor of a method description into individual argument descriptors.
     * 
     * @param desc the method description containing the argument descriptor
     * @return an array of individual argument descriptors
     */
    public static String[] splitArgDesc(String desc) {
        String argDescs = substringBetween(desc, '(', ')'); // Extract argument descriptor
        if(argDescs == null || argDescs.isEmpty()) {
            return new String[0]; // Return empty array if no arguments found
        }
        List<String> args = new ArrayList<>();
        for(int i = 0; i < argDescs.length(); i++) {
            char c = argDescs.charAt(i);
            if(c == 'L') {
                int end = argDescs.indexOf(';', i);
                args.add(argDescs.substring(i, end + 1)); // Add object argument descriptor
                i = end;
            } else if(c == '[') {
                int end = i;
                while(argDescs.charAt(end) == '[') {
                    end++;
                }
                if(argDescs.charAt(end) == 'L') {
                    end = argDescs.indexOf(';', end);
                }
                args.add(argDescs.substring(i, end + 1)); // Add array argument descriptor
                i = end;
            } else {
                args.add(String.valueOf(c)); // Add primitive argument descriptor
            }
        }
        return args.toArray(String[]::new); // Convert list to array and return
    }

    /**
     * Concatenates a list of strings to form a single description with parentheses
     * 
     * @param descList the list of strings to be concatenated
     * @return a string containing all the descriptions in the list enclosed in parentheses
     */
    public static String joinArgDesc(List<String> descList) {
        StringBuilder sb = new StringBuilder();
        sb.append("("); // Add opening parentheses
        for(String desc : descList) {
            sb.append(desc); // Append each description to the StringBuilder
        }
        sb.append(")"); // Add closing parentheses
        return sb.toString(); // Return the concatenated string
    }

    /**
     * Constructs the source path for the prepatch of a given vulnerability in the database.
     * The source path is formed by concatenating the root database path, prepatch name, vulnerability id,
     * and the inner path of the Java source top level directory of the vulnerability.
     * 
     * @param vuln the Vulnerability object representing the vulnerability
     * @return the source path for the prepatch of the vulnerability in the database
     */
    public static String getDatabasePrepatchSrcPath(Vulnerability vuln) {
        int id = vuln.getDatabaseId(); // Get the database id of the vulnerability
        String innerPath =  "/" + vuln.getJavaSrcTopLevelDir(); // Get the inner path of the Java source top level directory
        return String.format("%s/%s/%d%s",
                DB_ROOT, PREPATCH_NAME, id, innerPath); // Format and return the complete source path
    }

    /**
     * Returns the source path for the postpatch of a vulnerability in the database.
     * The source path is constructed using the database root, postpatch name, vulnerability id,
     * and the top level directory of the Java source file related to the vulnerability.
     * 
     * @param vuln the Vulnerability object representing the vulnerability
     * @return the source path for the postpatch of the vulnerability
     */
    public static String getDatabasePostpatchSrcPath(Vulnerability vuln) {
        int id = vuln.getDatabaseId(); // Get the database id of the vulnerability
        String innerPath = "/" + vuln.getJavaSrcTopLevelDir(); // Get the top level directory of the Java source file
        return String.format("%s/%s/%d%s",
                DB_ROOT, POSTPATCH_NAME, id, innerPath); // Format and return the source path
    }

    /**
     * This method constructs the class path for the prepatch classes related to a given vulnerability.
     * The class path is constructed based on the vulnerability's database ID and top-level directory.
     * 
     * @param vuln the Vulnerability object for which to construct the class path
     * @return the class path for the prepatch classes related to the given vulnerability
     */
    public static String getDatabasePrepatchClassPath(Vulnerability vuln) {
        // Get the database ID of the vulnerability
        int id = vuln.getDatabaseId();
        
        // Construct the class path using the database root, prepatch name, vulnerability ID, and top-level directory
        return resolvePath(String.format("%s/%s/%d/%s",
                DB_ROOT, PREPATCH_NAME, id, vuln.getClassesTopLevelDir()));
    }

    /**
     * Constructs a database postpatch class path for a given vulnerability by combining the root directory of the database, the postpatch name, the vulnerability's database ID, and the top-level directory of the vulnerability's classes.
     *
     * @param vuln the Vulnerability object for which the database postpatch class path is being generated
     * @return the database postpatch class path for the given vulnerability
     */
    public static String getDatabasePostpatchClassPath(Vulnerability vuln) {
        // Get the database ID of the vulnerability
        int id = vuln.getDatabaseId();
        
        // Construct the database postpatch class path by formatting the root directory, postpatch name, database ID, and top-level directory of classes
        return resolvePath(String.format("%s/%s/%d/%s",
                DB_ROOT, POSTPATCH_NAME, id, vuln.getClassesTopLevelDir()));
    }

    /**
     * Retrieves the third party source directories related to a given vulnerability from the prepatch database.
     * The method constructs the full path for each directory using the database root, prepatch name, vulnerability id, and the directory name.
     * It then resolves the full path to ensure proper formatting before returning an array of the third party source directories.
     *
     * @param vuln the Vulnerability object representing the vulnerability
     * @return an array of strings containing the full paths of the third party source directories
     */
    public static String[] getThirdPartySrcDirsFromPrepatch(Vulnerability vuln) {
            int id = vuln.getDatabaseId();
            String[] thirdParties = vuln.getThirdPartySrcDirs().clone(); // Copying the array to avoid modifying the original
            for(int i = 0; i < thirdParties.length; i++) {
                thirdParties[i] = String.format("%s/%s/%d/%s",
                    DB_ROOT, PREPATCH_NAME, id, thirdParties[i]); // Constructing the full path
                thirdParties[i] = resolvePath(thirdParties[i]); // Resolving the full path
            }
            return thirdParties;
        }

    /**
     * Retrieves the directories of third-party libraries associated with a given vulnerability
     * from the prepatch directory structure. The method appends the prepatch root, prepatch name,
     * vulnerability database ID, and third-party library directory to each directory path. 
     * It then resolves the full path of each directory before returning an array of strings
     * containing the updated directory paths.
     * 
     * @param vuln the Vulnerability object for which to retrieve third-party library directories
     * @return an array of strings representing the full paths of third-party library directories
     */
    public static String[] getThirdPartyLibDirsFromPrepatch(Vulnerability vuln) {
        int id = vuln.getDatabaseId();
        String[] thirdParties = vuln.getThirdPartyLibDirs().clone(); // create a copy of the array
        for (int i = 0; i < thirdParties.length; i++) {
            // format the directory path with prepatch root, prepatch name, vulnerability ID, and third-party library directory
            thirdParties[i] = String.format("%s/%s/%d/%s", DB_ROOT, PREPATCH_NAME, id, thirdParties[i]);
            thirdParties[i] = resolvePath(thirdParties[i]); // resolve the full path
        }
        return thirdParties; // return the array of updated directory paths
    }

    /**
     * Extracts the database ID from the provided database name in the format VUL4J-%d.
     * The method parses the input string to check if it matches the pattern VUL4J-%d,
     * where %d is a numerical value. If the input matches the pattern, it extracts
     * the numerical value and returns it as an integer. If the input does not match
     * the expected pattern, an IllegalStateException is thrown.
     *
     * @param name the database name in the format VUL4J-%d
     * @return the extracted database ID as an integer
     * @throws IllegalStateException if the input string does not match the expected pattern
     */
    public static int extractDatabaseId(String name) {
        // pattern: VUL4J-%d
        // extract the number and return it
        if(name.matches("VUL4J-\\d+")) {
            return Integer.parseInt(name.substring(6)); // extracting the numerical value starting from index 6
        } else {
            throw new IllegalStateException("Invalid database name: " + name);
        }
    }

    /**
     * This method determines the top level source directory based on the information provided in the VulnerabilityInfo object.
     * If the src_top_level_dir is specified in the object, it is returned. Otherwise, it determines the top level directory
     * based on the build system specified in the object (Maven or Gradle) and the src_classes_dir information.
     * For Maven projects, it checks the target directory to determine the source directory.
     * For Gradle projects, it checks the target directory to determine the source directory.
     * If the build system is not Maven or Gradle, it throws an IllegalStateException.
     * 
     * @param info the VulnerabilityInfo object containing the information needed to determine the top level source directory
     * @return the top level source directory based on the provided information
     */
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

    /**
     * This method takes an array of Strings and builds a new String representation of the array in code format.
     * If the input array is null or empty, it returns "new String[0]".
     * Otherwise, it constructs a code snippet representing the input array in the format "new String[] {"element1", "element2", ...}".
     * 
     * @param arr the input array of Strings
     * @return a String representing the input array in code format
     */
    public static String buildNewStringArrayCode(String[] arr) {
        if(arr == null || arr.length == 0) {
            return "new String[0]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("new String[] {");
        for(String e : arr) {
            sb.append("\"").append(e).append("\","); // add each element surrounded by double quotes
        }
        sb.deleteCharAt(sb.length() - 1); // remove the trailing comma
        sb.append("}");
        return sb.toString();
    }

    /**
     * This method takes a VulnerabilityInfo object as input and retrieves the third party source directories stored in it.
     * It then calls the buildNewStringArrayCode method to convert the array of directories into a string representation.
     * 
     * @param info the VulnerabilityInfo object containing the third party source directories
     * @return a string representation of the third party source directories
     */
    public static String getThirdPartySrcDirsString(VulnerabilityInfo info) {
        // Call buildNewStringArrayCode method to convert the array of directories into a string
        return buildNewStringArrayCode(info.third_party_src_dirs);
    }

    /**
     * Constructs the classpath needed to load the necessary resources for a given vulnerability.
     * The classpath includes the base CLASSPATH, the database prepatch class path, the database postpatch class path,
     * and the third party library directories from the prepatch.
     * 
     * @param vuln the vulnerability for which the classpath is being generated
     * @return the classpath as a string
     */
    public static String getClassPathToLoad(Vulnerability vuln) {
        // Joining all the elements of the classpath with the system path separator
        return String.join(File.pathSeparator,
                CLASSPATH,
                getDatabasePrepatchClassPath(vuln), // Obtaining the database prepatch class path
                getDatabasePostpatchClassPath(vuln), // Obtaining the database postpatch class path
                String.join(File.pathSeparator,
                        getThirdPartyLibDirsFromPrepatch(vuln))); // Obtaining the third party library directories from the prepatch
    }

    /**
     * Returns a string representation of the third party library directories from the given VulnerabilityInfo object.
     * 
     * @param info the VulnerabilityInfo object containing the third party library directories
     * @return a string representation of the third party library directories
     */
    public static String getThirdPartyLibDirsString(VulnerabilityInfo info) {
        // Call buildNewStringArrayCode method to build a new string array code based on the third_party_lib_dirs
        return buildNewStringArrayCode(info.third_party_lib_dirs);
    }
}