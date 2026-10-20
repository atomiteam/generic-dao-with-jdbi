package org.atomiteam.jdbi.generic.dao;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonElement;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import java.util.*;

class TypedPostgresContractTest {
    public static class Record {
        public String tenantId;
        public String id;
        public Long version;
        public String displayName;
        @Json public JsonElement sourceDocument;
    }

    @Test
    void quotedTypedJsonCrudOnPostgres() {
        String url = System.getenv("DAO_JDBC_URL");
        assumeTrue(url != null && url.startsWith("jdbc:postgresql:"));
        Jdbi db =
                DsqlSupport.configure(
                        Jdbi.create(
                                url,
                                System.getenv("DAO_JDBC_USER"),
                                System.getenv("DAO_JDBC_PASSWORD")));
        String table = "typed_" + UUID.randomUUID().toString().replace("-", "");
        db.useHandle(
                h ->
                        h.execute(
                                "CREATE TABLE "
                                        + table
                                        + " (\"tenantId\" text,\"id\" text,\"version\" bigint,\"displayName\" text,\"sourceDocument\" jsonb, PRIMARY KEY(\"tenantId\",\"id\"))"));
        try {
            var codec = new PostgresEntityCodec<>(Record.class, ColumnNaming.QUOTED_IDENTITY);
            var dao = DsqlSupport.dao(db, Record.class, table, ColumnNaming.QUOTED_IDENTITY);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("large", 9007199254740993L);
            payload.put("explicitNull", null);
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", "same");
            values.put("version", 1L);
            values.put("displayName", "Before");
            values.put("sourceDocument", payload);
            dao.scoped(Map.of("tenantId", "a")).insert(codec.fromProperties(values));
            dao.scoped(Map.of("tenantId", "b")).insert(codec.fromProperties(values));
            var stored = dao.scoped(Map.of("tenantId", "a")).getById("same").orElseThrow();
            assertEquals(
                    9007199254740993L,
                    stored.sourceDocument.getAsJsonObject().get("large").getAsLong());
            assertTrue(stored.sourceDocument.getAsJsonObject().get("explicitNull").isJsonNull());
            assertEquals(
                    1,
                    dao.scoped(Map.of("tenantId", "a"))
                            .filter(
                                    Filtering.create()
                                            .in("sourceDocument", List.of(stored.sourceDocument)))
                            .size());
            values.put("tenantId", "a");
            values.put("version", 2L);
            values.put("displayName", null);
            assertEquals(
                    1,
                    dao.scoped(Map.of("tenantId", "a"))
                            .replaceByKey(
                                    Map.of("id", "same", "version", 1L),
                                    codec.fromProperties(values)));
            assertNull(
                    dao.scoped(Map.of("tenantId", "a")).getById("same").orElseThrow().displayName);
            assertEquals(
                    "Before",
                    dao.scoped(Map.of("tenantId", "b")).getById("same").orElseThrow().displayName);
            assertEquals(
                    0,
                    dao.scoped(Map.of("tenantId", "a"))
                            .replaceByKey(
                                    Map.of("id", "same", "version", 1L),
                                    codec.fromProperties(values)));
        } finally {
            db.useHandle(h -> h.execute("DROP TABLE " + table));
        }
    }
}
