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

    public static void load(InputStream is) {
        try {
            properties.load(is);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void override(Properties props) {
        properties.putAll(props);
    }

    public static void init() {
        PackageScanner.getClasses("ppt4j").forEach(PropertyUtils::initValue);
    }

    public static void initValue(Class<?> clazz) {
        Arrays.stream(clazz.getDeclaredFields())
                .filter(PropertyUtils::verifyField)
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

    public static String getProperty(String key) {
        if(key == null || key.isEmpty()) {
            throw new IllegalStateException("Key cannot be null or empty");
        }
        Object value = properties.getOrDefault(key,
                System.getProperty(key));
        if(value == null) {
            throw new IllegalStateException("Property " + key + " not found");
        }
        return value.toString();
    }

    private static boolean verifyField(Field field) {
        return field.isAnnotationPresent(Property.class)
                && Modifier.isStatic(field.getModifiers())
                && !Modifier.isFinal(field.getModifiers());
    }

    private static void setArrayField(Field field, String[] value)
            throws IllegalAccessException {
        Class<?> type = field.getType();
        Class<?> elementType = type.getComponentType();
        if(elementType == String.class) {
            field.set(null, value);
        } else if(elementType == int.class || elementType == Integer.class) {
            int[] array = new int[value.length];
            for(int i = 0; i < value.length; i++) {
                array[i] = Integer.parseInt(value[i]);
            }
            field.set(null, array);
        } else if(elementType == boolean.class || elementType == Boolean.class) {
            boolean[] array = new boolean[value.length];
            for(int i = 0; i < value.length; i++) {
                array[i] = Boolean.parseBoolean(value[i]);
            }
            field.set(null, array);
        } else if(elementType == long.class || elementType == Long.class) {
            long[] array = new long[value.length];
            for(int i = 0; i < value.length; i++) {
                array[i] = Long.parseLong(value[i]);
            }
            field.set(null, array);
        } else if(elementType == double.class || elementType == Double.class) {
            double[] array = new double[value.length];
            for(int i = 0; i < value.length; i++) {
                array[i] = Double.parseDouble(value[i]);
            }
            field.set(null, array);
        } else if(elementType == float.class || elementType == Float.class) {
            float[] array = new float[value.length];
            for(int i = 0; i < value.length; i++) {
                array[i] = Float.parseFloat(value[i]);
            }
            field.set(null, array);
        } else if(elementType == short.class || elementType == Short.class) {
            short[] array = new short[value.length];
            for(int i = 0; i < value.length; i++) {
                array[i] = Short.parseShort(value[i]);
            }
            field.set(null, array);
        } else if(elementType == byte.class || elementType == Byte.class) {
            byte[] array = new byte[value.length];
            for(int i = 0; i < value.length; i++) {
                array[i] = Byte.parseByte(value[i]);
            }
            field.set(null, array);
        } else if(elementType == char.class || elementType == Character.class) {
            char[] array = new char[value.length];
            for(int i = 0; i < value.length; i++) {
                array[i] = value[i].charAt(0);
            }
            field.set(null, array);
        } else if(elementType == Class.class) {
            Class<?>[] array = new Class[value.length];
            try {
                for(int i = 0; i < value.length; i++) {
                    array[i] = Class.forName(value[i]);
                }
                field.set(null, array);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        } else {
            throw new IllegalStateException("Unsupported array type: " + elementType);
        }
    }

    private static void setField(Field field, String value)
            throws IllegalAccessException {
        Class<?> type = field.getType();
        value = value.trim();
        if(type.isArray()) {
            String[] values = value.split(",");
            for (int i = 0; i < values.length; i++) {
                values[i] = values[i].trim();
            }
            setArrayField(field, values);
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
