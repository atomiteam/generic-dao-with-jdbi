package org.atomiteam.jdbi.generic.dao;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/** Immutable reflection metadata shared by the portable and PostgreSQL codecs. */
final class EntityModel<T> {
    private static final ClassValue<EntityModel<?>> MODELS = new ClassValue<>() {
        @Override protected EntityModel<?> computeValue(Class<?> type) { return new EntityModel<>(type); }
    };

    private final Class<T> type;
    private final Map<String, Field> properties;

    @SuppressWarnings("unchecked")
    static <T> EntityModel<T> of(Class<T> type) {
        return (EntityModel<T>) MODELS.get(Objects.requireNonNull(type, "type"));
    }

    private EntityModel(Class<T> type) {
        this.type = type;
        Map<String, Field> fields = new LinkedHashMap<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!persistent(field)) continue;
                if (fields.putIfAbsent(field.getName(), field) != null)
                    throw new IllegalArgumentException("Ambiguous persistent property: " + field.getName());
                field.setAccessible(true);
            }
        }
        properties = Collections.unmodifiableMap(fields);
    }

    Collection<Field> fields() { return properties.values(); }
    boolean contains(String name) { return properties.containsKey(name); }

    Field require(String name) {
        Field field = properties.get(name);
        if (field == null)
            throw new IllegalArgumentException("Unknown or unmapped property '" + name + "' for entity " + type.getName());
        return field;
    }

    T create() {
        try { return type.getDeclaredConstructor().newInstance(); }
        catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot construct entity " + type.getName()
                    + "; provide an accessible no-argument constructor or custom EntityCodec", failure);
        }
    }

    Object read(T entity, Field field) {
        try { return field.get(entity); }
        catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot read property " + field.getName(), failure);
        }
    }

    void write(T entity, Field field, Object value) {
        PropertyUtils.setPropertyValue(entity, type, field, value);
    }

    private static boolean persistent(Field field) {
        int modifiers = field.getModifiers();
        return !Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)
                && !field.isSynthetic() && !field.isAnnotationPresent(Transient.class);
    }
}
