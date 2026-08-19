package org.atomiteam.jdbi.generic.dao;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;

/** Default {@link EntityCodec} preserving GenericDao's reflection mapping. */
public class ReflectionEntityCodec<T> implements EntityCodec<T> {

    private final Class<T> type;
    private final Gson gson = new Gson();

    public ReflectionEntityCodec(Class<T> type) {
        this.type = type;
    }

    @Override
    public Map<String, Object> toStorage(T entity, boolean includeNulls) {
        Map<String, Object> values = new LinkedHashMap<>();
        Class<?> current = entity.getClass();
        try {
            while (current != null) {
                for (Field field : current.getDeclaredFields()) {
                    if (!isPersistent(field)) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    if (value != null || includeNulls) {
                        values.put(field.getName(), field.isAnnotationPresent(Json.class) && value != null
                                ? gson.toJson(value)
                                : value);
                    }
                }
                current = current.getSuperclass();
            }
            return values;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to inspect entity " + entity.getClass().getName(), e);
        }
    }

    @Override
    public T fromStorage(Map<String, Object> values) {
        try {
            T instance = type.getDeclaredConstructor().newInstance();
            Class<?> current = type;
            while (current != null) {
                for (Field field : current.getDeclaredFields()) {
                    if (!isPersistent(field)) {
                        continue;
                    }
                    String property = field.getName();
                    if (!values.containsKey(property)) {
                        continue;
                    }
                    Object value = values.get(property);
                    if (field.isAnnotationPresent(Json.class) && value != null) {
                        value = gson.fromJson(String.valueOf(value), field.getType());
                    }
                    PropertyUtils.setPropertyValue(instance, type, field, value);
                }
                current = current.getSuperclass();
            }
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to map storage values to " + type.getName(), e);
        }
    }

    @Override
    public boolean isMappedProperty(String propertyName) {
        Field field = findField(type, propertyName);
        return field != null && isPersistent(field);
    }

    @Override
    public String columnName(String propertyName, ColumnNaming columnNaming) {
        Field field = requireField(propertyName);
        return field.isAnnotationPresent(Column.class)
                ? field.getAnnotation(Column.class).value()
                : columnNaming.toColumnName(propertyName);
    }

    @Override
    public Object getId(T entity) {
        Field field = requireField("id");
        try {
            field.setAccessible(true);
            return field.get(entity);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read id from " + type.getName(), e);
        }
    }

    @Override
    public void setId(T entity, Object id) {
        PropertyUtils.setPropertyValue(entity, type, requireField("id"), id);
    }

    @Override
    public Object toStorageValue(String propertyName, Object value) {
        Field field = requireField(propertyName);
        return value != null && field.isAnnotationPresent(Json.class) ? gson.toJson(value) : value;
    }

    private Field requireField(String name) {
        Field field = findField(type, name);
        if (field == null || !isPersistent(field)) {
            throw new IllegalArgumentException("Unknown or unmapped property '" + name + "' for entity " + type.getName());
        }
        return field;
    }

    private static boolean isPersistent(Field field) {
        int modifiers = field.getModifiers();
        return !Modifier.isStatic(modifiers)
                && !Modifier.isTransient(modifiers)
                && !field.isSynthetic()
                && !field.isAnnotationPresent(Transient.class);
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
