package com.servpp.service.transaction;

import java.util.List;

public interface QuantumJournalStore {
    default boolean supportsInvocationBounds() { return false; }

    void append(TransactionContext context, QuantumEntanglementBoundary boundary);

    List<QuantumJournalEntry> findByTransactionId(String transactionId);

    default void clear() {
    }
}
