package org.atomiteam.jdbi.generic.dao;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to indicate that a field should be treated as a JSON field in the database.
 */
@Retention(RetentionPolicy.RUNTIME)  // Makes it available at runtime via reflection
@Target(ElementType.FIELD)           // Can be applied to fields only
public @interface Json {
}
