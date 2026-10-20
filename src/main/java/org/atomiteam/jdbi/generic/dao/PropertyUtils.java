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
        try {
            // getMethod already searches public inherited methods.
            return type.getMethod(setterName, parameterType);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static Object convertValue(Object value, Class<?> targetType) {
        if (value == null || targetType.isInstance(value)) {
            return value;
        }
        if (value instanceof String) {
            String text = (String) value;
            if (targetType == java.time.Instant.class) return java.time.Instant.parse(text);
            if (targetType == java.time.OffsetDateTime.class) return java.time.OffsetDateTime.parse(text);
            if (targetType == java.time.LocalDate.class) return java.time.LocalDate.parse(text);
            if (targetType == java.time.LocalDateTime.class) return java.time.LocalDateTime.parse(text);
        }
        if (targetType == java.time.Instant.class) {
            if (value instanceof java.sql.Timestamp) return ((java.sql.Timestamp)value).toInstant();
            if (value instanceof java.time.OffsetDateTime) return ((java.time.OffsetDateTime)value).toInstant();
        }
        if (targetType == java.time.LocalDate.class && value instanceof java.sql.Date) return ((java.sql.Date)value).toLocalDate();
        if (targetType == java.time.LocalDateTime.class && value instanceof java.sql.Timestamp) return ((java.sql.Timestamp)value).toLocalDateTime();
        if (targetType == java.time.OffsetDateTime.class && value instanceof java.sql.Timestamp) return ((java.sql.Timestamp)value).toInstant().atOffset(java.time.ZoneOffset.UTC);
        if (targetType == java.util.UUID.class && value instanceof String) return java.util.UUID.fromString((String)value);
        if (value instanceof Number) {
            Number number = (Number) value;
            if (targetType == Long.class || targetType == long.class) return number.longValue();
            if (targetType == Integer.class || targetType == int.class) return number.intValue();
            if (targetType == Short.class || targetType == short.class) return number.shortValue();
            if (targetType == Byte.class || targetType == byte.class) return number.byteValue();
            if (targetType == Double.class || targetType == double.class) return number.doubleValue();
            if (targetType == Float.class || targetType == float.class) return number.floatValue();
        }
        if (targetType.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) targetType, String.valueOf(value));
        }
        if (targetType == String.class) {
            return String.valueOf(value);
        }
        return value;
    }
}
