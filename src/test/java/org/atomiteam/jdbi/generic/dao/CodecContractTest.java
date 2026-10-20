package org.atomiteam.jdbi.generic.dao;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class CodecContractTest {
    public static class Parent { public String inherited; }
    public static class Model extends Parent {
        @Column("display_name") public String displayName;
        public Long id;
        public LocalDate dueDate;
        public OffsetDateTime updatedAt;
        @Json public List<Map<String,String>> items;
        @Json public JsonElement tree;
        @Transient public String ignored;
        public transient String temporary;
        public static String global;
        public void setDisplayName(String value) { displayName=value==null?null:value.trim(); }
    }
    public static class Shadow extends Parent { public String inherited; }
    public static class NoConstructor { public NoConstructor(String ignored) {} public String id; }
    public static class Legacy extends Entity {
        public String name;
        public transient String temporary;
        @Json public Map<String,Object> details;
    }

    @Test void inheritedMetadataAliasesSettersAndGenericJsonShareOneCodec() {
        var codec=new ReflectionEntityCodec<>(Model.class);
        var row=codec.fromStorage(Map.of("DISPLAY_NAME"," value ","ID",12,
                "INHERITED","parent","items","[{\"key\":\"value\"}]"));
        assertEquals("value",row.displayName);assertEquals(12L,row.id);
        assertEquals("parent",row.inherited);assertEquals("value",row.items.get(0).get("key"));
        row.ignored="hidden";row.temporary="temporary";
        assertFalse(codec.toStorage(row,true).containsKey("ignored"));
        assertFalse(codec.toStorage(row,true).containsKey("temporary"));
        assertFalse(codec.toStorage(row,true).containsKey("global"));
        assertFalse(codec.isMappedProperty("temporary"));
        assertEquals("display_name",codec.columnName("displayName",ColumnNaming.IDENTITY));
        assertEquals("updated_at",codec.columnName("updatedAt",ColumnNaming.SNAKE_CASE));
        codec.setId(row,19);assertEquals(19L,codec.getId(row));
        assertThrows(IllegalArgumentException.class,()->codec.fromProperties(Map.of("ignored","bad")));
        assertThrows(IllegalArgumentException.class,()->codec.toStorageValue("unknown","bad"));
        assertThrows(IllegalArgumentException.class,()->codec.fromStorage(Map.of("DISPLAY_NAME","a","Display_Name","b")));
    }
    @Test void legacyChangesDelegateToTheSamePersistentPropertyRules() {
        Legacy row=new Legacy();row.setId("id");row.name="name";row.temporary="skip";
        row.details=Map.of("key","value");
        var expected=new ReflectionEntityCodec<>(Legacy.class).toStorage(row,false);
        assertEquals(expected,row.toChanges());assertFalse(row.toChanges().containsKey("temporary"));
    }
    @Test void invalidModelsAndUnsupportedConstructionFailClearly() {
        assertThrows(IllegalArgumentException.class,()->new ReflectionEntityCodec<>(Shadow.class));
        var failure=assertThrows(IllegalStateException.class,
                ()->new ReflectionEntityCodec<>(NoConstructor.class).fromStorage(Map.of("id","x")));
        assertTrue(failure.getMessage().contains("no-argument constructor"));
        assertThrows(NullPointerException.class,()->new ReflectionEntityCodec<>(Model.class,null));
        assertThrows(IllegalArgumentException.class,()->ColumnNaming.QUOTED_IDENTITY.toColumnName("name;DROP"));
    }
    static Stream<Arguments> conversions() {
        return Stream.of(
            Arguments.of("2026-10-20T12:00:00Z",Instant.class,Instant.parse("2026-10-20T12:00:00Z")),
            Arguments.of("2026-10-20",LocalDate.class,LocalDate.parse("2026-10-20")),
            Arguments.of("2026-10-20T12:00:00",LocalDateTime.class,LocalDateTime.parse("2026-10-20T12:00:00")),
            Arguments.of("2026-10-20T12:00:00Z",OffsetDateTime.class,OffsetDateTime.parse("2026-10-20T12:00:00Z")),
            Arguments.of(java.sql.Date.valueOf("2026-10-20"),LocalDate.class,LocalDate.parse("2026-10-20")),
            Arguments.of(java.sql.Timestamp.valueOf("2026-10-20 12:00:00"),LocalDateTime.class,LocalDateTime.parse("2026-10-20T12:00:00")),
            Arguments.of(java.sql.Timestamp.from(Instant.EPOCH),Instant.class,Instant.EPOCH),
            Arguments.of(java.sql.Timestamp.from(Instant.EPOCH),OffsetDateTime.class,Instant.EPOCH.atOffset(ZoneOffset.UTC)),
            Arguments.of(Instant.EPOCH.atOffset(ZoneOffset.UTC),Instant.class,Instant.EPOCH),
            Arguments.of("00000000-0000-0000-0000-000000000001",UUID.class,UUID.fromString("00000000-0000-0000-0000-000000000001")),
            Arguments.of(2L,Integer.class,2),Arguments.of(2,Long.class,2L),
            Arguments.of(2,Short.class,(short)2),Arguments.of(2,Byte.class,(byte)2),
            Arguments.of(2,Double.class,2d),Arguments.of(2,Float.class,2f),
            Arguments.of("DESC",Sorting.class,Sorting.DESC),Arguments.of(2,String.class,"2")
        );
    }
    @ParameterizedTest @MethodSource("conversions")
    void nativeJdbcAndPropertyConversions(Object value,Class<?> target,Object expected) {
        assertEquals(expected,PropertyUtils.convertValue(value,target));
    }
    @Test void postgresJsonIsEncodedOnceForCompleteAndPartialWrites() {
        var codec=new PostgresEntityCodec<>(Model.class,ColumnNaming.QUOTED_IDENTITY);
        Map<String,Object> tree=new LinkedHashMap<>();tree.put("null",null);tree.put("large",9007199254740993L);
        Model row=codec.fromProperties(Map.of("id",1,"tree",tree,"dueDate","2026-10-20",
                "updatedAt","2026-10-20T12:00:00Z"));
        var stored=codec.toStorage(row,true);
        var json=(DsqlSupport.JsonValue)stored.get("tree");
        assertTrue(json.getValue().contains("\"null\":null"));
        assertTrue(json.getValue().contains("9007199254740993"));
        assertEquals(json,codec.toStorageValue("tree",row.tree));
        assertNull(((DsqlSupport.JsonValue)codec.toStorageValue("tree",null)).getValue());
        assertEquals(row.dueDate,stored.get("dueDate"));
    }
}
