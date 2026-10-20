package org.atomiteam.jdbi.generic.query;

import org.atomiteam.jdbi.generic.dao.Sorting;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QueryValidationTest {
    @Test void lexicalCompositionIgnoresQuotedAndNestedKeywordsAndPlaceholders() {
        String predicate="name = $$ ? where $$ AND id = ?";
        assertEquals("name = $$ ? where $$ AND id = :bound",
                SqlFragments.predicate(predicate,new Object[]{1},v->"bound"));
        assertEquals("name='it''s ?' AND id=:bound",
                SqlFragments.predicate("name='it''s ?' AND id=?",new Object[]{1},v->"bound"));
        assertThrows(IllegalArgumentException.class,()->SqlFragments.predicate("name=$tag$?",new Object[]{},v->"x"));
        assertThrows(IllegalArgumentException.class,()->SqlFragments.append("SELECT id FROM items /* unfinished","id=1"));
        assertThrows(IllegalArgumentException.class,()->SqlFragments.append("SELECT (id FROM items","id=1"));
        assertThrows(IllegalArgumentException.class,()->SqlFragments.append("SELECT id) FROM items","id=1"));
    }
    @Test void sortAndKeysetValidationRejectsAmbiguousOrUnboundedRequests() {
        var order=List.of(new SeekColumn("id",Sorting.ASC,SeekColumn.Nulls.LAST));
        assertThrows(IllegalArgumentException.class,()->JdbiQuery.select(" "));
        assertThrows(IllegalArgumentException.class,()->JdbiQuery.select("SELECT 1").seekAfter(List.of(),null));
        assertThrows(IllegalArgumentException.class,()->JdbiQuery.select("SELECT 1").seekAfter(order,List.of(1,2)));
        assertThrows(IllegalArgumentException.class,()->JdbiQuery.select("SELECT 1").orderBy("id",Sorting.ASC).seekAfter(order,null));
        var seeking=JdbiQuery.select("SELECT 1 id").seekAfter(order,null);
        assertThrows(IllegalArgumentException.class,()->seeking.offset(0L));
        assertThrows(IllegalArgumentException.class,()->seeking.orderBy("id",Sorting.ASC));
        var executor=new JdbiQueryExecutor(org.jdbi.v3.core.Jdbi.create("jdbc:h2:mem:validation"));
        assertThrows(IllegalArgumentException.class,()->executor.seekPage(seeking,0,row->row,row->List.of()));
        assertThrows(IllegalArgumentException.class,()->executor.seekPage(seeking,10001,row->row,row->List.of()));
        assertThrows(IllegalArgumentException.class,()->executor.seekPage(JdbiQuery.select("SELECT 1"),1,row->row,row->List.of()));
        assertThrows(IllegalArgumentException.class,()->JdbiQuery.select("SELECT 1")
                .orderByAllowed("name sideways",Map.of("name","id"),"name",Sorting.ASC));
        assertThrows(IllegalArgumentException.class,()->JdbiQuery.select("SELECT 1")
                .orderByAllowed("unknown",Map.of("name","id"),null,Sorting.ASC));
        assertTrue(JdbiQuery.select("SELECT 1").orderByAllowed("unknown",Map.of("name","id"),"name",Sorting.DESC)
                .sql().contains("ORDER BY id DESC"));
    }
}
