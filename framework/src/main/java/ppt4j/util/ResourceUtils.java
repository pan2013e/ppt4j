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

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static InputStream readDatabase(String name) {
        InputStream db = Main.class.getResourceAsStream(databasePath);
        assert db != null;
        try (ZipInputStream zis = new ZipInputStream(db);
             BufferedInputStream bis = new BufferedInputStream(zis)) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.getName().equals(name)) {
                    break;
                }
            }
            if (ze == null) {
                return null;
            }
            int size = (int) ze.getSize();
            byte[] buffer = new byte[size];
            bis.read(buffer, 0, size);
            return new ByteArrayInputStream(buffer);
        } catch (IOException e) {
            log.error(e);
            throw new RuntimeException();
        }
    }

    public static InputStream readVulTemplate() {
        InputStream is = Main.class.getResourceAsStream(vulTemplatePath);
        return Objects.requireNonNull(is);
    }

    public static InputStream readProperties() {
        InputStream is = Main.class.getResourceAsStream(propertiesPath);
        return Objects.requireNonNull(is);
    }

    public static InputStream readSerializedFile(int id, DatabaseType type, String className) {
        className = className.replace(".", "_");
        className = className.replace("/", "_");
        String expected = "ser/" + type + "_" + id + "_" + className + ".bin";
        return readDatabase(expected);
    }

}
