package com.servpp.service.transaction;

public interface QuantumServiceClient {
    default boolean supportsInvocationBounds() { return false; }

    void commit(QuantumServiceCommitRequest request);

    void reverse(QuantumServiceReversalRequest request);
}
