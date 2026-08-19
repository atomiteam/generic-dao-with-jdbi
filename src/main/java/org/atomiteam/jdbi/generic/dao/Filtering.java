package org.atomiteam.jdbi.generic.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A class for building and managing a collection of filters to be applied in queries.
 *
 * <p>Filter and sorting names represent Java entity property names, not arbitrary SQL.
 * {@link GenericDao} validates that each supplied name is a mapped property of the
 * entity before generating SQL. Values are always passed as JDBI bind parameters.</p>
 */
public class Filtering {

    private final List<Filter> filters = new ArrayList<>();
    private Long offset;
    private Long limit;
    private LogicalOperator operator = LogicalOperator.AND;
    private String sorting;

    private Filtering() {
    }

    public static Filtering create() {
        return new Filtering();
    }

    public Filtering eq(String name, Object value) {
        this.filters.add(filter(name, Operator.Eq, value));
        return this;
    }

    public Filtering notEq(String name, Object value) {
        this.filters.add(filter(name, Operator.NotEq, value));
        return this;
    }

    public Filtering like(String name, Object value) {
        this.filters.add(filter(name, Operator.Like, value));
        return this;
    }

    public Filtering notLike(String name, Object value) {
        this.filters.add(filter(name, Operator.NotLike, value));
        return this;
    }

    public Filtering in(String name, Collection<?> values) {
        this.filters.add(filter(name, Operator.In, values));
        return this;
    }

    public Filtering notIn(String name, Collection<?> values) {
        this.filters.add(filter(name, Operator.NotIn, values));
        return this;
    }

    public Filtering isNull(String name) {
        this.filters.add(filter(name, Operator.IsNull, null));
        return this;
    }

    public Filtering isNotNull(String name) {
        this.filters.add(filter(name, Operator.IsNotNull, null));
        return this;
    }

    public List<Filter> filterings() {
        return filters.stream()
                .filter(f -> Objects.nonNull(f.getName()) && !f.getName().isBlank())
                .filter(f -> f.getOperator() == Operator.IsNull || f.getOperator() == Operator.IsNotNull
                        || Objects.nonNull(f.getValue()))
                .collect(Collectors.toList());
    }

    public Long getOffset() {
        return offset;
    }

    public Filtering withOffset(Long offset) {
        if (offset != null && offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative");
        }
        this.offset = offset;
        return this;
    }

    public Long getLimit() {
        return limit;
    }

    public Filtering withLimit(Long limit) {
        if (limit != null && limit < 0) {
            throw new IllegalArgumentException("Limit cannot be negative");
        }
        this.limit = limit;
        return this;
    }

    public Filtering withSorting(String name, Sorting sorting) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or blank");
        }
        if (sorting == null) {
            throw new IllegalArgumentException("Sorting cannot be null");
        }
        this.sorting = name + " " + sorting.name();
        return this;
    }

    public LogicalOperator getOperator() {
        return operator;
    }

    public Filtering withOperator(LogicalOperator operator) {
        this.operator = Objects.requireNonNull(operator, "operator");
        return this;
    }

    public String getSorting() {
        return sorting;
    }

    private Filter filter(String name, Operator operator, Object value) {
        return Filter.create().withName(name).withOperator(operator).withValue(value);
    }
}
