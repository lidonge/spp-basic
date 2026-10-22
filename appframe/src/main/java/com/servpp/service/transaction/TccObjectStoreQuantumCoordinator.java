package com.servpp.service.transaction;

public interface TccObjectStoreQuantumCoordinator {
    void commit(String storeName, TransactionContext context, QuantumEntanglementBoundary boundary);

    void reverse(String storeName, TransactionContext context, QuantumEntanglementBoundary reversalBoundary);

    default boolean executable() {
        return true;
    }

    default String implementationName() {
        return getClass().getName();
    }

    default String journalTableName() {
        return "";
    }

    default String valueTableName() {
        return "";
    }

    default void clear() {
    }

    static TccObjectStoreQuantumCoordinator noop() {
        return NoopTccObjectStoreQuantumCoordinator.INSTANCE;
    }
}
