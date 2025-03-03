package ppt4j.util;

import ppt4j.annotation.Property;
import lombok.extern.log4j.Log4j;

import java.io.File;

@Log4j
public class VMUtils {

    @Property("java.class.path")
    private static String VM_CLASSPATH;

    public static void checkVMClassPathPresent(String classPath) {
        log.debug("Validating classpath");
        String[] paths = VM_CLASSPATH.split(File.pathSeparator);
        String absClassPath = StringUtils.resolvePath(classPath);
        for (String path : paths) {
            if(path.equals(absClassPath)) {
                return;
            }
        }
        log.warn(
                "Classpath not set correctly in VM options, add " +
                classPath
        );
        log.warn("This may cause PatchAnalyzer to give incorrect results");
    }

}
