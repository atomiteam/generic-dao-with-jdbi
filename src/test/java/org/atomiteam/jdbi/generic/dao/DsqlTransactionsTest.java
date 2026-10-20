package org.atomiteam.jdbi.generic.dao;

import static org.junit.jupiter.api.Assertions.*;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class DsqlTransactionsTest {
    @Test
    void commitTimeSerializationFailureRetriesOnAFreshHandle() {
        Jdbi db = org.mockito.Mockito.mock(Jdbi.class);
        org.jdbi.v3.core.Handle first = org.mockito.Mockito.mock(org.jdbi.v3.core.Handle.class);
        org.jdbi.v3.core.Handle second = org.mockito.Mockito.mock(org.jdbi.v3.core.Handle.class);
        org.mockito.Mockito.when(db.open()).thenReturn(first, second);
        org.mockito.Mockito.when(first.commit())
                .thenThrow(new IllegalStateException(new SQLException("OC000", "40001")));
        AtomicInteger calls = new AtomicInteger();
        int result = DsqlTransactions.run(db, 3, h -> calls.incrementAndGet());
        assertEquals(2, result);
        org.mockito.Mockito.verify(first, org.mockito.Mockito.never()).rollback();
        org.mockito.Mockito.verify(first).close();
        org.mockito.Mockito.verify(second).commit();
        org.mockito.Mockito.verify(second).close();
        org.mockito.Mockito.verify(second, org.mockito.Mockito.never()).rollback();
    }

    @Test
    void conflictRollsBackAndRetriesWholeCallbackButOtherFailuresDoNot() {
        Jdbi db = Jdbi.create("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        db.useHandle(h -> h.execute("CREATE TABLE effects(id int primary key)"));
        AtomicInteger calls = new AtomicInteger();
        int result =
                DsqlTransactions.run(
                        db,
                        3,
                        h -> {
                            h.execute("INSERT INTO effects VALUES(1)");
                            if (calls.incrementAndGet() == 1)
                                throw new SQLException("OC000", "40001");
                            return 7;
                        });
        assertEquals(7, result);
        assertEquals(2, calls.get());
        assertEquals(
                1,
                db.withHandle(
                                h ->
                                        h.createQuery("SELECT count(*) FROM effects")
                                                .mapTo(Integer.class)
                                                .one())
                        .intValue());
        calls.set(0);
        assertThrows(
                IllegalStateException.class,
                () ->
                        DsqlTransactions.run(
                                db,
                                3,
                                h -> {
                                    calls.incrementAndGet();
                                    throw new SQLException("denied", "42501");
                                }));
        assertEquals(1, calls.get());
        calls.set(0);
        assertThrows(
                IllegalStateException.class,
                () ->
                        DsqlTransactions.run(
                                db,
                                2,
                                h -> {
                                    calls.incrementAndGet();
                                    throw new SQLException("OC001", "40001");
                                }));
        assertEquals(2, calls.get());
        assertThrows(IllegalArgumentException.class, () -> DsqlTransactions.run(db, 0, h -> null));
    }
}
