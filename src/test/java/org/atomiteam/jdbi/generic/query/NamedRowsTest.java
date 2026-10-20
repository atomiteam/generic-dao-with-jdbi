package org.atomiteam.jdbi.generic.query;

import static org.junit.jupiter.api.Assertions.*;

import org.atomiteam.jdbi.generic.dao.Sorting;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class NamedRowsTest {
    @Test
    void namedQueryPreservesNativeDateProjectionAndBorrowedHandle() {
        var db = Jdbi.create("jdbc:h2:mem:" + UUID.randomUUID());
        try (var handle = db.open()) {
            handle.execute("CREATE TABLE items(id int, due_date date)");
            handle.execute("INSERT INTO items VALUES(1, DATE '2026-10-20'),(2, DATE '2026-10-21')");
            var query =
                    JdbiQuery.select("SELECT due_date FROM items")
                            .in("id", List.of(1, 2))
                            .orderBy("id", Sorting.ASC);
            var executor = new JdbiQueryExecutor(db).using(handle);
            var result = executor.rows(query, 10, (rows, context) -> rows.getDate(1).toLocalDate());
            assertEquals(
                    List.of(LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-21")), result);
            assertFalse(handle.isClosed());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> executor.rows(query, 0, (rows, context) -> rows.getDate(1)));
        }
    }
}
