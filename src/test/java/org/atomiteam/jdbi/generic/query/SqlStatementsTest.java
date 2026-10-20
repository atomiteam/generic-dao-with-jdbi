package org.atomiteam.jdbi.generic.query;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SqlStatementsTest {
    @Test void specializedSqlBorrowsTransactionAndClosesStatementResources() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        try (Connection c=source.getConnection()) {
            c.createStatement().execute("CREATE TABLE effects(id int primary key,payload varchar)");
            c.setAutoCommit(false);
            assertEquals(1,SqlStatements.update(c,"INSERT INTO effects VALUES(?,?)",Arrays.asList(1,null)));
            assertTrue(SqlStatements.exists(c,"SELECT 1 FROM effects WHERE id=?",List.of(1)));
            assertEquals(0,SqlStatements.list(source,"SELECT id FROM effects",List.of(),r->r.getInt(1)).size());
            c.rollback();
            assertFalse(SqlStatements.exists(c,"SELECT 1 FROM effects",List.of()));
            assertFalse(c.isClosed());
            SqlStatements.update(c,"INSERT INTO effects VALUES(?,?)",List.of(2,"retained"));
            c.commit();
        }
        assertEquals(List.of(2),SqlStatements.list(source,"SELECT id FROM effects",List.of(),r->r.getInt(1)));
        assertThrows(SQLException.class,()->SqlStatements.list(source,"SELECT missing FROM effects",List.of(),r->r.getString(1)));
    }
}
