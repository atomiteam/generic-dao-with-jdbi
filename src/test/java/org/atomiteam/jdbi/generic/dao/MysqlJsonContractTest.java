package org.atomiteam.jdbi.generic.dao;

import static org.junit.jupiter.api.Assertions.*;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.*;

@EnabledIfEnvironmentVariable(named = "DAO_MYSQL_JDBC_URL", matches = "jdbc:mysql:.*")
class MysqlJsonContractTest {
    public static class Row {
        public Long id;
        public String tenantId, displayName;
        @Json public Map<String, Object> payload;
    }

    @Test
    void mysqlGeneratedIdsSnakeCaseJsonAndAllMutationModes() {
        Jdbi db =
                Jdbi.create(
                        System.getenv("DAO_MYSQL_JDBC_URL"),
                        System.getenv("DAO_MYSQL_JDBC_USER"),
                        System.getenv("DAO_MYSQL_JDBC_PASSWORD"));
        String table = "typed_mysql_" + UUID.randomUUID().toString().replace("-", "");
        db.useHandle(
                h ->
                        h.execute(
                                "CREATE TABLE "
                                        + table
                                        + "(id bigint AUTO_INCREMENT PRIMARY KEY,tenant_id varchar(64),display_name varchar(64),payload JSON)"));
        try {
            GenericDao<Row> dao =
                    new GenericDao<>(db, Row.class, table, ColumnNaming.SNAKE_CASE)
                            .scoped(Map.of("tenantId", "a"));
            Row row = new Row();
            row.displayName = "Before";
            row.payload = Map.of("key", "one");
            dao.insert(row);
            assertNotNull(row.id);
            assertEquals("one", dao.getById(row.id).orElseThrow().payload.get("key"));
            row.displayName = "Updated";
            row.payload = Map.of("key", "two");
            assertEquals(1, dao.update(row));
            assertEquals("two", dao.getById(row.id).orElseThrow().payload.get("key"));
            assertEquals(1, dao.update(row.id, Map.of("payload", Map.of("key", "three"))));
            assertEquals("three", dao.getById(row.id).orElseThrow().payload.get("key"));
            Row replacement = dao.getById(row.id).orElseThrow();
            replacement.payload = null;
            replacement.displayName = null;
            assertEquals(1, dao.replaceByKey(Map.of("id", row.id), replacement));
            assertNull(dao.getById(row.id).orElseThrow().payload);
            assertEquals(1, dao.deleteByKey(Map.of("id", row.id)));
        } finally {
            db.useHandle(h -> h.execute("DROP TABLE " + table));
        }
    }
}
