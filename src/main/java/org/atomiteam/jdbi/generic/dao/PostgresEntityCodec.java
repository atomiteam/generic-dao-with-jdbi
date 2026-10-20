package org.atomiteam.jdbi.generic.dao;

import java.lang.reflect.Field;
import java.util.Map;

/** Opt-in PostgreSQL/DSQL JSON binding, preserving the default codec's portable behavior. */
public final class PostgresEntityCodec<T> extends ReflectionEntityCodec<T> {
    private final Class<T> type;
    public PostgresEntityCodec(Class<T> type, ColumnNaming naming) { super(type,naming);this.type=type; }
    @Override public Map<String,Object> toStorage(T entity, boolean includeNulls) {
        Map<String,Object> values=super.toStorage(entity,includeNulls);
        values.replaceAll((key,value) -> json(key) ? new DsqlSupport.JsonValue((String)value) : value);
        return values;
    }
    @Override public Object toStorageValue(String property, Object value) {
        Object encoded=super.toStorageValue(property,value);
        return json(property)?new DsqlSupport.JsonValue((String)encoded):encoded;
    }
    private boolean json(String property) {
        for(Class<?> current=type;current!=null;current=current.getSuperclass()) {
            try { Field field=current.getDeclaredField(property);return field.isAnnotationPresent(Json.class); }
            catch(NoSuchFieldException ignored) { /* inspect inherited fields */ }
        }
        return false;
    }
}
