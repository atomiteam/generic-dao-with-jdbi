package org.atomiteam.jdbi.generic.query;

import org.atomiteam.jdbi.generic.dao.Sorting;

import java.util.Objects;

/** Trusted SQL expression, direction and explicit null ordering for keyset pagination. */
public final class SeekColumn {
    public enum Nulls {
        FIRST,
        LAST
    }

    private final String expression;
    private final Sorting direction;
    private final Nulls nulls;

    public SeekColumn(String expression, Sorting direction, Nulls nulls) {
        this.expression = Objects.requireNonNull(expression);
        this.direction = Objects.requireNonNull(direction);
        this.nulls = Objects.requireNonNull(nulls);
    }

    public String getExpression() {
        return expression;
    }

    public Sorting getDirection() {
        return direction;
    }

    public Nulls getNulls() {
        return nulls;
    }
}
