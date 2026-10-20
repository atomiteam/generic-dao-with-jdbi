package org.atomiteam.jdbi.generic.dao;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

class TypedProjectionTest {
    public static class Row {
        public String tenantId;
        public String id;
        public Long version;
        public String displayName;
        public String optional;
        public LocalDate dueDate;
        public OffsetDateTime updatedAt;
        @Json public JsonElement payload;
        @Json public List<String> tags;
    }

    @Test
    void typedPropertiesQuotedColumnsNullReplacementAndBorrowedRollback() {
        Jdbi db =
                DsqlSupport.configure(
                        Jdbi.create(
                                "jdbc:h2:mem:"
                                        + UUID.randomUUID()
                                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1"));
        db.useHandle(
                h ->
                        h.execute(
                                "CREATE TABLE records (\"tenantId\" varchar,\"id\" varchar,\"version\" bigint,\"displayName\" varchar,\"optional\" varchar,\"dueDate\" date,\"updatedAt\" timestamp with time zone,\"payload\" varchar,\"tags\" varchar, PRIMARY KEY(\"tenantId\",\"id\"))"));
        PostgresEntityCodec<Row> codec =
                new PostgresEntityCodec<>(Row.class, ColumnNaming.QUOTED_IDENTITY);
        GenericDao<Row> dao =
                new GenericDao<>(db, Row.class, "records", ColumnNaming.QUOTED_IDENTITY, codec);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", "same");
        values.put("version", 1L);
        values.put("displayName", "Before");
        values.put("optional", "clear");
        values.put("dueDate", "2026-10-20");
        values.put("updatedAt", "2026-10-20T12:00:00Z");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("integer", 9007199254740993L);
        payload.put("explicitNull", null);
        values.put("payload", payload);
        values.put("tags", List.of("a", "b"));
        dao.scoped(Map.of("tenantId", "a")).insert(codec.fromProperties(values));
        dao.scoped(Map.of("tenantId", "b")).insert(codec.fromProperties(values));
        Row stored = dao.scoped(Map.of("tenantId", "a")).getById("same").orElseThrow();
        assertEquals("Before", stored.displayName);
        assertEquals(LocalDate.parse("2026-10-20"), stored.dueDate);
        assertEquals(
                9007199254740993L, stored.payload.getAsJsonObject().get("integer").getAsLong());
        assertEquals(List.of("a", "b"), stored.tags);
        assertTrue(stored.payload.getAsJsonObject().has("explicitNull"));
        assertTrue(stored.payload.getAsJsonObject().get("explicitNull").isJsonNull());
        values.put("tenantId", "a");
        values.put("version", 2L);
        values.put("displayName", "After");
        values.put("optional", null);
        Row replacement = codec.fromProperties(values);
        try (org.jdbi.v3.core.Handle h = db.open()) {
            h.begin();
            assertEquals(
                    1,
                    dao.scoped(Map.of("tenantId", "a"))
                            .using(h)
                            .replaceByKey(Map.of("id", "same", "version", 1L), replacement));
            h.rollback();
        }
        assertEquals(
                "Before",
                dao.scoped(Map.of("tenantId", "a")).getById("same").orElseThrow().displayName);
        assertEquals(
                1,
                dao.scoped(Map.of("tenantId", "a"))
                        .replaceByKey(Map.of("id", "same", "version", 1L), replacement));
        assertEquals(
                0,
                dao.scoped(Map.of("tenantId", "a"))
                        .replaceByKey(Map.of("id", "same", "version", 1L), replacement));
        assertNull(dao.scoped(Map.of("tenantId", "a")).getById("same").orElseThrow().optional);
        assertEquals(
                "Before",
                dao.scoped(Map.of("tenantId", "b")).getById("same").orElseThrow().displayName);
        values.put("tenantId", "b");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        dao.scoped(Map.of("tenantId", "a"))
                                .replaceByKey(
                                        Map.of("id", "same", "version", 2L),
                                        codec.fromProperties(values)));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.fromProperties(Map.of("unmapped", "bad")));
        assertThrows(
                java.time.format.DateTimeParseException.class,
                () -> codec.fromProperties(Map.of("dueDate", "invalid")));
    }
}
