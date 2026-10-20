package org.atomiteam.jdbi.generic.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

class SchemaQualifiedTableTest {
    public static class Installation extends Entity {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @Test
    void defaultCodecSupportsCrudOnQualifiedTable() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        jdbi.useHandle(handle -> {
            handle.execute("CREATE SCHEMA PORTDB");
            handle.execute("CREATE TABLE PORTDB.inst (id VARCHAR PRIMARY KEY, name VARCHAR)");
            // A different default-schema table catches accidental loss of the qualifier.
            handle.execute("CREATE TABLE inst (id VARCHAR PRIMARY KEY, name VARCHAR)");
        });
        GenericDao<Installation> dao = new GenericDao<>(jdbi, Installation.class, "PORTDB.inst");
        Installation item = new Installation();
        item.setId("one");
        item.setName("Before");
        dao.insert(item);
        assertEquals("Before", dao.getById("one").orElseThrow().getName());
        assertEquals(1, dao.filter(Filtering.create().eq("name", "Before")
                .withSorting("name", Sorting.ASC)).size());
        assertEquals(1, dao.count(Filtering.create().eq("name", "Before")));
        item.setName("After");
        assertEquals(1, dao.update(item));
        assertEquals("After", dao.getById("one").orElseThrow().getName());
        assertEquals(1, dao.update("one", Map.of("name", "Patched")));
        assertEquals("Patched", dao.get(Map.of("name", "Patched")).orElseThrow().getName());
        assertEquals(1, dao.delete("one"));
        assertFalse(dao.getById("one").isPresent());
        dao.insert(item);
        assertEquals(1, dao.delete(Filtering.create().eq("id", "one")));
        assertEquals(0, dao.count(Filtering.create()));
        int unqualifiedCount = jdbi.withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM inst").mapTo(Integer.class).one());
        assertEquals(0, unqualifiedCount);
    }

    @Test
    void rejectsMalformedQualifiedNamesAndSqlFragments() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:unused");
        for (String table : Arrays.asList(null, "", ".inst", "PORTDB.", "PORTDB..inst",
                "PORTDB.inst;", "PORTDB.inst -- comment", "PORTDB/*x*/.inst",
                "PORTDB.inst alias", "PORTDB. inst", "PORTDB.inst JOIN other",
                "PORTDB.inst; DROP TABLE inst", "PORTDB.1inst")) {
            assertThrows(IllegalArgumentException.class,
                    () -> new GenericDao<>(jdbi, Installation.class, table),
                    "Must reject table: " + table);
        }
        GenericDao<Installation> dao = new GenericDao<>(jdbi, Installation.class, "PORTDB.inst");
        assertThrows(IllegalArgumentException.class,
                () -> dao.filter(Filtering.create().eq("inst.name", "x")));
        assertThrows(IllegalArgumentException.class,
                () -> dao.filter(Filtering.create().withSorting("inst.name", Sorting.ASC)));
    }
}
