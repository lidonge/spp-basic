package com.servpp.service.transaction;

public interface LogicalQuantumServiceClient {
    LogicalQuantumServiceResponse execute(LogicalQuantumServiceCommand command);

    /**
     * Read-only lookup of the durable result under a key: {@code GET /logical/commands/{ns}/{key}}.
     * A key that was never received answers NOT_RECEIVED with no boundaryResponse.
     */
    default LogicalQuantumServiceResponse query(String namespace, String requestKey) {
        throw new UnsupportedOperationException("This logical quantum client has no query.");
    }

    /** {@code GET /capabilities}; null when the client does not advertise, which keeps the local default. */
    default LogicalQuantumCapabilities capabilities() {
        return null;
    }
}
