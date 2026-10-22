package com.servpp.service.transaction;

public interface QuantumAccessAdapter {
    /** True only when the implementation enforces caller intervals as part of atomic settlement. */
    default boolean supportsInvocationBounds() { return false; }

    default void executeInAccessScope(Runnable action) {
        action.run();
    }

    void commit(TransactionContext context, QuantumEntanglementBoundary boundary);

    default void reverse(TransactionContext context, QuantumEntanglementBoundary boundary) {
        throw new UnsupportedOperationException("Quantum reversal is not supported by this access adapter.");
    }

    default void rollback(TransactionContext context, QuantumEntanglementBoundary boundary) {
        if (boundary != null) {
            boundary.markRolledBack();
        }
    }
}
