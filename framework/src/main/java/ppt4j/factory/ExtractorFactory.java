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

    public static ExtractorFactory get(String prepatchPath,
                                       String postpatchPath,
                                       String classPath,
                                       String... thirdPartySrcPath) {
        return new ExtractorFactory(prepatchPath, postpatchPath,
                classPath, thirdPartySrcPath);
    }

    public static ExtractorFactory get(Vulnerability vuln,
                                       DatabaseType type) {
        String prepatchPath = StringUtils.getDatabasePrepatchSrcPath(vuln);
        String postpatchPath = StringUtils.getDatabasePostpatchSrcPath(vuln);
        String classPath = Path.of(
                                type.getPath(vuln.getDatabaseId()),
                                vuln.getClassesTopLevelDir()
                            ).toString();
        VMUtils.checkVMClassPathPresent(classPath);
        String[] thirdPartySrcPath = StringUtils.getThirdPartySrcDirsFromPrepatch(vuln);
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

    public JavaExtractor getPreJavaClass(String className) {
        return getJavaExtractor(className, cachedPreExtractors, DatabaseType.PREPATCH);
    }

    public JavaExtractor getPostJavaClass(String className) {
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

        private boolean _exclude(String s) {
            String mod = s.substring(0, s.lastIndexOf("."));
            if(mod.endsWith(moduleName)) {
                return false;
            }
            for (String className : includeClasses) {
                if(s.endsWith(className)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean exclude(String s) {
            s = s.substring(0, s.length() - 5);
            s = s.replace("/", ".");
            return _exclude(s);
        }
    }

    private JavaExtractor getJavaExtractor(String className,
           Map<String, JavaExtractor> cachedExtractors, DatabaseType type) {
        if(cachedExtractors.containsKey(className)) {
            return cachedExtractors.get(className);
        }
        if(vuln != null) {
            InputStream is = ResourceUtils.readSerializedFile(vuln.getDatabaseId(), type, className);
            if(is != null) {
                JavaExtractor extractor = FileUtils.deserializeObject(JavaExtractor.class, is);
                assert extractor != null;
                cachedExtractors.put(className, extractor);
                return extractor;
            }
        }
        Launcher temp = new Launcher();
        String basePath = type == DatabaseType.PREPATCH ? prepatchPath : postpatchPath;
        String path = Path.of(basePath, className.replace(".", "/") + ".java").toString();
        if(!new File(path).exists()) {
            log.debug("File not found: " + path);
            return JavaExtractor.nil();
        }
        temp.addInputResource(path);
        temp.buildModel();
        ImportScannerImpl importScanner = new ImportScannerImpl();
        importScanner.scan(temp.getFactory().Class().get(className));
        Set<String> includeClasses = new HashSet<>();
        for(CtImport ctImport: importScanner.getAllImports()) {
            if(ctImport.getReference() instanceof CtTypeReference<?> ty) {
                includeClasses.add(ty.getQualifiedName());
            }
        }
        CuFilter filter = new CuFilter(basePath, className, includeClasses);
        Launcher launcher = new Launcher();
        launcher.addInputResource(basePath);
        Arrays.stream(thirdPartySrcPath).forEach(launcher::addInputResource);
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
        JavaExtractor ex = new JavaExtractor(clazz);
        ex.parse();
        cachedExtractors.put(className, ex);
        return ex;
    }

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

    public CrossMatcher getPre2Class(String className) throws IOException {
        if(cachedPre2Class.containsKey(className)) {
            return cachedPre2Class.get(className);
        }
        CrossMatcher matcher = CrossMatcher.get(getPreJavaClass(className),
                getBytecodeClass(className), false);
        cachedPre2Class.put(className, matcher);
        return matcher;
    }

    public CrossMatcher getPost2Class(String className) throws IOException {
        if(cachedPost2Class.containsKey(className)) {
            return cachedPost2Class.get(className);
        }
        CrossMatcher matcher = CrossMatcher.get(getPostJavaClass(className),
                getBytecodeClass(className), true);
        cachedPost2Class.put(className, matcher);
        return matcher;
    }

}
