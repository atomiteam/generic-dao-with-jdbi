package org.atomiteam.jdbi.generic.query;

import static org.junit.jupiter.api.Assertions.*;

import org.atomiteam.jdbi.generic.dao.*;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.*;

@EnabledIfEnvironmentVariable(named = "DAO_MYSQL_JDBC_URL", matches = "jdbc:mysql:.*")
class MysqlEngineContractTest {
    public static class Row {
        public String id, tenantId, displayName;
        @Json public Map<String, Object> payload;
    }

    @Test
    void engineConfigSupportsQuotedCamelCaseJsonScopesAndNullableSeek() {
        var engine = DatabaseEngine.parse("mysql");
        Jdbi db =
                engine.configure(
                        Jdbi.create(
                                System.getenv("DAO_MYSQL_JDBC_URL"),
                                System.getenv("DAO_MYSQL_JDBC_USER"),
                                System.getenv("DAO_MYSQL_JDBC_PASSWORD")));
        String table = "engine_" + UUID.randomUUID().toString().replace("-", "");
        db.useHandle(
                h ->
                        h.execute(
                                "CREATE TABLE "
                                        + table
                                        + " (`id` varchar(64) PRIMARY KEY,`tenantId` varchar(64),`displayName` varchar(64),`payload` JSON)"));
        try {
            var dao = engine.dao(db, Row.class, table).scoped(Map.of("tenantId", "a"));
            for (String id : List.of("one", "two", "three")) {
                var row = new Row();
                row.id = id;
                row.displayName = id.equals("three") ? null : id;
                row.payload = Map.of("key", id);
                dao.insert(row);
            }
            assertEquals("one", dao.getById("one").orElseThrow().payload.get("key"));
            assertTrue(
                    engine.dao(db, Row.class, table)
                            .scoped(Map.of("tenantId", "b"))
                            .getById("one")
                            .isEmpty());
            for (SeekColumn.Nulls nulls : SeekColumn.Nulls.values()) {
                var order =
                        List.of(
                                new SeekColumn("`displayName`", Sorting.ASC, nulls),
                                new SeekColumn("`id`", Sorting.ASC, SeekColumn.Nulls.LAST));
                var first =
                        engine.query("SELECT `displayName` AS sort_name,`id` AS id FROM " + table)
                                .eq("`tenantId`", "a")
                                .seekAfter(order, null);
                var all = new JdbiQueryExecutor(db).maps(first);
                assertEquals(
                        nulls == SeekColumn.Nulls.FIRST ? "three" : "one", all.get(0).get("id"));
                List<?> position = Arrays.asList(all.get(0).get("sort_name"), all.get(0).get("id"));
                var following =
                        engine.query("SELECT `displayName` AS sort_name,`id` AS id FROM " + table)
                                .eq("`tenantId`", "a")
                                .seekAfter(order, position);
                assertEquals(2, new JdbiQueryExecutor(db).maps(following.copy()).size());
                assertFalse(following.sql().contains("NULLS "));
            }
        } finally {
            db.useHandle(h -> h.execute("DROP TABLE " + table));
        }
    }
}
