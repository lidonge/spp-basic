package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record PhysicalRepositoryReadinessIndex(
        List<PhysicalRepositoryPlan> plans,
        Map<String, List<PhysicalRepositoryPlan>> byReadiness,
        Map<String, List<PhysicalRepositoryPlan>> byReadyReason,
        Map<String, List<PhysicalRepositoryPlan>> byMissingReason,
        Map<String, List<PhysicalRepositoryPlan>> byAction,
        Map<String, List<PhysicalRepositoryPlan>> byRepository,
        Map<String, List<PhysicalRepositoryPlan>> byAdapterRole,
        Map<String, List<PhysicalRepositoryPlan>> byAppDdlTable,
        Map<String, List<PhysicalRepositoryPlan>> byStorageTable,
        Map<String, List<PhysicalRepositoryPlan>> byDatasource,
        Map<String, List<PhysicalRepositoryPlan>> byRepositoryContract,
        Map<String, List<PhysicalRepositoryPlan>> byRuntimeContract) {
}
