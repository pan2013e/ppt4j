package ppt4j.util;

import lombok.extern.log4j.Log4j;
import ppt4j.Main;
import ppt4j.annotation.Property;
import ppt4j.database.DatabaseType;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Log4j
public class ResourceUtils {

    @Property("ppt4j.resources.database")
    private static String databasePath;

    @Property("ppt4j.database.root")
    private static String root;

    @Property("ppt4j.resources.vul_template")
    private static String vulTemplatePath;

    private static final String propertiesPath = "/ppt4j.properties";

    /**
     * Reads a specified database entry from a zip file located at the given path.
     *
     * @param name the name of the database entry to read
     * @return an InputStream containing the data of the specified database entry
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static InputStream readDatabase(String name) {
        InputStream db = Main.class.getResourceAsStream(databasePath);
        assert db != null; // Ensure that the input stream is not null
        
        try (ZipInputStream zis = new ZipInputStream(db);
             BufferedInputStream bis = new BufferedInputStream(zis)) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.getName().equals(name)) { // Check if the current entry matches the specified name
                    break;
                }
            }
            if (ze == null) {
                return null; // Return null if the specified entry is not found in the zip file
            }
            
            int size = (int) ze.getSize(); // Get the size of the database entry
            byte[] buffer = new byte[size]; // Create a buffer to store the data
            bis.read(buffer, 0, size); // Read data from the input stream into the buffer
            return new ByteArrayInputStream(buffer); // Return an InputStream containing the data of the specified database entry
        } catch (IOException e) {
            log.error(e); // Log any IOException that occurs
            throw new RuntimeException(); // Throw a RuntimeException in case of an error
        }
    }

    /**
     * Reads the vulnerability template file and returns it as an InputStream.
     *
     * @return InputStream of the vulnerability template file
     */
    public static InputStream readVulTemplate() {
        // Get the InputStream of the vulnerability template file
        InputStream is = Main.class.getResourceAsStream(vulTemplatePath);
        
        // Ensure that the InputStream is not null before returning
        return Objects.requireNonNull(is);
    }

    /**
     * This method reads the properties file located at the specified path and returns an InputStream object.
     * 
     * @return InputStream object containing the properties file data
     */
    public static InputStream readProperties() {
        // Read the properties file as an InputStream using getResourceAsStream method
        InputStream is = Main.class.getResourceAsStream(propertiesPath);
        
        // Ensure that the InputStream is not null using Objects.requireNonNull method
        return Objects.requireNonNull(is);
    }

    /**
     * Reads a serialized file from the specified location based on the given id, database type, and class name.
     * Replaces dots and slashes in the class name with underscores to create the expected file path.
     * Reads the serialized file from the expected file path using the readDatabase method.
     *
     * @param id the unique identifier for the file
     * @param type the type of database being used
     * @param className the name of the class containing the serialized data
     * @return an InputStream of the serialized file
     */
    public static InputStream readSerializedFile(int id, DatabaseType type, String className) {
        // Replace dots and slashes in class name with underscores
        className = className.replace(".", "_");
        className = className.replace("/", "_");
        
        // Create the expected file path
        String expected = "ser/" + type + "_" + id + "_" + className + ".bin";
        
        // Read the serialized file using the readDatabase method
        return readDatabase(expected);
    }

}
