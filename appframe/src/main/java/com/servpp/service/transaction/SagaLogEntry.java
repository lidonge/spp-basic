package com.servpp.service.transaction;

public record SagaLogEntry(String sagaId, String globalSerialNumber, String serviceName,
                           String status, String message, String transactionId,
                           String quantumBoundaryServiceId, String createdAt,
                           String recoveryPayload) {
    public SagaLogEntry(String sagaId, String globalSerialNumber, String serviceName,
                        String status, String message, String transactionId,
                        String quantumBoundaryServiceId, String createdAt) {
        this(sagaId, globalSerialNumber, serviceName, status, message, transactionId,
                quantumBoundaryServiceId, createdAt, null);
    }
}
