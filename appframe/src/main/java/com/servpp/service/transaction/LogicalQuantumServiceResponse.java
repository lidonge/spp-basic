package com.servpp.service.transaction;

public record LogicalQuantumServiceResponse(
        String namespace,
        String requestKey,
        String originalRequestKey,
        BoundaryResponse boundaryResponse) {

    public enum Outcome {
        SUCCESS,
        BUSINESS_FAILED,
        NOT_RECEIVED,
        UNKNOWN
    }

    public record BoundaryResponse(
            long conservationID,
            Long originalConservationID,
            LogicalQuantumServiceCommand.Operation operation,
            Outcome status) {
    }

    public LogicalQuantumServiceResponse {
        if (boundaryResponse == null) {
            throw new IllegalArgumentException("boundaryResponse must not be null");
        }
    }

    public void requireIdentity(LogicalQuantumServiceCommand command) {
        if (!command.namespace().equals(namespace)
                || !command.requestKey().equals(requestKey)
                || !equal(command.originalRequestKey(), originalRequestKey)
                || command.operation() != boundaryResponse.operation()) {
            throw new IllegalStateException("QService logical response identity mismatch");
        }
    }

    private static boolean equal(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }
}
