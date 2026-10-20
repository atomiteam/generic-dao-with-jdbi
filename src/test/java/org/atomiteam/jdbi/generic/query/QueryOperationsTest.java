package org.atomiteam.jdbi.generic.query;

import static org.junit.jupiter.api.Assertions.*;

import org.atomiteam.jdbi.generic.dao.*;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.*;
import java.util.stream.Stream;

class QueryOperationsTest {
    public static class Projection {
        public Integer id;
        public String name;
    }

    private Jdbi db;
    private JdbiQueryExecutor executor;

    @BeforeEach
    void setup() {
        db =
                Jdbi.create(
                        "jdbc:h2:mem:"
                                + UUID.randomUUID()
                                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        db.useHandle(
                h -> {
                    h.execute(
                            "CREATE TABLE items(id int primary key,tenantId varchar,score int,name varchar)");
                    h.execute(
                            "INSERT INTO items VALUES(1,'a',1,'Alpha'),(2,'a',2,'Beta'),(3,'a',3,'Gamma'),(4,'a',null,'Null'),(5,'b',2,'Foreign')");
                });
        executor = new JdbiQueryExecutor(db);
    }

    private JdbiQuery query() {
        return JdbiQuery.select("SELECT * FROM items").eq("tenantId", "a");
    }

    interface Comparison {
        JdbiQuery apply(JdbiQuery query, Integer value);
    }

    static Stream<Arguments> comparisons() {
        return Stream.of(
                Arguments.of((Comparison) (q, v) -> q.eq("score", v), List.of(2)),
                Arguments.of((Comparison) (q, v) -> q.ne("score", v), List.of(1, 3)),
                Arguments.of((Comparison) (q, v) -> q.lt("score", v), List.of(1)),
                Arguments.of((Comparison) (q, v) -> q.lte("score", v), List.of(1, 2)),
                Arguments.of((Comparison) (q, v) -> q.gt("score", v), List.of(3)),
                Arguments.of((Comparison) (q, v) -> q.gte("score", v), List.of(2, 3)),
                Arguments.of((Comparison) (q, v) -> q.eqIfPresent("score", v), List.of(2)),
                Arguments.of((Comparison) (q, v) -> q.neIfPresent("score", v), List.of(1, 3)),
                Arguments.of((Comparison) (q, v) -> q.ltIfPresent("score", v), List.of(1)),
                Arguments.of((Comparison) (q, v) -> q.lteIfPresent("score", v), List.of(1, 2)),
                Arguments.of((Comparison) (q, v) -> q.gtIfPresent("score", v), List.of(3)),
                Arguments.of((Comparison) (q, v) -> q.gteIfPresent("score", v), List.of(2, 3)));
    }

    @ParameterizedTest
    @MethodSource("comparisons")
    void comparisonsHaveRealSqlSemantics(Comparison comparison, List<Integer> expected) {
        var selected = comparison.apply(query(), 2).orderBy("id", Sorting.ASC);
        assertEquals(expected, executor.list(selected, row -> ((Number) row.get("id")).intValue()));
    }

    @Test
    void optionalFiltersNullPredicatesAndCollectionsWorkTogether() {
        var q =
                query().eqIfPresent("score", null)
                        .neIfPresent("score", null)
                        .ltIfPresent("score", null)
                        .lteIfPresent("score", null)
                        .gtIfPresent("score", null)
                        .gteIfPresent("score", null)
                        .likeIfPresent("name", "")
                        .inIfNotEmpty("id", List.of())
                        .notInIfNotEmpty("id", null)
                        .whereIf(false, "score=?", 99);
        assertEquals(4, executor.maps(q).size());
        assertEquals(1, executor.maps(query().eq("score", null)).size());
        assertEquals(3, executor.maps(query().ne("score", null)).size());
        assertEquals(1, executor.maps(query().isNull("score")).size());
        assertEquals(3, executor.maps(query().isNotNull("score")).size());
        assertEquals(1, executor.maps(query().likeIfPresent("name", "A%")).size());
        assertEquals(2, executor.maps(query().inIfNotEmpty("id", List.of(1, 2))).size());
        assertEquals(2, executor.maps(query().notInIfNotEmpty("id", List.of(1, 2))).size());
        assertEquals(1, executor.maps(query().whereIf(true, "score=?", 2)).size());
    }

    @Test
    void allProjectionAndPageMethodsRespectFiltersAndEmptyResults() {
        var q = query().orderBy("id", Sorting.ASC).page(1, 1);
        assertEquals(2, ((Number) executor.firstMap(q).orElseThrow().get("id")).intValue());
        assertEquals("Beta", executor.first(q, row -> row.get("name")).orElseThrow());
        assertEquals(
                "Beta",
                executor.firstPojo(q, Projection.class, ColumnNaming.IDENTITY).orElseThrow().name);
        assertEquals(
                "Beta", executor.pojos(q, Projection.class, ColumnNaming.IDENTITY).get(0).name);
        assertEquals(
                "Beta",
                executor.pojos(q, new ReflectionEntityCodec<>(Projection.class)).get(0).name);
        assertEquals(2, executor.firstScalar(q, Integer.class).orElseThrow());
        assertEquals(4, executor.pageMaps(q).getTotal());
        assertEquals(1, executor.page(q, row -> row.get("name")).getItems().size());
        assertEquals(4, executor.count(q));
        var empty = query().eq("id", 99);
        assertTrue(executor.firstMap(empty).isEmpty());
        assertTrue(executor.first(empty, row -> row).isEmpty());
        assertTrue(executor.firstPojo(empty, Projection.class, ColumnNaming.IDENTITY).isEmpty());
        assertTrue(executor.firstScalar(empty, Integer.class).isEmpty());
        var items = new ArrayList<>(List.of("before"));
        var page = new QueryPage<>(items, 1);
        items.add("after");
        assertEquals(List.of("before"), page.getItems());
        assertThrows(UnsupportedOperationException.class, () -> page.getItems().add("bad"));
    }

    @Test
    void baseWhereOrSubqueriesLiteralsAndCommentsCannotWeakenAnAddedFilter() {
        String[] bases = {
            "SELECT * FROM items WHERE id=1 OR tenantId='b'",
            "SELECT * FROM (SELECT * FROM items WHERE score>0) nested",
            "SELECT * FROM items WHERE name<>'where' OR tenantId='b'",
            "SELECT * FROM items -- where in comment",
            "SELECT * FROM items WHERE id=1 OR tenantId='b' -- trailing comment"
        };
        for (String base : bases) {
            var rows = executor.maps(JdbiQuery.select(base).eq("tenantId", "a"));
            assertFalse(rows.isEmpty(), base);
            assertTrue(rows.stream().allMatch(row -> "a".equals(row.get("tenantid"))), base);
        }
        var grouped =
                JdbiQuery.select("SELECT tenantId,COUNT(*) total FROM items GROUP BY tenantId")
                        .eq("tenantId", "a");
        assertEquals(1, executor.maps(grouped).size());
    }

    @Test
    void invalidBuilderCallsDoNotPoisonAReusableQuery() {
        var q = query();
        assertThrows(IllegalArgumentException.class, () -> q.where("score=? AND id=?", 1));
        assertThrows(IllegalArgumentException.class, () -> q.where("score=?", 1, 2));
        assertThrows(IllegalArgumentException.class, () -> q.eq("name;DROP TABLE items", "bad"));
        assertThrows(IllegalArgumentException.class, () -> q.like("name--unsafe", "bad"));
        assertEquals(4, executor.maps(q).size());
        assertEquals(1, executor.maps(q.where("name='A?''literal' OR score=?", 1)).size());
        assertThrows(IllegalArgumentException.class, () -> q.where("name='unterminated", 1));
        assertThrows(IllegalArgumentException.class, () -> q.bind("_query_limit", 1));
        assertThrows(IllegalArgumentException.class, () -> q.bindList("empty", List.of()));
        assertThrows(IllegalArgumentException.class, () -> q.limit(-1L));
        assertThrows(IllegalArgumentException.class, () -> q.offset(-1L));
        assertThrows(IllegalArgumentException.class, () -> q.gt("score", null));
        assertThrows(IllegalArgumentException.class, () -> q.in("id", List.of()));
        assertThrows(IllegalArgumentException.class, () -> q.notIn("id", List.of()));
    }

    @Test
    void queryBindingsAndCopiesHaveIndependentCollectionSnapshots() {
        var values = new ArrayList<>(List.of(1, 2));
        var q =
                JdbiQuery.select("SELECT id FROM items WHERE id IN (<ids>)")
                        .bindList("ids", values);
        values.clear();
        assertEquals(2, executor.maps(q).size());
        var copy = q.copy().limit(1L);
        assertNull(q.getLimit());
        assertEquals(1, executor.maps(copy).size());
        assertThrows(UnsupportedOperationException.class, () -> q.bindings().put("bad", 1));
        assertThrows(IllegalArgumentException.class, () -> q.bind("ids", List.of(3)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        JdbiQuery.select("SELECT id FROM items UNION SELECT id FROM items")
                                .eq("id", 1)
                                .sql());
    }
}
