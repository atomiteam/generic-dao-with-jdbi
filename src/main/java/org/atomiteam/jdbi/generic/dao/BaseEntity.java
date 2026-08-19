package org.atomiteam.jdbi.generic.dao;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

/**
 * Generic base entity supporting strongly typed primary keys.
 *
 * @param <ID> primary key type
 */
public class BaseEntity<ID> {

    private ID id;

    public ID getId() {
        return id;
    }

    public void setId(ID id) {
        this.id = id;
    }

    /**
     * Returns non-null persistent fields keyed by Java property name.
     */
    public Map<String, Object> toChanges() {
        Gson gson = new Gson();
        Map<String, Object> changes = new HashMap<>();
        Class<?> currentClass = getClass();

        try {
            while (currentClass != null) {
                for (Field field : currentClass.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && !field.isAnnotationPresent(Transient.class)) {
                        field.setAccessible(true);
                        Object value = field.get(this);
                        if (value != null) {
                            changes.put(field.getName(), field.isAnnotationPresent(Json.class) ? gson.toJson(value) : value);
                        }
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read entity fields", e);
        }

        return changes;
    }
}
