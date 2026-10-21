package org.atomiteam.jdbi.generic.dao;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import java.util.*;

class DatabaseEngineTest {
    public static class Row {
        public String id;
        public String tenantId;
        public String order;
        @Json public Map<String, Object> payload;
    }

    @Test
    void configurationIsExplicitAndInvalidEnginesOrQuotingAreRejected() {
        for (DatabaseEngine engine : DatabaseEngine.values()) {
            assertEquals(
                    engine,
                    DatabaseEngine.parse(" " + engine.name().toLowerCase(Locale.ROOT) + " "));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            engine.dao(
                                    Jdbi.create("jdbc:h2:mem:unused"),
                                    Row.class,
                                    "items",
                                    engine == DatabaseEngine.MYSQL
                                            ? ColumnNaming.QUOTED_IDENTITY
                                            : ColumnNaming.BACKTICK_IDENTITY));
            assertThrows(NullPointerException.class, () -> engine.configure(null));
            assertThrows(NullPointerException.class, () -> engine.create(null));
            assertThrows(
                    NullPointerException.class,
                    () -> engine.dao(Jdbi.create("jdbc:h2:mem:unused"), Row.class, "items", null));
        }
        assertThrows(IllegalArgumentException.class, () -> DatabaseEngine.parse("oracle"));
        assertThrows(IllegalArgumentException.class, () -> DatabaseEngine.parse(""));
        assertThrows(NullPointerException.class, () -> DatabaseEngine.parse(null));
        assertEquals("`camelCase`", ColumnNaming.BACKTICK_IDENTITY.toColumnName("camelCase"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ColumnNaming.BACKTICK_IDENTITY.toColumnName(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> ColumnNaming.BACKTICK_IDENTITY.toColumnName("id;DROP"));
    }

    @Test
    void eachEngineMapsScopedPojoCrudAndKeepsCallerTransactionOwnership() {
        for (DatabaseEngine engine : DatabaseEngine.values()) {
            String mode = engine == DatabaseEngine.MYSQL ? "MySQL" : "PostgreSQL";
            var source = new org.h2.jdbcx.JdbcDataSource();
            source.setURL(
                    "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=" + mode + ";DB_CLOSE_DELAY=-1");
            Jdbi db = engine.create(source);
            String q = engine == DatabaseEngine.MYSQL ? "`" : "\"";
            db.useHandle(
                    h ->
                            h.execute(
                                    "CREATE TABLE items("
                                            + q
                                            + "id"
                                            + q
                                            + " varchar,"
                                            + q
                                            + "tenantId"
                                            + q
                                            + " varchar,"
                                            + q
                                            + "order"
                                            + q
                                            + " varchar,"
                                            + q
                                            + "payload"
                                            + q
                                            + " varchar)"));
            var dao = engine.dao(db, Row.class, "items").scoped(Map.of("tenantId", "a"));
            var row = new Row();
            row.id = "one";
            row.order = "Before";
            row.payload = Map.of("key", "value");
            dao.insert(row);
            assertEquals("value", dao.getById("one").orElseThrow().payload.get("key"));
            assertTrue(
                    engine.dao(db, Row.class, "items")
                            .scoped(Map.of("tenantId", "b"))
                            .getById("one")
                            .isEmpty());
            assertEquals(1, dao.updateByKey(Map.of("id", "one"), Map.of("order", "After")));
            assertEquals("After", dao.getById("one").orElseThrow().order);
            try (var handle = db.open()) {
                handle.begin();
                assertTrue(dao.using(handle).getByKeyForUpdate(Map.of("id", "one")).isPresent());
                dao.using(handle).deleteByKey(Map.of("id", "one"));
                handle.rollback();
                assertFalse(handle.isClosed());
            }
            row.tenantId = "a";
            row.order = null;
            row.payload = null;
            assertEquals(1, dao.replaceByKey(Map.of("id", "one"), row));
            assertNull(dao.getById("one").orElseThrow().order);
            assertNull(dao.getById("one").orElseThrow().payload);
            assertEquals(1, dao.deleteByKey(Map.of("id", "one")));
            assertTrue(dao.getById("one").isEmpty());
            assertNotNull(engine.dao(db, Row.class, "items", ColumnNaming.IDENTITY));
        }
    }

    @Test
    void mysqlDoesNotInstallPostgresJsonBinding() {
        Jdbi db = mock(Jdbi.class);
        assertSame(db, DatabaseEngine.MYSQL.configure(db));
        verifyNoInteractions(db);
    }
}
