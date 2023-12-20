package ppt4j.util;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class PackageScanner {

    public static Set<Class<?>> getClasses(String packageName) {
        Set<Class<?>> classes = new HashSet<>();
        String packDir = packageName.replace('.', '/');
        Enumeration<URL> dirs;
        try {
            dirs = Thread.currentThread()
                    .getContextClassLoader()
                    .getResources(packDir);
            while (dirs.hasMoreElements()) {
                URL url = dirs.nextElement();
                String protocol = url.getProtocol();
                if ("file".equals(protocol)) {
                    String packagePath = URLDecoder.decode(url.getFile(),
                            StandardCharsets.UTF_8);
                    addClassesByFile(packageName, packagePath, classes);
                } else if("jar".equals(protocol)) {
                    JarFile jar = ((JarURLConnection) url.openConnection())
                            .getJarFile();
                    addClassesByJar(jar, packageName, packDir, classes);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return classes;
    }

    private static void addClassesByFile(String packageName,
                                         String packagePath,
                                         Set<Class<?>> classes) {
        File dir = new File(packagePath);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] dirFiles = dir.listFiles(file ->
                (file.isDirectory() || file.getName().endsWith(".class")));
        assert dirFiles != null;
        for (File file : dirFiles) {
            if (file.isDirectory()) {
                String newPackageName = packageName.isEmpty() ?
                        file.getName() : packageName + "." + file.getName();
                addClassesByFile(newPackageName,
                        file.getAbsolutePath(), classes);
            } else {
                String className = file.getName().substring(0,
                        file.getName().length() - 6);
                try {
                    String fullClassName = packageName.isEmpty() ?
                            className : packageName + "." + className;
                    classes.add(
                        Thread.currentThread()
                            .getContextClassLoader()
                            .loadClass(fullClassName)
                    );
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void addClassesByJar(JarFile jar,
                                        String packageName,
                                        String packagePath,
                                        Set<Class<?>> classes)
            throws ClassNotFoundException {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.charAt(0) == '/') {
                name = name.substring(1);
            }
            if (name.startsWith(packagePath)) {
                int idx = name.lastIndexOf('/');
                if (idx == -1) {
                    continue;
                }
                packageName = name.substring(0, idx)
                        .replace('/', '.');
                if (name.endsWith(".class") && !entry.isDirectory()) {
                    String className = name.substring(
                            packageName.length() + 1,
                            name.length() - 6);
                    String fullClassName = packageName.isEmpty() ? className :
                                    packageName + "." + className;
                    classes.add(
                            Thread.currentThread()
                                    .getContextClassLoader()
                                    .loadClass(fullClassName)
                    );
                }
            }
        }
    }
}
