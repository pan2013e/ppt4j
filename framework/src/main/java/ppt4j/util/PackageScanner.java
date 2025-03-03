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

    /**
     * This method retrieves all classes within a specified package, including classes within sub-packages.
     * It first determines whether the package is stored as a directory or within a jar file, then proceeds
     * to add the classes accordingly. The method returns a set containing all the classes found.
     *
     * @param packageName the name of the package to retrieve classes from
     * @return a set of Class objects representing the classes found in the specified package
     */
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
                    addClassesByFile(packageName, packagePath, classes); // Add classes if package is stored as a directory
                } else if("jar".equals(protocol)) {
                    JarFile jar = ((JarURLConnection) url.openConnection())
                            .getJarFile();
                    addClassesByJar(jar, packageName, packDir, classes); // Add classes if package is stored within a jar file
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return classes;
    }

    /**
     * Recursively scans the specified package path for .class files and adds the corresponding Class objects to the provided Set.
     * If the package path does not exist or is not a directory, the method returns without doing anything.
     * 
     * @param packageName the package name of the classes being scanned
     * @param packagePath the file path of the package being scanned
     * @param classes a Set to store the Class objects found
     */
    private static void addClassesByFile(String packageName, String packagePath, Set<Class<?>> classes) {
        File dir = new File(packagePath);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] dirFiles = dir.listFiles(file -> (file.isDirectory() || file.getName().endsWith(".class")));
        assert dirFiles != null;
        for (File file : dirFiles) {
            if (file.isDirectory()) {
                String newPackageName = packageName.isEmpty() ? file.getName() : packageName + "." + file.getName();
                addClassesByFile(newPackageName, file.getAbsolutePath(), classes); // recursively call the method for subdirectories
            } else {
                String className = file.getName().substring(0, file.getName().length() - 6);
                try {
                    String fullClassName = packageName.isEmpty() ? className : packageName + "." + className;
                    classes.add(Thread.currentThread().getContextClassLoader().loadClass(fullClassName));
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Adds classes from a JAR file to a Set of classes based on the specified package name and package path.
     *
     * @param jar the JAR file to extract classes from
     * @param packageName the package name of the classes to be added
     * @param packagePath the package path within the JAR file
     * @param classes the Set to add the extracted classes to
     * @throws ClassNotFoundException if a class cannot be found or loaded
     */
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
                packageName = name.substring(0, idx).replace('/', '.');
                if (name.endsWith(".class") && !entry.isDirectory()) {
                    String className = name.substring(packageName.length() + 1, name.length() - 6);
                    String fullClassName = packageName.isEmpty() ? className : packageName + "." + className;
                    // Load the class using the current thread's context class loader and add it to the Set
                    classes.add(Thread.currentThread().getContextClassLoader().loadClass(fullClassName));
                }
            }
        }
    }
}
