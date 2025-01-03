package org.atomiteam.jdbi.generic.dao;

/**
 * Represents a filter that can be applied to a query.
 * A filter consists of a field name, an operator, and a value to compare against.
 * This class is used in conjunction with the {@link Filtering} class to build dynamic queries.
 */
public class Filter {
    private String name;
    private Operator operator;
    private Object value;

    /**
     * Private constructor to enforce the use of the factory method {@link #create()}.
     */
    private Filter() {
    }

    /**
     * Creates a new instance of Filter.
     *
     * @return a new Filter instance.
     */
    public static Filter create() {
        return new Filter();
    }

    /**
     * Gets the name of the field to filter on.
     *
     * @return the name of the field.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the field to filter on.
     *
     * @param name the name of the field.
     * @return the current Filter instance for method chaining.
     */
    public Filter withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Gets the operator used in the filter.
     *
     * @return the operator.
     */
    public Operator getOperator() {
        return operator;
    }

    /**
     * Sets the operator used in the filter.
     *
     * @param operator the operator to apply.
     * @return the current Filter instance for method chaining.
     */
    public Filter withOperator(Operator operator) {
        this.operator = operator;
        return this;
    }

    /**
     * Gets the value to compare against in the filter.
     *
     * @return the value.
     */
    public Object getValue() {
        return value;
    }

    /**
     * Sets the value to compare against in the filter.
     *
     * @param value the value to compare.
     * @return the current Filter instance for method chaining.
     */
    public Filter withValue(Object value) {
        this.value = value;
        return this;
    }
}