package org.atomiteam.jdbi.generic.dao;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

/** Bounded, whole-transaction retry for DSQL serialization conflicts (SQLSTATE 40001). */
public final class DsqlTransactions {
    private DsqlTransactions() {}
    @FunctionalInterface public interface Work<T> { T run(Handle handle) throws Exception; }

    /** Callback must be replayable: no email, S3 operations, or other external side effects. */
    public static <T> T run(Jdbi jdbi, int maxAttempts, Work<T> work) {
        Objects.requireNonNull(jdbi); Objects.requireNonNull(work);
        if (maxAttempts < 1 || maxAttempts > 10) throw new IllegalArgumentException("maxAttempts must be 1..10");
        for (int attempt = 1; ; attempt++) {
            try (Handle handle = jdbi.open()) {
                // No isolation setter or savepoints: DSQL uses fixed REPEATABLE READ.
                handle.begin();
                try {
                    T result = work.run(handle);
                    handle.commit();
                    return result;
                } catch (Exception failure) {
                    try { handle.rollback(); } catch (Exception rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                    throw failure;
                }
            } catch (Exception failure) {
                if (attempt >= maxAttempts || !isConflict(failure)) {
                    if (failure instanceof RuntimeException) throw (RuntimeException) failure;
                    throw new IllegalStateException("DSQL transaction failed", failure);
                }
                try { Thread.sleep(ThreadLocalRandom.current().nextLong(10L << (attempt - 1), (20L << (attempt - 1)) + 1)); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("DSQL retry interrupted", interrupted);
                }
            }
        }
    }

    private static boolean isConflict(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (failure != null && seen.add(failure)) {
            if (failure instanceof SQLException && "40001".equals(((SQLException) failure).getSQLState())) return true;
            failure = failure.getCause();
        }
        return false;
    }
}
