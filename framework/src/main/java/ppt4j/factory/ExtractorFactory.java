package ppt4j.factory;

import ppt4j.analysis.patch.CrossMatcher;
import ppt4j.database.DatabaseType;
import ppt4j.database.Vulnerability;
import ppt4j.feature.bytecode.BytecodeExtractor;
import ppt4j.feature.java.JavaExtractor;
import ppt4j.util.FileUtils;
import ppt4j.util.ResourceUtils;
import ppt4j.util.StringUtils;
import ppt4j.util.VMUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.ImportScannerImpl;
import spoon.support.compiler.jdt.CompilationUnitFilter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Log4j
public final class ExtractorFactory {

    String prepatchPath, postpatchPath;
    String[] thirdPartySrcPath;

    @Getter
    String classPath;

    boolean isJar = false;
    private JarFile jarFile = null;

    @Setter
    Vulnerability vuln = null;

    final Map<String, JavaExtractor> cachedPreExtractors = new HashMap<>();
    final Map<String, JavaExtractor> cachedPostExtractors = new HashMap<>();
    final Map<String, BytecodeExtractor> cachedBytecodeExtractors = new HashMap<>();

    final Map<String, CrossMatcher> cachedPre2Class = new HashMap<>();
    final Map<String, CrossMatcher> cachedPost2Class = new HashMap<>();

    /**
     * Returns an instance of ExtractorFactory initialized with the provided prepatchPath, postpatchPath,
     * classPath, and thirdPartySrcPath.
     *
     * @param prepatchPath the path to the pre-patch files
     * @param postpatchPath the path to the post-patch files
     * @param classPath the class path for the ExtractorFactory
     * @param thirdPartySrcPath additional source paths (optional)
     * @return an instance of ExtractorFactory
     */
    public static ExtractorFactory get(String prepatchPath,
                                           String postpatchPath,
                                           String classPath,
                                           String... thirdPartySrcPath) {
            // Create a new instance of ExtractorFactory with the provided paths
            return new ExtractorFactory(prepatchPath, postpatchPath,
                    classPath, thirdPartySrcPath);
    }

    /**
     * Retrieves an ExtractorFactory based on the specified Vulnerability and DatabaseType.
     * This method generates necessary paths, checks the VM class path, and initializes the ExtractorFactory.
     *
     * @param vuln the Vulnerability object
     * @param type the DatabaseType object
     * @return the ExtractorFactory object
     */
    public static ExtractorFactory get(Vulnerability vuln, DatabaseType type) {
        // Generate prepatch and postpatch paths based on the Vulnerability
        String prepatchPath = StringUtils.getDatabasePrepatchSrcPath(vuln);
        String postpatchPath = StringUtils.getDatabasePostpatchSrcPath(vuln);
        
        // Generate class path based on the DatabaseType and Vulnerability
        String classPath = Path.of(
                                type.getPath(vuln.getDatabaseId()),
                                vuln.getClassesTopLevelDir()
                            ).toString();
        
        // Check if the VM class path is present
        VMUtils.checkVMClassPathPresent(classPath);
        
        // Get third party source paths from the prepatch
        String[] thirdPartySrcPath = StringUtils.getThirdPartySrcDirsFromPrepatch(vuln);
        
        // Initialize ExtractorFactory with the generated paths
        ExtractorFactory factory = get(prepatchPath, postpatchPath,
                classPath, thirdPartySrcPath);
        factory.vuln = vuln;
        
        return factory;
    }

