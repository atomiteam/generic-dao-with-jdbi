package org.atomiteam.jdbi.generic.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.Handle;
import java.util.Collections;
import java.util.function.Function;
import org.jdbi.v3.core.statement.Query;

/** Generic CRUD DAO for arbitrary Java POJOs backed by JDBI. */
public class GenericDao<T> {

    private static final String SQL_IDENTIFIER_PATTERN = "[A-Za-z_][A-Za-z0-9_]*";

    private final Jdbi jdbi;
    private final Class<T> klazz;
    private final String table;
    private final ColumnNaming columnNaming;
    private final EntityCodec<T> codec;
    private final Map<String,Object> scope;
    private final Handle handle;

    public GenericDao(Jdbi jdbi, Class<T> klazz, String table) {
        this(jdbi, klazz, table, ColumnNaming.IDENTITY);
    }

    public GenericDao(Jdbi jdbi, Class<T> klazz, String table, ColumnNaming columnNaming) {
        this(jdbi, klazz, table, columnNaming, new ReflectionEntityCodec<>(klazz, columnNaming));
    }

    public GenericDao(
            Jdbi jdbi,
            Class<T> klazz,
            String table,
            ColumnNaming columnNaming,
            EntityCodec<T> codec) {
        this(jdbi, klazz, table, columnNaming, codec, Collections.emptyMap(), null);
    }

    private GenericDao(Jdbi jdbi, Class<T> klazz, String table, ColumnNaming columnNaming,
            EntityCodec<T> codec, Map<String,Object> scope, Handle handle) {
        this.scope = Collections.unmodifiableMap(new LinkedHashMap<>(scope));
        this.handle = handle;
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi");
        this.klazz = Objects.requireNonNull(klazz, "klazz");
        this.table = requireTableIdentifier(table);
        this.columnNaming = Objects.requireNonNull(columnNaming, "columnNaming");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    /** Returns an immutable DAO view whose predicates always AND the supplied scope. */
    public GenericDao<T> scoped(Map<String,?> values) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("Scope cannot be empty");
        Map<String,Object> merged = new LinkedHashMap<>(scope);
        values.forEach((key,value) -> {
            String property = requireMappedProperty(key);
            if (value == null) throw new IllegalArgumentException("Scope values cannot be null");
            Object encoded = codec.toStorageValue(property, value);
            if (merged.containsKey(property) && !Objects.equals(merged.get(property), encoded))
                throw new IllegalArgumentException("Cannot replace an existing scope");
            merged.put(property, encoded);
        });
        return new GenericDao<>(jdbi, klazz, table, columnNaming, codec, merged, handle);
    }

    /** Borrows the caller's Handle. Never commits, rolls back or closes it. Not thread-safe. */
    public GenericDao<T> using(Handle handle) {
        Objects.requireNonNull(handle);
        if (codec instanceof PostgresEntityCodec<?>) DsqlSupport.configure(handle);
        return new GenericDao<>(jdbi, klazz, table, columnNaming, codec, scope, Objects.requireNonNull(handle));
    }

    private <R> R withHandle(Function<Handle,R> operation) {
        return handle == null ? jdbi.withHandle(h -> operation.apply(h)) : operation.apply(handle);
    }

    private Filtering keyFilter(Map<String,?> keys) {
        if (keys == null || keys.isEmpty()) throw new IllegalArgumentException("Key cannot be empty");
        Filtering result = Filtering.create();
        keys.forEach((key,value) -> {
            requireMappedProperty(key);
            if (value == null) throw new IllegalArgumentException("Key values cannot be null");
            result.equalTo(key,value);
        });
        return result;
    }

    /** Composite/optimistic keys, e.g. id + version, combined with the DAO's mandatory scope. */
    public Optional<T> getByKey(Map<String,?> keys) {
        return filter(keyFilter(keys).withLimit(1L)).stream().findFirst();
    }

    public int deleteByKey(Map<String,?> keys) { return delete(keyFilter(keys)); }

    public int updateByKey(Map<String,?> keys, Map<String,?> changes) {
        return updateStorageValues(keyFilter(keys), encodeChanges(changes));
    }

