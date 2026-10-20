package org.atomiteam.jdbi.generic.dao;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import java.lang.reflect.Field;
import java.util.*;

/** Portable POJO mapping; SQL generation and database-specific binding are separate concerns. */
public class ReflectionEntityCodec<T> implements EntityCodec<T> {
    private static final Gson JSON = new Gson();
    private static final Gson PROPERTY_JSON = new GsonBuilder().serializeNulls().create();
    private final EntityModel<T> model;
    private final ColumnNaming naming;

    public ReflectionEntityCodec(Class<T> type) {
        this(type, ColumnNaming.IDENTITY);
    }

    public ReflectionEntityCodec(Class<T> type, ColumnNaming naming) {
        this.model = EntityModel.of(type);
        this.naming = Objects.requireNonNull(naming, "naming");
    }

    @Override
    public Map<String, Object> toStorage(T entity, boolean includeNulls) {
        Objects.requireNonNull(entity, "entity");
        Map<String, Object> values = new LinkedHashMap<>();
        for (Field field : model.fields()) {
            Object value = model.read(entity, field);
            if (value != null || includeNulls)
                values.put(field.getName(), toStorageValue(field.getName(), value));
        }
        return values;
    }

    @Override
    public T fromStorage(Map<String, Object> values) {
        Objects.requireNonNull(values, "values");
        T entity = model.create();
        for (Field field : model.fields()) {
            String label = label(values, columnName(field.getName(), naming));
            if (label == null) continue;
            Object value = values.get(label);
            if (value != null && field.isAnnotationPresent(Json.class))
                value = JSON.fromJson(String.valueOf(value), field.getGenericType());
            model.write(entity, field, value);
        }
        return entity;
    }

    /**
     * Selects raw values by mapped POJO property name, without converting or encoding them.
     * Unknown document extensions are ignored; SQL expressions are never mapped properties.
     */
    public Map<String, Object> mappedProperties(Map<String, ?> source, boolean includeNulls) {
        Objects.requireNonNull(source, "source");
        Map<String, Object> values = new LinkedHashMap<>();
        for (Field field : model.fields()) {
            Object value = source.get(field.getName());
            if (value != null || includeNulls) values.put(field.getName(), value);
        }
        return values;
    }

    /**
     * Maps validated Java properties, including typed JSON and ISO dates; unknown keys are
     * rejected.
     */
    public T fromProperties(Map<String, ?> values) {
        Objects.requireNonNull(values, "values");
        T entity = model.create();
        values.forEach(
                (name, requested) -> {
                    Field field = model.require(name);
                    Object value = requested;
                    if (value != null && field.isAnnotationPresent(Json.class))
                        value =
                                PROPERTY_JSON.fromJson(
                                        PROPERTY_JSON.toJson(value), field.getGenericType());
                    model.write(entity, field, value);
                });
        return entity;
    }

    @Override
    public boolean isMappedProperty(String name) {
        return model.contains(name);
    }

    @Override
    public String columnName(String name, ColumnNaming requestedNaming) {
        Field field = model.require(name);
        return field.isAnnotationPresent(Column.class)
                ? field.getAnnotation(Column.class).value()
                : requestedNaming.toColumnName(name);
    }

    @Override
    public Object getId(T entity) {
        return model.read(entity, model.require("id"));
    }

    @Override
    public void setId(T entity, Object id) {
        model.write(entity, model.require("id"), id);
    }

    @Override
    public Object toStorageValue(String name, Object value) {
        boolean json = isJsonProperty(name);
        if (value == null || !json) return value;
        return value instanceof JsonElement ? value.toString() : JSON.toJson(value);
    }

    /** Extension point for native JSON binding, without repeating reflection or serialization. */
    protected final boolean isJsonProperty(String name) {
        return model.require(name).isAnnotationPresent(Json.class);
    }

    private static String label(Map<String, ?> values, String column) {
        String unquoted =
                column.startsWith("\"") && column.endsWith("\"")
                        ? column.substring(1, column.length() - 1)
                        : column;
        if (values.containsKey(unquoted)) return unquoted;
        String match = null;
        for (String key : values.keySet()) {
            if (!key.equalsIgnoreCase(unquoted)) continue;
            if (match != null)
                throw new IllegalArgumentException("Ambiguous column label: " + unquoted);
            match = key;
        }
        return match;
    }
}
