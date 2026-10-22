package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record RecoveryDeploymentReadinessIndex(
        List<RecoveryDeploymentPlan> plans,
        Map<String, List<RecoveryDeploymentPlan>> byReadiness,
        Map<String, List<RecoveryDeploymentPlan>> byReadyReason,
        Map<String, List<RecoveryDeploymentPlan>> byMissingReason,
        Map<String, List<RecoveryDeploymentPlan>> byAction,
        Map<String, List<RecoveryDeploymentPlan>> byPlan,
        Map<String, List<RecoveryDeploymentPlan>> byBackend,
        Map<String, List<RecoveryDeploymentPlan>> byDatasource,
        Map<String, List<RecoveryDeploymentPlan>> byWorkItemKind,
        Map<String, List<RecoveryDeploymentPlan>> byEvidenceSource,
        Map<String, List<RecoveryDeploymentPlan>> byUnifiedView) {
}
