package org.atomiteam.jdbi.generic.dao;

import org.jdbi.v3.core.*;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionFailureTest {
    @Test void optInRetryPolicyReplaysWholeUnitAndNeverCompletesTheFailedHandle() {
        Jdbi db=mock(Jdbi.class);Handle first=mock(Handle.class),second=mock(Handle.class);
        when(db.open()).thenReturn(first,second);
        AtomicInteger calls=new AtomicInteger();
        String result=DsqlTransactions.run(db,2,Set.of("23505"),
                failure->new IllegalArgumentException("mapped",failure),h->{
                    if(calls.incrementAndGet()==1)throw new SQLException("race","23505");
                    return "saved";
                });
        assertEquals("saved",result);assertEquals(2,calls.get());
        verify(first).rollback();verify(first,never()).commit();verify(first).close();
        verify(second).commit();verify(second).close();
    }
    @Test void uniqueAndConnectionFailuresAreNotRetriedByDefault() {
        for(String state:List.of("23505","08006","42501")) {
            Jdbi db=mock(Jdbi.class);Handle handle=mock(Handle.class);when(db.open()).thenReturn(handle);
            AtomicInteger calls=new AtomicInteger();
            assertThrows(IllegalStateException.class,()->DsqlTransactions.run(db,4,h->{
                calls.incrementAndGet();throw new SQLException("not retryable",state);
            }));
            assertEquals(1,calls.get());verify(db,times(1)).open();verify(handle).rollback();
        }
    }
    @Test void rollbackFailureIsSuppressedWithoutReplacingTheBusinessError() {
        Jdbi db=mock(Jdbi.class);Handle handle=mock(Handle.class);when(db.open()).thenReturn(handle);
        var business=new IllegalArgumentException("invalid");
        doThrow(new IllegalStateException("rollback")).when(handle).rollback();
        var failure=assertThrows(IllegalArgumentException.class,()->DsqlTransactions.run(db,3,h->{throw business;}));
        assertSame(business,failure);assertEquals(1,failure.getSuppressed().length);
        verify(handle).close();verify(db,times(1)).open();
    }
    @Test void checkedFailureUsesTheApplicationFactoryAndHonorsAttemptLimit() {
        Jdbi db=mock(Jdbi.class);Handle handle=mock(Handle.class);when(db.open()).thenReturn(handle);
        var checked=new Exception("checked");
        var failure=assertThrows(IllegalArgumentException.class,()->DsqlTransactions.run(db,1,Set.of(),
                cause->new IllegalArgumentException("application",cause),h->{throw checked;}));
        assertSame(checked,failure.getCause());
        assertThrows(IllegalArgumentException.class,()->DsqlTransactions.run(db,11,h->null));
    }
    @Test void interruptionStopsRetryAndRestoresTheInterruptFlag() {
        Jdbi db=mock(Jdbi.class);Handle handle=mock(Handle.class);when(db.open()).thenReturn(handle);
        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalStateException.class,()->DsqlTransactions.run(db,3,h->{
                throw new SQLException("conflict","40001");
            }));
            assertTrue(Thread.currentThread().isInterrupted());verify(db,times(1)).open();
        } finally {Thread.interrupted();}
    }
}
