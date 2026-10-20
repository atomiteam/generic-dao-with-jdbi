package org.atomiteam.jdbi.generic.query;

import java.util.*;
import org.atomiteam.jdbi.generic.dao.*;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QueryRegressionTest {
    @Test void orFragmentsCannotEscapeEarlierScopePredicates() {
        Jdbi db=Jdbi.create("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        db.useHandle(h->{h.execute("CREATE TABLE items(id int, tenantId varchar, name varchar)");h.execute("INSERT INTO items VALUES(1,'a','Alpha'),(2,'b','Beta')");});
        JdbiQuery query=JdbiQuery.select("SELECT * FROM items").eq("tenantId","a").where("name = ? OR name = ?","Alpha","Beta");
        List<Map<String,Object>> rows=new JdbiQueryExecutor(db).maps(query);
        assertEquals(1,rows.size(),"OR must stay grouped under the tenant predicate");
        assertEquals("a",rows.get(0).get("tenantid"));
    }
    public static class Item {
        public Integer id;
        public String name;
        public Item() {}
    }
    @Test void offsetOnlyStillWorksForExistingMysqlConsumersAndPostgresql() {
        for(String mode:List.of("MySQL","PostgreSQL")) {
            Jdbi db=Jdbi.create("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;MODE="+mode);
            db.useHandle(h->{h.execute("CREATE TABLE items(id int,name varchar)");h.execute("INSERT INTO items VALUES(1,'One'),(2,'Two'),(3,'Three')");});
            JdbiQuery query=JdbiQuery.select("SELECT * FROM items").orderBy("id",Sorting.ASC).offset(1L);
            assertTrue(query.sql().contains("LIMIT"),"MySQL requires LIMIT with OFFSET");
            assertFalse(query.sql().contains("18446744073709551615"),"old unsigned maximum overflows PostgreSQL bigint");
            assertEquals(2,new JdbiQueryExecutor(db).maps(query).size());
            assertEquals(2,new GenericDao<>(db,Item.class,"items").filter(Filtering.create().withSorting("id",Sorting.ASC).withOffset(1L)).size());
        }
    }
}