    public T insert(T entity) {
        Objects.requireNonNull(entity, "entity");
        Map<String, Object> data = sanitized(codec.toStorage(entity, false), false);
        scope.forEach((key,value) -> {
            if (data.containsKey(key) && !Objects.equals(data.get(key),value))
                throw new IllegalArgumentException("Entity is outside the DAO scope");
            data.put(key,value);
        });
        Object currentId = data.get("id");
        boolean generatedId = codec.isMappedProperty("id") && (
                currentId == null
                        || (currentId instanceof Number
                                && ((Number) currentId).longValue() == 0L));
        if (generatedId) data.remove("id");
        if (data.isEmpty()) {
            throw new IllegalArgumentException(
                    "Entity has no non-null values to insert into table " + table);
        }

        List<String> properties = new ArrayList<>(data.keySet());
        List<String> columns =
                properties.stream().map(this::columnName).collect(Collectors.toList());
        String placeholders =
                properties.stream()
                        .map(this::bindName)
                        .map(p -> ":" + p)
                        .collect(Collectors.joining(", "));
        String sql =
                String.format(
                        "INSERT INTO %s (%s) VALUES (%s)",
                        table, String.join(", ", columns), placeholders);

        if (generatedId) {
            Object generated =
                    withHandle(
                            handle ->
                                    handle.createUpdate(sql)
                                            .bindMap(data)
                                            .executeAndReturnGeneratedKeys(columnName("id"))
                                            .map((rs, ctx) -> rs.getObject(1))
                                            .one());
            codec.setId(entity, generated);
        } else {
            withHandle(handle -> handle.createUpdate(sql).bindMap(data).execute());
        }
        return entity;
    }

    public Optional<T> getById(Object id) {
        if (id == null) return Optional.empty();
        return getByKey(Collections.singletonMap("id",id));
    }

    public Optional<T> get(Map<String, ?> values) {
        return list(values).stream().findFirst();
    }

    public List<T> list(Map<String, ?> values) {
        Filtering filtering = Filtering.create();
        if (values != null) values.forEach(filtering::eq);
        return filter(filtering);
    }

    public List<T> filter(Filtering conditions) {
        Objects.requireNonNull(conditions, "conditions");
        StringBuilder suffix = new StringBuilder();
        if (!conditions.getSortings().isEmpty()) {
            suffix.append(" ORDER BY ")
                    .append(
                            conditions.getSortings().stream()
                                    .map(this::mapSorting)
                                    .collect(Collectors.joining(", ")));
        }
        if (conditions.getLimit() != null) {
            suffix.append(" LIMIT ").append(conditions.getLimit());
            if (conditions.getOffset() != null) {
                suffix.append(" OFFSET ").append(conditions.getOffset());
            }
        } else if (conditions.getOffset() != null) {
            suffix.append(" LIMIT ").append(Long.MAX_VALUE).append(" OFFSET ").append(conditions.getOffset());
        }
        String sql =
                String.format(
                        "SELECT * FROM %s %s%s", table, buildWhereClause(conditions), suffix);
        return withHandle(
                handle -> {
                    Query query = handle.createQuery(sql);
                    bindQueryParameters(query, conditions);
                    return query.mapToMap().list().stream()
                            .map(codec::fromStorage)
                            .collect(Collectors.toList());
                });
    }

    public Integer count(Filtering conditions) {
        Objects.requireNonNull(conditions, "conditions");
        String sql =
                String.format("SELECT count(*) FROM %s %s", table, buildWhereClause(conditions));
        return withHandle(
                handle -> {
                    Query query = handle.createQuery(sql);
                    bindQueryParameters(query, conditions);
                    return query.mapTo(Integer.class).one();
                });
    }

    public int delete(Object idOrEntity) {
        Object id = idOrEntity;
        if (idOrEntity != null && klazz.isInstance(idOrEntity)) {
            id = codec.getId(klazz.cast(idOrEntity));
        }
        if (id == null) {
            throw new IllegalArgumentException("Primary key cannot be null for table " + table);
        }
        return deleteByKey(Collections.singletonMap("id",id));
    }

    public int delete(Filtering conditions) {
        Objects.requireNonNull(conditions, "conditions");
        if (conditions.filterings().isEmpty()) {
            throw new IllegalArgumentException("Refusing unfiltered delete from " + table);
        }
        String sql =
                String.format("DELETE FROM %s %s", table, buildWhereClause(conditions));
        return withHandle(
                handle -> {
                    org.jdbi.v3.core.statement.Update update = handle.createUpdate(sql);
                    bindStatementParameters(update, conditions);
                    return update.execute();
                });
    }

