package com.servpp.service.transaction;

final class NoopTccObjectStoreQuantumCoordinator implements TccObjectStoreQuantumCoordinator {
    static final NoopTccObjectStoreQuantumCoordinator INSTANCE = new NoopTccObjectStoreQuantumCoordinator();

    private NoopTccObjectStoreQuantumCoordinator() {
    }

    @Override
    public void commit(String storeName, TransactionContext context, QuantumEntanglementBoundary boundary) {
    }

    @Override
    public void reverse(String storeName, TransactionContext context, QuantumEntanglementBoundary reversalBoundary) {
    }

    @Override
    public boolean executable() {
        return false;
    }
}
