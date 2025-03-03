package ppt4j.factory;

import ppt4j.database.Vulnerability;
import ppt4j.database.VulnerabilityInfo;
import ppt4j.util.ResourceUtils;
import ppt4j.util.StringUtils;
import lombok.NonNull;
import lombok.extern.log4j.Log4j;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.CharBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j
public final class DatabaseFactory {

    private static final JavaCompiler
            compiler = ToolProvider.getSystemJavaCompiler();

    private static final StandardJavaFileManager
            stdManager = compiler.getStandardFileManager(null, null, null);

    private static final String classTemplate;

    private static final Map<Integer, Vulnerability>
            cachedClasses = new HashMap<>();

    private static final Map<String, Vulnerability>
            cachedClassesByCVE = new HashMap<>();

    static {
        try (InputStream data = ResourceUtils.readVulTemplate()) {
            classTemplate = new String(data.readAllBytes());
        } catch (IOException e) {
            log.error(e);
            throw new RuntimeException();
        }
    }

    /**
     * Creates a Vulnerability instance using the provided VulnerabilityInfo object,
     * compiles and loads the instance, and adds it to the cached classes by Database ID and CVE ID.
     * If a Vulnerability with the same Database ID or CVE ID already exists in the cache,
     * an IllegalStateException is thrown.
     *
     * @param info the VulnerabilityInfo object used to create the Vulnerability instance
     * @return the created Vulnerability instance
     */
    public static Vulnerability makeDataset(@NonNull VulnerabilityInfo info) {
        if(cachedClasses.containsKey(StringUtils.extractDatabaseId(info.vul_id)) ||
                cachedClassesByCVE.containsKey(info.cve_id)) {
            log.error(String.format(
                    "Vulnerability with Database ID %s or" +
                            "CVE ID %s is already loaded.",
                    info.vul_id, info.cve_id));
            throw new IllegalStateException();
        }
        try {
            // Compile and load the Vulnerability instance
            Vulnerability instance = compileAndLoad(info);
            
            // Add the instance to the cached classes by Database ID and CVE ID
            cachedClasses.put(instance.getDatabaseId(), instance);
            cachedClassesByCVE.put(instance.getCVEId(), instance);
            
            return instance;
        } catch (Exception e) {
            log.error(e);
            throw new IllegalStateException();
        }
    }

    /**
     * Reads a JSON input stream and creates a Vulnerability object based on the information provided.
     *
     * @param jsonInput the input stream containing JSON data
     * @return a Vulnerability object created from the JSON input
     * @throws IOException if an I/O error occurs while reading the input stream
     */
    public static Vulnerability makeDataset(InputStream jsonInput)
                throws IOException {
            // Read JSON input stream and create VulnerabilityInfo object
            VulnerabilityInfo info = VulnerabilityInfo.fromJSON(jsonInput);
            
            // Create and return a Vulnerability object based on the VulnerabilityInfo
            return makeDataset(info);
        }

    /**
     * Retrieves a Vulnerability object by its database id. If the Vulnerability object is already cached, it is returned from the cache.
     * If the object is not cached, it is read from a JSON file using the id and returned as a Vulnerability object.
     * 
     * @param id The database id of the Vulnerability
     * @return A Vulnerability object with the specified id
     * @throws IllegalStateException if the JSON file is not found or if an IOException occurs during processing
     */
    public static Vulnerability getByDatabaseId(int id) {
        // Check if the Vulnerability object is already cached
        if(cachedClasses.containsKey(id)) {
            return cachedClasses.get(id);
        }
        
        // Read the JSON file corresponding to the id
        InputStream data = ResourceUtils.readDatabase(String.format("VUL4J-%d.json", id));
        
        // If data is null, log an error and throw IllegalStateException
        if(data == null) {
            log.error("No vulnerability with id " + id + " found");
            throw new IllegalStateException();
        }
        
        try {
            // Create a Vulnerability object using the data from the JSON file
            return makeDataset(data);
        } catch (IOException e) {
            // Log any IOException that occurs and throw IllegalStateException
            log.error(e);
            throw new IllegalStateException();
        }
    }

