package org.atomiteam.jdbi.generic.dao;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

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
	 * @param id
	 *            the new ID for the entity.
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * Retrieves a map of the non-null fields and their values for the entity,
	 * // including fields from parent classes.
	 * 
	 * @param <T>
	 *            the type of the entity.
	 * @return a map containing field names as keys and their corresponding //
	 *         values.
	 */
	public <T> Map<String, Object> toChanges() {
		Gson gson = new Gson();
		Map<String, Object> changes = new HashMap<>();
		Class<?> currentClass = getClass();

		try {
			while (currentClass != null) {
				for (Field field : currentClass.getDeclaredFields()) {
					// Exclude static fields
					if (!Modifier.isStatic(field.getModifiers())) {
						field.setAccessible(true);
						Object value = field.get(this);
						if (value != null && !field.isAnnotationPresent(Transient.class)) {
							if (field.isAnnotationPresent(Json.class)) {
								changes.put(field.getName(),
										gson.toJson(value));
							} else {
								changes.put(field.getName(), value);
							}
						}
					}
				}
				currentClass = currentClass.getSuperclass();
			}
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}

		return changes;
	}
}