    public int update(T target) {
        Objects.requireNonNull(target, "target");
        Object id = codec.getId(target);
        if (id == null) {
            throw new IllegalArgumentException(
                    "Entity id cannot be null when updating table " + table);
        }

        // codec.toStorage() has already converted application values to their storage form.
        // Do not pass these values through codec.toStorageValue() again, otherwise encoded
        // fields such as JSON are serialized twice.
        Map<String, Object> changes = sanitized(codec.toStorage(target, false), false);
        changes.remove("id");
        return updateStorageValues(keyFilter(Collections.singletonMap("id",id)), changes);
    }

    /** Explicit patch update. A null map value writes SQL NULL. */
    public int update(Object id, Map<String, ?> requestedChanges) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "Entity id cannot be null when updating table " + table);
        }
        return updateByKey(Collections.singletonMap("id",id),requestedChanges);
    }

    private Map<String,Object> encodeChanges(Map<String,?> requestedChanges) {
        Objects.requireNonNull(requestedChanges, "changes");
        Map<String,Object> result = new LinkedHashMap<>();
        requestedChanges.forEach((key,value) -> {
            if (!"id".equals(key)) {
                String property = requireMappedProperty(key);
                result.put(property,codec.toStorageValue(property,value));
            }
        });
        return result;
    }

    private int updateStorageValues(Filtering keys, Map<String,Object> requested) {
        Map<String,Object> changes = new LinkedHashMap<>(requested);
        scope.forEach((key,value) -> {
            if (changes.containsKey(key) && !Objects.equals(changes.get(key),value))
                throw new IllegalArgumentException("Cannot change the DAO scope");
            changes.remove(key);
        });
        if (changes.isEmpty()) return 0;
        List<String> properties = new ArrayList<>(changes.keySet());
        List<String> assignments = new ArrayList<>();
        for(int i=0;i<properties.size();i++) assignments.add(columnName(properties.get(i))+" = :_u"+i);
        String sql = "UPDATE "+table+" SET "+String.join(", ",assignments)+" "+buildWhereClause(keys);
        return withHandle(h -> {
            org.jdbi.v3.core.statement.Update update = h.createUpdate(sql);
            bindStatementParameters(update,keys);
            for(int i=0;i<properties.size();i++) update.bind("_u"+i,changes.get(properties.get(i)));
            return update.execute();
        });
    }

    public T updateAndReturn(T target) {
        update(target);
        return target;
    }

    public T fromStorage(Map<String, Object> values) {
        return codec.fromStorage(values);
    }

    public Map<String, Object> toStorage(T entity, boolean includeNulls) {
        return codec.toStorage(entity, includeNulls);
    }

    private Map<String, Object> sanitized(Map<String, Object> input, boolean allowNull) {
        Map<String, Object> output = new LinkedHashMap<>();
        if (input == null) return output;
        input.forEach(
                (property, value) -> {
                    if (allowNull || value != null) {
                        String mapped = requireMappedProperty(property);
                        output.put(mapped, value);
                    }
                });
        return output;
    }

    private String buildWhereClause(Filtering conditions) {
        List<Filter> filters = conditions.filterings();


        List<String> clauses = new ArrayList<>();
        for (int i = 0; i < filters.size(); i++) {
            Filter filter = filters.get(i);
            String property = requireMappedProperty(filter.getName());
            String column = columnName(property);
            String parameter = filterParameter(i);
            switch (filter.getOperator()) {
                case In:
                    clauses.add(column + " IN (<" + parameter + ">)");
                    break;
                case NotIn:
                    clauses.add(column + " NOT IN (<" + parameter + ">)");
                    break;
                case Like:
                    clauses.add(column + " LIKE :" + parameter);
                    break;
                case NotLike:
                    clauses.add(column + " NOT LIKE :" + parameter);
                    break;
                case Eq:
                    clauses.add(column + " = :" + parameter);
                    break;
                case NotEq:
                    clauses.add(column + " != :" + parameter);
                    break;
                case Lt:
                    clauses.add(column + " < :" + parameter);
                    break;
                case Lte:
                    clauses.add(column + " <= :" + parameter);
                    break;
                case Gt:
                    clauses.add(column + " > :" + parameter);
                    break;
                case Gte:
                    clauses.add(column + " >= :" + parameter);
                    break;
                case IsNull:
                    clauses.add(column + " IS NULL");
                    break;
                case IsNotNull:
                    clauses.add(column + " IS NOT NULL");
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported operator: " + filter.getOperator());
            }
        }
        List<String> scoped = new ArrayList<>();
        int index = 0;
        for(String key:scope.keySet()) scoped.add(columnName(key)+" = :_scope"+index++);
        if (!clauses.isEmpty()) scoped.add("("+String.join(" " + conditions.getOperator().name() + " ", clauses)+")");
        return scoped.isEmpty() ? "" : "WHERE "+String.join(" AND ",scoped);
    }

    private void bindQueryParameters(Query query, Filtering conditions) {
        bindParameters(
                (name, value) -> {
                    if (value instanceof Collection<?>) {
                        query.bindList(name, (Collection<?>) value);
                    } else {
                        query.bind(name, value);
                    }
                },
                conditions);
    }

    private void bindStatementParameters(
            org.jdbi.v3.core.statement.SqlStatement<?> statement, Filtering conditions) {
        bindParameters(
                (name, value) -> {
                    if (value instanceof Collection<?>) {
                        statement.bindList(name, (Collection<?>) value);
                    } else {
                        statement.bind(name, value);
                    }
                },
                conditions);
    }

    private void bindParameters(
            java.util.function.BiConsumer<String, Object> binder, Filtering conditions) {
        int scopedIndex = 0;
        for(Object value:scope.values()) binder.accept("_scope"+scopedIndex++,value);
        List<Filter> filters = conditions.filterings();
        for (int i = 0; i < filters.size(); i++) {
            Filter filter = filters.get(i);
            if (filter.getOperator() == Operator.IsNull
                    || filter.getOperator() == Operator.IsNotNull) {
                continue;
            }
            String property = requireMappedProperty(filter.getName());
            binder.accept(filterParameter(i), codec.toStorageValue(property, filter.getValue()));
        }
    }

    private String filterParameter(int index) {
        return "_f" + index;
    }

    private String mapSorting(Filtering.SortClause clause) {
        String property = requireMappedProperty(clause.getName());
        return columnName(property) + " " + clause.getSorting().name();
    }

    private String columnName(String propertyName) {
        String property = requireMappedProperty(propertyName);
        return requireSqlIdentifier(codec.columnName(property, columnNaming), "column");
    }

    private static String requireSqlIdentifier(String column, String description) {
        // A codec may explicitly preserve a case-sensitive PostgreSQL column.
        // Only one quoted identifier is accepted, never a SQL expression.
        if (column != null && column.length() > 2 && column.startsWith("\"") && column.endsWith("\"")) {
            requireIdentifier(column.substring(1, column.length() - 1), description);
            return column;
        }
        return requireIdentifier(column, description);
    }

    private String requireMappedProperty(String propertyName) {
        if (propertyName == null || propertyName.isBlank()) {
            throw new IllegalArgumentException("Property name cannot be blank");
        }
        requireIdentifier(propertyName, "property");
        if (!codec.isMappedProperty(propertyName)) {
            throw new IllegalArgumentException(
                    "Unknown or unmapped property '"
                            + propertyName
                            + "' for entity "
                            + klazz.getName());
        }
        return propertyName;
    }

    private String bindName(String propertyName) {
        return requireIdentifier(requireMappedProperty(propertyName), "bind property");
    }

    /** Allows qualified table names while validating every identifier component. */
    private static String requireTableIdentifier(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Invalid SQL table identifier: null");
        }
        // Keep empty components so leading, trailing and consecutive dots are rejected.
        for (String component : value.split("\\.", -1)) {
            requireSqlIdentifier(component, "table");
        }
        return value;
    }

    private static String requireIdentifier(String value, String description) {
        if (value == null || !value.matches(SQL_IDENTIFIER_PATTERN)) {
            throw new IllegalArgumentException(
                    "Invalid SQL " + description + " identifier: " + value);
        }
        return value;
    }
}
