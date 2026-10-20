package org.atomiteam.jdbi.generic.query;

import com.google.gson.Gson;

import java.sql.*;
import java.util.*;

import javax.sql.DataSource;

/**
 * Resource-safe execution of trusted, parameterized specialized SQL. Use GenericDao for entity
 * CRUD. Borrowed connections are never completed or closed.
 */
public final class SqlStatements {
    private static final Gson JSON = new Gson();

    private SqlStatements() {}

    @FunctionalInterface
    public interface ResultReader<T> {
        T read(ResultSet rows) throws SQLException;
    }

    public static void bind(PreparedStatement statement, List<?> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) statement.setObject(i + 1, values.get(i));
    }

    public static void json(PreparedStatement statement, int index, Object value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.OTHER);
        else statement.setObject(index, JSON.toJson(value), Types.OTHER);
    }

    public static int update(Connection connection, String sql, List<?> values)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            return statement.executeUpdate();
        }
    }

    public static <T> T query(
            Connection connection, String sql, List<?> values, ResultReader<T> reader)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet rows = statement.executeQuery()) {
                return reader.read(rows);
            }
        }
    }

    public static boolean exists(Connection connection, String sql, List<?> values)
            throws SQLException {
        return query(connection, sql, values, ResultSet::next);
    }

    /** Owns only the acquired read connection; rolls back a failed read before closing. */
    public static <T> List<T> list(
            DataSource source, String sql, List<?> values, ResultReader<T> rowMapper)
            throws SQLException {
        try (Connection connection = source.getConnection()) {
            try {
                List<T> result = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    bind(statement, values);
                    statement.setQueryTimeout(10);
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) result.add(rowMapper.read(rows));
                    }
                }
                if (!connection.getAutoCommit()) connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try {
                    if (!connection.getAutoCommit()) connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }
    }
}
