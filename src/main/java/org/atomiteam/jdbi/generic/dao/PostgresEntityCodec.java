package org.atomiteam.jdbi.generic.dao;

/** Native PostgreSQL/DSQL JSON binding; portable mapping stays in ReflectionEntityCodec. */
public final class PostgresEntityCodec<T> extends ReflectionEntityCodec<T> {
    public PostgresEntityCodec(Class<T> type, ColumnNaming naming) { super(type, naming); }

    @Override public Object toStorageValue(String property, Object value) {
        Object encoded = super.toStorageValue(property, value);
        return isJsonProperty(property) ? new DsqlSupport.JsonValue((String) encoded) : encoded;
    }
}
