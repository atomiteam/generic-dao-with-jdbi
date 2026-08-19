package org.atomiteam.jdbi.generic.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.h2.H2DatabasePlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SnakeCaseLongIdTest {

    private Jdbi jdbi;
    private SnakeLongDao dao;

    @BeforeEach
    void setUp() {
        jdbi = Jdbi.create("jdbc:h2:mem:snake_long;DB_CLOSE_DELAY=-1;database_to_upper=false");
        jdbi.installPlugin(new H2DatabasePlugin());
        jdbi.useHandle(handle -> {
            handle.execute("DROP TABLE IF EXISTS snake_long_record");
            handle.execute("CREATE TABLE snake_long_record (" +
                    "id BIGINT PRIMARY KEY, " +
                    "sample_column VARCHAR(255), " +
                    "created_at BIGINT, " +
                    "legacy_label VARCHAR(255))");
        });
        dao = new SnakeLongDao(jdbi);
    }

    @Test
    void snakeCaseNamingConvertsCamelCaseAndAcronyms() {
        assertEquals("sample_column", ColumnNaming.SNAKE_CASE.toColumnName("sampleColumn"));
        assertEquals("created_at", ColumnNaming.SNAKE_CASE.toColumnName("createdAt"));
        assertEquals("url_value", ColumnNaming.SNAKE_CASE.toColumnName("URLValue"));
        assertEquals("id", ColumnNaming.SNAKE_CASE.toColumnName("id"));
    }

    @Test
    void insertAndReadSupportLongIdSnakeCaseAndColumnOverride() {
        SnakeLongRecord record = record(1001L, "alpha", 123456789L, "Legacy A");
        dao.insert(record);

        Optional<SnakeLongRecord> loaded = dao.getById(1001L);
        assertTrue(loaded.isPresent());
        assertEquals(1001L, loaded.get().getId());
        assertEquals("alpha", loaded.get().getSampleColumn());
        assertEquals(123456789L, loaded.get().getCreatedAt());
        assertEquals("Legacy A", loaded.get().getDisplayLabel());
    }

    @Test
    void updateUsesSnakeCaseColumnsAndLongPrimaryKey() {
        dao.insert(record(2001L, "before", 10L, "Old"));

        SnakeLongRecord update = new SnakeLongRecord();
        update.setId(2001L);
        update.setSampleColumn("after");
        update.setDisplayLabel("New");
        assertEquals(1, dao.update(update));

        SnakeLongRecord loaded = dao.getById(2001L).get();
        assertEquals("after", loaded.getSampleColumn());
        assertEquals(10L, loaded.getCreatedAt());
        assertEquals("New", loaded.getDisplayLabel());
    }

    @Test
    void explicitPatchCanWriteSqlNull() {
        dao.insert(record(2101L, "before", 10L, "Old"));
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("sampleColumn", null);
        changes.put("displayLabel", "Still here");

        assertEquals(1, dao.update(2101L, changes));
        SnakeLongRecord loaded = dao.getById(2101L).get();
        assertNull(loaded.getSampleColumn());
        assertEquals("Still here", loaded.getDisplayLabel());
    }

    @Test
    void filterAndCountAcceptJavaPropertyNames() {
        dao.insert(record(3001L, "beta", 30L, "B"));
        dao.insert(record(3002L, "alpha", 20L, "A"));
        dao.insert(record(3003L, "gamma", 10L, "C"));

        List<SnakeLongRecord> matches = dao.filter(Filtering.create().eq("sampleColumn", "alpha"));
        assertEquals(1, matches.size());
        assertEquals(3002L, matches.get(0).getId());
        assertEquals(2, dao.count(Filtering.create().in("createdAt", List.of(20L, 30L))));
    }

    @Test
    void nullPredicatesAreTypedAndBoundValueFree() {
        dao.insert(record(3101L, null, 30L, "A"));
        dao.insert(record(3102L, "present", 20L, "B"));

        assertEquals(1, dao.filter(Filtering.create().isNull("sampleColumn")).size());
        assertEquals(1, dao.filter(Filtering.create().isNotNull("sampleColumn")).size());
    }

    @Test
    void sortingUsesJavaPropertyNameButDatabaseSnakeCaseColumn() {
        dao.insert(record(4001L, "one", 30L, "1"));
        dao.insert(record(4002L, "two", 10L, "2"));
        dao.insert(record(4003L, "three", 20L, "3"));

        List<SnakeLongRecord> sorted = dao.filter(Filtering.create().withSorting("createdAt", Sorting.ASC));
        assertEquals(List.of(4002L, 4003L, 4001L),
                sorted.stream().map(SnakeLongRecord::getId).collect(Collectors.toList()));
    }

    @Test
    void rejectsUnknownFilterPropertyBeforeExecutingSql() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> dao.filter(Filtering.create().eq("doesNotExist", "x")));
        assertTrue(error.getMessage().contains("Unknown or unmapped property"));
    }

    @Test
    void rejectsSqlInjectionInFilterPropertyBeforeExecutingSql() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.filter(Filtering.create().eq("sampleColumn OR 1=1", "x")));
    }

    @Test
    void rejectsSqlInjectionInSortingPropertyBeforeExecutingSql() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.filter(Filtering.create().withSorting("createdAt DESC; DROP TABLE snake_long_record", Sorting.ASC)));
        assertEquals(0, dao.count(Filtering.create()));
    }

    @Test
    void rejectsInvalidTableIdentifierAtConstructionTime() {
        assertThrows(IllegalArgumentException.class,
                () -> new GenericDao<>(jdbi, SnakeLongRecord.class,
                        "snake_long_record; DROP TABLE snake_long_record", ColumnNaming.SNAKE_CASE));
    }

    @Test
    void deleteSupportsLongPrimaryKey() {
        dao.insert(record(5001L, "delete", 1L, "D"));
        assertEquals(1, dao.delete(5001L));
        assertFalse(dao.getById(5001L).isPresent());
    }

    @Test
    void identityNamingRemainsDefaultForExistingDaos() {
        assertEquals("sampleColumn", ColumnNaming.IDENTITY.toColumnName("sampleColumn"));
    }

    private SnakeLongRecord record(Long id, String sampleColumn, Long createdAt, String label) {
        SnakeLongRecord record = new SnakeLongRecord();
        record.setId(id);
        record.setSampleColumn(sampleColumn);
        record.setCreatedAt(createdAt);
        record.setDisplayLabel(label);
        return record;
    }
}
