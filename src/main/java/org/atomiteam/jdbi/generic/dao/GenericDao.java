package org.atomiteam.jdbi.generic.dao;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
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
 * GenericDao provides a flexible and reusable implementation of data access operations
 * (CRUD and filtering) for any entity extending {@link Entity}, using JDBI for SQL database access.
 *
 * @param <T> the entity type handled by this DAO
 */
public class GenericDao<T extends Entity> {

    private Jdbi jdbi;
    private Class<T> klazz;
    private String table;

    /**
     * Constructs a new GenericDao instance.
     *
     * @param jdbi  the Jdbi instance for managing database operations
     * @param klazz the class type of the entity
     * @param table the name of the target database table
     */
    public GenericDao(Jdbi jdbi, Class<T> klazz, String table) {
        this.jdbi = jdbi;
        this.klazz = klazz;
        this.table = table;
    }

    /**
     * Inserts the given entity into the database.
     *
     * @param entity the entity to persist
     */
    public void insert(T entity) {
        try {
            Map<String, Object> data = entity.toChanges();
            List<String> columns = data.keySet().stream()
                    .collect(Collectors.toList());
            List<String> placeholders = columns.stream()
                    .map(column -> ":" + column).collect(Collectors.toList());

            String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", table,
                    String.join(", ", columns),
                    String.join(", ", placeholders));

            jdbi.withHandle(
                    handle -> handle.createUpdate(sql).bindMap(data).execute());
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error inserting entity into table " + table, e);
        }
    }

    /**
     * Retrieves an entity by its unique ID.
     *
     * @param id the ID of the entity to fetch
     * @return an {@link Optional} containing the entity if found, otherwise empty
     */
    public Optional<T> getById(String id) {
        String sql = String.format("SELECT * FROM %s WHERE id = ?", table);
        return jdbi.withHandle(handle -> handle.createQuery(sql).bind(0, id)
                .map(new JsonRowMapper<>(klazz)).findFirst());
    }

    /**
     * Retrieves a list of entities matching the given filtering conditions.
     *
     * @param conditions the filtering rules to apply
     * @return list of entities matching the filter criteria
     */
    public List<T> filter(Filtering conditions) {
        String whereClause = buildWhereClause(conditions);

        StringBuilder pagination = new StringBuilder();
        if (conditions.getLimit() != null) {
            pagination.append(" LIMIT ").append(conditions.getLimit());
        }
        if (conditions.getOffset() != null) {
            pagination.append(" OFFSET ").append(conditions.getOffset());
        }
        if (conditions.getSorting() != null) {
            pagination.append(" ORDER BY ").append(conditions.getSorting());
        }

        String sql = String.format("SELECT * FROM %s %s %s", table, whereClause, pagination);

        return jdbi.withHandle(handle -> {
            Query query = handle.createQuery(sql);
            bindQueryParameters(query, conditions);
            return query.map(new JsonRowMapper<>(klazz)).list();
        });
    }

    /**
     * Returns the number of entities that match the given filtering criteria.
     *
     * @param conditions the filtering rules to apply
     * @return the count of matching entities
     */
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
     * Deletes the entity identified by the given ID.
     *
     * @param id the ID of the entity to delete
     * @return number of rows deleted
     */
    public int delete(String id) {
        String sql = String.format("DELETE FROM %s WHERE id = ?", table);
        return jdbi.withHandle(
                handle -> handle.createUpdate(sql).bind(0, id).execute());
    }

    /**
     * Updates the given entity in the database using its ID as the reference.
     *
     * @param target the entity with updated data
     * @return number of rows affected by the update
     */
    public int update(T target) {
        Map<String, Object> changes = target.toChanges();
        changes.remove("id");
        if (changes.isEmpty()) {
            return 0;
        }

        String sql = String.format("UPDATE %s SET %s WHERE id = :id", table,
                changes.entrySet().stream()
                        .map(entry -> String.format("%s = :%s", entry.getKey(),
                                entry.getKey()))
                        .collect(Collectors.joining(", ")));

        return jdbi.withHandle(handle -> handle.createUpdate(sql)
                .bind("id", target.getId()).bindMap(changes).execute());
    }

    /**
     * Builds a dynamic WHERE clause for SQL queries based on provided filtering conditions.
     *
     * @param conditions the filtering conditions
     * @return the SQL WHERE clause
     */
    private String buildWhereClause(Filtering conditions) {
        if (conditions.filterings().isEmpty()) return "";

        String clause = conditions.filterings().stream()
            .map(filter -> {
                String key = filter.getName();
                Operator operator = filter.getOperator();
                switch (operator) {
                    case In:
                        return String.format("%s IN (<%s>)", key, key);
                    case NotIn:
                        return String.format("%s NOT IN (<%s>)", key, key);
                    case Like:
                        return String.format("%s LIKE :%s", key, key);
                    case NotLike:
                        return String.format("%s NOT LIKE :%s", key, key);
                    case Eq:
                        return String.format("%s = :%s", key, key);
                    case NotEq:
                        return String.format("%s != :%s", key, key);
                    default:
                        throw new IllegalArgumentException("Unsupported operator: " + operator);
                }
            })
            .collect(Collectors.joining(" " + conditions.getOperator().name() + " "));

        return "WHERE " + clause;
    }

    /**
     * Binds filtering parameters to the given SQL query.
     *
     * @param query      the query to bind parameters to
     * @param conditions the filtering parameters
     */
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
}

/**
 * A row mapper that maps result sets into entity objects using reflection and optional JSON deserialization.
 *
 * @param <T> the target type of the mapping
 */
class JsonRowMapper<T> implements RowMapper<T> {
    private final Class<T> type;
    private final Gson gson = new Gson();

    /**
     * Constructs a JsonRowMapper for a given class type.
     *
     * @param type the class type to map result rows into
     */
    public JsonRowMapper(Class<T> type) {
        this.type = type;
    }

    @Override
    public T map(ResultSet rs, StatementContext ctx) throws SQLException {
        try {
            T instance = type.getDeclaredConstructor().newInstance();
            Class<?> currentClass = type;
            while (Objects.nonNull(currentClass)) {
                for (Field field : currentClass.getDeclaredFields()) {
                    // Exclude static fields
                    if (!Modifier.isStatic(field.getModifiers()) && !field.isAnnotationPresent(Transient.class)) {
                        field.setAccessible(true);
                        try {
                            Object value = field.isAnnotationPresent(Json.class)
                                    ? gson.fromJson(rs.getString(field.getName()), field.getType())
                                    : rs.getObject(field.getName());
                            PropertyUtils.setPropertyValue(instance, type, field, value);
                        } catch (SQLException e) {
                            e.printStackTrace(); // Column may not exist
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
