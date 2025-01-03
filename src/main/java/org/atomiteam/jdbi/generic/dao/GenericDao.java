package org.atomiteam.jdbi.generic.dao;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.BeanMapper;
import org.jdbi.v3.core.statement.Query;

/**
 * A generic DAO class for CRUD operations using JDBI.
 * 
 * @param <T> the type of the entity, which must extend {@link Entity}.
 */
public class GenericDao<T extends Entity> {

    private Jdbi jdbi;
    private Class<T> klazz;
    private String table;

    /**
     * Constructs a new GenericDao instance.
     * 
     * @param jdbi  the Jdbi instance for database interaction.
     * @param klazz the class of the entity.
     * @param table the name of the database table.
     */
    public GenericDao(Jdbi jdbi, Class<T> klazz, String table) {
        this.jdbi = jdbi;
        this.table = table;
        this.klazz = klazz;
    }

    /**
     * Inserts a new entity into the database.
     * 
     * @param entity the entity to insert.
     */
    public void insert(T entity) {
        try {
            Map<String, Object> data = entity.getChanges();
            List<String> columns = data.keySet().stream()
                    .collect(Collectors.toList());
            List<String> placeholders = columns.stream()
                    .map(column -> ":" + column).collect(Collectors.toList());

            String sql = String.format("INSERT INTO %s (%s) VALUES (%s)",
                    table, String.join(", ", columns), String.join(", ", placeholders));

            jdbi.withHandle(handle -> handle.createUpdate(sql).bindMap(data).execute());
        } catch (Exception e) {
            throw new RuntimeException("Error inserting entity into table " + table, e);
        }
    }

    /**
     * Retrieves an entity by its ID.
     * 
     * @param id the ID of the entity to retrieve.
     * @return an Optional containing the entity if found, or empty if not.
     */
    public Optional<T> getById(String id) {
        String sql = String.format("SELECT * FROM %s WHERE id = ?", table);
        return jdbi.withHandle(handle -> handle.createQuery(sql).bind(0, id)
                .map(BeanMapper.of(klazz)).findFirst());
    }

    /**
     * Filters entities based on the provided conditions.
     * 
     * @param conditions the filtering conditions to apply.
     * @return a list of entities that match the conditions.
     */
    public List<T> filter(Filtering conditions) {
        String whereClause = conditions.filterings()
                .stream()
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
                }).collect(Collectors.joining(" AND "));

        String where = conditions.filterings().size() == 0 ? "" : "WHERE";
        String sql = String.format("SELECT * FROM %s %s %s", table, where, whereClause);

        return jdbi.withHandle(handle -> {
            Query query = handle.createQuery(sql);
            for (Filter filter : conditions.filterings()) {
                String key = filter.getName();
                Object value = filter.getValue();
                if (value instanceof Collection<?>) {
                    // Handle collections (e.g., for IN or NOT IN operators)
                    query.bindList(key, (Collection<?>) value);
                } else {
                    // Handle single values
                    query.bind(key, value);
                }
            }
            return query.map(BeanMapper.of(klazz)).list();
        });
    }

    /**
     * Deletes an entity by its ID.
     * 
     * @param id the ID of the entity to delete.
     * @return the number of rows affected.
     */
    public int delete(String id) {
        String sql = String.format("DELETE FROM %s WHERE id = ?", table);
        return jdbi.withHandle(handle -> handle.createUpdate(sql).bind(0, id).execute());
    }

    /**
     * Updates an entity in the database.
     * 
     * @param target the entity to update.
     * @return the number of rows affected.
     */
    public int update(T target) {
        Map<String, Object> changes = target.getChanges();
        changes.remove("id");
        if (changes.isEmpty()) {
            return 0;
        }

        StringBuilder sql = new StringBuilder(String.format("UPDATE %s SET ", table));
        List<String> setClauses = changes.entrySet().stream()
                .map(entry -> String.format("%s = :%s", entry.getKey(), entry.getKey()))
                .collect(Collectors.toList());
        sql.append(String.join(", ", setClauses));
        sql.append(" WHERE id = :id");

        return jdbi.withHandle(handle -> handle.createUpdate(sql.toString())
                .bind("id", target.getId()).bindMap(changes).execute());
    }
}