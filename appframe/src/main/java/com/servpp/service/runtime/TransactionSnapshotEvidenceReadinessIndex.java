package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record TransactionSnapshotEvidenceReadinessIndex(
        List<TransactionSnapshotPlan> plans,
        Map<String, List<TransactionSnapshotPlan>> byReadiness,
        Map<String, List<TransactionSnapshotPlan>> byReadyReason,
        Map<String, List<TransactionSnapshotPlan>> byMissingReason,
        Map<String, List<TransactionSnapshotPlan>> byAction,
        Map<String, List<TransactionSnapshotPlan>> byService,
        Map<String, List<TransactionSnapshotPlan>> byResource,
        Map<String, List<TransactionSnapshotPlan>> byRepository,
        Map<String, List<TransactionSnapshotPlan>> byObjectType,
        Map<String, List<TransactionSnapshotPlan>> byEvidencePath,
        Map<String, List<TransactionSnapshotPlan>> byLatestTable,
        Map<String, List<TransactionSnapshotPlan>> byHistoryTable,
        Map<String, List<TransactionSnapshotPlan>> byArchiveTable,
        Map<String, List<TransactionSnapshotPlan>> byExecutionContract) {
}
