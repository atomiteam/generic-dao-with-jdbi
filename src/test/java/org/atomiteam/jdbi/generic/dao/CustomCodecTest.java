package org.atomiteam.jdbi.generic.dao;

import static org.junit.jupiter.api.Assertions.*;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import java.util.*;

class CustomCodecTest {
    static final class External {
        String key, label;
    }

    static final class Codec implements EntityCodec<External> {
        public Map<String, Object> toStorage(External entity, boolean nulls) {
            var values = new LinkedHashMap<String, Object>();
            values.put("id", entity.key);
            if (nulls || entity.label != null) values.put("name", entity.label);
            return values;
        }

        public External fromStorage(Map<String, Object> values) {
            var entity = new External();
            entity.key = (String) values.get("id");
            entity.label = (String) values.get("name");
            return entity;
        }

        public boolean isMappedProperty(String property) {
            return Set.of("id", "name").contains(property);
        }

        public String columnName(String property, ColumnNaming naming) {
            return naming.toColumnName(property);
        }

        public Object getId(External entity) {
            return entity.key;
        }

        public void setId(External entity, Object value) {
            entity.key = String.valueOf(value);
        }
    }

    @Test
    void externalModelsPlugIntoAllCrudWithoutInheritingTheReflectionCodec() {
        Jdbi db =
                Jdbi.create(
                        "jdbc:h2:mem:"
                                + UUID.randomUUID()
                                + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        db.useHandle(
                h -> h.execute("CREATE TABLE external_rows(id varchar primary key,name varchar)"));
        var dao =
                new GenericDao<>(
                        db, External.class, "external_rows", ColumnNaming.IDENTITY, new Codec());
        External entity = new External();
        entity.key = "one";
        entity.label = "Before";
        var mapped = dao.fromStorage(dao.toStorage(entity, true));
        assertEquals(entity.key, mapped.key);
        assertEquals(entity.label, mapped.label);
        dao.insert(entity);
        assertEquals("Before", dao.getById("one").orElseThrow().label);
        assertEquals(1, dao.updateByKey(Map.of("id", "one"), Map.of("name", "After")));
        assertEquals("After", dao.filter(Filtering.create().equalTo("name", "After")).get(0).label);
        entity.label = "Complete";
        assertEquals(1, dao.update(entity));
        assertEquals(1, dao.delete(entity));
        assertTrue(dao.getById("one").isEmpty());
    }

    @Test
    void filteringRetainsItsLegacySortAccessorAndReadOnlySortList() {
        Filtering filtering = Filtering.create();
        assertNull(filtering.getSorting());
        filtering.withSorting("score", Sorting.DESC).withSorting("id", Sorting.ASC);
        assertEquals("score DESC", filtering.getSorting());
        assertThrows(UnsupportedOperationException.class, () -> filtering.getSortings().clear());
        assertThrows(IllegalArgumentException.class, () -> Filtering.create().withOffset(-1L));
        assertThrows(IllegalArgumentException.class, () -> Filtering.create().withLimit(-1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> Filtering.create().withSorting(" ", Sorting.ASC));
        assertThrows(
                IllegalArgumentException.class, () -> Filtering.create().withSorting("id", null));
    }
}
