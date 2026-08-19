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
 * Generic CRUD DAO for entities backed by JDBI.
 *
 * @param <T> entity type
 */
public class GenericDao<T extends BaseEntity<?>> {

    private final Jdbi jdbi;
    private final Class<T> klazz;
    private final String table;
    private final ColumnNaming columnNaming;

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
        this.table = Objects.requireNonNull(table, "table");
        this.columnNaming = Objects.requireNonNull(columnNaming, "columnNaming");
    }

    public void insert(T entity) {
        try {
            Map<String, Object> data = entity.toChanges();
            List<String> properties = data.keySet().stream().collect(Collectors.toList());
            List<String> columns = properties.stream().map(this::columnName).collect(Collectors.toList());
            List<String> placeholders = properties.stream().map(property -> ":" + property).collect(Collectors.toList());

            String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", table,
                    String.join(", ", columns), String.join(", ", placeholders));

            jdbi.withHandle(handle -> handle.createUpdate(sql).bindMap(data).execute());
        } catch (Exception e) {
            throw new RuntimeException("Error inserting entity into table " + table, e);
        }
    }

    /**
     * Retrieves an entity by its primary key. String and Long keys are both supported.
     */
    public Optional<T> getById(Object id) {
        String sql = String.format("SELECT * FROM %s WHERE %s = ?", table, columnName("id"));
        return jdbi.withHandle(handle -> handle.createQuery(sql).bind(0, id)
                .map(new JsonRowMapper<>(klazz, columnNaming)).findFirst());
    }

    public List<T> filter(Filtering conditions) {
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
        String whereClause = buildWhereClause(conditions);
        String sql = String.format("SELECT count(*) FROM %s %s", table, whereClause);
        return jdbi.withHandle(handle -> {
            Query query = handle.createQuery(sql);
            bindQueryParameters(query, conditions);
            return query.mapTo(Integer.class).one();
        });
    }

    /**
     * Deletes an entity by primary key. String and Long keys are both supported.
     */
    public int delete(Object id) {
        String sql = String.format("DELETE FROM %s WHERE %s = ?", table, columnName("id"));
        return jdbi.withHandle(handle -> handle.createUpdate(sql).bind(0, id).execute());
    }

    public int update(T target) {
        Map<String, Object> changes = new LinkedHashMap<>(target.toChanges());
        changes.remove("id");
        if (changes.isEmpty()) {
            return 0;
        }

        String assignments = changes.keySet().stream()
                .map(property -> String.format("%s = :%s", columnName(property), property))
                .collect(Collectors.joining(", "));
        String sql = String.format("UPDATE %s SET %s WHERE %s = :id", table,
                assignments, columnName("id"));

        return jdbi.withHandle(handle -> handle.createUpdate(sql)
                .bind("id", target.getId()).bindMap(changes).execute());
    }

    private String buildWhereClause(Filtering conditions) {
        if (conditions.filterings().isEmpty()) {
            return "";
        }

        String clause = conditions.filterings().stream()
                .map(filter -> {
                    String property = filter.getName();
                    String column = columnName(property);
                    switch (filter.getOperator()) {
                        case In:
                            return String.format("%s IN (<%s>)", column, property);
                        case NotIn:
                            return String.format("%s NOT IN (<%s>)", column, property);
                        case Like:
                            return String.format("%s LIKE :%s", column, property);
                        case NotLike:
                            return String.format("%s NOT LIKE :%s", column, property);
                        case Eq:
                            return String.format("%s = :%s", column, property);
                        case NotEq:
                            return String.format("%s != :%s", column, property);
                        default:
                            throw new IllegalArgumentException("Unsupported operator: " + filter.getOperator());
                    }
                })
                .collect(Collectors.joining(" " + conditions.getOperator().name() + " "));
        return "WHERE " + clause;
    }

    private void bindQueryParameters(Query query, Filtering conditions) {
        for (Filter filter : conditions.filterings()) {
            String key = filter.getName();
            Object value = filter.getValue();
            if (value instanceof Collection<?>) {
                query.bindList(key, (Collection<?>) value);
            } else {
                query.bind(key, value);
            }
        }
    }

    private String mapSorting(String sorting) {
        int separator = sorting.lastIndexOf(' ');
        if (separator < 0) {
            return columnName(sorting);
        }
        String property = sorting.substring(0, separator);
        return columnName(property) + sorting.substring(separator);
    }

    private String columnName(String propertyName) {
        Field field = findField(klazz, propertyName);
        if (field != null && field.isAnnotationPresent(Column.class)) {
            return field.getAnnotation(Column.class).value();
        }
        return columnNaming.toColumnName(propertyName);
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
                    if (!Modifier.isStatic(field.getModifiers()) && !field.isAnnotationPresent(Transient.class)) {
                        field.setAccessible(true);
                        String column = field.isAnnotationPresent(Column.class)
                                ? field.getAnnotation(Column.class).value()
                                : columnNaming.toColumnName(field.getName());
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
