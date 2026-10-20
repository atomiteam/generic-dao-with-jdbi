package org.atomiteam.jdbi.generic.query;

import static org.junit.jupiter.api.Assertions.*;

import org.atomiteam.jdbi.generic.dao.Sorting;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import java.util.*;

class SeekPaginationTest {
    @Test
    void keysetTraversesDuplicateValuesAndNullsInBothDirections() {
        Jdbi db =
                Jdbi.create(
                        "jdbc:h2:mem:"
                                + UUID.randomUUID()
                                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        db.useHandle(
                h -> {
                    h.execute(
                            "CREATE TABLE items(id int primary key, score int, tenantId varchar)");
                    h.execute(
                            "INSERT INTO items VALUES (1,10,'a'),(2,10,'a'),(3,20,'a'),(4,null,'a'),(5,null,'a'),(6,30,'b')");
                });
        JdbiQueryExecutor executor = new JdbiQueryExecutor(db);
        for (Sorting direction : Sorting.values())
            for (SeekColumn.Nulls nulls : SeekColumn.Nulls.values()) {
                List<Integer> ids = new ArrayList<>();
                List<?> cursor = null;
                List<SeekColumn> columns =
                        List.of(
                                new SeekColumn("score", direction, nulls),
                                new SeekColumn("id", direction, SeekColumn.Nulls.LAST));
                for (int page = 0; page < 10; page++) {
                    JdbiQuery query =
                            JdbiQuery.select("SELECT * FROM items")
                                    .eq("tenantId", "a")
                                    .seekAfter(columns, cursor);
                    SeekPage<Map<String, Object>> result =
                            executor.seekPage(
                                    query,
                                    2,
                                    row -> row,
                                    row -> Arrays.asList(row.get("score"), row.get("id")));
                    assertNull(query.getLimit(), "page execution must not mutate caller query");
                    result.getItems().forEach(row -> ids.add(((Number) row.get("id")).intValue()));
                    if (!result.hasMore()) {
                        assertNull(result.getNextValues());
                        break;
                    }
                    cursor = result.getNextValues();
                }
                List<Integer> expected =
                        db.withHandle(
                                h ->
                                        h.createQuery(
                                                        "SELECT id FROM items WHERE tenantId='a' ORDER BY score "
                                                                + direction
                                                                + " NULLS "
                                                                + nulls
                                                                + ", id "
                                                                + direction)
                                                .mapTo(Integer.class)
                                                .list());
                assertEquals(expected, ids);
                assertEquals(5, new HashSet<>(ids).size());
            }
        JdbiQuery query = JdbiQuery.select("SELECT * FROM items").offset(1L);
        assertFalse(query.sql().contains("184467"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        query.seekAfter(
                                List.of(new SeekColumn("id", Sorting.ASC, SeekColumn.Nulls.LAST)),
                                null));
    }

    @Test
    void queryExecutorBorrowsTheSameTransactionHandle() {
        Jdbi db = Jdbi.create("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        db.useHandle(h -> h.execute("CREATE TABLE items(id int)"));
        assertThrows(
                IllegalStateException.class,
                () ->
                        db.useTransaction(
                                h -> {
                                    h.execute("INSERT INTO items VALUES(1)");
                                    assertEquals(
                                            1L,
                                            new JdbiQueryExecutor(db)
                                                    .using(h)
                                                    .count(
                                                            JdbiQuery.select(
                                                                    "SELECT * FROM items")));
                                    assertFalse(h.isClosed());
                                    throw new IllegalStateException("rollback");
                                }));
        assertEquals(0L, new JdbiQueryExecutor(db).count(JdbiQuery.select("SELECT * FROM items")));
    }
}
