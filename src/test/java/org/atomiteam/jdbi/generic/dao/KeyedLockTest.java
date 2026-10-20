package org.atomiteam.jdbi.generic.dao;

import static org.junit.jupiter.api.Assertions.*;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

class KeyedLockTest {
    public static class Row {
        public String tenantId;
        public String id;
        public String name;
    }

    @Test
    void keyedLockRequiresABorrowedTransactionAndKeepsItsScope() {
        var db = Jdbi.create("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        db.useHandle(
                handle -> {
                    handle.execute(
                            "CREATE TABLE items(tenantId varchar, id varchar, name varchar)");
                    handle.execute(
                            "INSERT INTO items VALUES('a','one','Before'),('b','two','Foreign')");
                });
        var dao = new GenericDao<>(db, Row.class, "items").scoped(Map.of("tenantId", "a"));
        assertThrows(IllegalStateException.class, () -> dao.getByKeyForUpdate(Map.of("id", "one")));
        try (var handle = db.open()) {
            assertThrows(
                    IllegalStateException.class,
                    () -> dao.using(handle).getByKeyForUpdate(Map.of("id", "one")));
            handle.begin();
            assertEquals(
                    "Before",
                    dao.using(handle).getByKeyForUpdate(Map.of("id", "one")).orElseThrow().name);
            assertTrue(dao.using(handle).getByKeyForUpdate(Map.of("id", "two")).isEmpty());
            dao.using(handle).update("one", Map.of("name", "Changed"));
            handle.rollback();
            assertFalse(handle.isClosed());
        }
        assertEquals("Before", dao.getById("one").orElseThrow().name);
    }
}
