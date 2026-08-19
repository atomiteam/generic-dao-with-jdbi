package org.atomiteam.jdbi.generic.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.atomiteam.jdbi.generic.dao.ColumnNaming;
import org.atomiteam.jdbi.generic.dao.EntityCodec;
import org.atomiteam.jdbi.generic.dao.ReflectionEntityCodec;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.SqlStatement;

/**
 * Central execution/mapping layer for {@link JdbiQuery}.
 *
 * <p>All bindings, collection expansion, pagination, total counts and common
 * row mapping live here so application DAOs do not repeat mapToMap/bind/count
 * boilerplate.</p>
 */
public final class JdbiQueryExecutor {

    private final Jdbi jdbi;

    public JdbiQueryExecutor(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi");
    }

    public List<Map<String, Object>> maps(JdbiQuery query) {
        Objects.requireNonNull(query, "query");
        return jdbi.withHandle(handle -> {
            Query statement = handle.createQuery(query.sql());
            bind(statement, query.bindings());
            return statement.mapToMap().list();
        });
    }

    public Optional<Map<String, Object>> firstMap(JdbiQuery query) {
        Objects.requireNonNull(query, "query");
        return jdbi.withHandle(handle -> {
            Query statement = handle.createQuery(query.sql());
            bind(statement, query.bindings());
            return statement.mapToMap().findFirst();
        });
    }

    public <T> List<T> list(JdbiQuery query, Function<Map<String, Object>, T> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return maps(query).stream().map(mapper).collect(Collectors.toList());
    }

    public <T> Optional<T> first(JdbiQuery query, Function<Map<String, Object>, T> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return firstMap(query).map(mapper);
    }

    /**
     * Maps a projection through the same reflection codec used by GenericDao.
     * SQL columns/aliases therefore follow the selected naming strategy.
     */
    public <T> List<T> pojos(JdbiQuery query, Class<T> type, ColumnNaming naming) {
        return pojos(query, new ReflectionEntityCodec<>(type, naming));
    }

    public <T> List<T> pojos(JdbiQuery query, EntityCodec<T> codec) {
        Objects.requireNonNull(codec, "codec");
        return list(query, codec::fromStorage);
    }

    public <T> Optional<T> firstPojo(JdbiQuery query, Class<T> type, ColumnNaming naming) {
        EntityCodec<T> codec = new ReflectionEntityCodec<>(type, naming);
        return first(query, codec::fromStorage);
    }

    public <T> List<T> scalars(JdbiQuery query, Class<T> type) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(type, "type");
        return jdbi.withHandle(handle -> {
            Query statement = handle.createQuery(query.sql());
            bind(statement, query.bindings());
            return statement.mapTo(type).list();
        });
    }

    public <T> Optional<T> firstScalar(JdbiQuery query, Class<T> type) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(type, "type");
        return jdbi.withHandle(handle -> {
            Query statement = handle.createQuery(query.sql());
            bind(statement, query.bindings());
            return statement.mapTo(type).findFirst();
        });
    }

    public long count(JdbiQuery query) {
        Objects.requireNonNull(query, "query");
        return jdbi.withHandle(handle -> {
            Query statement = handle.createQuery(query.countSql());
            bind(statement, query.countBindings());
            Number value = statement.mapTo(Number.class).one();
            return value == null ? 0L : value.longValue();
        });
    }

    public <T> QueryPage<T> page(JdbiQuery query, Function<Map<String, Object>, T> mapper) {
        List<T> items = list(query, mapper);
        long total = count(query);
        return new QueryPage<>(items, total);
    }

    public QueryPage<Map<String, Object>> pageMaps(JdbiQuery query) {
        List<Map<String, Object>> items = new ArrayList<>(maps(query));
        return new QueryPage<>(items, count(query));
    }

    private static void bind(SqlStatement<?> statement, Map<String, ?> bindings) {
        if (bindings == null) return;
        bindings.forEach((name, value) -> {
            if (value instanceof Collection<?>) {
                Collection<?> collection = (Collection<?>) value;
                if (collection.isEmpty()) {
                    throw new IllegalArgumentException("Cannot bind empty collection: " + name);
                }
                statement.bindList(name, collection);
            } else {
                statement.bind(name, value);
            }
        });
    }
}
