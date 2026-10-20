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
    private static final String BIND_NAME = "[A-Za-z_][A-Za-z0-9_]*";

    private final String baseSql;
    private final List<String> predicates = new ArrayList<>();
    private final Map<String, Object> bindings = new LinkedHashMap<>();
    private final List<String> orderings = new ArrayList<>();
    private long parameterSequence;
    private Long limit;
    private Long offset;
    private boolean seeking;

    private JdbiQuery(String baseSql) {
        if (baseSql == null || baseSql.trim().isEmpty()) {
            throw new IllegalArgumentException("Base SQL cannot be blank");
        }
        this.baseSql = stripTrailingSemicolon(baseSql);
    }

    public static JdbiQuery select(String baseSql) { return new JdbiQuery(baseSql); }

    /** Binds a named placeholder already present in the stable base SQL. */
    public JdbiQuery bind(String name, Object value) {
        requireBindName(name);
        if (bindings.containsKey(name)) throw new IllegalArgumentException("Duplicate binding: " + name);
        bindings.put(name, immutableValue(value));
        return this;
    }

    /** Binds a named JDBI list placeholder already present in the stable base SQL. */
    public JdbiQuery bindList(String name, Collection<?> values) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("List binding cannot be empty: " + name);
        return bind(name, new ArrayList<>(values));
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
        String safe = requireExpression(expression);
        String parameter = addBinding(value);
        predicates.add(safe + " LIKE :" + parameter);
        return this;
    }

    public JdbiQuery likeIfPresent(String expression, String value) { return value == null || value.isEmpty() ? this : like(expression, value); }

    public JdbiQuery in(String expression, Collection<?> values) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("IN values cannot be null or empty");
        String safe = requireExpression(expression);
        String parameter = addBinding(new ArrayList<>(values));
        predicates.add(safe + " IN (<" + parameter + ">)");
        return this;
    }

    public JdbiQuery inIfNotEmpty(String expression, Collection<?> values) { return values == null || values.isEmpty() ? this : in(expression, values); }

    public JdbiQuery notIn(String expression, Collection<?> values) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("NOT IN values cannot be null or empty");
        String safe = requireExpression(expression);
        String parameter = addBinding(new ArrayList<>(values));
        predicates.add(safe + " NOT IN (<" + parameter + ">)");
        return this;
    }

    public JdbiQuery notInIfNotEmpty(String expression, Collection<?> values) { return values == null || values.isEmpty() ? this : notIn(expression, values); }

    public JdbiQuery isNull(String expression) { predicates.add(requireExpression(expression) + " IS NULL"); return this; }
    public JdbiQuery isNotNull(String expression) { predicates.add(requireExpression(expression) + " IS NOT NULL"); return this; }

    /** Adds a developer-authored predicate fragment and safely binds every '?' placeholder. */
    public JdbiQuery where(String predicate, Object... values) {
        if (predicate == null || predicate.trim().isEmpty()) throw new IllegalArgumentException("Predicate cannot be blank");
        rejectStatementBreakout(predicate);
        Object[] supplied = values == null ? new Object[0] : values;
        String rendered = SqlFragments.predicate(predicate, supplied, this::addBinding);
        predicates.add(rendered);
        return this;
    }

    public JdbiQuery whereIf(boolean condition, String predicate, Object... values) { return condition ? where(predicate, values) : this; }

    /** Developer-authored static ordering expression. Never pass raw client text here. */
    public JdbiQuery orderBy(String expression, Sorting sorting) {
        if (seeking) throw new IllegalArgumentException("Cannot append to seek order");
        String safeExpression = requireExpression(expression);
        orderings.add(safeExpression + " " + Objects.requireNonNull(sorting, "sorting").name());
        return this;
    }

    /** Parses client sort text and resolves the key only through an explicit allow-list. */
    public JdbiQuery orderByAllowed(String requested, Map<String, String> allowedExpressions,
            String defaultKey, Sorting defaultDirection) {
        Objects.requireNonNull(allowedExpressions, "allowedExpressions");
        Sorting fallbackDirection = Objects.requireNonNull(defaultDirection, "defaultDirection");
        String key = defaultKey;
        Sorting direction = fallbackDirection;
        if (requested != null && !requested.trim().isEmpty()) {
            String[] parts = requested.trim().split("\\s+");
            if (parts.length < 1 || parts.length > 2) throw new IllegalArgumentException("Invalid sorting expression: " + requested);
            key = parts[0];
            if (parts.length == 2) {
                try { direction = Sorting.valueOf(parts[1].toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid sorting direction: " + parts[1], e); }
            }
        }
        String expression = allowedExpressions.get(key);
        if (expression == null) {
            if (defaultKey == null || !allowedExpressions.containsKey(defaultKey)) throw new IllegalArgumentException("Unsupported sorting key: " + key);
            expression = allowedExpressions.get(defaultKey);
            direction = fallbackDirection;
        }
        return orderBy(expression, direction);
    }

    public JdbiQuery limit(Long limit) { if (limit != null && limit < 0) throw new IllegalArgumentException("Limit cannot be negative"); this.limit = limit; return this; }
    public JdbiQuery offset(Long offset) { if (seeking && offset != null) throw new IllegalArgumentException("Cannot offset a seek query"); if (offset != null && offset < 0) throw new IllegalArgumentException("Offset cannot be negative"); this.offset = offset; return this; }
    public JdbiQuery page(Number offset, Number limit) { offset(offset == null ? null : offset.longValue()); limit(limit == null ? null : limit.longValue()); return this; }

    /**
     * Keyset order must end in a unique, stable tie-breaker. Expressions come from trusted
     * application metadata, never request text. Null cursor means first page.
     */
    public JdbiQuery seekAfter(List<SeekColumn> columns, List<?> cursor) {
        if (columns == null || columns.isEmpty() || columns.size() > 16)
            throw new IllegalArgumentException("Use 1..16 seek columns");
        if (!orderings.isEmpty() || offset != null) throw new IllegalArgumentException("Seek order cannot mix with offset/orderBy");
        if (cursor != null && cursor.size() != columns.size()) throw new IllegalArgumentException("Cursor arity differs from order");
        List<String> expressions = new ArrayList<>();
        for(SeekColumn column:columns) expressions.add(requireExpression(column.getExpression()));
        List<String> names = new ArrayList<>();
        if (cursor != null) for(Object value:cursor) names.add(value == null ? null : addBinding(value));
        List<String> alternatives = new ArrayList<>();
        for(int i=0;i<columns.size();i++) {
            SeekColumn column=columns.get(i);String expression=expressions.get(i);
            orderings.add(expression+" "+column.getDirection()+" NULLS "+column.getNulls());
            if(cursor == null) continue;
            List<String> prefix = new ArrayList<>();
            for(int j=0;j<i;j++) prefix.add(expressions.get(j)+(cursor.get(j)==null?" IS NULL":" = :"+names.get(j)));
            String comparison;
            if(cursor.get(i)==null) comparison=column.getNulls()==SeekColumn.Nulls.FIRST?expression+" IS NOT NULL":"1 = 0";
            else {
                comparison=expression+(column.getDirection()==Sorting.ASC?" > :":" < :")+names.get(i);
                if(column.getNulls()==SeekColumn.Nulls.LAST) comparison="("+comparison+" OR "+expression+" IS NULL)";
            }
            prefix.add(comparison);alternatives.add("("+String.join(" AND ",prefix)+")");
        }
        if(cursor != null) predicates.add("("+String.join(" OR ",alternatives)+")");
        seeking=true;
        return this;
    }

    public JdbiQuery copy() {
        JdbiQuery copy=new JdbiQuery(baseSql);
        copy.predicates.addAll(predicates);copy.bindings.putAll(bindings);copy.orderings.addAll(orderings);
        copy.parameterSequence=parameterSequence;copy.limit=limit;copy.offset=offset;copy.seeking=seeking;
        return copy;
    }

    public String sql() { return render(true, true); }
    public String countSql() { return "SELECT COUNT(*) FROM (" + render(false, false) + ") _atomi_count"; }

    public Map<String, Object> bindings() {
        Map<String, Object> result = new LinkedHashMap<>(bindings);
        if (limit != null || offset != null) result.put("_query_limit", limit == null ? Long.MAX_VALUE : limit);
        if (offset != null) result.put("_query_offset", offset);
        return Collections.unmodifiableMap(result);
    }

    public Map<String, Object> countBindings() { return Collections.unmodifiableMap(new LinkedHashMap<>(bindings)); }
    public Long getLimit() { return limit; }
    public Long getOffset() { return offset; }
    boolean isSeeking() { return seeking; }

    private JdbiQuery compare(String expression, String operator, Object value) {
        if (value == null) {
            if ("=".equals(operator)) return isNull(expression);
            if ("<>".equals(operator)) return isNotNull(expression);
            throw new IllegalArgumentException("Null is not valid for operator " + operator);
        }
        String safe = requireExpression(expression);
        String parameter = addBinding(value);
        predicates.add(safe + " " + operator + " :" + parameter);
        return this;
    }

    private String addBinding(Object value) {
        String name;
        do { name = PARAM_PREFIX + parameterSequence++; } while (bindings.containsKey(name));
        bindings.put(name, value);
        return name;
    }

    private static Object immutableValue(Object value) {
        return value instanceof Collection<?>
                ? Collections.unmodifiableList(new ArrayList<>((Collection<?>) value)) : value;
    }

    private String render(boolean includeOrder, boolean includePaging) {
        String selected = predicates.isEmpty() ? baseSql : SqlFragments.append(baseSql,
                predicates.stream().map(predicate -> "(" + predicate + ")")
                        .collect(java.util.stream.Collectors.joining(" AND ")));
        StringBuilder result = new StringBuilder(selected);
        if (includeOrder && !orderings.isEmpty()) result.append(" ORDER BY ").append(String.join(", ", orderings));
        if (includePaging) {
            if (limit != null) {
                result.append(" LIMIT :_query_limit");
                if (offset != null) result.append(" OFFSET :_query_offset");
            } else if (offset != null) result.append(" LIMIT :_query_limit OFFSET :_query_offset");
        }
        return result.toString();
    }

    private static String requireExpression(String expression) {
        if (expression == null || expression.trim().isEmpty()) throw new IllegalArgumentException("SQL expression cannot be blank");
        rejectStatementBreakout(expression);
        return expression.trim();
    }

    private static void requireBindName(String name) {
        if (name == null || !name.matches(BIND_NAME)) throw new IllegalArgumentException("Invalid binding name: " + name);
        if ("_query_limit".equals(name) || "_query_offset".equals(name)) throw new IllegalArgumentException("Reserved binding name: " + name);
    }

    private static void rejectStatementBreakout(String expression) {
        String lowered = expression.toLowerCase(Locale.ROOT);
        if (expression.indexOf(';') >= 0 || lowered.contains("--") || lowered.contains("/*") || lowered.contains("*/")) throw new IllegalArgumentException("Unsafe SQL expression: " + expression);
    }

    private static String stripTrailingSemicolon(String sql) {
        String trimmed = sql.trim();
        return trimmed.endsWith(";") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
