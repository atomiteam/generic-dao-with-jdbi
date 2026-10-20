package org.atomiteam.jdbi.generic.query;

import java.util.*;
import org.atomiteam.jdbi.generic.dao.*;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="DAO_MYSQL_JDBC_URL",matches="jdbc:mysql:.*")
class MysqlContractTest {
    public static class Item { public Integer id; public String tenantId,name; public Item() {} }
    @Test void legacyMysqlOffsetAndScopeRemainCompatible() {
        Jdbi db=Jdbi.create(System.getenv("DAO_MYSQL_JDBC_URL"),System.getenv("DAO_MYSQL_JDBC_USER"),System.getenv("DAO_MYSQL_JDBC_PASSWORD"));
        String table="dao_contract_"+UUID.randomUUID().toString().replace("-","");
        db.useHandle(h->{h.execute("CREATE TABLE "+table+"(tenantId varchar(64),id int,name varchar(64),PRIMARY KEY(tenantId,id))");
            h.execute("INSERT INTO "+table+" VALUES('a',1,'One'),('a',2,'Two'),('a',3,'Three'),('b',1,'Foreign')");});
        try {
            GenericDao<Item> dao=new GenericDao<>(db,Item.class,table).scoped(Map.of("tenantId","a"));
            List<Item> rows=dao.filter(Filtering.create().withSorting("id",Sorting.ASC).withOffset(1L));
            assertEquals(List.of(2,3),rows.stream().map(row->row.id).collect(java.util.stream.Collectors.toList()));
            JdbiQuery query=JdbiQuery.select("SELECT * FROM "+table).eq("tenantId","a").orderBy("id",Sorting.ASC).offset(1L);
            assertEquals(2,new JdbiQueryExecutor(db).maps(query).size());
            JdbiQuery grouped=JdbiQuery.select("SELECT * FROM "+table).eq("tenantId","a").where("name = ? OR name = ?","One","Foreign");
            assertEquals(1,new JdbiQueryExecutor(db).maps(grouped).size());
            assertEquals(1,dao.update(1,Map.of("name","Changed")));assertEquals(1,dao.delete(1));
            assertEquals(1,new GenericDao<>(db,Item.class,table).scoped(Map.of("tenantId","b")).count(Filtering.create()));
        } finally {db.useHandle(h->h.execute("DROP TABLE "+table));}
    }
}
