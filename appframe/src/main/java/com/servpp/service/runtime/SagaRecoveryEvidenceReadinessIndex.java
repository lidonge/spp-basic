package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record SagaRecoveryEvidenceReadinessIndex(
        List<SagaRecoveryEvidenceReview> plans,
        Map<String, List<SagaRecoveryEvidenceReview>> byReadiness,
        Map<String, List<SagaRecoveryEvidenceReview>> byReadyReason,
        Map<String, List<SagaRecoveryEvidenceReview>> byMissingReason,
        Map<String, List<SagaRecoveryEvidenceReview>> byAction,
        Map<String, List<SagaRecoveryEvidenceReview>> bySaga,
        Map<String, List<SagaRecoveryEvidenceReview>> byTargetService,
        Map<String, List<SagaRecoveryEvidenceReview>> byEvidenceRole,
        Map<String, List<SagaRecoveryEvidenceReview>> byEvidencePath,
        Map<String, List<SagaRecoveryEvidenceReview>> byObjectType,
        Map<String, List<SagaRecoveryEvidenceReview>> byRepository,
        Map<String, List<SagaRecoveryEvidenceReview>> bySnapshotBoundary,
        Map<String, List<SagaRecoveryEvidenceReview>> byRecoveryContract) {
}
