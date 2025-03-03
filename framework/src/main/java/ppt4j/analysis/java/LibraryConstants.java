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

    /**
     * Initializes the PropertyUtils class by putting all classes in the 'classes' array into the LibraryConstants map.
     * If the 'classes' array is null, a RuntimeException is thrown indicating that PropertyUtils was not initialized correctly.
     */
    public static void init() {
        // Check if classes array is null
        if (classes == null) {
            // Throw a RuntimeException if classes array is null
            throw new RuntimeException("PropertyUtils not initialized correctly");
        }
        
        // For each class in the classes array, put it into the LibraryConstants map
        Arrays.stream(classes).forEach(LibraryConstants::put);
    }

    /**
     * Retrieves and stores the values of public static final fields of primitive types, String, and wrapper classes (e.g., Integer, Boolean, etc.) from the specified class.
     * The values are stored in a map with the field name prefixed by the class name.
     * 
     * @param clazz the class from which to retrieve the fields
     */
    private static void put(Class<?> clazz) {
        try {
            // Get all fields of the specified class
            Field[] fields = clazz.getDeclaredFields();
            for (Field field: fields) {
                Class<?> ty = field.getType();
                // Check if the field type is primitive or one of the allowed classes
                if (ty.isPrimitive() || ty == String.class || ty == Character.class
                        || ty == Boolean.class || ty == Byte.class || ty == Short.class
                        || ty == Integer.class || ty == Long.class || ty == Float.class
                        || ty == Double.class) {
                    int mod = field.getModifiers();
                    // Check if the field is public, static, and final
                    if (Modifier.isPublic(mod) && Modifier.isStatic(mod) && Modifier.isFinal(mod)) {
                        field.setAccessible(true); // Allow access to the field
                        put(String.format("%s#%s", clazz.getName(), field.getName()), field.get(null)); // Store the field value in the map
                    }
                }
            }
        } catch (IllegalAccessException e) {
            log.warn(e); // Log a warning if access to the field is denied
        }
    }

    /**
     * Adds the specified key-value pair to the map.
     *
     * @param key the key to be added to the map
     * @param value the value associated with the key
     */
    public static void put(String key, Object value) {
        // Adds the key-value pair to the map
        map.put(key, value);
    }

    /**
     * Retrieves an object associated with the provided key from a cache. If the provided class is not already cached,
     * it adds the class to the cache and returns the object associated with the key. If the class is already cached, it
     * directly returns the object associated with the key from the cache.
     * 
     * @param clazz the class to retrieve the object for
     * @param key the key associated with the object in the cache
     * @return the object associated with the key from the cache
     */
    public static Object get(CtClass<?> clazz, String key) {
            // Check if the provided class is null or already cached
            if (clazz == null || cachedClasses.contains(clazz)) {
                return get(key); // Return object directly from cache
            }
            try {
                put(clazz.getActualClass()); // Add class to cache
            } catch (NoClassDefFoundError e) {
                log.warn(e); // Log warning about class not found
                log.warn("This might affect constant analysis"); // Log additional warning
            }
            cachedClasses.add(clazz); // Add class to cached list
            return get(key); // Return object from cache
        }

    /**
     * Retrieves the value associated with the specified key from the map.
     *
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or null if the map contains no mapping for the key
     */
    public static Object get(String key) {
        // Returns the value associated with the specified key from the map
        return map.get(key);
    }

}
