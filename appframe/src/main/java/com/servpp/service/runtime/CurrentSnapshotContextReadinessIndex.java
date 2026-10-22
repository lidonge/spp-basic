package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record CurrentSnapshotContextReadinessIndex(
        List<CurrentSnapshotContextPlan> plans,
        Map<String, List<CurrentSnapshotContextPlan>> byReadiness,
        Map<String, List<CurrentSnapshotContextPlan>> byReadyReason,
        Map<String, List<CurrentSnapshotContextPlan>> byMissingReason,
        Map<String, List<CurrentSnapshotContextPlan>> byAction,
        Map<String, List<CurrentSnapshotContextPlan>> bySaga,
        Map<String, List<CurrentSnapshotContextPlan>> byTargetService,
        Map<String, List<CurrentSnapshotContextPlan>> byObjectType,
        Map<String, List<CurrentSnapshotContextPlan>> byContextPath,
        Map<String, List<CurrentSnapshotContextPlan>> byContextObjectSource,
        Map<String, List<CurrentSnapshotContextPlan>> byResolution,
        Map<String, List<CurrentSnapshotContextPlan>> byKeyPushdown,
        Map<String, List<CurrentSnapshotContextPlan>> byObjectKeySource,
        Map<String, List<CurrentSnapshotContextPlan>> byObjectKeyColumnSet,
        Map<String, List<CurrentSnapshotContextPlan>> byClosureContract,
        Map<String, List<CurrentSnapshotContextPlan>> byAppDdlIndex) {
}
