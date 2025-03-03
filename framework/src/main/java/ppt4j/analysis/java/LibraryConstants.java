package ppt4j.analysis.java;

import ppt4j.annotation.Property;
import lombok.extern.log4j.Log4j;
import spoon.reflect.declaration.CtClass;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

@Log4j
public class LibraryConstants {

    @Property("ppt4j.features.constprop_classes")
    private static Class<?>[] classes;

    private static final Map<String, Object> map = new HashMap<>();

    private static final Set<CtClass<?>> cachedClasses = new HashSet<>();

    public static void init() {
        if (classes == null) {
            throw new RuntimeException("PropertyUtils not initialized correctly");
        }
        Arrays.stream(classes).forEach(LibraryConstants::put);
    }

    private static void put(Class<?> clazz) {
        try {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field: fields) {
                Class<?> ty = field.getType();
                if (ty.isPrimitive() || ty == String.class || ty == Character.class
                        || ty == Boolean.class || ty == Byte.class || ty == Short.class
                        || ty == Integer.class || ty == Long.class || ty == Float.class
                        || ty == Double.class) {
                    int mod = field.getModifiers();
                    if (Modifier.isPublic(mod) && Modifier.isStatic(mod) && Modifier.isFinal(mod)) {
                        field.setAccessible(true);
                        put(String.format("%s#%s", clazz.getName(), field.getName()), field.get(null));
                    }
                }
            }
        } catch (IllegalAccessException e) {
            log.warn(e);
        }
    }

    public static void put(String key, Object value) {
        map.put(key, value);
    }

    public static Object get(CtClass<?> clazz, String key) {
        if (clazz == null || cachedClasses.contains(clazz)) {
            return get(key);
        }
        try {
            put(clazz.getActualClass());
        } catch (NoClassDefFoundError e) {
            log.warn(e);
            log.warn("This might affect constant analysis");
        }
        cachedClasses.add(clazz);
        return get(key);
    }

    public static Object get(String key) {
        return map.get(key);
    }

}
