package org.atomiteam.jdbi.generic.query;

import static org.junit.jupiter.api.Assertions.*;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.*;

class CompiledQueryTest {
    @Test
    void compiledRowsUsePositionalTypesAndBorrowedHandle() {
        Jdbi db = Jdbi.create("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        db.useHandle(h -> h.execute("CREATE TABLE records(id int primary key,label varchar)"));
        var executor = new JdbiQueryExecutor(db);
        try (var handle = db.open()) {
            handle.begin();
            handle.execute("INSERT INTO records VALUES(1,'literal ? value')");
            var rows =
                    executor.using(handle)
                            .rows(
                                    "SELECT label FROM records WHERE id=? AND label=?",
                                    List.of(
                                            new JdbiQueryExecutor.TypedValue(1, Types.INTEGER),
                                            "literal ? value"),
                                    10,
                                    (r, ctx) -> r.getString(1));
            assertEquals(List.of("literal ? value"), rows);
            assertTrue(handle.isInTransaction());
            handle.rollback();
            assertFalse(handle.isClosed());
        }
        assertTrue(
                executor.rows("SELECT id FROM records", List.of(), 10, (r, ctx) -> r.getInt(1))
                        .isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        executor.rows(
                                "SELECT id FROM records", List.of(), 0, (r, ctx) -> r.getInt(1)));
    }
}
