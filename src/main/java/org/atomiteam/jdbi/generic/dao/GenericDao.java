package org.atomiteam.jdbi.generic.dao;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.StatementContext;

import com.google.gson.Gson;

/**
 * Generic CRUD DAO for arbitrary Java POJOs backed by JDBI.
 *
 * <p>Entities may optionally extend {@link BaseEntity}, {@link Entity}, or
 * {@link LongEntity}, but inheritance from a persistence base class is not
 * required. A conventional property named {@code id} is used as the primary
 * key when primary-key operations are requested.</p>
 *
 * @param <T> entity type
 */
public class GenericDao<T> {

    private static final String SQL_IDENTIFIER_PATTERN = "[A-Za-z_][A-Za-z0-9_]*";

    private final Jdbi jdbi;
    private final Class<T> klazz;
    private final String table;
    private final ColumnNaming columnNaming;
    private final Gson gson = new Gson();

    /**
     * Creates a DAO using legacy identity column naming.
     */
    public GenericDao(Jdbi jdbi, Class<T> klazz, String table) {
        this(jdbi, klazz, table, ColumnNaming.IDENTITY);
    }

    /**
     * Creates a DAO using the requested Java-property to database-column naming strategy.
     */
    public GenericDao(Jdbi jdbi, Class<T> klazz, String table, ColumnNaming columnNaming) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi");
        this.klazz = Objects.requireNonNull(klazz, "klazz");
        this.table = requireIdentifier(table, "table");
        this.columnNaming = Objects.requireNonNull(columnNaming, "columnNaming");
        validateMappedColumns();
    }

    /**
     * Inserts an entity and returns the same instance.
     *
     * <p>If an {@code id} property exists and is null, it is omitted from the
     * INSERT and the generated database key is read back and assigned to the
     * entity. If the id is already set, it is inserted normally.</p>
     */
    public T insert(T entity) {
        Objects.requireNonNull(entity, "entity");
        try {
            Map<String, Object> data = toChanges(entity);
            Field idField = findField(klazz, "id");
            boolean generatedId = idField != null && getFieldValue(entity, idField) == null;
            if (generatedId) {
                data.remove("id");
            }

            if (data.isEmpty()) {
                throw new IllegalArgumentException("Entity has no non-null values to insert into table " + table);
            }

            List<String> properties = data.keySet().stream().collect(Collectors.toList());
            List<String> columns = properties.stream().map(this::columnName).collect(Collectors.toList());
            List<String> placeholders = properties.stream().map(this::bindName).map(property -> ":" + property)
                    .collect(Collectors.toList());

            String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", table,
                    String.join(", ", columns), String.join(", ", placeholders));

            if (generatedId) {
                Object generatedKey = jdbi.withHandle(handle -> handle.createUpdate(sql)
                        .bindMap(data)
                        .executeAndReturnGeneratedKeys(columnName("id"))
                        .map((rs, ctx) -> rs.getObject(1))
                        .one());
                PropertyUtils.setPropertyValue(entity, klazz, idField, generatedKey);
            } else {
                jdbi.withHandle(handle -> handle.createUpdate(sql).bindMap(data).execute());
            }
            return entity;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error inserting entity into table " + table, e);
        }
    }

    /**
     * Retrieves an entity by its primary key. String and numeric keys are supported.
     */
    public Optional<T> getById(Object id) {
        String sql = String.format("SELECT * FROM %s WHERE %s = ?", table, columnName("id"));
        return jdbi.withHandle(handle -> handle.createQuery(sql).bind(0, id)
                .map(new JsonRowMapper<>(klazz, columnNaming)).findFirst());
    }

    /**
     * Retrieves the first entity matching all supplied Java-property values.
     */
    public Optional<T> get(Map<String, ?> values) {
        List<T> items = list(values);
        return items.stream().findFirst();
    }

    /**
     * Retrieves all entities matching all supplied Java-property values.
     */
    public List<T> list(Map<String, ?> values) {
        Filtering filtering = Filtering.create();
        if (values != null) {
            values.forEach(filtering::eq);
        }
        return filter(filtering);
    }

    public List<T> filter(Filtering conditions) {
        Objects.requireNonNull(conditions, "conditions");
        String whereClause = buildWhereClause(conditions);

        StringBuilder pagination = new StringBuilder();
        if (conditions.getSorting() != null) {
            pagination.append(" ORDER BY ").append(mapSorting(conditions.getSorting()));
        }
        if (conditions.getLimit() != null) {
            pagination.append(" LIMIT ").append(conditions.getLimit());
        }
        if (conditions.getOffset() != null) {
            pagination.append(" OFFSET ").append(conditions.getOffset());
        }

        String sql = String.format("SELECT * FROM %s %s %s", table, whereClause, pagination);
        return jdbi.withHandle(handle -> {
            Query query = handle.createQuery(sql);
            bindQueryParameters(query, conditions);
            return query.map(new JsonRowMapper<>(klazz, columnNaming)).list();
        });
    }

    public Integer count(Filtering conditions) {
        Objects.requireNonNull(conditions, "conditions");
        String whereClause = buildWhereClause(conditions);
        String sql = String.format("SELECT count(*) FROM %s %s", table, whereClause);
        return jdbi.withHandle(handle -> {
            Query query = handle.createQuery(sql);
            bindQueryParameters(query, conditions);
            return query.mapTo(Integer.class).one();
        });
    }

    /**
     * Deletes by primary-key value or by entity instance.
     */
    public int delete(Object idOrEntity) {
        Object id = idOrEntity;
        if (idOrEntity != null && klazz.isInstance(idOrEntity)) {
            id = getIdValue(klazz.cast(idOrEntity));
        }
        if (id == null) {
            throw new IllegalArgumentException("Primary key cannot be null for table " + table);
        }
        String sql = String.format("DELETE FROM %s WHERE %s = ?", table, columnName("id"));
        Object finalId = id;
        return jdbi.withHandle(handle -> handle.createUpdate(sql).bind(0, finalId).execute());
    }

    /**
     * Updates all non-null properties except id and returns the affected row count.
     */
    public int update(T target) {
        Objects.requireNonNull(target, "target");
        Object id = getIdValue(target);
        if (id == null) {
            throw new IllegalArgumentException("Entity id cannot be null when updating table " + table);
        }

        Map<String, Object> changes = new LinkedHashMap<>(toChanges(target));
        changes.remove("id");
        if (changes.isEmpty()) {
            return 0;
        }

        String assignments = changes.keySet().stream()
                .map(property -> String.format("%s = :%s", columnName(property), bindName(property)))
                .collect(Collectors.joining(", "));
        String sql = String.format("UPDATE %s SET %s WHERE %s = :id", table,
                assignments, columnName("id"));

        return jdbi.withHandle(handle -> handle.createUpdate(sql)
                .bind("id", id).bindMap(changes).execute());
    }

    public T updateAndReturn(T target) {
        update(target);
        return target;
    }

    private Map<String, Object> toChanges(T entity) {
        Map<String, Object> changes = new LinkedHashMap<>();
        Class<?> currentClass = entity.getClass();
        try {
            while (currentClass != null) {
                for (Field field : currentClass.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())
                            || field.isSynthetic() || field.isAnnotationPresent(Transient.class)) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    if (value != null) {
                        changes.put(field.getName(), field.isAnnotationPresent(Json.class)
                                ? gson.toJson(value)
                                : value);
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
            return changes;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to inspect entity " + entity.getClass().getName(), e);
        }
    }

    private Object getIdValue(T entity) {
        Field idField = requireMappedField("id");
        return getFieldValue(entity, idField);
    }

    private static Object getFieldValue(Object entity, Field field) {
        try {
            field.setAccessible(true);
            return field.get(entity);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read property " + field.getName(), e);
        }
    }

    private String buildWhereClause(Filtering conditions) {
        if (conditions.filterings().isEmpty()) {
            return "";
        }

        String clause = conditions.filterings().stream()
                .map(filter -> {
                    String property = requireMappedProperty(filter.getName());
                    String column = columnName(property);
                    String parameter = bindName(property);
                    switch (filter.getOperator()) {
                        case In:
                            return String.format("%s IN (<%s>)", column, parameter);
                        case NotIn:
                            return String.format("%s NOT IN (<%s>)", column, parameter);
                        case Like:
                            return String.format("%s LIKE :%s", column, parameter);
                        case NotLike:
                            return String.format("%s NOT LIKE :%s", column, parameter);
                        case Eq:
                            return String.format("%s = :%s", column, parameter);
                        case NotEq:
                            return String.format("%s != :%s", column, parameter);
                        default:
                            throw new IllegalArgumentException("Unsupported operator: " + filter.getOperator());
                    }
                })
                .collect(Collectors.joining(" " + conditions.getOperator().name() + " "));
        return "WHERE " + clause;
    }

    private void bindQueryParameters(Query query, Filtering conditions) {
        for (Filter filter : conditions.filterings()) {
            String key = bindName(requireMappedProperty(filter.getName()));
            Object value = filter.getValue();
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
        Field field = requireMappedField(propertyName);
        String column = field.isAnnotationPresent(Column.class)
                ? field.getAnnotation(Column.class).value()
                : columnNaming.toColumnName(propertyName);
        return requireIdentifier(column, "column");
    }

    private String requireMappedProperty(String propertyName) {
        if (propertyName == null || propertyName.isBlank()) {
            throw new IllegalArgumentException("Property name cannot be blank");
        }
        requireMappedField(propertyName);
        return propertyName;
    }

    private Field requireMappedField(String propertyName) {
        Field field = findField(klazz, propertyName);
        if (field == null || Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())
                || field.isSynthetic() || field.isAnnotationPresent(Transient.class)) {
            throw new IllegalArgumentException("Unknown or unmapped property '" + propertyName
                    + "' for entity " + klazz.getName());
        }
        return field;
    }

    private String bindName(String propertyName) {
        requireMappedProperty(propertyName);
        return requireIdentifier(propertyName, "bind property");
    }

    private void validateMappedColumns() {
        Class<?> current = klazz;
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())
                        || field.isSynthetic() || field.isAnnotationPresent(Transient.class)) {
                    continue;
                }
                String column = field.isAnnotationPresent(Column.class)
                        ? field.getAnnotation(Column.class).value()
                        : columnNaming.toColumnName(field.getName());
                requireIdentifier(column, "column");
                requireIdentifier(field.getName(), "property");
            }
            current = current.getSuperclass();
        }
    }

    private static String requireIdentifier(String value, String description) {
        if (value == null || !value.matches(SQL_IDENTIFIER_PATTERN)) {
            throw new IllegalArgumentException("Invalid SQL " + description + " identifier: " + value);
        }
        return value;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}

