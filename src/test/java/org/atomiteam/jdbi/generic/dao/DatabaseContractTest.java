package org.atomiteam.jdbi.generic.dao;

import java.util.*;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.atomiteam.jdbi.generic.query.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs unchanged against PostgreSQL or an actual Aurora DSQL JDBC URL. */
@EnabledIfEnvironmentVariable(named="DAO_JDBC_URL", matches="jdbc:.*")
class DatabaseContractTest {
    public static class Row {
        public String tenantId, workspaceId, id, displayName;
        public Long version;
        @Json public Map<String,Object> details;
        public Row() {}
    }
    @Test void realDatabaseContractForFoldedCamelCaseScopeJsonTransactionsAndSeek() {
        String url=System.getenv("DAO_JDBC_URL");Properties properties=new Properties();
        if(System.getenv("DAO_JDBC_USER")!=null)properties.setProperty("user",System.getenv("DAO_JDBC_USER"));
        if(System.getenv("DAO_JDBC_PASSWORD")!=null)properties.setProperty("password",System.getenv("DAO_JDBC_PASSWORD"));
        Jdbi db=DsqlSupport.configure(Jdbi.create(url,properties));
        String table="dao_contract_"+UUID.randomUUID().toString().replace("-","");
        // DDL is autocommit, separate from DML; no savepoints/identity/serial dependencies.
        db.useHandle(h->h.execute("CREATE TABLE "+table+" (tenantId varchar(64),workspaceId varchar(64),id varchar(64),displayName varchar(128),version bigint,details jsonb,PRIMARY KEY(tenantId,workspaceId,id))"));
        try {
            GenericDao<Row> all=DsqlSupport.dao(db,Row.class,table),dao=all.scoped(Map.of("tenantId","a","workspaceId","w"));
            for(String tenant:List.of("a","b"))DsqlTransactions.run(db,4,h->{
                Row row=new Row();row.id="same";row.displayName="Alpha";row.version=1L;row.details=Map.of("hello","world");
                all.scoped(Map.of("tenantId",tenant,"workspaceId","w")).using(h).insert(row);return null;
            });
            Row loaded=dao.getById("same").orElseThrow();assertEquals("a",loaded.tenantId);assertEquals("Alpha",loaded.displayName);assertEquals("world",loaded.details.get("hello"));
            assertEquals(1,dao.filter(Filtering.create().withOffset(0L)).size());
            assertEquals(1,new JdbiQueryExecutor(db).maps(JdbiQuery.select("SELECT * FROM "+table).eq("tenantId","a").offset(0L)).size());
            assertThrows(IllegalStateException.class,()->DsqlTransactions.run(db,2,h->{dao.using(h).update("same",Map.of("displayName","Rollback"));throw new IllegalStateException("abort");}));
            assertEquals("Alpha",dao.getById("same").orElseThrow().displayName);
            assertEquals(1,dao.updateByKey(Map.of("id","same","version",1L),Map.of("version",2L)));
            assertEquals(0,dao.updateByKey(Map.of("id","same","version",1L),Map.of("version",3L)));
            Map<String,Object> patch=new HashMap<>();patch.put("details",null);assertEquals(1,dao.update("same",patch));assertNull(dao.getById("same").orElseThrow().details);
            JdbiQuery query=JdbiQuery.select("SELECT id,displayName FROM "+table).eq("tenantId","a").eq("workspaceId","w")
                    .seekAfter(List.of(new SeekColumn("id",Sorting.ASC,SeekColumn.Nulls.LAST)),null);
            SeekPage<Map<String,Object>> page=new JdbiQueryExecutor(db).seekPage(query,1,row->row,row->List.of(row.get("id")));
            assertEquals(1,page.getItems().size());assertFalse(page.hasMore());
            assertEquals(1,dao.delete("same"));assertTrue(dao.getById("same").isEmpty());assertEquals(1,all.count(Filtering.create()));
        } finally { db.useHandle(h->h.execute("DROP TABLE "+table)); }
    }
}
