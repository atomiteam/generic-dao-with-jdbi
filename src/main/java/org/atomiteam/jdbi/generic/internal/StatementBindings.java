package org.atomiteam.jdbi.generic.internal;

import java.util.*;
import org.jdbi.v3.core.statement.SqlStatement;

/** Shared named binding for CRUD and dynamic reads. */
public final class StatementBindings {
    private StatementBindings() {}

    public static void bind(SqlStatement<?> statement, Map<String,?> values) {
        values.forEach((name,value) -> {
            if (value instanceof Collection<?>) {
                Collection<?> items = (Collection<?>) value;
                if (items.isEmpty()) throw new IllegalArgumentException("Cannot bind empty collection: " + name);
                statement.bindList(name, items);
            } else statement.bind(name,value);
        });
    }
}
