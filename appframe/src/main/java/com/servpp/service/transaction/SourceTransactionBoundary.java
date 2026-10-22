package com.servpp.service.transaction;

import com.servpp.service.jdbc.JdbcTransactionScope;

/** Propagation belongs to source boundaries; resource strategy remains deployment metadata. */
public final class SourceTransactionBoundary {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    private SourceTransactionBoundary() { }
    private static final class Frame { volatile Throwable rollbackCause; }
    static Runnable bind(Runnable action) {
        var captured = CURRENT.get();
        return () -> {
            var previous = CURRENT.get();
            if (captured == null) CURRENT.remove(); else CURRENT.set(captured);
            try { action.run(); } finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
        };
    }

    public static void requireQuantumExecution() {
        TransactionMode mode = DeclaredTransactionContext.currentMode();
        if (CURRENT.get() == null || !DefaultTransactionManager.getInstance().isExecutingTransaction())
            throw new IllegalStateException("QUANTUM_SERVICE_REQUIRES_MANAGED_SOURCE_TRANSACTION");
        if (mode != TransactionMode.LOCAL && mode != TransactionMode.XA && mode != TransactionMode.AT && mode != TransactionMode.QUANTUM)
            throw new IllegalStateException("QUANTUM_CONSTRAINT_STRATEGY_UNSUPPORTED: " + mode);
    }

    public static void rejectCurrent(String code) {
        Frame frame = CURRENT.get();
        if (frame == null) throw new IllegalStateException("BREAK_TRANSACTION_REQUIRES_BOUNDARY");
        TransactionMode mode = DeclaredTransactionContext.currentMode();
        if (mode != TransactionMode.LOCAL && mode != TransactionMode.XA && mode != TransactionMode.AT && mode != TransactionMode.QUANTUM)
            throw new IllegalStateException("BUSINESS_ROLLBACK_STRATEGY_UNSUPPORTED: " + mode);
        BusinessTransactionRejection rejected = new BusinessTransactionRejection(code);
        if (frame.rollbackCause == null) frame.rollbackCause = rejected;
        throw rejected;
    }

    public static void assertCommittable() {
        Frame frame = CURRENT.get();
        if (frame != null && frame.rollbackCause instanceof BusinessTransactionRejection rejected) throw rejected;
        if (frame != null && frame.rollbackCause != null)
            throw new IllegalStateException("TRANSACTION_ROLLBACK_ONLY", frame.rollbackCause);
    }

    static void executeIndependent(Runnable action) {
        Frame previous = CURRENT.get(); CURRENT.remove();
        try { JdbcTransactionScope.suspend(() -> DefaultTransactionManager.getInstance().executeIndependently(action)); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
    /** A Saga stage commits separately; it cannot inherit a surrounding JDBC connection. */
    static void executeStage(TransactionMode mode, Runnable action) {
        executeIndependent(() -> TransactionOutcomes.observeIndependent(() -> execute(TransactionPropagation.REQUIRED,mode,action)));
    }

    public static void execute(TransactionPropagation propagation, TransactionMode mode, Runnable action) {
        Frame parent = CURRENT.get();
        boolean joined = parent != null && propagation == TransactionPropagation.REQUIRED;
        if (parent != null && (mode == TransactionMode.SAGA || mode == TransactionMode.TCC))
            throw new IllegalStateException("TRANSACTION_NESTED_STRATEGY_UNSUPPORTED: " + mode);
        if (parent != null && mode != DeclaredTransactionContext.currentMode() && joined)
            throw new IllegalStateException("TRANSACTION_JOIN_MODE_MISMATCH");
        // The supplied local implementation uses JDBC suspension/savepoints. Other adapters
        // must provide explicit propagation capabilities before these semantics can be selected.
        if (parent != null && propagation != TransactionPropagation.REQUIRED
                && (JdbcTransactionScope.currentConnections().isEmpty()
                    || (mode != TransactionMode.LOCAL && mode != TransactionMode.XA && mode != TransactionMode.AT && mode != TransactionMode.QUANTUM)
                    || DeclaredTransactionContext.currentMode() == TransactionMode.SAGA))
            throw new IllegalStateException("TRANSACTION_PROPAGATION_UNSUPPORTED: " + propagation);
        Frame frame = joined ? parent : new Frame();
        Runnable body = () -> {
            CURRENT.set(frame);
            try {
                DeclaredTransactionContext.execute(mode, action);
                if (parent == null || propagation == TransactionPropagation.REQUIRES_NEW) ScenarioCommitEvidence.committed();
            } catch (RuntimeException | Error failure) {
                if (frame.rollbackCause == null) frame.rollbackCause = failure;
                throw failure;
            } finally {
                if (parent == null) CURRENT.remove(); else CURRENT.set(parent);
            }
        };
        if (parent != null && propagation == TransactionPropagation.REQUIRES_NEW) {
            try { JdbcTransactionScope.suspend(() -> DefaultTransactionManager.getInstance().executeIndependently(body)); }
            catch (BusinessTransactionRejection rejected) { reportRejection(rejected); }
        } else if (parent != null && propagation == TransactionPropagation.NESTED) {
            try { JdbcTransactionScope.withSavepoints(body); }
            catch (RuntimeException | Error failure) {
                if (failure.getSuppressed().length > 0 || (failure.getMessage() != null
                        && failure.getMessage().startsWith("TRANSACTION_SAVEPOINT")))
                    parent.rollbackCause = failure;
                if (failure instanceof BusinessTransactionRejection rejected && failure.getSuppressed().length == 0)
                    reportRejection(rejected);
                else throw failure;
            }
        } else body.run();
    }
    private static void reportRejection(BusinessTransactionRejection rejected) {
        if (rejected.getSuppressed().length != 0) throw rejected;
        com.servpp.service.runtime.BusinessChecks.recordRejection(rejected.getMessage());
    }
}
