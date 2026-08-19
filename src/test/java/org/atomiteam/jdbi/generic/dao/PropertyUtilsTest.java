package org.atomiteam.jdbi.generic.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PropertyUtilsTest {

    private enum State {
        Active,
        Closed
    }

    @Test
    void convertsJdbcNumbersToRequestedJavaNumericTypes() {
        assertEquals(Long.valueOf(12L), PropertyUtils.convertValue(Integer.valueOf(12), Long.class));
        assertEquals(Integer.valueOf(12), PropertyUtils.convertValue(Long.valueOf(12L), Integer.class));
    }

    @Test
    void convertsDatabaseStringsToEnums() {
        assertEquals(State.Active, PropertyUtils.convertValue("Active", State.class));
        assertEquals(State.Closed, PropertyUtils.convertValue("Closed", State.class));
    }
}
