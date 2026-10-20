package org.atomiteam.jdbi.generic.dao;

import java.sql.Types;
import java.util.Optional;
import javax.sql.DataSource;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.argument.Argument;

/** JDBC-driver independent PostgreSQL/DSQL integration; authentication belongs to the DataSource. */
public final class DsqlSupport {
    private DsqlSupport() {}
    public static final class JsonValue {
        private final String value;
        public JsonValue(String value) { this.value=value; }
        public String getValue() { return value; }
        @Override public boolean equals(Object other) { return other instanceof JsonValue && java.util.Objects.equals(value,((JsonValue)other).value); }
        @Override public int hashCode() { return java.util.Objects.hashCode(value); }
    }
    public static Jdbi create(DataSource source) { return configure(Jdbi.create(source)); }
    public static final class Settings implements org.jdbi.v3.core.config.JdbiConfig<Settings> {
        private boolean configured;
        public Settings() {}
        private Settings(Settings source) { configured=source.configured; }
        @Override public Settings createCopy() { return new Settings(this); }
    }
    public static Jdbi configure(Jdbi jdbi) { return configureTarget(jdbi); }
    public static org.jdbi.v3.core.Handle configure(org.jdbi.v3.core.Handle handle) { return configureTarget(handle); }
    private static <C extends org.jdbi.v3.core.config.Configurable<C>> C configureTarget(C jdbi) {
        java.util.Objects.requireNonNull(jdbi);
        synchronized(jdbi) {
        Settings settings=jdbi.getConfig(Settings.class);
        if(settings.configured) return jdbi;
        jdbi.registerArgument((org.jdbi.v3.core.argument.ArgumentFactory)(type,value,config) -> {
            if(type!=JsonValue.class) return Optional.empty();
            String json=value==null?null:((JsonValue)value).getValue();
            Argument argument=(position,statement,context) -> statement.setObject(position,json,Types.OTHER);
            return Optional.of(argument);
        });
        settings.configured=true;
        return jdbi;
        }
    }
    public static <T> GenericDao<T> dao(Jdbi jdbi, Class<T> type, String table) {
        return dao(jdbi,type,table,ColumnNaming.IDENTITY);
    }
    public static <T> GenericDao<T> dao(Jdbi jdbi, Class<T> type, String table, ColumnNaming naming) {
        return new GenericDao<>(configure(jdbi),type,table,naming,new PostgresEntityCodec<>(type,naming));
    }
}
