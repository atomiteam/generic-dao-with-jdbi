package org.atomiteam.jdbi.generic.dao;

import java.sql.Types;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PostgresCodecTest {
    @Test void aPreviouslyOpenedHandleGetsTheJsonBinderWhenBorrowed() {
        Jdbi db=Jdbi.create("jdbc:h2:mem:borrowedjson");
        try(org.jdbi.v3.core.Handle handle=db.open()) {
            DsqlSupport.dao(db,Row.class,"records").using(handle);
            DsqlSupport.configure(db);DsqlSupport.configure(handle);
            assertTrue(handle.getConfig(org.jdbi.v3.core.argument.Arguments.class)
                    .findFor(DsqlSupport.JsonValue.class,new DsqlSupport.JsonValue("{}")).isPresent());
            assertFalse(handle.isClosed());
        }
    }
    public static class Row {
        public String id, displayName;
        public Instant createdAt;
        public LocalDate dueDate;
        public UUID publicId;
        @Json public Map<String,Object> details;
        public Row() {}
    }
    @Test void mapsFoldedCamelCaseAndJdbcTemporalUuidValues() {
        Map<String,Object> values=new LinkedHashMap<>();
        Instant time=Instant.parse("2026-10-20T12:00:00Z");UUID uuid=UUID.randomUUID();
        values.put("id","a");values.put("displayname","Name");values.put("createdat",Timestamp.from(time));
        values.put("duedate",java.sql.Date.valueOf("2026-10-21"));values.put("publicid",uuid.toString());values.put("details","{\"message\":\"ok\"}");
        Row row=new ReflectionEntityCodec<>(Row.class).fromStorage(values);
        assertEquals("Name",row.displayName);assertEquals(time,row.createdAt);assertEquals(LocalDate.parse("2026-10-21"),row.dueDate);assertEquals(uuid,row.publicId);assertEquals("ok",row.details.get("message"));
    }
    @Test void jsonUsesOtherTypeInsteadOfVarcharAndPatchNullIsSqlNull() throws Exception {
        PostgresEntityCodec<Row> codec=new PostgresEntityCodec<>(Row.class,ColumnNaming.IDENTITY);
        Row row=new Row();row.id="a";row.details=Map.of("message","ok");
        DsqlSupport.JsonValue value=(DsqlSupport.JsonValue)codec.toStorage(row,false).get("details");
        assertEquals("{\"message\":\"ok\"}",value.getValue());
        Jdbi jdbi=DsqlSupport.configure(Jdbi.create("jdbc:h2:mem:json"));
        java.sql.PreparedStatement statement=mock(java.sql.PreparedStatement.class);
        jdbi.getConfig(org.jdbi.v3.core.argument.Arguments.class).findFor(DsqlSupport.JsonValue.class,value).orElseThrow().apply(1,statement,null);
        verify(statement).setObject(1,value.getValue(),Types.OTHER);
        DsqlSupport.JsonValue nil=(DsqlSupport.JsonValue)codec.toStorageValue("details",null);
        jdbi.getConfig(org.jdbi.v3.core.argument.Arguments.class).findFor(DsqlSupport.JsonValue.class,nil).orElseThrow().apply(2,statement,null);
        verify(statement).setObject(2,null,Types.OTHER);
    }
}
