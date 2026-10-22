package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record ObjectRepositoryReadinessIndex(
        List<ObjectRepositoryPlan> plans,
        Map<String, List<ObjectRepositoryPlan>> byReadiness,
        Map<String, List<ObjectRepositoryPlan>> byReadyReason,
        Map<String, List<ObjectRepositoryPlan>> byMissingReason,
        Map<String, List<ObjectRepositoryPlan>> byAction,
        Map<String, List<ObjectRepositoryPlan>> byService,
        Map<String, List<ObjectRepositoryPlan>> byRepository,
        Map<String, List<ObjectRepositoryPlan>> byObjectType,
        Map<String, List<ObjectRepositoryPlan>> byRole,
        Map<String, List<ObjectRepositoryPlan>> byKeySource,
        Map<String, List<ObjectRepositoryPlan>> byObjectKeyColumnSet,
        Map<String, List<ObjectRepositoryPlan>> byStorageTable,
        Map<String, List<ObjectRepositoryPlan>> byRuntimeContract) {
}
