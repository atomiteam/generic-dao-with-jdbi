package org.atomiteam.jdbi.generic.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.atomiteam.jdbi.generic.dao.Sorting;

/**
 * JDBI-native dynamic SELECT query specification.
 *
 * <p>The base SQL contains the stable projection/from/join portion. Dynamic
 * values are always bound; callers never concatenate user values into SQL.
 * Dynamic sorting should use {@link #orderByAllowed(String, Map, String, Sorting)}
 * so client-provided sort keys are resolved through an explicit allow-list.</p>
 */
public final class JdbiQuery {

    private static final String PARAM_PREFIX = "_q";

    private final String baseSql;
    private final List<String> predicates = new ArrayList<>();
    private final Map<String, Object> bindings = new LinkedHashMap<>();
    private final List<String> orderings = new ArrayList<>();
    private long parameterSequence;
    private Long limit;
    private Long offset;

    private JdbiQuery(String baseSql) {
        if (baseSql == null || baseSql.trim().isEmpty()) {
            throw new IllegalArgumentException("Base SQL cannot be blank");
        }
        this.baseSql = stripTrailingSemicolon(baseSql);
    }

    public static JdbiQuery select(String baseSql) {
        return new JdbiQuery(baseSql);
    }

    public JdbiQuery eq(String expression, Object value) { return compare(expression, "=", value); }
    public JdbiQuery ne(String expression, Object value) { return compare(expression, "<>", value); }
    public JdbiQuery lt(String expression, Object value) { return compare(expression, "<", value); }
    public JdbiQuery lte(String expression, Object value) { return compare(expression, "<=", value); }
    public JdbiQuery gt(String expression, Object value) { return compare(expression, ">", value); }
    public JdbiQuery gte(String expression, Object value) { return compare(expression, ">=", value); }

    public JdbiQuery eqIfPresent(String expression, Object value) { return value == null ? this : eq(expression, value); }
    public JdbiQuery neIfPresent(String expression, Object value) { return value == null ? this : ne(expression, value); }
    public JdbiQuery ltIfPresent(String expression, Object value) { return value == null ? this : lt(expression, value); }
    public JdbiQuery lteIfPresent(String expression, Object value) { return value == null ? this : lte(expression, value); }
    public JdbiQuery gtIfPresent(String expression, Object value) { return value == null ? this : gt(expression, value); }
    public JdbiQuery gteIfPresent(String expression, Object value) { return value == null ? this : gte(expression, value); }

    public JdbiQuery like(String expression, String value) {
        String parameter = addBinding(value);
        predicates.add(requireExpression(expression) + " LIKE :" + parameter);
        return this;
    }

    public JdbiQuery likeIfPresent(String expression, String value) {
        return value == null || value.isEmpty() ? this : like(expression, value);
    }

