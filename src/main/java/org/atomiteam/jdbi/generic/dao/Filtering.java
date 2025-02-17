package org.atomiteam.jdbi.generic.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A class for building and managing a collection of filters to be applied in queries.
 * Filters can be added using methods like `eq`, `notEq`, `like`, `notLike`, `in`, and `notIn`.
 * The filters are stored in a list and can be retrieved using the `filterings` method.
 *
 * ⚠ WARNING: The `name` parameter is used to generate SQL statements. **DO NOT** accept `name` as 
 * user input directly, as it may lead to SQL injection vulnerabilities. The values are safe to use, 
 * but not the field names. Developers are responsible for ensuring the safe usage of this class. 
 * Improper usage can risk the security of their application.
 */
public class Filtering {

    private final List<Filter> filters = new ArrayList<>();
    private Long offset;
    private Long limit;
    private LogicalOperator operator = LogicalOperator.AND;
    
    /**
     * Private constructor to enforce the use of the factory method `create`.
     */
    private Filtering() {
    }

    /**
     * Creates a new instance of Filtering.
     *
     * @return a new Filtering instance.
     */
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
    
    
    public LogicalOperator getOperator() {
        return operator;
    }

    public Filtering withOperator(LogicalOperator operator) {
        this.operator = operator;
        return this;
    }

}
