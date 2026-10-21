package org.atomiteam.jdbi.generic.dao;

import java.util.Locale;
import java.util.Objects;
import javax.sql.DataSource;
import org.jdbi.v3.core.Jdbi;

/**
 * Explicit engine selection for POJO mapping and JSON binding. JDBC drivers, URLs,
 * credentials, IAM renewal and connection pools remain owned by the caller.
 */
public enum DatabaseEngine {
    DSQL,
    POSTGRESQL,
    MYSQL;

    public static DatabaseEngine parse(String configured) {
        Objects.requireNonNull(configured, "engine");
        return valueOf(configured.trim().toUpperCase(Locale.ROOT));
    }

    public Jdbi create(DataSource source) {
        return configure(Jdbi.create(Objects.requireNonNull(source, "source")));
    }

    public Jdbi configure(Jdbi database) {
        Objects.requireNonNull(database, "database");
        return this == MYSQL ? database : DsqlSupport.configure(database);
    }

    public org.atomiteam.jdbi.generic.query.JdbiQuery query(String sql) {
        return org.atomiteam.jdbi.generic.query.JdbiQuery.select(sql, this);
    }

    public ColumnNaming columnNaming() {
        return this == MYSQL ? ColumnNaming.BACKTICK_IDENTITY : ColumnNaming.QUOTED_IDENTITY;
    }

    public <T> GenericDao<T> dao(Jdbi database, Class<T> type, String table) {
        return dao(database, type, table, columnNaming());
    }

    public <T> GenericDao<T> dao(
            Jdbi database, Class<T> type, String table, ColumnNaming naming) {
        Objects.requireNonNull(naming, "naming");
        if (this == MYSQL && naming == ColumnNaming.QUOTED_IDENTITY
                || this != MYSQL && naming == ColumnNaming.BACKTICK_IDENTITY) {
            throw new IllegalArgumentException("Column quoting differs from the selected engine");
        }
        return this == MYSQL
                ? new GenericDao<>(configure(database), type, table, naming)
                : DsqlSupport.dao(configure(database), type, table, naming);
    }
}
