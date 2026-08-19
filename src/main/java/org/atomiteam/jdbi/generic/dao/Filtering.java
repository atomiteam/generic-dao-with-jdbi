package org.atomiteam.jdbi.generic.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A class for building and managing a collection of filters to be applied in queries.
 * Filters can be added using methods like {@code eq}, {@code notEq}, {@code like},
 * {@code notLike}, {@code in}, and {@code notIn}.
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
        this.filters.add(Filter.create()
                .withName(name)
                .withOperator(Operator.Eq)
                .withValue(value));
        return this;
    }

    public Filtering notEq(String name, Object value) {
        this.filters.add(Filter.create()
                .withName(name)
                .withOperator(Operator.NotEq)
                .withValue(value));
        return this;
    }

    public Filtering like(String name, Object value) {
        this.filters.add(Filter.create()
                .withName(name)
                .withOperator(Operator.Like)
                .withValue(value));
        return this;
    }

    public Filtering notLike(String name, Object value) {
        this.filters.add(Filter.create()
                .withName(name)
                .withOperator(Operator.NotLike)
                .withValue(value));
        return this;
    }

    public Filtering in(String name, Collection<?> values) {
        this.filters.add(Filter.create()
                .withName(name)
                .withOperator(Operator.In)
                .withValue(values));
        return this;
    }

    public Filtering notIn(String name, Collection<?> values) {
        this.filters.add(Filter.create()
                .withName(name)
                .withOperator(Operator.NotIn)
                .withValue(values));
        return this;
    }

    public List<Filter> filterings() {
        return filters.stream()
                .filter(f -> Objects.nonNull(f.getValue()) && Objects.nonNull(f.getName()) &&
                        !f.getName().isBlank())
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
}
