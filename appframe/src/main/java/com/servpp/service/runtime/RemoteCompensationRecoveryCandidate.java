package com.servpp.service.runtime;

import com.servpp.service.transaction.SagaLogEntry;

public record RemoteCompensationRecoveryCandidate(String sagaId,
                                                  String globalSerialNumber,
                                                  String serviceName,
                                                  String status,
                                                  String message,
                                                  String transactionId,
                                                  String createdAt,
                                                  boolean recoveryPayloadAvailable,
                                                  boolean manualInterventionRequired) {
    public static RemoteCompensationRecoveryCandidate from(SagaLogEntry entry) {
        return new RemoteCompensationRecoveryCandidate(
                entry.sagaId(),
                entry.globalSerialNumber(),
                entry.serviceName(),
                entry.status(),
                entry.message(),
                entry.transactionId(),
                entry.createdAt(),
                entry.recoveryPayload() != null && !entry.recoveryPayload().isBlank(),
                "REMOTE_COMPENSATION_MANUAL_REQUIRED".equals(entry.status()));
    }
}
