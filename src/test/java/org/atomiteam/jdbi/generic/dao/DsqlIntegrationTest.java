package org.atomiteam.jdbi.generic.dao;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.jdbi.v3.core.*;
import org.jdbi.v3.core.argument.ArgumentFactory;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;

class DsqlIntegrationTest {
    @Test
    void configureIsIdempotentAndTheArgumentFactoryUsesNativeJsonTypes() throws Exception {
        Jdbi db = mock(Jdbi.class);
        DsqlSupport.Settings settings = new DsqlSupport.Settings();
        when(db.getConfig(DsqlSupport.Settings.class)).thenReturn(settings);
        var captured = org.mockito.ArgumentCaptor.forClass(ArgumentFactory.class);
        assertSame(db, DsqlSupport.configure(db));
        assertSame(db, DsqlSupport.configure(db));
        verify(db, times(1)).registerArgument(captured.capture());
        var factory = captured.getValue();
        var statement = mock(PreparedStatement.class);
        factory.build(DsqlSupport.JsonValue.class, new DsqlSupport.JsonValue("{\"a\":1}"), null)
                .orElseThrow()
                .apply(1, statement, null);
        verify(statement).setObject(1, "{\"a\":1}", Types.OTHER);
        factory.build(DsqlSupport.JsonValue.class, null, null)
                .orElseThrow()
                .apply(2, statement, null);
        verify(statement).setObject(2, null, Types.OTHER);
        assertTrue(factory.build(String.class, "unchanged", null).isEmpty());
        assertNotSame(settings, settings.createCopy());
    }

    @Test
    void dsqlCallbacksDoNotUseSavepointsOrChangeIsolation() throws Exception {
        var source = mock(javax.sql.DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(false);
        Jdbi db = DsqlSupport.create(source);
        assertEquals("done", DsqlTransactions.run(db, 1, handle -> "done"));
        verify(connection).commit();
        verify(connection).close();
        verify(connection, never()).setTransactionIsolation(anyInt());
        verify(connection, never()).setSavepoint();
        verify(connection, never()).setSavepoint(anyString());
    }

    @Test
    void jsonValuesUseValueEqualityAndHashingForScopedBindings() {
        var first = new DsqlSupport.JsonValue("{}");
        var same = new DsqlSupport.JsonValue("{}");
        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, new DsqlSupport.JsonValue("[]"));
        assertNotEquals(first, "{}");
        assertNotEquals(first, null);
    }
}