class JsonRowMapper<T> implements RowMapper<T> {
    private final Class<T> type;
    private final Gson gson = new Gson();
    private final ColumnNaming columnNaming;

    JsonRowMapper(Class<T> type, ColumnNaming columnNaming) {
        this.type = type;
        this.columnNaming = columnNaming;
    }

    @Override
    public T map(ResultSet rs, StatementContext ctx) throws SQLException {
        try {
            T instance = type.getDeclaredConstructor().newInstance();
            Class<?> currentClass = type;
            while (currentClass != null) {
                for (Field field : currentClass.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())
                            && !field.isSynthetic() && !field.isAnnotationPresent(Transient.class)) {
                        field.setAccessible(true);
                        String column = field.isAnnotationPresent(Column.class)
                                ? field.getAnnotation(Column.class).value()
                                : columnNaming.toColumnName(field.getName());
                        if (column == null || !column.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                            throw new SQLException("Invalid mapped SQL column identifier: " + column);
                        }
                        try {
                            Object value = field.isAnnotationPresent(Json.class)
                                    ? gson.fromJson(rs.getString(column), field.getType())
                                    : rs.getObject(column);
                            PropertyUtils.setPropertyValue(instance, type, field, value);
                        } catch (SQLException ignored) {
                            // A partial SELECT may legitimately omit a mapped field.
                        }
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
            return instance;
        } catch (Exception e) {
            throw new SQLException("Failed to map result set to " + type.getName(), e);
        }
    }
}
