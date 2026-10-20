package org.atomiteam.jdbi.generic.dao;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.atomiteam.jdbi.generic.internal.StatementBindings;
import org.jdbi.v3.core.statement.SqlStatement;

/** Compiles one scoped predicate snapshot and binds the same snapshot for every CRUD operation. */
final class DaoPredicates {
    private final String sql;
    private final Map<String,Object> values;

    private DaoPredicates(String sql, Map<String,Object> values) {
        this.sql = sql;
        this.values = Collections.unmodifiableMap(values);
    }

    static DaoPredicates compile(Filtering filters, Map<String,Object> scope,
            Function<String,String> column, BiFunction<String,Object,Object> encode) {
        List<String> clauses = new ArrayList<>();
        Map<String,Object> bindings = new LinkedHashMap<>();
        int scopedIndex = 0;
        for (Map.Entry<String,Object> entry : scope.entrySet()) {
            String parameter = "_scope" + scopedIndex++;
            clauses.add(column.apply(entry.getKey()) + " = :" + parameter);
            bindings.put(parameter, entry.getValue());
        }
        List<String> requested = new ArrayList<>();
        int index = 0;
        for (Filter filter : filters.filterings()) {
            String name = filter.getName();
            String propertyColumn = column.apply(name);
            Operator operator = Objects.requireNonNull(filter.getOperator(), "operator");
            String parameter = "_f" + index++;
            if (operator.unary()) {
                requested.add(propertyColumn + " " + operator.sql());
            } else if (operator.collection()) {
                if (!(filter.getValue() instanceof Collection<?>))
                    throw new IllegalArgumentException("Collection required for " + operator);
                Collection<?> items = (Collection<?>) filter.getValue();
                if (items.isEmpty()) throw new IllegalArgumentException("Collection cannot be empty");
                requested.add(propertyColumn + " " + operator.sql() + " (<" + parameter + ">)");
                bindings.put(parameter, items.stream().map(value -> encode.apply(name,value))
                        .collect(Collectors.toList()));
            } else {
                requested.add(propertyColumn + " " + operator.sql() + " :" + parameter);
                bindings.put(parameter, encode.apply(name,filter.getValue()));
            }
        }
        if (!requested.isEmpty())
            clauses.add("(" + String.join(" " + filters.getOperator().name() + " ",requested) + ")");
        return new DaoPredicates(clauses.isEmpty() ? "" : "WHERE " + String.join(" AND ",clauses),bindings);
    }

    String sql() { return sql; }
    void bind(SqlStatement<?> statement) { StatementBindings.bind(statement,values); }
}
