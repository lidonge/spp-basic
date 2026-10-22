package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record ObjectSnapshotClosureIndex(
        List<ObjectSnapshotPlan> plans,
        Map<String, List<ObjectSnapshotPlan>> byReadiness,
        Map<String, List<ObjectSnapshotPlan>> byReadyReason,
        Map<String, List<ObjectSnapshotPlan>> byMissingReason,
        Map<String, List<ObjectSnapshotPlan>> byAction,
        Map<String, List<ObjectSnapshotPlan>> byService,
        Map<String, List<ObjectSnapshotPlan>> byObjectType,
        Map<String, List<ObjectSnapshotPlan>> bySnapshotPath,
        Map<String, List<ObjectSnapshotPlan>> byBoundary,
        Map<String, List<ObjectSnapshotPlan>> byRecoveryRole,
        Map<String, List<ObjectSnapshotPlan>> byRepository,
        Map<String, List<ObjectSnapshotPlan>> byObjectKeySource,
        Map<String, List<ObjectSnapshotPlan>> byObjectKeyColumnSet,
        Map<String, List<ObjectSnapshotPlan>> byClosureContract,
        Map<String, List<ObjectSnapshotPlan>> byStorageTable) {
}
