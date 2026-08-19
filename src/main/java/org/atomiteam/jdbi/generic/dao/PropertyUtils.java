package org.atomiteam.jdbi.generic.dao;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection utilities used by row mapping.
 */
public class PropertyUtils {

    public static void setPropertyValue(Object instance, Class<?> type, Field field, Object value) {
        Object converted = convertValue(value, field.getType());
        String setterName = "set" + field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1);
        try {
            Method setter = findSetter(type, setterName, field.getType());
            if (setter != null) {
                setter.invoke(instance, converted);
                return;
            }
            field.setAccessible(true);
            field.set(instance, converted);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to set property " + field.getName() + " on " + type.getName(), e);
        }
    }

    private static Method findSetter(Class<?> type, String setterName, Class<?> parameterType) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getMethod(setterName, parameterType);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    static Object convertValue(Object value, Class<?> targetType) {
        if (value == null || targetType.isInstance(value)) {
            return value;
        }
        if (value instanceof Number) {
            Number number = (Number) value;
            if (targetType == Long.class || targetType == long.class) return number.longValue();
            if (targetType == Integer.class || targetType == int.class) return number.intValue();
            if (targetType == Short.class || targetType == short.class) return number.shortValue();
            if (targetType == Byte.class || targetType == byte.class) return number.byteValue();
            if (targetType == Double.class || targetType == double.class) return number.doubleValue();
            if (targetType == Float.class || targetType == float.class) return number.floatValue();
        }
        return value;
    }
}