    /**
     * Compiles and loads a Vulnerability class using the provided VulnerabilityInfo object.
     * If the VulnerabilityInfo object is empty, an error is logged and an IllegalStateException is thrown.
     * The class name is generated based on the CVE ID from the info object.
     * The code for the class is generated based on the classTemplate and information from the info object.
     * The code is compiled using a MemoryJavaFileManager and JavaCompiler.
     * If the compilation fails, errors are logged and a RuntimeException is thrown.
     * The compiled class bytes are retrieved from the MemoryJavaFileManager and used to create a MemoryClassLoader.
     * The class is loaded from the MemoryClassLoader and instantiated as a Vulnerability object.
     * 
     * @param info the VulnerabilityInfo object containing information for the Vulnerability class
     * @return the compiled and loaded Vulnerability object
     * @throws NoSuchMethodException if a specified method cannot be found
     * @throws ClassNotFoundException if the class with the specified name cannot be found
     * @throws InvocationTargetException if an invoked method throws an exception
     * @throws InstantiationException if an application tries to create an instance of a class using the newInstance method in class Class, but the specified class object cannot be instantiated
     * @throws IllegalAccessException if an application tries to reflectively create an instance (other than an array), set or get a field, or invoke a method, but the currently executing method does not have access to the definition of the specified class, field, method or constructor
     */
    private static Vulnerability compileAndLoad(VulnerabilityInfo info)
            throws NoSuchMethodException, ClassNotFoundException,
            InvocationTargetException, InstantiationException,
            IllegalAccessException {
        if(info.isEmpty()) {
            log.error("VulnerabilityInfo object is not valid");
            throw new IllegalStateException();
        }
        
        // Generate class name and code based on info object
        String className = info.cve_id.replace("-", "_");
        String code = String.format(classTemplate,
                info.cve_id,
                StringUtils.extractDatabaseId(info.vul_id),
                className,
                className,
                info.project_url,
                info.fixing_commit_hash,
                info.human_patch_url + ".diff",
                StringUtils.getJavaSrcTopLevelDir(info),
                info.src_classes_dir,
                info.should_scan_all_modules,
                StringUtils.getThirdPartySrcDirsString(info),
                StringUtils.getThirdPartyLibDirsString(info)
        );
        
        // Compile the code
        MemoryJavaFileManager manager = new MemoryJavaFileManager(stdManager);
        JavaFileObject file = manager.makeStringSource(className + ".java", code);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler.CompilationTask task = compiler.getTask(
                null, manager, diagnostics, null, null, List.of(file));
        Boolean result = task.call();
        
        if (result == null || !result) {
            // Log compilation errors
            for (var diagnostic : diagnostics.getDiagnostics()) {
                log.error(diagnostic.getMessage(null));
            }
            throw new RuntimeException("Compilation failed.");
        }
        
        // Load and instantiate the class
        Map<String, byte[]> classBytes = manager.getClassBytes();
        manager.close();
        MemoryClassLoader loader = new MemoryClassLoader(classBytes);
        Class<?> clazz = loader.loadClass("ppt4j.database." + className);
        return (Vulnerability) clazz.getConstructor().newInstance();
    }

