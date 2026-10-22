package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record SagaTransactionRecoveryClosureIndex(
        List<SagaTransactionCallPlan> plans,
        Map<String, List<SagaTransactionCallPlan>> byReadiness,
        Map<String, List<SagaTransactionCallPlan>> byReadyReason,
        Map<String, List<SagaTransactionCallPlan>> byMissingReason,
        Map<String, List<SagaTransactionCallPlan>> byAction,
        Map<String, List<SagaTransactionCallPlan>> bySaga,
        Map<String, List<SagaTransactionCallPlan>> byTargetService,
        Map<String, List<SagaTransactionCallPlan>> byCall,
        Map<String, List<SagaTransactionCallPlan>> byTargetTransactionMode,
        Map<String, List<SagaTransactionCallPlan>> byRecoveryContract,
        Map<String, List<SagaTransactionCallPlan>> byEvidenceObjectType,
        Map<String, List<SagaTransactionCallPlan>> byImplementationStrategy,
        Map<String, List<SagaTransactionCallPlan>> byRecoveryBoundary,
        Map<String, List<SagaTransactionCallPlan>> byRecoveryOwner,
        Map<String, List<SagaTransactionCallPlan>> byObjectSnapshotResponsibility) {
    public SagaTransactionRecoveryClosureIndex(
            List<SagaTransactionCallPlan> plans,
            Map<String, List<SagaTransactionCallPlan>> byReadiness,
            Map<String, List<SagaTransactionCallPlan>> byReadyReason,
            Map<String, List<SagaTransactionCallPlan>> byMissingReason,
            Map<String, List<SagaTransactionCallPlan>> byAction,
            Map<String, List<SagaTransactionCallPlan>> bySaga,
            Map<String, List<SagaTransactionCallPlan>> byTargetService,
            Map<String, List<SagaTransactionCallPlan>> byCall,
            Map<String, List<SagaTransactionCallPlan>> byTargetTransactionMode,
            Map<String, List<SagaTransactionCallPlan>> byRecoveryContract,
            Map<String, List<SagaTransactionCallPlan>> byEvidenceObjectType) {
        this(plans,
                byReadiness,
                byReadyReason,
                byMissingReason,
                byAction,
                bySaga,
                byTargetService,
                byCall,
                byTargetTransactionMode,
                byRecoveryContract,
                byEvidenceObjectType,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of());
    }
}
