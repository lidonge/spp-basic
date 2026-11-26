package com.servpp.service.quantum.settlement;

import java.util.Optional;

/** One of four outcomes, never collapsed; the caller verifies that the identity echoes the request. */
public record SettlementResponse(SettlementKey key, Optional<String> originalRequestKey,
                                 Operation operation, Outcome outcome,
                                 Optional<String> receipt,
                                 Optional<String> failureReason) {
    public enum Operation { SETTLE, REVERSE, RESERVE, CONFIRM, CANCEL, QUERY }

    /**
     * SUCCESS: applied now, or already applied under this key.
     * BUSINESS_FAILED: a bound check failed and nothing changed.
     * NOT_RECEIVED: no execution fact was formed; a retry under the same key is allowed.
     * UNKNOWN: query or wait for recovery; never resend under a new key.
     */
    public enum Outcome { SUCCESS, BUSINESS_FAILED, NOT_RECEIVED, UNKNOWN }

    public SettlementResponse {
        key = Values.required(key, "key");
        originalRequestKey = Values.required(originalRequestKey, "originalRequestKey");
        operation = Values.required(operation, "operation");
        outcome = Values.required(outcome, "outcome");
        receipt = Values.required(receipt, "receipt");
        failureReason = Values.required(failureReason, "failureReason");
    }

    /** Verifies the echo of the request identity and operation. */
    public void requireIdentity(SettlementCommand command, Operation expected) {
        if (!key.equals(command.key())
                || !originalRequestKey.equals(command.originalRequestKey())
                || operation != expected) {
            throw new IllegalStateException("Settlement response identity mismatch");
        }
    }
}
