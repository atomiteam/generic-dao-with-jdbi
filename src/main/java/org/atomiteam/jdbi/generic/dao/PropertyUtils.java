package org.atomiteam.jdbi.generic.dao;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Utility class for setting property values on objects using reflection.
 * <p>
 * This class attempts to set a property value on a given object. If a setter
 * method is available, it will use it. Otherwise, it falls back to setting the
 * field directly.
 * </p>
 */
public class PropertyUtils {

    /**
     * Sets a property value on the given object instance.
     *
     * <p>
     * This method first attempts to use a setter method to set the value. If the
     * setter is not found or cannot be invoked, it will attempt to set the field
     * value directly.
     * </p>
     *
     * @param instance the object instance on which the property is being set.
     * @param type     the class type of the object instance.
     * @param field    the field representing the property to set.
     * @param value    the value to set on the property.
     */
    public static void setPropertyValue(Object instance, Class<?> type, Field field, Object value) {
        // Try to use the setter method if it exists
        String setterName = "set" + field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1);
        try {
            // Find and invoke the setter method
            Method setter = type.getMethod(setterName, field.getType());
            setter.invoke(instance, value);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // If setter doesn't exist or fails, fall back to setting the field directly
            try {
                field.setAccessible(true); // Ensure the field is accessible
                field.set(instance, value);
            } catch (IllegalArgumentException | IllegalAccessException e1) {
                // Log or handle failure to set the property
                
            }
        }
    }
}
