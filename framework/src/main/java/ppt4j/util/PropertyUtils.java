package ppt4j.util;

import ppt4j.annotation.Property;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Properties;

public class PropertyUtils {

    private static final Properties properties = new Properties();

    /**
     * Loads a properties file from the given input stream.
     * 
     * @param is the input stream from which to load the properties file
     */
    public static void load(InputStream is) {
        try {
            // Load the properties file from the input stream
            properties.load(is);
        } catch (IOException e) {
            // Print the stack trace
            e.printStackTrace();
            // Exit the program with a status of 1 if an IOException occurs
            System.exit(1);
        }
    }

    /**
     * Overrides the current properties with the new properties provided.
     * Copies all of the mappings from the specified properties to this properties object.
     *
     * @param props the properties to be added/overridden
     */
    public static void override(Properties props) {
        // Copy all mappings from the specified properties to this properties object
        properties.putAll(props);
    }

    /**
     * Initializes the properties of all classes in the "ppt4j" package using reflection.
     * For each class in the package, calls the initValue method from the PropertyUtils class to initialize the properties.
     */
    public static void init() {
        // Get a list of classes in the "ppt4j" package
        PackageScanner.getClasses("ppt4j").forEach(PropertyUtils::initValue);
    }

    /**
     * Initializes the values of fields in a given class using properties defined in the Property annotation.
     * 
     * @param clazz the class containing the fields to be initialized
     */
    public static void initValue(Class<?> clazz) {
            // Stream through all declared fields in the class
            Arrays.stream(clazz.getDeclaredFields())
                    // Filter fields based on custom verification logic
                    .filter(PropertyUtils::verifyField)
                    // For each filtered field, set its accessibility to true, retrieve the property key from the Property annotation,
                    // and set the field's value using the corresponding property value
                    .forEach(field -> {
                        field.setAccessible(true);
                        String key = field.getAnnotation(Property.class).value();
                        try {
                            setField(field, getProperty(key));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    });
        }

    /**
     * Retrieves the value of a property based on the provided key. If the key is null or empty, an exception is thrown.
     * If the property is not found in the properties map, it falls back to retrieving it from the system properties.
     * If the property is still not found, an exception is thrown.
     * @param key the key of the property to retrieve
     * @return the value of the property as a String
     * @throws IllegalStateException if the key is null or empty, or if the property is not found
     */
    public static String getProperty(String key) {
        // Check if key is null or empty
        if(key == null || key.isEmpty()) {
            throw new IllegalStateException("Key cannot be null or empty");
        }
        // Retrieve value of the property from properties map or system properties
        Object value = properties.getOrDefault(key, System.getProperty(key));
        // Check if property is not found
        if(value == null) {
            throw new IllegalStateException("Property " + key + " not found");
        }
        return value.toString();
    }

    /**
     * Verifies if a given Field meets the following criteria:
     * 1. It is annotated with @Property annotation
     * 2. It is a static field
     * 3. It is not a final field
     * 
     * @param field the Field to be verified
     * @return true if the field meets all criteria, false otherwise
     */
    private static boolean verifyField(Field field) {
        // Check if the field is annotated with @Property annotation
        boolean hasPropertyAnnotation = field.isAnnotationPresent(Property.class);
        
        // Check if the field is a static field
        boolean isStatic = Modifier.isStatic(field.getModifiers());
        
        // Check if the field is not a final field
        boolean isNotFinal = !Modifier.isFinal(field.getModifiers());
        
        // Return true only if all criteria are met
        return hasPropertyAnnotation && isStatic && isNotFinal;
    }

    /**
     * Sets the value of an array field using reflection.
     *
     * @param field the field to set the value of
     * @param value the array of strings containing the values to set
     * @throws IllegalAccessException if the field is inaccessible
     */
    private static void setArrayField(Field field, String[] value)
                throws IllegalAccessException {
            Class<?> type = field.getType();
            Class<?> elementType = type.getComponentType();
            if(elementType == String.class) {
                field.set(null, value); // Set the array field directly
            } else if(elementType == int.class || elementType == Integer.class) {
                int[] array = new int[value.length];
                for(int i = 0; i < value.length; i++) {
                    array[i] = Integer.parseInt(value[i]);
                }
                field.set(null, array); // Set the int array field
            } else if(elementType == boolean.class || elementType == Boolean.class) {
                boolean[] array = new boolean[value.length];
                for(int i = 0; i < value.length; i++) {
                    array[i] = Boolean.parseBoolean(value[i]);
                }
                field.set(null, array); // Set the boolean array field
            } else if(elementType == long.class || elementType == Long.class) {
                long[] array = new long[value.length];
                for(int i = 0; i < value.length; i++) {
                    array[i] = Long.parseLong(value[i]);
                }
                field.set(null, array); // Set the long array field
            } else if(elementType == double.class || elementType == Double.class) {
                double[] array = new double[value.length];
                for(int i = 0; i < value.length; i++) {
                    array[i] = Double.parseDouble(value[i]);
                }
                field.set(null, array); // Set the double array field
            } else if(elementType == float.class || elementType == Float.class) {
                float[] array = new float[value.length];
                for(int i = 0; i < value.length; i++) {
                    array[i] = Float.parseFloat(value[i]);
                }
                field.set(null, array); // Set the float array field
            } else if(elementType == short.class || elementType == Short.class) {
                short[] array = new short[value.length];
                for(int i = 0; i < value.length; i++) {
                    array[i] = Short.parseShort(value[i]);
                }
                field.set(null, array); // Set the short array field
            } else if(elementType == byte.class || elementType == Byte.class) {
                byte[] array = new byte[value.length];
                for(int i = 0; i < value.length; i++) {
                    array[i] = Byte.parseByte(value[i]);
                }
                field.set(null, array); // Set the byte array field
            } else if(elementType == char.class || elementType == Character.class) {
                char[] array = new char[value.length];
                for(int i = 0; i < value.length; i++) {
                    array[i] = value[i].charAt(0);
                }
                field.set(null, array); // Set the char array field
            } else if(elementType == Class.class) {
                Class<?>[] array = new Class[value.length];
                try {
                    for(int i = 0; i < value.length; i++) {
                        array[i] = Class.forName(value[i]);
                    }
                    field.set(null, array); // Set the Class array field
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            } else {
                throw new IllegalStateException("Unsupported array type: " + elementType);
            }
        }

    /**
     * Sets the value of a field based on its type. Supports primitive types, wrapper classes, String, char, and Class types.
     * If the field is an array, the value is split by commas and trimmed before setting.
     *
     * @param field the field to set the value for
     * @param value the value to set
     * @throws IllegalAccessException if the field cannot be accessed or modified
     */
    private static void setField(Field field, String value)
                throws IllegalAccessException {
            Class<?> type = field.getType();
            value = value.trim();
            
            if(type.isArray()) { // Check if field is an array
                String[] values = value.split(","); // Split values by commas
                for (int i = 0; i < values.length; i++) {
                    values[i] = values[i].trim(); // Trim each value
                }
                setArrayField(field, values); // Set array field
                return;
            }
            
            if (type == String.class) {
                field.set(null, value);
            } else if (type == int.class || type == Integer.class) {
                int d;
                if(value.contains(".")) {
                     d = Integer.parseInt(value.substring(0, value.indexOf('.')));
                } else {
                    d = Integer.parseInt(value);
                }
                field.set(null, d);
            } else if (type == boolean.class || type == Boolean.class) {
                field.set(null, Boolean.parseBoolean(value));
            } else if (type == long.class || type == Long.class) {
                field.set(null, Long.parseLong(value));
            } else if (type == double.class || type == Double.class) {
                field.set(null, Double.parseDouble(value));
            } else if (type == float.class || type == Float.class) {
                field.set(null, Float.parseFloat(value));
            } else if (type == short.class || type == Short.class) {
                field.set(null, Short.parseShort(value));
            } else if (type == byte.class || type == Byte.class) {
                field.set(null, Byte.parseByte(value));
            } else if (type == char.class || type == Character.class) {
                field.set(null, value.charAt(0));
            } else if (type == Class.class) {
                try {
                    field.set(null, Class.forName(value));
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            } else {
                throw new IllegalStateException("Unsupported type: " + type);
            }
        }
}
