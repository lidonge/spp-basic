package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record TransactionSnapshotRetentionExecutionReadinessIndex(
        List<TransactionSnapshotRetentionExecutionPlan> plans,
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byReadiness,
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byReadyReason,
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byMissingReason,
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byAction,
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byResource,
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byRepository,
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byAutomationPlan,
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> bySchedulerPrefix,
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byRetentionSource,
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byArchiveRetentionSource,
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byArchiveOperation,
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byPruneArchiveOperation,
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byStorageTier,
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byExecutionContract) {
}
