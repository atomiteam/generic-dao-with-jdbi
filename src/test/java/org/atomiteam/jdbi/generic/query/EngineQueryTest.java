package org.atomiteam.jdbi.generic.query;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.atomiteam.jdbi.generic.dao.*;
import org.junit.jupiter.api.Test;

class EngineQueryTest {
    @Test
    void explicitDialectControlsNullOrderingAndSurvivesCopies() {
        for (DatabaseEngine engine : DatabaseEngine.values()) {
            for (SeekColumn.Nulls nulls : SeekColumn.Nulls.values()) {
                var query = engine.query("SELECT * FROM items").seekAfter(
                        List.of(new SeekColumn("name", Sorting.DESC, nulls)), List.of("last"));
                assertEquals(query.sql(), query.copy().sql());
                assertEquals(query.bindings(), query.copy().bindings());
                if (engine == DatabaseEngine.MYSQL) {
                    assertFalse(query.sql().contains("NULLS "));
                    assertTrue(query.sql().contains("(name IS NULL) " + (nulls == SeekColumn.Nulls.FIRST ? "DESC" : "ASC")));
                } else assertTrue(query.sql().contains("name DESC NULLS " + nulls));
            }
        }
        assertThrows(NullPointerException.class, () -> JdbiQuery.select("SELECT 1", null));
        assertTrue(JdbiQuery.select("SELECT * FROM items")
                .seekAfter(List.of(new SeekColumn("id", Sorting.ASC, SeekColumn.Nulls.LAST)), null)
                .sql().contains("NULLS LAST"));
    }
}
