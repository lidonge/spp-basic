package com.servpp.service.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

public final class JdbcTransactionScope {
    private static final ThreadLocal<Map<Connection, java.util.List<Runnable>>> AFTER_COMMIT =
            ThreadLocal.withInitial(IdentityHashMap::new);

    /** Technical follow-up work runs only after the owning JDBC scope has committed and unbound. */
    public static void afterCommit(DataSource source, Runnable action) {
        Connection connection = currentConnection(source).orElseThrow(
                () -> new IllegalStateException("AFTER_COMMIT_REQUIRES_JDBC_SCOPE"));
        AFTER_COMMIT.get().computeIfAbsent(connection, ignored -> new java.util.ArrayList<>()).add(action);
    }

    private static final ThreadLocal<Map<DataSource, Connection>> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<java.util.Set<Connection>> NESTED_RESOURCES = new ThreadLocal<>();
    private static void requireNestedResource(Connection connection) {
        var resources = NESTED_RESOURCES.get();
        if (resources != null && !resources.contains(connection))
            throw new IllegalStateException("TRANSACTION_NESTED_NEW_RESOURCE_UNSUPPORTED");
    }

    /** A borrowed connection is never committed by a worker; JDBC access shares one owner lock. */
    public static Runnable bindWorker(Runnable action) {
        var shared = CURRENT.get();
        var nested = NESTED_RESOURCES.get();
        var followups = AFTER_COMMIT.get();
        return () -> {
            Runnable bound = () -> {
                var previous = CURRENT.get(); var previousNested = NESTED_RESOURCES.get();
                var previousFollowups = AFTER_COMMIT.get();
                if (shared == null) CURRENT.remove(); else CURRENT.set(shared);
                if (nested == null) NESTED_RESOURCES.remove(); else NESTED_RESOURCES.set(nested);
                AFTER_COMMIT.set(followups);
                try { action.run(); }
                finally {
                    if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
                    if (previousNested == null) NESTED_RESOURCES.remove(); else NESTED_RESOURCES.set(previousNested);
                    AFTER_COMMIT.set(previousFollowups);
                }
            };
            if (shared == null) bound.run(); else synchronized (shared) { bound.run(); }
        };
    }

    private JdbcTransactionScope() {
    }

    public static Optional<Connection> currentConnection(DataSource dataSource) {
        Map<DataSource, Connection> scope = CURRENT.get();
        if (scope == null || !scope.containsKey(dataSource)) {
            return Optional.empty();
        }
        return Optional.of(scope.get(dataSource));
    }
    public static java.util.List<Connection> currentConnections() {
        Map<DataSource, Connection> scope = CURRENT.get();
        if (scope == null) return java.util.List.of();
        var unique = java.util.Collections.newSetFromMap(new IdentityHashMap<Connection, Boolean>());
        unique.addAll(scope.values());
        return java.util.List.copyOf(unique);
    }