    ExtractorFactory(String prepatchPath,
                     String postpatchPath,
                     String classPath,
                     String... thirdPartySrcPath) {
        this.prepatchPath = StringUtils.resolvePath(prepatchPath);
        this.postpatchPath = StringUtils.resolvePath(postpatchPath);
        this.thirdPartySrcPath = thirdPartySrcPath;

        this.classPath = StringUtils.resolvePath(classPath);

        if(classPath.endsWith("jar")) {
            isJar = true;
            try {
                jarFile = new JarFile(this.classPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Retrieves a pre-patch Java class extractor for the specified class name.
     *
     * @param className the name of the Java class to extract
     * @return a JavaExtractor object for the specified class name from the pre-patch database
     */
    public JavaExtractor getPreJavaClass(String className) {
        // Return the JavaExtractor object for the specified class name from the pre-patch database
        return getJavaExtractor(className, cachedPreExtractors, DatabaseType.PREPATCH);
    }

    /**
     * Returns a JavaExtractor object for the specified class name from the cachedPostExtractors map.
     * If the JavaExtractor object does not exist in the map, a new JavaExtractor object is created using the specified class name
     * and added to the map before returning.
     *
     * @param className the name of the Java class to extract
     * @return the JavaExtractor object for the specified class name
     */
    public JavaExtractor getPostJavaClass(String className) {
        // Get the JavaExtractor object from the cachedPostExtractors map
        // If the object does not exist, create a new JavaExtractor object using the specified className
        // and add it to the map
        return getJavaExtractor(className, cachedPostExtractors, DatabaseType.POSTPATCH);
    }

    @SuppressWarnings("all")
    private static class CuFilter implements CompilationUnitFilter {

        private final Set<String> includeClasses = new HashSet<>();

        private final String moduleName;
        private final String basePath;

        public CuFilter(String basePath, String className, Set<String> includeClasses) {
            this.basePath = basePath;
            this.moduleName = className.substring(0, className.lastIndexOf("."));
            this.includeClasses.addAll(includeClasses);
        }

        /**
         * Determines if a given string should be excluded based on the module name and included classes.
         * If the substring before the last '.' in the input string ends with the specified module name,
         * or if the input string ends with any of the included classes, the method returns false.
         * Otherwise, it returns true.
         * 
         * @param s the input string to be checked for exclusion
         * @return true if the input string should be excluded, false otherwise
         */
        private boolean _exclude(String s) {
            // Extract module name
            String mod = s.substring(0, s.lastIndexOf("."));
            
            // Check if input string ends with the specified module name
            if(mod.endsWith(moduleName)) {
                return false;
            }
            
            // Check if input string ends with any of the included classes
            for (String className : includeClasses) {
                if(s.endsWith(className)) {
                    return false;
                }
            }
            
            return true;
        }

        /**
         * Removes the last 5 characters from the input string, replaces any "/" characters with ".", and then calls the private method _exclude with the modified string.
         * 
         * @param s the input string to be modified
         * @return true if the modified string is excluded, false otherwise
         */
        @Override
        public boolean exclude(String s) {
            // Remove the last 5 characters from the input string
            s = s.substring(0, s.length() - 5);
            
            // Replace any "/" characters with "."
            s = s.replace("/", ".");
            
            // Call the private method _exclude with the modified string
            return _exclude(s);
        }
    }

    /**
     * Retrieves a JavaExtractor for the specified class name from the cachedExtractors map. 
     * If the JavaExtractor is not found in the cache, it attempts to load it from a serialized file or by parsing the Java source code.
     * 
     * @param className the name of the class to extract
     * @param cachedExtractors a map containing cached JavaExtractor instances
     * @param type the type of the database
     * @return a JavaExtractor for the specified class name
     */
    private JavaExtractor getJavaExtractor(String className,
               Map<String, JavaExtractor> cachedExtractors, DatabaseType type) {
            if(cachedExtractors.containsKey(className)) {
                return cachedExtractors.get(className);
            }
            if(vuln != null) { // assuming vuln is a class variable
                InputStream is = ResourceUtils.readSerializedFile(vuln.getDatabaseId(), type, className);
                if(is != null) {
                    JavaExtractor extractor = FileUtils.deserializeObject(JavaExtractor.class, is);
                    assert extractor != null;
                    cachedExtractors.put(className, extractor);
                    return extractor;
                }
            }
            Launcher temp = new Launcher();
            String basePath = type == DatabaseType.PREPATCH ? prepatchPath : postpatchPath; // assuming prepatchPath and postpatchPath are class variables
            String path = Path.of(basePath, className.replace(".", "/") + ".java").toString();
            if(!new File(path).exists()) {
                log.debug("File not found: " + path); // assuming log is a class variable for logging
                return JavaExtractor.nil();
            }
            temp.addInputResource(path);
            temp.buildModel();
            ImportScannerImpl importScanner = new ImportScannerImpl(); // assuming ImportScannerImpl is a class
            importScanner.scan(temp.getFactory().Class().get(className));
            Set<String> includeClasses = new HashSet<>();
            for(CtImport ctImport: importScanner.getAllImports()) {
                if(ctImport.getReference() instanceof CtTypeReference<?> ty) {
                    includeClasses.add(ty.getQualifiedName());
                }
            }
            CuFilter filter = new CuFilter(basePath, className, includeClasses); // assuming CuFilter is a class
            Launcher launcher = new Launcher();
            launcher.addInputResource(basePath);
            Arrays.stream(thirdPartySrcPath).forEach(launcher::addInputResource); // assuming thirdPartySrcPath is an array of paths
            launcher.getEnvironment().setPreserveLineNumbers(true);
            launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
            try {
                launcher.getModelBuilder().addCompilationUnitFilter(filter);
                launcher.buildModel();
            } catch (Throwable e) {
                launcher = new Launcher();
                launcher.addInputResource(basePath);
                Arrays.stream(thirdPartySrcPath).forEach(launcher::addInputResource);
                launcher.getEnvironment().setPreserveLineNumbers(true);
                launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
                launcher.buildModel();
            }
            CtClass<?> clazz = launcher.getFactory().Class().get(className);
            if(clazz == null) {
                return JavaExtractor.nil();
            }
            JavaExtractor ex = new JavaExtractor(clazz); // assuming JavaExtractor class has a constructor that takes a CtClass parameter
            ex.parse();
            cachedExtractors.put(className, ex);
            return ex;
        }

    /**
     * Retrieves the bytecode of a class specified by the given class name. 
     * If the bytecode for the class is already cached, it returns the cached instance.
     * If the class is contained within a JAR file, it extracts the bytecode from the JAR file.
     * If the class is not found in the JAR file, it logs a debug message and returns a nil BytecodeExtractor.
     * If the class is not within a JAR file, it extracts the bytecode from the classpath directory.
     * It parses the extracted bytecode, adds any inner classes found within the same directory or JAR file,
     * caches the extracted bytecode, and returns the BytecodeExtractor instance.
     * 
     * @param className the name of the class for which bytecode needs to be extracted
     * @return the BytecodeExtractor instance containing the bytecode of the specified class
     * @throws IOException if an I/O error occurs while attempting to extract bytecode
     */
    public BytecodeExtractor getBytecodeClass(String className) throws IOException {
            if(cachedBytecodeExtractors.containsKey(className)) {
                return cachedBytecodeExtractors.get(className);
            }
            BytecodeExtractor ex;
            if(isJar) {
                JarEntry entry = jarFile.getJarEntry(
                        className.replace('.', '/') + ".class");
                try {
                    if(entry == null) {
                        throw new IOException("Entry is null");
                    }
                    InputStream is = jarFile.getInputStream(entry);
                    ex = new BytecodeExtractor(is);
                } catch (IOException e) {
                    log.debug("Bytecode of class " + className + " not found.");
                    return BytecodeExtractor.nil();
                }
                for (Iterator<JarEntry> it = jarFile.entries().asIterator(); it.hasNext(); ) {
                    JarEntry entry1 = it.next();
                    if(entry1.getName().startsWith(className.replace('.', '/') + "$")) {
                        log.debug("Adding inner class " + entry1.getName() + " from jar file.");
                        BytecodeExtractor innerEx = new BytecodeExtractor(jarFile.getInputStream(entry1));
                        ex.putInnerClass(innerEx);
                    }
                }
            } else {
                String dir = Path.of(classPath,
                        className.replace('.', '/') + ".class").toString();
                try {
                    ex = new BytecodeExtractor(dir);
                } catch (IOException e) {
                    log.debug("Bytecode of class " + className + " not found.");
                    return BytecodeExtractor.nil();
                }
                dir = dir.substring(0, dir.lastIndexOf('/'));
                File packageDir = new File(dir);
                assert packageDir.exists() && packageDir.isDirectory();
                File[] files = packageDir.listFiles();
                assert files != null;
                String regex = ".*" + className.replace('.', '/') + "\\$.*\\.class";
                for (File file : files) {
                    if (file.getAbsolutePath().matches(regex)) {
                        BytecodeExtractor innerEx = new BytecodeExtractor(file.getAbsolutePath());
                        ex.putInnerClass(innerEx);
                    }
                }
            }
            ex.parse();
            cachedBytecodeExtractors.put(className, ex);
            return ex;
        }

    /**
     * Retrieves a CrossMatcher object for the given class name. If the CrossMatcher object
     * for the class name is already cached, it is returned. Otherwise, a new CrossMatcher
     * object is created using the pre-java class and bytecode class of the given class name,
     * and then cached for future use.
     *
     * @param className the name of the class
     * @return the CrossMatcher object for the given class name
     * @throws IOException if an I/O error occurs
     */
    public CrossMatcher getPre2Class(String className) throws IOException {
            // Check if CrossMatcher object for the class name is already cached
            if(cachedPre2Class.containsKey(className)) {
                // If cached, return the cached CrossMatcher object
                return cachedPre2Class.get(className);
            }
            // If not cached, create a new CrossMatcher object using pre-java class and bytecode class
            CrossMatcher matcher = CrossMatcher.get(getPreJavaClass(className),
                    getBytecodeClass(className), false);
            // Cache the CrossMatcher object for future use
            cachedPre2Class.put(className, matcher);
            // Return the newly created CrossMatcher object
            return matcher;
        }

    /**
     * Retrieves a CrossMatcher object associated with a given class name. If the CrossMatcher object
     * is already cached, it is returned directly. Otherwise, a new CrossMatcher object is created
     * using the class name to retrieve the corresponding Java class and bytecode class. The new
     * CrossMatcher object is then cached for future use.
     *
     * @param className the name of the class to retrieve the CrossMatcher object for
     * @return the CrossMatcher object associated with the provided class name
     * @throws IOException if an I/O error occurs during the retrieval process
     */
    public CrossMatcher getPost2Class(String className) throws IOException {
        if(cachedPost2Class.containsKey(className)) {
            // If the CrossMatcher object is already cached, return it
            return cachedPost2Class.get(className);
        }
        
        // Retrieve the Java class and bytecode class for the given class name
        CrossMatcher matcher = CrossMatcher.get(getPostJavaClass(className),
                getBytecodeClass(className), true);
        
        // Cache the newly created CrossMatcher object
        cachedPost2Class.put(className, matcher);
        
        return matcher;
    }

}
