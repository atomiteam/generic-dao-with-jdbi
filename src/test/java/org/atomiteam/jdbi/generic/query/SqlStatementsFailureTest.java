package org.atomiteam.jdbi.generic.query;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SqlStatementsFailureTest {
    @Test void readFailureRollsBackOwnedConnectionAndClosesItsResources() throws Exception {
        DataSource source=mock(DataSource.class);
        Connection connection=mock(Connection.class);PreparedStatement statement=mock(PreparedStatement.class);
        ResultSet rows=mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT value")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(connection.getAutoCommit()).thenReturn(false);
        doThrow(new SQLException("rollback failed")).when(connection).rollback();
        var original=new IllegalArgumentException("mapping failed");
        var failure=assertThrows(IllegalArgumentException.class,()->SqlStatements.list(
                source,"SELECT value",List.of(),row->{throw original;}));
        assertSame(original,failure);assertEquals(1,failure.getSuppressed().length);
        verify(statement).setQueryTimeout(10);
        verify(rows).close();verify(statement).close();verify(connection).rollback();verify(connection).close();
        verify(connection,never()).commit();
    }
    @Test void autoCommitReadDoesNotCompleteAnExternalTransaction() throws Exception {
        DataSource source=mock(DataSource.class);Connection connection=mock(Connection.class);
        PreparedStatement statement=mock(PreparedStatement.class);ResultSet rows=mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);when(connection.prepareStatement("SELECT 1")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);when(rows.next()).thenReturn(true,false);
        when(connection.getAutoCommit()).thenReturn(true);
        assertEquals(List.of("value"),SqlStatements.list(source,"SELECT 1",List.of(),r->"value"));
        verify(connection,never()).commit();verify(connection,never()).rollback();verify(connection).close();
    }
    @Test void jsonNullAndPositionalNullValuesUseTheExpectedJdbcBinding() throws Exception {
        PreparedStatement statement=mock(PreparedStatement.class);
        SqlStatements.json(statement,1,null);SqlStatements.json(statement,2,Map.of("x",1));
        SqlStatements.bind(statement,Arrays.asList("first",null));
        verify(statement).setNull(1,Types.OTHER);
        verify(statement).setObject(2,"{\"x\":1}",Types.OTHER);
        verify(statement).setObject(1,"first");verify(statement).setObject(2,null);
    }
}
