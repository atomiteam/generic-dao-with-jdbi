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
 * A generic DAO class for CRUD operations using JDBI.
 *
 * @param <T>
 *            the type of the entity, which must extend {@link Entity}.
 */
public class GenericDao<T extends Entity> {

	private Jdbi jdbi;
	private Class<T> klazz;
	private String table;

	/**
	 * Constructs a new GenericDao instance.
	 *
	 * @param jdbi
	 *            the Jdbi instance for database interaction.
	 * @param klazz
	 *            the class of the entity.
	 * @param table
	 *            the name of the database table.
	 */
	public GenericDao(Jdbi jdbi, Class<T> klazz, String table) {
		this.jdbi = jdbi;
		this.klazz = klazz;
		this.table = table;
	}

	/**
	 * Inserts a new entity into the database.
	 *
	 * @param entity
	 *            the entity to insert.
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
	 * Retrieves an entity by its ID.
	 *
	 * @param id
	 *            the ID of the entity to retrieve.
	 * @return an Optional containing the entity if found, or empty if not.
	 */
	public Optional<T> getById(String id) {
		String sql = String.format("SELECT * FROM %s WHERE id = ?", table);
		return jdbi.withHandle(handle -> handle.createQuery(sql).bind(0, id)
				.map(new JsonRowMapper<>(klazz)).findFirst());
	}

	/**
	 * Filters entities based on the provided conditions.
	 *
	 * @param conditions
	 *            the filtering conditions to apply.
	 * @return a list of entities that match the conditions.
	 */
	public List<T> filter(Filtering conditions) {
		String whereClause = conditions.filterings().stream().map(filter -> {
			String key = filter.getName();
			Operator operator = filter.getOperator();
			switch (operator) {
				case In :
					return String.format("%s IN (<%s>)", key, key);
				case NotIn :
					return String.format("%s NOT IN (<%s>)", key, key);
				case Like :
					return String.format("%s LIKE :%s", key, key);
				case NotLike :
					return String.format("%s NOT LIKE :%s", key, key);
				case Eq :
					return String.format("%s = :%s", key, key);
				case NotEq :
					return String.format("%s != :%s", key, key);
				default :
					throw new IllegalArgumentException(
							"Unsupported operator: " + operator);
			}
		}).collect(Collectors.joining(" " + conditions.getOperator().name() + " "));
		
		StringBuilder pagination = new StringBuilder();
		if (conditions.getLimit() != null) {
		    pagination
		      .append(" LIMIT ")
		      .append(conditions.getLimit())
		      .append(" ");
		}
        if (conditions.getOffset() != null) {
            pagination
              .append(" OFFSET ")
              .append(conditions.getOffset())
              .append(" ");
        }

		String where = conditions.filterings().isEmpty() ? "" : "WHERE";
		String sql = String.format("SELECT * FROM %s %s %s %s", table, where,
				whereClause, pagination.toString());

		return jdbi.withHandle(handle -> {
			Query query = handle.createQuery(sql);
			for (Filter filter : conditions.filterings()) {
				String key = filter.getName();
				Object value = filter.getValue();
				if (value instanceof Collection<?>) {
					query.bindList(key, (Collection<?>) value);
				} else {
					query.bind(key, value);
				}
			}
			return query.map(new JsonRowMapper<>(klazz)).list();
		});
	}

	/**
	 * Deletes an entity by its ID.
	 *
	 * @param id
	 *            the ID of the entity to delete.
	 * @return the number of rows affected.
	 */
	public int delete(String id) {
		String sql = String.format("DELETE FROM %s WHERE id = ?", table);
		return jdbi.withHandle(
				handle -> handle.createUpdate(sql).bind(0, id).execute());
	}

	/**
	 * Updates an entity in the database.
	 *
	 * @param target
	 *            the entity to update.
	 * @return the number of rows affected.
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
}

/**
 * RowMapper to map database rows to JSON objects.
 *
 * @param <T>
 *            the type of object to map.
 */
class JsonRowMapper<T> implements RowMapper<T> {
	private final Class<T> type;
	private final Gson gson = new Gson();

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
									? // If
									gson.fromJson(rs.getString(field.getName()),
											field.getType())
									: // else
									rs.getObject(field.getName());
							PropertyUtils.setPropertyValue(instance, type, field, value);
						} catch (SQLException e) {
						    e.printStackTrace();
							// Column does not exist
						}
					}
				}
				currentClass = currentClass.getSuperclass();
			}
			return instance;
		} catch (Exception e) {
			throw new SQLException(
					"Failed to map result set to " + type.getName(), e);
		}
	}
}
