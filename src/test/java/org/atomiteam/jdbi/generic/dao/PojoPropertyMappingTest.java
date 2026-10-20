package org.atomiteam.jdbi.generic.dao;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

class PojoPropertyMappingTest {
    public static class Row {
        public String id;
        public String displayName;
        @Json public JsonElement extensions;
        @Transient public String ignored;
    }

    @Test
    void documentExtensionsDoNotBecomeColumnsAndNullsCanClearProperties() {
        var codec = new PostgresEntityCodec<>(Row.class, ColumnNaming.QUOTED_IDENTITY);
        var source = new LinkedHashMap<String, Object>();
        source.put("id", "one");
        source.put("displayName", null);
        source.put("unknownExtension", Map.of("retainedAtDocumentBoundary", true));
        source.put("id; DROP TABLE rows", "unmapped");
        source.put("ignored", "unmapped");
        var selected = codec.mappedProperties(source, true);
        assertEquals(java.util.Set.of("id", "displayName", "extensions"), selected.keySet());
        assertNull(selected.get("displayName"));
        var row = codec.fromProperties(selected);
        assertEquals("one", row.id);
        assertNull(row.displayName);
        assertEquals(Map.of("id", "one"), codec.mappedProperties(source, false));
        assertTrue(source.containsKey("unknownExtension"));
    }
}
