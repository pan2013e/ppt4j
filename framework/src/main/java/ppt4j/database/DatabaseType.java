package ppt4j.database;

import ppt4j.annotation.Property;
import ppt4j.util.StringUtils;

import java.nio.file.Path;

public enum DatabaseType {

    PREPATCH, POSTPATCH;

    @Property("ppt4j.database.prepatch.name")
    private static String PREPATCH_DIR;

    @Property("ppt4j.database.postpatch.name")
    private static String POSTPATCH_DIR;

    @Property("ppt4j.database.root")
    private static String DATABASE_ROOT;

    /**
     * Returns the full path to the directory where database files are stored, based on whether the current instance is a PREPATCH or POSTPATCH.
     * If the current instance is PREPATCH, the subdirectory will be the PREPATCH directory. If the current instance is POSTPATCH, the subdirectory will be the POSTPATCH directory.
     * The full path is resolved using the DATABASE_ROOT as the parent directory, and the subdirectory is concatenated to it.
     *
     * @return the full path to the database directory
     */
    public String getPath() {
        // Determine the subdirectory based on the current instance
        String subDir = this == PREPATCH ? PREPATCH_DIR : POSTPATCH_DIR;
        
        // Resolve the full path by concatenating the DATABASE_ROOT with the subdirectory
        return Path.of(StringUtils.resolvePath(DATABASE_ROOT), subDir).toString();
    }

    /**
     * Constructs a path by concatenating the current path with the provided ID as a string.
     * 
     * @param id the ID to append to the current path
     * @return the complete path as a string
     */
    public String getPath(int id) {
        // Use Path.of to create a new Path object
        // Convert the integer ID to a string before concatenating it with the current path
        return Path.of(getPath(), String.valueOf(id)).toString();
    }

    /**
     * Returns a string representation of the object. 
     * If the object is equal to PREPATCH constant, returns "prepatch", 
     * otherwise returns "postpatch".
     */
    public String toString() {
        // Check if the object is equal to PREPATCH constant
        return this == PREPATCH ? "prepatch" : "postpatch";
    }

}
