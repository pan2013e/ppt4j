package ppt4j.util;

import ppt4j.annotation.Property;
import lombok.extern.log4j.Log4j;

import java.io.File;

@Log4j
public class VMUtils {

    @Property("java.class.path")
    private static String VM_CLASSPATH;

    /**
     * Checks if the provided classpath is present in the VM classpath.
     * If the classpath is not found in the VM classpath, a warning message is logged.
     *
     * @param classPath the classpath to be checked
     */
    public static void checkVMClassPathPresent(String classPath) {
        log.debug("Validating classpath");
        String[] paths = VM_CLASSPATH.split(File.pathSeparator); // Splitting the VM classpath into individual paths
        String absClassPath = StringUtils.resolvePath(classPath); // Resolving the absolute path of the provided classpath
        for (String path : paths) {
            if(path.equals(absClassPath)) { // Checking if the provided classpath is already in the VM classpath
                return; // Classpath is present, no action needed
            }
        }
        log.warn(
                "Classpath not set correctly in VM options, add " +
                classPath
        ); // Logging a warning message if the provided classpath is not found in the VM classpath
        log.warn("This may cause PatchAnalyzer to give incorrect results"); // Additional warning message about potential consequences
    }

}
