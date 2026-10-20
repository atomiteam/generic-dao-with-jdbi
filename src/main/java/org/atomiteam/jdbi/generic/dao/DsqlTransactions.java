package org.atomiteam.jdbi.generic.dao;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/** Bounded whole-transaction retries on fresh handles; no external effects in callbacks. */
public final class DsqlTransactions {
    private DsqlTransactions() {}

    @FunctionalInterface
    public interface Work<T> {
        T run(Handle handle) throws Exception;
    }

    /** Default policy retries only serialization conflicts, including commit-time conflicts. */
    public static <T> T run(Jdbi jdbi, int maxAttempts, Work<T> work) {
        return run(
                jdbi,
                maxAttempts,
                Collections.singleton("40001"),
                failure -> new IllegalStateException("DSQL transaction failed", failure),
                work);
    }

    /**
     * Application-owned retry states must be justified by replay/idempotency semantics. In
     * particular, unique violations are not retryable by default.
     */
    public static <T> T run(
            Jdbi jdbi,
            int maxAttempts,
            Set<String> retryableStates,
            Function<Throwable, RuntimeException> failureFactory,
            Work<T> work) {
        Objects.requireNonNull(jdbi);
        Objects.requireNonNull(work);
        Objects.requireNonNull(failureFactory);
        Set<String> states = Collections.unmodifiableSet(new HashSet<>(retryableStates));
        if (maxAttempts < 1 || maxAttempts > 10)
            throw new IllegalArgumentException("maxAttempts must be 1..10");
        for (int attempt = 1; ; attempt++) {
            try (Handle handle = jdbi.open()) {
                // No isolation setter or savepoints: DSQL uses fixed REPEATABLE READ.
                handle.begin();
                T result;
                try {
                    result = work.run(handle);
                } catch (Exception failure) {
                    try {
                        handle.rollback();
                    } catch (Exception rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                    throw failure;
                }
                // JDBI rolls back a failed commit itself. Never roll back that handle twice.
                handle.commit();
                return result;
            } catch (Exception failure) {
                SQLException sql = sqlFailure(failure);
                if (sql == null) {
                    if (failure instanceof RuntimeException) throw (RuntimeException) failure;
                    throw failureFactory.apply(failure);
                }
                if (attempt >= maxAttempts || !states.contains(sql.getSQLState()))
                    throw failureFactory.apply(sql);
                try {
                    Thread.sleep(
                            ThreadLocalRandom.current()
                                    .nextLong(10L << (attempt - 1), (20L << (attempt - 1)) + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Write interrupted", interrupted);
                }
            }
        }
    }

    private static SQLException sqlFailure(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (failure != null && seen.add(failure)) {
            if (failure instanceof SQLException) return (SQLException) failure;
            failure = failure.getCause();
        }
        return null;
    }
}
