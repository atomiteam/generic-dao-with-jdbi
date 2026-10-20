package org.atomiteam.jdbi.generic.dao;

import static org.junit.jupiter.api.Assertions.*;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.*;
import java.util.stream.*;

class DaoOperationsTest {
    public static class Row {
        public String tenantId, id, name;
        public Integer score;
        public Long version;
    }

    private GenericDao<Row> all, scoped;

    @BeforeEach
    void setup() {
        Jdbi db =
                Jdbi.create(
                        "jdbc:h2:mem:"
                                + UUID.randomUUID()
                                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        db.useHandle(
                h ->
                        h.execute(
                                "CREATE TABLE rows_table(tenantId varchar,id varchar,name varchar,score int,version bigint,PRIMARY KEY(tenantId,id))"));
        all = new GenericDao<>(db, Row.class, "rows_table");
        scoped = all.scoped(Map.of("tenantId", "a"));
        for (int i = 1; i <= 4; i++) {
            Row row = new Row();
            row.id = String.valueOf(i);
            row.name = i == 1 ? "Alpha" : i == 2 ? "Beta" : null;
            row.score = i == 3 ? null : i;
            row.version = 1L;
            all.scoped(Map.of("tenantId", i == 4 ? "b" : "a")).insert(row);
        }
    }

    static Stream<Arguments> operators() {
        return Stream.of(
                Arguments.of(Filtering.create().equalTo("score", 1), List.of("1")),
                Arguments.of(Filtering.create().notEq("score", 1), List.of("2")),
                Arguments.of(Filtering.create().like("name", "A%"), List.of("1")),
                Arguments.of(Filtering.create().notLike("name", "A%"), List.of("2")),
                Arguments.of(Filtering.create().in("score", List.of(1, 2, 4)), List.of("1", "2")),
                Arguments.of(Filtering.create().notIn("score", List.of(1, 4)), List.of("2")),
                Arguments.of(Filtering.create().lt("score", 2), List.of("1")),
                Arguments.of(Filtering.create().lte("score", 2), List.of("1", "2")),
                Arguments.of(Filtering.create().gt("score", 1), List.of("2")),
                Arguments.of(Filtering.create().gte("score", 1), List.of("1", "2")),
                Arguments.of(Filtering.create().isNull("score"), List.of("3")),
                Arguments.of(Filtering.create().isNotNull("score"), List.of("1", "2")));
    }

    @ParameterizedTest
    @MethodSource("operators")
    void everyOperatorFiltersAndCountsInsideMandatoryScope(
            Filtering filter, List<String> expected) {
        assertEquals(
                expected,
                scoped.filter(filter.withSorting("id", Sorting.ASC)).stream()
                        .map(row -> row.id)
                        .collect(Collectors.toList()));
        assertEquals(expected.size(), scoped.count(filter));
    }

    @Test
    void everyMutationVariantKeepsScopeAndOptimisticKeys() {
        Row original = scoped.getById("1").orElseThrow();
        original.name = "Changed";
        assertSame(original, scoped.updateAndReturn(original));
        assertEquals("Changed", scoped.get(Map.of("id", "1")).orElseThrow().name);
        assertEquals(
                1, scoped.updateByKey(Map.of("id", "1", "version", 1L), Map.of("version", 2L)));
        assertEquals(
                0, scoped.updateByKey(Map.of("id", "1", "version", 1L), Map.of("name", "Stale")));
        Row replacement = scoped.getById("1").orElseThrow();
        replacement.name = null;
        replacement.score = null;
        replacement.version = 3L;
        assertEquals(1, scoped.replaceByKey(Map.of("id", "1", "version", 2L), replacement));
        assertNull(scoped.getById("1").orElseThrow().name);
        Map<String, Object> clear = new LinkedHashMap<>();
        clear.put("score", null);
        assertEquals(1, scoped.update("2", clear));
        assertEquals(1, scoped.deleteByKey(Map.of("id", "3")));
        assertEquals(1, scoped.delete(scoped.getById("2").orElseThrow()));
        assertEquals(0, scoped.delete("4"));
        assertEquals(1, all.scoped(Map.of("tenantId", "b")).count(Filtering.create()));
        assertEquals(0, scoped.update("1", Map.of("id", "new")));
    }

    @Test
    void literalKeysAndLegacyComparisonPrefixesRemainDistinct() {
        Row row = new Row();
        row.id = ">literal";
        row.version = 1L;
        scoped.insert(row);
        assertTrue(scoped.getByKey(Map.of("id", ">literal")).isPresent());
        assertEquals(1, scoped.list(Map.of("score", ">=2")).size());
        assertEquals(1, scoped.list(Map.of("score", "<2")).size());
        assertEquals(2, scoped.list(Map.of("score", "<=2")).size());
        assertEquals(1, scoped.list(Map.of("score", ">1")).size());
        assertTrue(scoped.getById(null).isEmpty());
        assertEquals(4, scoped.list(null).size());
    }

    @Test
    void invalidOrUnfilteredMutationsAreRejectedBeforeChangingData() {
        assertThrows(IllegalArgumentException.class, () -> scoped.delete(Filtering.create()));
        assertThrows(IllegalArgumentException.class, () -> scoped.getByKey(Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> scoped.updateByKey(Map.of(), Map.of("name", "x")));
        assertThrows(IllegalArgumentException.class, () -> scoped.scoped(Map.of("tenantId", "b")));
        assertThrows(IllegalArgumentException.class, () -> scoped.scoped(Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> scoped.filter(Filtering.create().in("id", List.of())));
        assertThrows(
                IllegalArgumentException.class,
                () -> scoped.filter(Filtering.create().notIn("id", List.of())));
        assertThrows(
                IllegalArgumentException.class,
                () -> scoped.filter(Filtering.create().eq("id;DROP TABLE rows_table", "x")));
        assertThrows(
                IllegalArgumentException.class,
                () -> scoped.updateByKey(Map.of("id", "1"), Map.of("tenantId", "b")));
        assertEquals(3, scoped.count(Filtering.create()));
    }
}
