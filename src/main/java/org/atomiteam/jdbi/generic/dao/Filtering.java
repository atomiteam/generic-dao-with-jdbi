package org.atomiteam.jdbi.generic.dao;

import java.util.ArrayList;
import java.util.List;

/**
 * A class for building and managing a collection of filters to be applied in queries.
 * Filters can be added using methods like `eq`, `notEq`, `like`, `notLike`, `in`, and `notIn`.
 * The filters are stored in a list and can be retrieved using the `filterings` method.
 */
public class Filtering {

    private final List<Filter> filters = new ArrayList<>();

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

    /**
     * Adds an equality filter to the list of filters.
     *
     * @param name  the name of the field to filter on.
     * @param value the value to compare against.
     * @return the current Filtering instance for method chaining.
     */
    public Filtering eq(String name, Object value) {
        this.filters.add(
                Filter.create()
                        .withName(name)
                        .withOperator(Operator.Eq)
                        .withValue(value));
        return this;
    }

    /**
     * Adds a non-equality filter to the list of filters.
     *
     * @param name  the name of the field to filter on.
     * @param value the value to compare against.
     * @return the current Filtering instance for method chaining.
     */
    public Filtering notEq(String name, Object value) {
        this.filters.add(
                Filter.create()
                        .withName(name)
                        .withOperator(Operator.NotEq)
                        .withValue(value));
        return this;
    }

    /**
     * Adds a LIKE filter to the list of filters.
     *
     * @param name  the name of the field to filter on.
     * @param value the value to compare against.
     * @return the current Filtering instance for method chaining.
     */
    public Filtering like(String name, Object value) {
        this.filters.add(
                Filter.create()
                        .withName(name)
                        .withOperator(Operator.Like)
                        .withValue(value));
        return this;
    }

    /**
     * Adds a NOT LIKE filter to the list of filters.
     *
     * @param name  the name of the field to filter on.
     * @param value the value to compare against.
     * @return the current Filtering instance for method chaining.
     */
    public Filtering notLike(String name, Object value) {
        this.filters.add(
                Filter.create()
                        .withName(name)
                        .withOperator(Operator.NotLike)
                        .withValue(value));
        return this;
    }

    /**
     * Adds an IN filter to the list of filters.
     *
     * @param name  the name of the field to filter on.
     * @param value the collection of values to compare against.
     * @return the current Filtering instance for method chaining.
     */
    public Filtering in(String name, Object value) {
        this.filters.add(
                Filter.create()
                        .withName(name)
                        .withOperator(Operator.In)
                        .withValue(value));
        return this;
    }

    /**
     * Adds a NOT IN filter to the list of filters.
     *
     * @param name  the name of the field to filter on.
     * @param value the collection of values to compare against.
     * @return the current Filtering instance for method chaining.
     */
    public Filtering notIn(String name, Object value) {
        this.filters.add(
                Filter.create()
                        .withName(name)
                        .withOperator(Operator.NotIn)
                        .withValue(value));
        return this;
    }

    /**
     * Retrieves the list of filters.
     *
     * @return the list of filters.
     */
    public List<Filter> filterings() {
        return filters;
    }
}