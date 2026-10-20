package org.atomiteam.jdbi.generic.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.atomiteam.jdbi.generic.dao.ColumnNaming;
import org.atomiteam.jdbi.generic.dao.Sorting;
import org.h2.jdbcx.JdbcDataSource;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbiQueryTest {

    private JdbiQueryExecutor executor;

    public static class ItemProjection {
        private Long id;
        private String displayName;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }

    @BeforeEach
    void setup() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:query-layer;MODE=MySQL;DB_CLOSE_DELAY=-1");
        Jdbi jdbi = Jdbi.create(ds);
        jdbi.useHandle(h -> {
            h.execute("DROP TABLE IF EXISTS query_item");
            h.execute("CREATE TABLE query_item(id BIGINT AUTO_INCREMENT PRIMARY KEY, display_name VARCHAR(64), state VARCHAR(20))");
            h.execute("INSERT INTO query_item(display_name, state) VALUES ('Alpha', 'OPEN'), ('Beta', 'OPEN'), ('Gamma', 'CLOSED')");
        });
        executor = new JdbiQueryExecutor(jdbi);
    }

    @Test
    void filtersPagesCountsAndMapsSnakeCaseProjection() {
        JdbiQuery query = JdbiQuery.select("SELECT id, display_name FROM query_item")
                .eq("state", "OPEN")
                .orderBy("id", Sorting.DESC)
                .page(0, 1);

        QueryPage<ItemProjection> page = new QueryPage<>(
                executor.pojos(query, ItemProjection.class, ColumnNaming.SNAKE_CASE),
                executor.count(query));

        assertEquals(1, page.getItems().size());
        assertEquals(2L, page.getTotal());
        assertEquals("Beta", page.getItems().get(0).getDisplayName());
    }

    @Test
    void expandsCollectionBindings() {
        JdbiQuery query = JdbiQuery.select("SELECT id FROM query_item")
                .in("state", Arrays.asList("OPEN", "CLOSED"))
                .orderBy("id", Sorting.ASC);

        List<Long> ids = executor.scalars(query, Long.class);
        assertEquals(Arrays.asList(1L, 2L, 3L), ids);
    }

    @Test
    void clientSortIsResolvedThroughAllowList() {
        Map<String, String> sorts = new LinkedHashMap<>();
        sorts.put("name", "display_name");
        sorts.put("id", "id");

        JdbiQuery query = JdbiQuery.select("SELECT id FROM query_item")
                .orderByAllowed("name desc", sorts, "id", Sorting.DESC);

        assertTrue(query.sql().contains("ORDER BY display_name DESC"));
        assertThrows(IllegalArgumentException.class,
                () -> JdbiQuery.select("SELECT id FROM query_item")
                        .orderByAllowed("name DESC; DROP TABLE query_item", sorts, "id", Sorting.DESC));
    }

    @Test
    void rawPredicateValuesAreConvertedToNamedBindings() {
        JdbiQuery query = JdbiQuery.select("SELECT id FROM query_item")
                .where("(display_name LIKE ? OR display_name LIKE ?)", "A%", "B%")
                .orderBy("id", Sorting.ASC);

        assertEquals(Arrays.asList(1L, 2L), executor.scalars(query, Long.class));
        assertTrue(query.sql().contains(":_q0"));
        assertTrue(query.sql().contains(":_q1"));
    }
}
