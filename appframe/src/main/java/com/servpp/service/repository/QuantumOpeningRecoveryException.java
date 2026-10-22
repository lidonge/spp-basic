package com.servpp.service.repository;

/** A durable owner-side opening task needs technical replay, not a business reverse transaction. */
public final class QuantumOpeningRecoveryException extends IllegalStateException {
    public QuantumOpeningRecoveryException(Throwable cause) {
        super("QUANTUM_OPENING_RECOVERY_PENDING", cause);
    }

}
