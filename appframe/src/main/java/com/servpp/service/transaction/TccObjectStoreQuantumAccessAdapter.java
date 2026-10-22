package com.servpp.service.transaction;

public class TccObjectStoreQuantumAccessAdapter implements QuantumAccessAdapter {
    private final String storeName;
    private final TccObjectStoreQuantumCoordinator coordinator;

    public TccObjectStoreQuantumAccessAdapter(String storeName) {
        this(storeName, TccObjectStoreQuantumCoordinator.noop());
    }

    public TccObjectStoreQuantumAccessAdapter(String storeName, TccObjectStoreQuantumCoordinator coordinator) {
        this.storeName = storeName == null ? "" : storeName.trim();
        if (this.storeName.isBlank()) {
            throw new IllegalArgumentException("TCC object store quantum access adapter requires a store name.");
        }
        this.coordinator = coordinator == null ? TccObjectStoreQuantumCoordinator.noop() : coordinator;
    }

    @Override
    public void commit(TransactionContext context, QuantumEntanglementBoundary boundary) {
        if (boundary != null) {
            coordinator.commit(storeName, context, boundary);
            boundary.markCommitted();
        }
    }

    @Override
    public void reverse(TransactionContext context, QuantumEntanglementBoundary boundary) {
        if (boundary != null) {
            coordinator.reverse(storeName, context, boundary.negativeDeltaReversal());
            boundary.markRolledBack();
        }
    }

    public String getStoreName() {
        return storeName;
    }

    public TccObjectStoreQuantumCoordinator getCoordinator() {
        return coordinator;
    }
}
