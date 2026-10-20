package org.atomiteam.jdbi.generic.dao;

import java.util.Map;

/** Optional compatibility base; ordinary POJOs are equally supported. */
public class BaseEntity<ID> {
    private ID id;

    public ID getId() {
        return id;
    }

    public void setId(ID id) {
        this.id = id;
    }

    /** Non-null persistent Java properties, using the same portable codec as GenericDao. */
    public Map<String, Object> toChanges() {
        return changesFor(getClass());
    }

    private <T> Map<String, Object> changesFor(Class<T> type) {
        return new ReflectionEntityCodec<>(type).toStorage(type.cast(this), false);
    }
}
