package org.atomiteam.jdbi.generic.dao;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Query;

/** Generic CRUD DAO for arbitrary Java POJOs backed by JDBI. */
public class GenericDao<T> {

    private static final String SQL_IDENTIFIER_PATTERN = "[A-Za-z_][A-Za-z0-9_]*";

    private final Jdbi jdbi;
    private final Class<T> klazz;
    private final String table;
    private final ColumnNaming columnNaming;
    private final EntityCodec<T> codec;

    public GenericDao(Jdbi jdbi, Class<T> klazz, String table) {
        this(jdbi, klazz, table, ColumnNaming.IDENTITY);
    }

    public GenericDao(Jdbi jdbi, Class<T> klazz, String table, ColumnNaming columnNaming) {
        this(jdbi, klazz, table, columnNaming, new ReflectionEntityCodec<>(klazz, columnNaming));
    }

    public GenericDao(Jdbi jdbi, Class<T> klazz, String table, ColumnNaming columnNaming, EntityCodec<T> codec) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi");
        this.klazz = Objects.requireNonNull(klazz, "klazz");
        this.table = requireIdentifier(table, "table");
        this.columnNaming = Objects.requireNonNull(columnNaming, "columnNaming");
        this.codec = Objects.requireNonNull(codec, "codec");
        requireMappedProperty("id");
    }

    public T insert(T entity) {
        Objects.requireNonNull(entity, "entity");
        Map<String, Object> data = new LinkedHashMap<>(codec.toStorage(entity, false));
        Object currentId = codec.getId(entity);
        boolean generatedId = currentId == null;
        if (generatedId) {
            data.remove("id");
        }
        sanitizeChanges(data, false);
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Entity has no non-null values to insert into table " + table);
        }

        List<String> properties = data.keySet().stream().collect(Collectors.toList());
        List<String> columns = properties.stream().map(this::columnName).collect(Collectors.toList());
        String placeholders = properties.stream().map(this::bindName).map(p -> ":" + p)
                .collect(Collectors.joining(", "));
        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", table,
                String.join(", ", columns), placeholders);

        if (generatedId) {
            Object generated = jdbi.withHandle(handle -> handle.createUpdate(sql)
                    .bindMap(data)
                    .executeAndReturnGeneratedKeys(columnName("id"))
                    .map((rs, ctx) -> rs.getObject(1))
                    .one());
            codec.setId(entity, generated);
        } else {
            jdbi.withHandle(handle -> handle.createUpdate(sql).bindMap(data).execute());
        }
        return entity;
    }

    public Optional<T> getById(Object id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = String.format("SELECT * FROM %s WHERE %s = :id", table, columnName("id"));
        return jdbi.withHandle(handle -> handle.createQuery(sql)
                .bind("id", id)
                .mapToMap()
                .findFirst()
                .map(codec::fromStorage));
    }

    public Optional<T> get(Map<String, ?> values) {
        List<T> items = list(values);
        return items.stream().findFirst();
    }

    public List<T> list(Map<String, ?> values) {
        Filtering filtering = Filtering.create();
        if (values != null) {
            values.forEach(filtering::eq);
        }
        return filter(filtering);
    }

    public List<T> filter(Filtering conditions) {
        Objects.requireNonNull(conditions, "conditions");
        String where = buildWhereClause(conditions);
        StringBuilder suffix = new StringBuilder();
        if (conditions.getSorting() != null) {
            suffix.append(" ORDER BY ").append(mapSorting(conditions.getSorting()));
        }
        if (conditions.getLimit() != null) {
            suffix.append(" LIMIT ").append(conditions.getLimit());
        }
        if (conditions.getOffset() != null) {
            suffix.append(" OFFSET ").append(conditions.getOffset());
        }
        String sql = String.format("SELECT * FROM %s %s%s", table, where, suffix);
        return jdbi.withHandle(handle -> {
            Query query = handle.createQuery(sql);
            bindQueryParameters(query, conditions);
            return query.mapToMap().list().stream().map(codec::fromStorage).collect(Collectors.toList());
        });
    }

    public Integer count(Filtering conditions) {
        Objects.requireNonNull(conditions, "conditions");
        String sql = String.format("SELECT count(*) FROM %s %s", table, buildWhereClause(conditions));
        return jdbi.withHandle(handle -> {
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
        Object finalId = id;
        String sql = String.format("DELETE FROM %s WHERE %s = :id", table, columnName("id"));
        return jdbi.withHandle(handle -> handle.createUpdate(sql).bind("id", finalId).execute());
    }

    public int update(T target) {
        Objects.requireNonNull(target, "target");
        Object id = codec.getId(target);
        if (id == null) {
            throw new IllegalArgumentException("Entity id cannot be null when updating table " + table);
        }
        Map<String, Object> changes = new LinkedHashMap<>(codec.toStorage(target, false));
        changes.remove("id");
        return update(id, changes);
    }

    /** Explicit patch update. A null map value writes SQL NULL. */
    public int update(Object id, Map<String, ?> requestedChanges) {
        if (id == null) {
            throw new IllegalArgumentException("Entity id cannot be null when updating table " + table);
        }
        Objects.requireNonNull(requestedChanges, "changes");
        Map<String, Object> changes = new LinkedHashMap<>();
        requestedChanges.forEach((property, value) -> {
            if (!"id".equals(property)) {
                String mapped = requireMappedProperty(property);
                changes.put(mapped, codec.toStorageValue(mapped, value));
            }
        });
        sanitizeChanges(changes, true);
        if (changes.isEmpty()) {
            return 0;
        }

        String assignments = changes.keySet().stream()
                .map(property -> columnName(property) + " = :" + bindName(property))
                .collect(Collectors.joining(", "));
        String sql = String.format("UPDATE %s SET %s WHERE %s = :_genericDaoId", table,
                assignments, columnName("id"));
        return jdbi.withHandle(handle -> {
            org.jdbi.v3.core.statement.Update update = handle.createUpdate(sql).bind("_genericDaoId", id);
            changes.forEach(update::bind);
            return update.execute();
        });
    }

    public T updateAndReturn(T target) {
        update(target);
        return target;
    }

    private void sanitizeChanges(Map<String, Object> changes, boolean allowNull) {
        java.util.Iterator<Map.Entry<String, Object>> iterator = changes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            String property = requireMappedProperty(entry.getKey());
            if (!allowNull && entry.getValue() == null) {
                iterator.remove();
            } else if (!property.equals(entry.getKey())) {
                Object value = entry.getValue();
                iterator.remove();
                changes.put(property, value);
            }
        }
    }

    private String buildWhereClause(Filtering conditions) {
        List<Filter> filters = conditions.filterings();
        if (filters.isEmpty()) {
            return "";
        }
        String clause = filters.stream().map(filter -> {
            String property = requireMappedProperty(filter.getName());
            String column = columnName(property);
            String parameter = bindName(property);
            switch (filter.getOperator()) {
                case In: return column + " IN (<" + parameter + ">)";
                case NotIn: return column + " NOT IN (<" + parameter + ">)";
                case Like: return column + " LIKE :" + parameter;
                case NotLike: return column + " NOT LIKE :" + parameter;
                case Eq: return column + " = :" + parameter;
                case NotEq: return column + " != :" + parameter;
                case IsNull: return column + " IS NULL";
                case IsNotNull: return column + " IS NOT NULL";
                default: throw new IllegalArgumentException("Unsupported operator: " + filter.getOperator());
            }
        }).collect(Collectors.joining(" " + conditions.getOperator().name() + " "));
        return "WHERE " + clause;
    }

    private void bindQueryParameters(Query query, Filtering conditions) {
        for (Filter filter : conditions.filterings()) {
            if (filter.getOperator() == Operator.IsNull || filter.getOperator() == Operator.IsNotNull) {
                continue;
            }
            String property = requireMappedProperty(filter.getName());
            String key = bindName(property);
            Object value = codec.toStorageValue(property, filter.getValue());
            if (value instanceof Collection<?>) {
                query.bindList(key, (Collection<?>) value);
            } else {
                query.bind(key, value);
            }
        }
    }

    private String mapSorting(String sorting) {
        String trimmed = Objects.requireNonNull(sorting, "sorting").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Sorting cannot be empty");
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 1 || parts.length > 2) {
            throw new IllegalArgumentException("Invalid sorting expression: " + sorting);
        }
        String property = requireMappedProperty(parts[0]);
        if (parts.length == 1) {
            return columnName(property);
        }
        String direction = parts[1].toUpperCase(java.util.Locale.ROOT);
        if (!"ASC".equals(direction) && !"DESC".equals(direction)) {
            throw new IllegalArgumentException("Invalid sorting direction: " + parts[1]);
        }
        return columnName(property) + " " + direction;
    }

    private String columnName(String propertyName) {
        String property = requireMappedProperty(propertyName);
        return requireIdentifier(codec.columnName(property, columnNaming), "column");
    }

    private String requireMappedProperty(String propertyName) {
        if (propertyName == null || propertyName.isBlank()) {
            throw new IllegalArgumentException("Property name cannot be blank");
        }
        requireIdentifier(propertyName, "property");
        if (!codec.isMappedProperty(propertyName)) {
            throw new IllegalArgumentException("Unknown or unmapped property '" + propertyName
                    + "' for entity " + klazz.getName());
        }
        return propertyName;
    }

    private String bindName(String propertyName) {
        return requireIdentifier(requireMappedProperty(propertyName), "bind property");
    }

    private static String requireIdentifier(String value, String description) {
        if (value == null || !value.matches(SQL_IDENTIFIER_PATTERN)) {
            throw new IllegalArgumentException("Invalid SQL " + description + " identifier: " + value);
        }
        return value;
    }
}
