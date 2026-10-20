package org.atomiteam.jdbi.generic.dao;

import java.util.Map;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuotedColumnsTest {
    public static class Row {
        public String id, tenantId, displayName;
        public Long version;
        public Row() {}
    }
    @Test void quotedColumnsSupportScopedCrudAndOptimisticKeys() {
        Jdbi db=Jdbi.create("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        db.useHandle(h->h.execute("CREATE TABLE records (\"id\" varchar, \"tenantId\" varchar, \"displayName\" varchar, \"version\" bigint, PRIMARY KEY(\"tenantId\",\"id\"))"));
        EntityCodec<Row> codec=new ReflectionEntityCodec<Row>(Row.class,ColumnNaming.IDENTITY) {
            @Override public String columnName(String property,ColumnNaming naming) {return "\""+super.columnName(property,naming)+"\"";}
        };
        GenericDao<Row> all=new GenericDao<>(db,Row.class,"\"PUBLIC\".\"RECORDS\"",ColumnNaming.IDENTITY,codec);
        GenericDao<Row> scoped=all.scoped(Map.of("tenantId","a"));
        Row row=new Row();row.id="same";row.displayName="First";row.version=1L;scoped.insert(row);
        assertEquals("First",scoped.getById("same").orElseThrow().displayName);
        assertEquals(0,all.scoped(Map.of("tenantId","b")).count(Filtering.create()));
        assertEquals(1,scoped.updateByKey(Map.of("id","same","version",1L),Map.of("displayName","Next","version",2L)));
        assertEquals(0,scoped.updateByKey(Map.of("id","same","version",1L),Map.of("displayName","Stale")));
        assertEquals(1,scoped.deleteByKey(Map.of("id","same","version",2L)));
    }
    @Test void aQuotedExpressionIsStillRejected() {
        Jdbi db=Jdbi.create("jdbc:h2:mem:"+UUID.randomUUID());
        EntityCodec<Row> codec=new ReflectionEntityCodec<Row>(Row.class,ColumnNaming.IDENTITY) {
            @Override public String columnName(String property,ColumnNaming naming) {return "\"id\" OR 1=1 --\"";}
        };
        assertThrows(IllegalArgumentException.class,()->new GenericDao<>(db,Row.class,"records",ColumnNaming.IDENTITY,codec).getById("x"));
    }
}
