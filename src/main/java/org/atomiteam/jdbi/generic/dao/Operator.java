package org.atomiteam.jdbi.generic.dao;

/** Supported comparisons; SQL syntax is metadata, never client-provided text. */
public enum Operator {
    Like("LIKE"),
    NotLike("NOT LIKE"),
    In("IN"),
    NotIn("NOT IN"),
    Eq("="),
    NotEq("!="),
    Lt("<"),
    Lte("<="),
    Gt(">"),
    Gte(">="),
    IsNull("IS NULL"),
    IsNotNull("IS NOT NULL");

    private final String sql;

    Operator(String sql) {
        this.sql = sql;
    }

    String sql() {
        return sql;
    }

    boolean collection() {
        return this == In || this == NotIn;
    }

    boolean unary() {
        return this == IsNull || this == IsNotNull;
    }
}
