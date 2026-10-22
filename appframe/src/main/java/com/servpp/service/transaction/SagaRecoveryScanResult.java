package com.servpp.service.transaction;

import java.util.List;

public record SagaRecoveryScanResult(int candidateCount,
                                     int recoveredCount,
                                     int failedCount,
                                     List<String> errors) {
    public SagaRecoveryScanResult {
        candidateCount = Math.max(0, candidateCount);
        recoveredCount = Math.max(0, recoveredCount);
        failedCount = Math.max(0, failedCount);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static SagaRecoveryScanResult empty() {
        return new SagaRecoveryScanResult(0, 0, 0, List.of());
    }
}
