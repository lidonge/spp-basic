package com.servpp.service.transaction;

import java.util.Objects;

public class JournalQuantumAccessAdapter implements QuantumAccessAdapter {
    private final QuantumJournalStore journalStore;

    public JournalQuantumAccessAdapter(QuantumJournalStore journalStore) {
        this.journalStore = Objects.requireNonNull(journalStore, "journalStore");
    }

    @Override
    public boolean supportsInvocationBounds() { return journalStore.supportsInvocationBounds(); }

    @Override
    public void executeInAccessScope(Runnable action) {
        if (journalStore instanceof JdbcQuantumJournalStore jdbcStore) {
            jdbcStore.executeInTransaction(action);
            return;
        }
        action.run();
    }

    @Override
    public void commit(TransactionContext context, QuantumEntanglementBoundary boundary) {
        if (boundary == null) {
            return;
        }
        boundary.markCommitted();
        journalStore.append(context, boundary);
    }

    @Override
    public void reverse(TransactionContext context, QuantumEntanglementBoundary boundary) {
        if (boundary == null) {
            return;
        }
        if (journalStore instanceof JdbcQuantumJournalStore jdbcStore) jdbcStore.reverseDeltas(context,boundary);
        else journalStore.append(context, boundary.negativeDeltaReversal());
        boundary.markRolledBack();
    }

    public QuantumJournalStore getJournalStore() {
        return journalStore;
    }
}
