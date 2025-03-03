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

    public String getPath() {
        String subDir = this == PREPATCH ? PREPATCH_DIR : POSTPATCH_DIR;
        return Path.of(StringUtils.resolvePath(DATABASE_ROOT), subDir).toString();
    }

    public String getPath(int id) {
        return Path.of(getPath(), String.valueOf(id)).toString();
    }

    public String toString() {
        return this == PREPATCH ? "prepatch" : "postpatch";
    }

}
