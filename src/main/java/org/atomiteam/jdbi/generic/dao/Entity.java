package org.atomiteam.jdbi.generic.dao;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a generic entity with an ID and utility methods to track changes.
 */
public class Entity {

    /**
     * Unique identifier for the entity.
     */
    private String id;

    /**
     * Retrieves the ID of the entity.
     * 
     * @return the ID of the entity.
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the ID of the entity.
     * 
     * @param id the new ID for the entity.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Retrieves a map of the non-null fields and their values for the entity, //
     * including fields from parent classes.
     * 
     * @param <T> the type of the entity.
     * @return a map containing field names as keys and their corresponding //
     * values.
     */
    public <T> Map<String, Object> getChanges() {
        Map<String, Object> map = new HashMap<>();
        Class<?> currentClass = getClass(); // Start with the class of the //
        // current instance

        try {
            // Traverse the class hierarchy (including subclasses)
            while (currentClass != null) {
                for (Field field : currentClass.getDeclaredFields()) {
                    field.setAccessible(true); // Make private fields accessible
                    Object value = field.get(this); // Get the value of the field
                    if (value != null) {
                        map.put(field.getName(), value);
                    }
                }
                currentClass = currentClass.getSuperclass(); // Move to the //
                // superclass
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return map;
    }
}
