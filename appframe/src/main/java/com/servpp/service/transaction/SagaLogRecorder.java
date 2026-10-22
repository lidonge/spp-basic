package com.servpp.service.transaction;

import java.time.Instant;
import java.util.UUID;

public class SagaLogRecorder {
    private final SagaLogStore store;

    public SagaLogRecorder(SagaLogStore store) {
        this.store = store;
    }

    public SagaLogEntry append(String globalSerialNumber, String serviceName, String status, String message) {
        return append(globalSerialNumber, serviceName, status, message, null);
    }

    public SagaLogEntry append(String globalSerialNumber, String serviceName, String status, String message,
                               String recoveryPayload) {
        String sagaId = UUID.randomUUID().toString();
        TransactionContext context = DefaultTransactionManager.getLastContext();
        QuantumEntanglementBoundary boundary = context == null ? null : context.getQuantumEntanglementBoundary();
        SagaLogEntry entry = new SagaLogEntry(sagaId, globalSerialNumber, serviceName, status, message,
                context == null ? null : context.getId(),
                boundary == null ? null : boundary.getServiceId(),
                Instant.now().toString(),
                recoveryPayload);
        store.append(entry);
        return entry;
    }
}
