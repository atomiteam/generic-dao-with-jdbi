package org.atomiteam.jdbi.generic.query;

import org.atomiteam.jdbi.generic.dao.ColumnNaming;
import org.atomiteam.jdbi.generic.dao.EntityCodec;
import org.atomiteam.jdbi.generic.dao.ReflectionEntityCodec;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Central execution/mapping layer for {@link JdbiQuery}.
 *
 * <p>All bindings, collection expansion, pagination, total counts and common row mapping live here
 * so application DAOs do not repeat mapToMap/bind/count boilerplate.
 */
public final class JdbiQueryExecutor {

    private final Jdbi jdbi;
    private final org.jdbi.v3.core.Handle handle;

    public JdbiQueryExecutor(Jdbi jdbi) {
        this(jdbi, null);
    }

    private JdbiQueryExecutor(Jdbi jdbi, org.jdbi.v3.core.Handle handle) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi");
        this.handle = handle;
    }

    /** Borrows a transaction Handle without managing its lifecycle. */
    public JdbiQueryExecutor using(org.jdbi.v3.core.Handle handle) {
        return new JdbiQueryExecutor(jdbi, Objects.requireNonNull(handle));
    }

    private <R> R withHandle(Function<org.jdbi.v3.core.Handle, R> operation) {
        return handle == null ? jdbi.withHandle(h -> operation.apply(h)) : operation.apply(handle);
    }

    /**
     * A positional value with an explicit JDBC type, e.g. Types.OTHER for inferred PostgreSQL
     * filters.
     */
    public static final class TypedValue {
        private final Object value;
        private final int sqlType;

        public TypedValue(Object value, int sqlType) {
            this.value = value;
            this.sqlType = sqlType;
        }
    }

    /**
     * Executes trusted application-compiled SQL with positional parameters and a row mapper. SQL
     * identifiers and authorization predicates must be supplied by an application allowlist.
     * Borrows using(handle) with the same lifecycle rules as other executor methods.
     */
    public <T> List<T> rows(
            String sql,
            List<?> parameters,
            int timeoutSeconds,
            org.jdbi.v3.core.mapper.RowMapper<T> mapper) {
        Objects.requireNonNull(sql);
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(mapper);
        if (timeoutSeconds < 1)
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        return withHandle(
                h -> {
                    org.jdbi.v3.core.statement.Query statement =
                            h.createQuery(sql).setQueryTimeout(timeoutSeconds);
                    for (int i = 0; i < parameters.size(); i++) {
                        Object value = parameters.get(i);
                        if (value instanceof TypedValue) {
                            TypedValue typed = (TypedValue) value;
                            statement.bindBySqlType(i, typed.value, typed.sqlType);
                        } else statement.bind(i, value);
                    }
                    return statement.map(mapper).list();
                });
    }

    public List<Map<String, Object>> maps(JdbiQuery query) {
        Objects.requireNonNull(query, "query");
        return execute(query.sql(), query.bindings(), statement -> statement.mapToMap().list());
    }

    public Optional<Map<String, Object>> firstMap(JdbiQuery query) {
        Objects.requireNonNull(query, "query");
        return execute(
                query.sql(), query.bindings(), statement -> statement.mapToMap().findFirst());
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
     * Maps a projection through the same reflection codec used by GenericDao. SQL columns/aliases
     * therefore follow the selected naming strategy.
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
        return execute(query.sql(), query.bindings(), statement -> statement.mapTo(type).list());
    }

    public <T> Optional<T> firstScalar(JdbiQuery query, Class<T> type) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(type, "type");
        return execute(
                query.sql(), query.bindings(), statement -> statement.mapTo(type).findFirst());
    }

    public long count(JdbiQuery query) {
        Objects.requireNonNull(query, "query");
        return execute(
                query.countSql(),
                query.countBindings(),
                statement -> statement.map((rs, ctx) -> rs.getLong(1)).one());
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

    /** Fetches limit+1 rows, returns continuation values without an exact-count query. */
    public <T> SeekPage<T> seekPage(
            JdbiQuery query,
            int pageSize,
            Function<Map<String, Object>, T> mapper,
            Function<T, List<?>> position) {
        Objects.requireNonNull(query);
        Objects.requireNonNull(mapper);
        Objects.requireNonNull(position);
        if (pageSize < 1 || pageSize > 10000 || query.getOffset() != null || !query.isSeeking())
            throw new IllegalArgumentException(
                    "Use seekAfter with pageSize 1..10000 without offset");
        List<T> fetched = list(query.copy().limit((long) pageSize + 1), mapper);
        boolean more = fetched.size() > pageSize;
        List<T> items = more ? fetched.subList(0, pageSize) : fetched;
        return new SeekPage<>(
                items, more, more ? position.apply(items.get(items.size() - 1)) : null);
    }

    private <T> T execute(String sql, Map<String, ?> bindings, Function<Query, T> result) {
        return withHandle(
                handle -> {
                    Query statement = handle.createQuery(sql);
                    org.atomiteam.jdbi.generic.internal.StatementBindings.bind(statement, bindings);
                    return result.apply(statement);
                });
    }
}
