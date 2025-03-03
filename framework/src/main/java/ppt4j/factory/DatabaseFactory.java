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
            Vulnerability instance = compileAndLoad(info);
            cachedClasses.put(instance.getDatabaseId(), instance);
            cachedClassesByCVE.put(instance.getCVEId(), instance);
            return instance;
        } catch (Exception e) {
            log.error(e);
            throw new IllegalStateException();
        }
    }

    public static Vulnerability makeDataset(InputStream jsonInput)
            throws IOException {
        VulnerabilityInfo info = VulnerabilityInfo.fromJSON(jsonInput);
        return makeDataset(info);
    }

    public static Vulnerability getByDatabaseId(int id) {
        if(cachedClasses.containsKey(id)) {
            return cachedClasses.get(id);
        }
        InputStream data = ResourceUtils.readDatabase(String.format("VUL4J-%d.json", id));
        if(data == null) {
            log.error("No vulnerability with id " + id + " found");
            throw new IllegalStateException();
        }
        try {
            return makeDataset(data);
        } catch (IOException e) {
            log.error(e);
            throw new IllegalStateException();
        }
    }

    private static Vulnerability compileAndLoad(VulnerabilityInfo info)
            throws NoSuchMethodException, ClassNotFoundException,
            InvocationTargetException, InstantiationException,
            IllegalAccessException {
        if(info.isEmpty()) {
            log.error("VulnerabilityInfo object is not valid");
            throw new IllegalStateException();
        }
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
        MemoryJavaFileManager manager =
                new MemoryJavaFileManager(stdManager);
        JavaFileObject file = manager.makeStringSource(
                className + ".java", code);
        DiagnosticCollector<JavaFileObject> diagnostics =
                new DiagnosticCollector<>();
        JavaCompiler.CompilationTask task =
                compiler.getTask(
                        null, manager, diagnostics, null, null,
                        List.of(file)
                );
        Boolean result = task.call();
        if (result == null || !result) {
            for (var diagnostic : diagnostics.getDiagnostics()) {
                log.error(diagnostic.getMessage(null));
            }
            throw new RuntimeException("Compilation failed.");

        }
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

        public Map<String, byte[]> getClassBytes() {
            return new HashMap<>(this.classBytes);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {
            classBytes.clear();
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                JavaFileManager.Location location, String className,
                JavaFileObject.Kind kind, FileObject sibling)
                throws IOException {
            if (kind == JavaFileObject.Kind.CLASS) {
                return new MemoryOutputJavaFileObject(className);
            } else {
                return super.getJavaFileForOutput(
                        location, className, kind, sibling);
            }
        }

        JavaFileObject makeStringSource(String name, String code) {
            return new MemoryInputJavaFileObject(name, code);
        }

        static class MemoryInputJavaFileObject extends SimpleJavaFileObject {

            final String code;

            MemoryInputJavaFileObject(String name, String code) {
                super(URI.create("string:///" + name), Kind.SOURCE);
                this.code = code;
            }

            @Override
            public CharBuffer getCharContent(boolean ignoreEncodingErrors) {
                return CharBuffer.wrap(code);
            }
        }

        class MemoryOutputJavaFileObject extends SimpleJavaFileObject {
            final String name;

            MemoryOutputJavaFileObject(String name) {
                super(URI.create("string:///" + name), Kind.CLASS);
                this.name = name;
            }

            @Override
            public OutputStream openOutputStream() {
                return new FilterOutputStream(new ByteArrayOutputStream()) {
                    @Override
                    public void close() throws IOException {
                        out.close();
                        ByteArrayOutputStream bos =
                                (ByteArrayOutputStream) out;
                        classBytes.put(name, bos.toByteArray());
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

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            byte[] buf = classBytes.get(name);
            if (buf == null) {
                return super.findClass(name);
            }
            classBytes.remove(name);
            return defineClass(name, buf, 0, buf.length);
        }

    }

}
