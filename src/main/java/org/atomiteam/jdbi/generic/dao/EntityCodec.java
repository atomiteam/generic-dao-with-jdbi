package org.atomiteam.jdbi.generic.dao;

import java.util.Map;

/**
 * Pluggable entity-to-storage mapping used by {@link GenericDao}.
 *
 * <p>The default implementation is reflection based. Applications with legacy
 * annotations, computed fields, foreign references, or other mapping rules can
 * provide their own codec without reimplementing CRUD SQL.</p>
 */
public interface EntityCodec<T> {

    Map<String, Object> toStorage(T entity, boolean includeNulls);

    T fromStorage(Map<String, Object> values);

    boolean isMappedProperty(String propertyName);

    String columnName(String propertyName, ColumnNaming columnNaming);

    Object getId(T entity);

    void setId(T entity, Object id);

    default Object toStorageValue(String propertyName, Object value) {
        return value;
    }
}
