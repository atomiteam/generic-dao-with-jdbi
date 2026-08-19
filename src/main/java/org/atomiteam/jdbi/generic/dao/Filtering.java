package org.atomiteam.jdbi.generic.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Type-safe query filtering and pagination using Java property names. */
public class Filtering {

    private final List<Filter> filters = new ArrayList<>();
    private final List<SortClause> sortings = new ArrayList<>();
    private Long offset;
    private Long limit;
    private LogicalOperator operator = LogicalOperator.AND;

    private Filtering() {
    }

    public static Filtering create() {
        return new Filtering();
    }

    public Filtering eq(String name, Object value) { filters.add(filter(name, Operator.Eq, value)); return this; }
    public Filtering notEq(String name, Object value) { filters.add(filter(name, Operator.NotEq, value)); return this; }
    public Filtering like(String name, Object value) { filters.add(filter(name, Operator.Like, value)); return this; }
    public Filtering notLike(String name, Object value) { filters.add(filter(name, Operator.NotLike, value)); return this; }
    public Filtering in(String name, Collection<?> values) { filters.add(filter(name, Operator.In, values)); return this; }
    public Filtering notIn(String name, Collection<?> values) { filters.add(filter(name, Operator.NotIn, values)); return this; }
    public Filtering lt(String name, Object value) { filters.add(filter(name, Operator.Lt, value)); return this; }
    public Filtering lte(String name, Object value) { filters.add(filter(name, Operator.Lte, value)); return this; }
    public Filtering gt(String name, Object value) { filters.add(filter(name, Operator.Gt, value)); return this; }
    public Filtering gte(String name, Object value) { filters.add(filter(name, Operator.Gte, value)); return this; }
    public Filtering isNull(String name) { filters.add(filter(name, Operator.IsNull, null)); return this; }
    public Filtering isNotNull(String name) { filters.add(filter(name, Operator.IsNotNull, null)); return this; }

    public List<Filter> filterings() {
        return filters.stream()
                .filter(f -> Objects.nonNull(f.getName()) && !f.getName().isBlank())
                .filter(f -> f.getOperator() == Operator.IsNull || f.getOperator() == Operator.IsNotNull
                        || Objects.nonNull(f.getValue()))
                .collect(Collectors.toList());
    }

    public Long getOffset() { return offset; }

    public Filtering withOffset(Long offset) {
        if (offset != null && offset < 0) throw new IllegalArgumentException("Offset cannot be negative");
        this.offset = offset;
        return this;
    }

    public Long getLimit() { return limit; }

    public Filtering withLimit(Long limit) {
        if (limit != null && limit < 0) throw new IllegalArgumentException("Limit cannot be negative");
        this.limit = limit;
        return this;
    }

    public Filtering withSorting(String name, Sorting sorting) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name cannot be null or blank");
        if (sorting == null) throw new IllegalArgumentException("Sorting cannot be null");
        sortings.add(new SortClause(name, sorting));
        return this;
    }

    public List<SortClause> getSortings() {
        return Collections.unmodifiableList(sortings);
    }

    /** Backward-compatible accessor for callers that only use one sort. */
    public String getSorting() {
        if (sortings.isEmpty()) return null;
        SortClause first = sortings.get(0);
        return first.getName() + " " + first.getSorting().name();
    }

    public LogicalOperator getOperator() { return operator; }

    public Filtering withOperator(LogicalOperator operator) {
        this.operator = Objects.requireNonNull(operator, "operator");
        return this;
    }

    private Filter filter(String name, Operator operator, Object value) {
        return Filter.create().withName(name).withOperator(operator).withValue(value);
    }

    public static final class SortClause {
        private final String name;
        private final Sorting sorting;

        private SortClause(String name, Sorting sorting) {
            this.name = name;
            this.sorting = sorting;
        }

        public String getName() { return name; }
        public Sorting getSorting() { return sorting; }
    }
}