    static class MemoryJavaFileManager
            extends ForwardingJavaFileManager<JavaFileManager> {

        final Map<String, byte[]> classBytes = new HashMap<>();

        MemoryJavaFileManager(JavaFileManager fileManager) {
            super(fileManager);
        }

        /**
         * Returns a shallow copy of the mapping of class names to byte arrays.
         *
         * @return a Map with class names as keys and byte arrays as values
         */
        public Map<String, byte[]> getClassBytes() {
            // Create a new HashMap object and pass a shallow copy of the classBytes map to it
            return new HashMap<>(this.classBytes);
        }

        /**
         * Flushes any buffered output to the underlying output stream. This method is called
         * automatically by the PrintWriter when the flush method is called. However, subclasses
         * may need to override this method for custom behavior.
         */
        @Override
        public void flush() {
            // No custom behavior needed, as the PrintWriter will handle flushing
        }

        /**
         * Clears the contents of the classBytes map.
         */
        @Override
        public void close() {
            // Clearing the contents of the classBytes map
            classBytes.clear();
        }

        /**
         * Retrieves a Java file object for the specified location, class name, kind, and sibling. If the kind is CLASS,
         * a new MemoryOutputJavaFileObject is created with the specified class name. Otherwise, the method defers to the
         * superclass implementation to retrieve the Java file object.
         *
         * @param location the location where the Java file object should be stored
         * @param className the name of the class
         * @param kind the kind of Java file object
         * @param sibling the sibling file object
         * @return a Java file object for the specified inputs
         * @throws IOException if an I/O error occurs
         */
        @Override
        public JavaFileObject getJavaFileForOutput(
                JavaFileManager.Location location, String className,
                JavaFileObject.Kind kind, FileObject sibling)
                throws IOException {
            if (kind == JavaFileObject.Kind.CLASS) { // check if the kind is CLASS
                return new MemoryOutputJavaFileObject(className); // create a new MemoryOutputJavaFileObject with the class name
            } else {
                return super.getJavaFileForOutput( // defer to the superclass implementation
                        location, className, kind, sibling);
            }
        }

        /**
         * Creates a new JavaFileObject with the specified name and code.
         *
         * @param name the name of the Java file
         * @param code the code content of the Java file
         * @return a new MemoryInputJavaFileObject with the specified name and code
         */
        JavaFileObject makeStringSource(String name, String code) {
            // Create a new MemoryInputJavaFileObject with the given name and code
            return new MemoryInputJavaFileObject(name, code);
        }

        static class MemoryInputJavaFileObject extends SimpleJavaFileObject {

            final String code;

            MemoryInputJavaFileObject(String name, String code) {
                super(URI.create("string:///" + name), Kind.SOURCE);
                this.code = code;
            }

            /**
             * Returns a CharBuffer containing the code of this code snippet.
             * 
             * @param ignoreEncodingErrors a boolean indicating whether to ignore encoding errors
             * @return a CharBuffer containing the code of this code snippet
             */
            @Override
            public CharBuffer getCharContent(boolean ignoreEncodingErrors) {
                // Wrap the code string in a CharBuffer
                return CharBuffer.wrap(code);
            }
        }

        class MemoryOutputJavaFileObject extends SimpleJavaFileObject {
            final String name;

            MemoryOutputJavaFileObject(String name) {
                super(URI.create("string:///" + name), Kind.CLASS);
                this.name = name;
            }

            /**
             * Opens an output stream to write bytecode of a class file.
             * Overrides the openOutputStream method to create a FilterOutputStream 
             * that wraps around a new ByteArrayOutputStream. The close method of 
             * the FilterOutputStream is overridden to close the output stream, 
             * convert it to a byte array, and store it in a map with the class name as 
             * the key.
             *
             * @return OutputStream - the output stream to write bytecode of a class file
             */
            @Override
            public OutputStream openOutputStream() {
                return new FilterOutputStream(new ByteArrayOutputStream()) {
                    @Override
                    public void close() throws IOException {
                        out.close(); // Close the output stream
                        ByteArrayOutputStream bos = (ByteArrayOutputStream) out; // Cast output stream to ByteArrayOutputStream
                        classBytes.put(name, bos.toByteArray()); // Store byte array in map with class name as key
                    }
                };
            }

        }

    }

    static class MemoryClassLoader extends URLClassLoader {

        Map<String, byte[]> classBytes = new HashMap<>();

        public MemoryClassLoader(Map<String, byte[]> classBytes) {
            super(new URL[0], MemoryClassLoader.class.getClassLoader());
            this.classBytes.putAll(classBytes);
        }

        /**
         * This method is responsible for finding and loading a class with the specified name. 
         * It first checks if the class bytes are already stored in the classBytes map. 
         * If the bytes are found, it removes them from the map and defines the class using the retrieved bytes. 
         * If the bytes are not found in the map, it delegates the class loading to the superclass. 
         * 
         * @param name the name of the class to find
         * @return the Class object representing the loaded class
         * @throws ClassNotFoundException if the class could not be found or loaded
         */
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            // Retrieve the bytes for the specified class name
            byte[] buf = classBytes.get(name);
            
            // If bytes are not found in the map, delegate to the superclass for class loading
            if (buf == null) {
                return super.findClass(name);
            }
            
            // Remove the class bytes from the map
            classBytes.remove(name);
            
            // Define the class using the retrieved bytes
            return defineClass(name, buf, 0, buf.length);
        }

    }

}