    public static void suspend(Runnable action) {
        Map<DataSource, Connection> previous = CURRENT.get();
        var resources = NESTED_RESOURCES.get();
        CURRENT.remove(); NESTED_RESOURCES.remove();
        try { action.run(); }
        finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            if (resources == null) NESTED_RESOURCES.remove(); else NESTED_RESOURCES.set(resources);
        }
    }

    /** All resources must already belong to the enclosing JDBC boundary. */
    public static void withSavepoints(Runnable action) {
        var baselines = com.servpp.service.repository.RepositoryParticipantSnapshots.checkpoint();
        Map<Connection, java.sql.Savepoint> points = new IdentityHashMap<>();
        Map<Connection,Integer> callbacks = new IdentityHashMap<>();
        for (var entry : AFTER_COMMIT.get().entrySet()) callbacks.put(entry.getKey(),entry.getValue().size());
        Throwable primary = null;
        var previousResources = NESTED_RESOURCES.get();
        try {
            for (Connection connection : currentConnections()) points.put(connection, connection.setSavepoint());
            if (points.isEmpty()) throw new IllegalStateException("TRANSACTION_NESTED_REQUIRES_JDBC_SCOPE");
            NESTED_RESOURCES.set(points.keySet());
            action.run();
        } catch (SQLException | RuntimeException | Error failure) {
            primary = failure;
            com.servpp.service.repository.RepositoryParticipantSnapshots.restore(baselines);
            AFTER_COMMIT.get().forEach((connection,actions) -> {
                int retained = callbacks.getOrDefault(connection,0);
                if (actions.size()>retained) actions.subList(retained,actions.size()).clear();
            });
            for (var entry : points.entrySet()) {
                try { entry.getKey().rollback(entry.getValue()); }
                catch (SQLException | RuntimeException | Error undo) {
                    if (undo != failure) failure.addSuppressed(undo);
                    com.servpp.service.transaction.TransactionOutcomes.record(failure,
                        com.servpp.service.transaction.TransactionOutcomes.Outcome.UNKNOWN);
                }
            }
            if (failure instanceof SQLException sql) throw new IllegalStateException("TRANSACTION_SAVEPOINT_FAILED", sql);
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw (Error) failure;
        } finally {
            if (previousResources == null) NESTED_RESOURCES.remove(); else NESTED_RESOURCES.set(previousResources);
            for (var entry : points.entrySet()) {
                try { entry.getKey().releaseSavepoint(entry.getValue()); }
                catch (SQLException | RuntimeException | Error cleanup) {
                    if (primary != null) { if (cleanup != primary) primary.addSuppressed(cleanup); }
                    else throw new IllegalStateException("TRANSACTION_SAVEPOINT_RELEASE_FAILED", cleanup);
                }
            }
        }
    }

    /** Join a caller-owned transaction without committing, rolling back or closing its connection. */
    public static <T> T withConnection(DataSource dataSource, Connection connection, JdbcWork<T> work) throws SQLException {
        requireNestedResource(connection);
        if (connection.getAutoCommit()) throw new IllegalArgumentException("Caller-owned JDBC connection must be transactional");
        Optional<Connection> current = currentConnection(dataSource);
        if (current.isPresent()) {
            if (current.get() != connection) throw new IllegalStateException("A different JDBC connection is already bound");
            return work.execute(connection);
        }
        Map<DataSource, Connection> scope = CURRENT.get();
        if (scope == null) { scope = new IdentityHashMap<>(); CURRENT.set(scope); }
        scope.put(dataSource, connection);
        try { return work.execute(connection); }
        finally {
            scope.remove(dataSource);
            if (scope.isEmpty()) CURRENT.remove();
            // The external owner will decide commit; durable work can be replayed afterwards.
            AFTER_COMMIT.get().remove(connection);
            if (AFTER_COMMIT.get().isEmpty()) AFTER_COMMIT.remove();
        }
    }

    public static <T> T execute(DataSource dataSource, JdbcWork<T> work) throws SQLException {
        Optional<Connection> current = currentConnection(dataSource);
        if (current.isPresent()) {
            return work.execute(current.get());
        }
        if (NESTED_RESOURCES.get() != null)
            throw new IllegalStateException("TRANSACTION_NESTED_NEW_RESOURCE_UNSUPPORTED");
        var baselines = com.servpp.service.repository.RepositoryParticipantSnapshots.checkpoint();
        Connection connection = dataSource.getConnection();
        boolean autoCommit = true, transactionReady = false, bound = false, commitAttempted = false;
        Throwable primary = null;
        var outcome = com.servpp.service.transaction.TransactionOutcomes.Outcome.UNREPORTED;
        Map<DataSource, Connection> scope = CURRENT.get();
        try {
            autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false); transactionReady = true;
            if (scope == null) { scope = new IdentityHashMap<>(); CURRENT.set(scope); }
            scope.put(dataSource, connection); bound = true;
            T result = work.execute(connection);
            com.servpp.service.runtime.ExampleJdbcFaults.beforeCommit(connection);
            commitAttempted = true;
            com.servpp.service.runtime.ExampleJdbcFaults.commit(connection);
            outcome = com.servpp.service.transaction.TransactionOutcomes.Outcome.COMMITTED;
            // Repository reads and technical request bookkeeping also use JDBC scopes.
            // Only commits inside a declared business transaction are request-stage evidence.
            if (com.servpp.service.transaction.DeclaredTransactionContext.currentMode() != null)
                com.servpp.service.transaction.ScenarioCommitEvidence.committed();
            com.servpp.service.runtime.ExampleJdbcFaults.afterCommit(connection);
            return result;
        } catch (SQLException | RuntimeException | Error failure) {
            primary = failure;
            if (transactionReady && outcome != com.servpp.service.transaction.TransactionOutcomes.Outcome.COMMITTED) {
                outcome = com.servpp.service.transaction.TransactionOutcomes.Outcome.UNKNOWN;
                try {
                    connection.rollback();
                    if (!commitAttempted) outcome = com.servpp.service.transaction.TransactionOutcomes.Outcome.ROLLED_BACK;
                } catch (SQLException | RuntimeException | Error rollbackFailure) {
                    if (rollbackFailure != failure) failure.addSuppressed(rollbackFailure);
                }
            }
            if (outcome != com.servpp.service.transaction.TransactionOutcomes.Outcome.COMMITTED)
                com.servpp.service.repository.RepositoryParticipantSnapshots.restore(baselines);
            com.servpp.service.transaction.TransactionOutcomes.record(failure, outcome);
            throw failure;
        } finally {
            if (bound) { scope.remove(dataSource); if (scope.isEmpty()) CURRENT.remove(); }
            Throwable cleanup = null;
            if (transactionReady) {
                try { connection.setAutoCommit(autoCommit); }
                catch (SQLException | RuntimeException | Error failure) { cleanup = failure; }
            }
            try { connection.close(); }
            catch (SQLException | RuntimeException | Error failure) { if(cleanup==null) cleanup=failure; else if(cleanup!=failure) cleanup.addSuppressed(failure); }
            com.servpp.service.transaction.TransactionOutcomes.recordResource(dataSource, outcome);
            var followUps = AFTER_COMMIT.get().remove(connection);
            if (AFTER_COMMIT.get().isEmpty()) AFTER_COMMIT.remove();
            if (outcome == com.servpp.service.transaction.TransactionOutcomes.Outcome.COMMITTED && followUps != null) {
                for (Runnable followUp : followUps) {
                    try { followUp.run(); }
                    catch (RuntimeException | Error failure) {
                        if (cleanup == null) cleanup = failure;
                        else if (cleanup != failure) cleanup.addSuppressed(failure);
                    }
                }
            }
            if (cleanup != null) {
                if (primary != null) { if(primary!=cleanup) primary.addSuppressed(cleanup); }
                else {
                    com.servpp.service.transaction.TransactionOutcomes.record(cleanup, outcome);
                    if (cleanup instanceof SQLException sql) throw sql;
                    if (cleanup instanceof RuntimeException runtime) throw runtime;
                    throw (Error) cleanup;
                }
            }
        }
    }

    @FunctionalInterface
    public interface JdbcWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