    public JdbiQuery in(String expression, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("IN values cannot be null or empty");
        }
        String parameter = addBinding(new ArrayList<>(values));
        predicates.add(requireExpression(expression) + " IN (<" + parameter + ">)");
        return this;
    }

    public JdbiQuery inIfNotEmpty(String expression, Collection<?> values) {
        return values == null || values.isEmpty() ? this : in(expression, values);
    }

    public JdbiQuery notIn(String expression, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("NOT IN values cannot be null or empty");
        }
        String parameter = addBinding(new ArrayList<>(values));
        predicates.add(requireExpression(expression) + " NOT IN (<" + parameter + ">)");
        return this;
    }

    public JdbiQuery notInIfNotEmpty(String expression, Collection<?> values) {
        return values == null || values.isEmpty() ? this : notIn(expression, values);
    }

    public JdbiQuery isNull(String expression) {
        predicates.add(requireExpression(expression) + " IS NULL");
        return this;
    }

    public JdbiQuery isNotNull(String expression) {
        predicates.add(requireExpression(expression) + " IS NOT NULL");
        return this;
    }

    /**
     * Adds a developer-authored predicate fragment. Every '?' placeholder is
     * replaced by an automatically generated named binding.
     */
    public JdbiQuery where(String predicate, Object... values) {
        if (predicate == null || predicate.trim().isEmpty()) {
            throw new IllegalArgumentException("Predicate cannot be blank");
        }
        Object[] supplied = values == null ? new Object[0] : values;
        StringBuilder rendered = new StringBuilder();
        int valueIndex = 0;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        for (int i = 0; i < predicate.length(); i++) {
            char c = predicate.charAt(i);
            if (c == '\'' && !doubleQuoted) singleQuoted = !singleQuoted;
            if (c == '"' && !singleQuoted) doubleQuoted = !doubleQuoted;
            if (c == '?' && !singleQuoted && !doubleQuoted) {
                if (valueIndex >= supplied.length) {
                    throw new IllegalArgumentException("Not enough values for predicate placeholders: " + predicate);
                }
                rendered.append(':').append(addBinding(supplied[valueIndex++]));
            } else {
                rendered.append(c);
            }
        }
        if (valueIndex != supplied.length) {
            throw new IllegalArgumentException("Too many values for predicate placeholders: " + predicate);
        }
        predicates.add(rendered.toString());
        return this;
    }

    public JdbiQuery whereIf(boolean condition, String predicate, Object... values) {
        return condition ? where(predicate, values) : this;
    }

    /** Developer-authored static ordering expression. Never pass raw client text here. */
    public JdbiQuery orderBy(String expression, Sorting sorting) {
        String safeExpression = requireOrderExpression(expression);
        orderings.add(safeExpression + " " + Objects.requireNonNull(sorting, "sorting").name());
        return this;
    }

    /**
     * Parses a client sort such as "id desc", resolves the key through an
     * allow-list, and appends only the mapped SQL expression.
     */
    public JdbiQuery orderByAllowed(String requested, Map<String, String> allowedExpressions,
            String defaultKey, Sorting defaultDirection) {
        Objects.requireNonNull(allowedExpressions, "allowedExpressions");
        Sorting fallbackDirection = Objects.requireNonNull(defaultDirection, "defaultDirection");

        String key = defaultKey;
        Sorting direction = fallbackDirection;
        if (requested != null && !requested.trim().isEmpty()) {
            String[] parts = requested.trim().split("\\s+");
            if (parts.length < 1 || parts.length > 2) {
                throw new IllegalArgumentException("Invalid sorting expression: " + requested);
            }
            key = parts[0];
            if (parts.length == 2) {
                try {
                    direction = Sorting.valueOf(parts[1].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid sorting direction: " + parts[1], e);
                }
            }
        }

        String expression = allowedExpressions.get(key);
        if (expression == null) {
            if (defaultKey == null || !allowedExpressions.containsKey(defaultKey)) {
                throw new IllegalArgumentException("Unsupported sorting key: " + key);
            }
            expression = allowedExpressions.get(defaultKey);
            direction = fallbackDirection;
        }
        return orderBy(expression, direction);
    }

    public JdbiQuery limit(Long limit) {
        if (limit != null && limit < 0) throw new IllegalArgumentException("Limit cannot be negative");
        this.limit = limit;
        return this;
    }

    public JdbiQuery offset(Long offset) {
        if (offset != null && offset < 0) throw new IllegalArgumentException("Offset cannot be negative");
        this.offset = offset;
        return this;
    }

    public JdbiQuery page(Number offset, Number limit) {
        offset(offset == null ? null : offset.longValue());
        limit(limit == null ? null : limit.longValue());
        return this;
    }

    public String sql() {
        return render(true, true);
    }

    /** SQL suitable for an exact total count; ORDER BY and paging are omitted. */
    public String countSql() {
        return "SELECT COUNT(*) FROM (" + render(false, false) + ") _atomi_count";
    }

    public Map<String, Object> bindings() {
        Map<String, Object> result = new LinkedHashMap<>(bindings);
        if (limit != null) result.put("_query_limit", limit);
        if (offset != null) result.put("_query_offset", offset);
        return Collections.unmodifiableMap(result);
    }

    public Map<String, Object> countBindings() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
    }

    public Long getLimit() { return limit; }
    public Long getOffset() { return offset; }

    private JdbiQuery compare(String expression, String operator, Object value) {
        if (value == null) {
            if ("=".equals(operator)) return isNull(expression);
            if ("<>".equals(operator)) return isNotNull(expression);
            throw new IllegalArgumentException("Null is not valid for operator " + operator);
        }
        String parameter = addBinding(value);
        predicates.add(requireExpression(expression) + " " + operator + " :" + parameter);
        return this;
    }

    private String addBinding(Object value) {
        String name = PARAM_PREFIX + parameterSequence++;
        bindings.put(name, value);
        return name;
    }

    private String render(boolean includeOrder, boolean includePaging) {
        StringBuilder result = new StringBuilder(baseSql);
        if (!predicates.isEmpty()) {
            result.append(" WHERE ").append(String.join(" AND ", predicates));
        }
        if (includeOrder && !orderings.isEmpty()) {
            result.append(" ORDER BY ").append(String.join(", ", orderings));
        }
        if (includePaging) {
            if (limit != null) {
                result.append(" LIMIT :_query_limit");
                if (offset != null) result.append(" OFFSET :_query_offset");
            } else if (offset != null) {
                // MySQL-compatible offset without an explicit limit.
                result.append(" LIMIT 18446744073709551615 OFFSET :_query_offset");
            }
        }
        return result.toString();
    }

    private static String requireExpression(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL expression cannot be blank");
        }
        rejectStatementBreakout(expression);
        return expression.trim();
    }

    private static String requireOrderExpression(String expression) {
        return requireExpression(expression);
    }

    private static void rejectStatementBreakout(String expression) {
        String lowered = expression.toLowerCase(Locale.ROOT);
        if (expression.indexOf(';') >= 0 || lowered.contains("--") || lowered.contains("/*") || lowered.contains("*/")) {
            throw new IllegalArgumentException("Unsafe SQL expression: " + expression);
        }
    }

    private static String stripTrailingSemicolon(String sql) {
        String trimmed = sql.trim();
        return trimmed.endsWith(";") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
