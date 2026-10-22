package com.servpp.service.transaction;

import com.servpp.service.scenario.IExecutor;
import java.util.List;

/** Enlists conditionally selected atomic updates within the same source scenario boundary. */
public final class SourceTransactionUpdates {
    private SourceTransactionUpdates() {}
    /** Request optional scheduling; the basic runtime executes the same updates serially. */
    public static void executeParallel(List<IExecutor> executors) {
        for (var executor : executors) requireMode(executor);
        DefaultTransactionManager.getInstance().execute(executors, true);
    }
    public static void execute(IExecutor executor) {
        requireMode(executor);
        DefaultTransactionManager.getInstance().execute(List.of(executor));
    }
    private static void requireMode(IExecutor executor) {
        TransactionMode mode = DeclaredTransactionContext.currentMode();
        if (mode != TransactionMode.LOCAL && mode != TransactionMode.XA && mode != TransactionMode.AT && mode != TransactionMode.QUANTUM) throw new IllegalStateException("UNSUPPORTED_SOURCE_CONDITIONAL_TRANSACTION_MODE");
        if (!(executor.getService() instanceof TransactionalService service) || service.getTransactionMode() != mode) {
            throw new IllegalStateException("SOURCE_UPDATE_TRANSACTION_MODE_MISMATCH");
        }
    }
}
