package org.atomiteam.jdbi.generic.dao;

/** A Java-property comparison used by Filtering; fluent accessors retain the legacy API. */
public class Filter {
    private String name;
    private Operator operator;
    private Object value;

    private Filter() {}

    public static Filter create() {
        return new Filter();
    }

    public String getName() {
        return name;
    }

    public Operator getOperator() {
        return operator;
    }

    public Object getValue() {
        return value;
    }

    public Filter withName(String name) {
        this.name = name;
        return this;
    }

    public Filter withOperator(Operator operator) {
        this.operator = operator;
        return this;
    }

    public Filter withValue(Object value) {
        this.value = value;
        return this;
    }
}
