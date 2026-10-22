package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record RecoveryWorkItemReadinessIndex(
        List<RecoveryWorkItemPlan> plans,
        Map<String, List<RecoveryWorkItemPlan>> byReadiness,
        Map<String, List<RecoveryWorkItemPlan>> byReadyReason,
        Map<String, List<RecoveryWorkItemPlan>> byMissingReason,
        Map<String, List<RecoveryWorkItemPlan>> byAction,
        Map<String, List<RecoveryWorkItemPlan>> byWorkItemKind,
        Map<String, List<RecoveryWorkItemPlan>> bySourceService,
        Map<String, List<RecoveryWorkItemPlan>> byWorkItemSource,
        Map<String, List<RecoveryWorkItemPlan>> byManualEndpoint,
        Map<String, List<RecoveryWorkItemPlan>> bySchedulerPrefix,
        Map<String, List<RecoveryWorkItemPlan>> byBackend,
        Map<String, List<RecoveryWorkItemPlan>> byEvidenceStore,
        Map<String, List<RecoveryWorkItemPlan>> byExecutionContract,
        Map<String, List<RecoveryWorkItemPlan>> byPhysicalEvidencePath,
        Map<String, List<RecoveryWorkItemPlan>> bySupportedDecision,
        Map<String, List<RecoveryWorkItemPlan>> bySuggestedAction) {
}
