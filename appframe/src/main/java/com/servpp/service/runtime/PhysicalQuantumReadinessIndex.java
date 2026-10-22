package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record PhysicalQuantumReadinessIndex(
        List<PhysicalQuantumPlan> plans,
        Map<String, List<PhysicalQuantumPlan>> byReadiness,
        Map<String, List<PhysicalQuantumPlan>> byReadyReason,
        Map<String, List<PhysicalQuantumPlan>> byMissingReason,
        Map<String, List<PhysicalQuantumPlan>> byAction,
        Map<String, List<PhysicalQuantumPlan>> byStore,
        Map<String, List<PhysicalQuantumPlan>> byImplementationStrategy,
        Map<String, List<PhysicalQuantumPlan>> byAccessMode,
        Map<String, List<PhysicalQuantumPlan>> byServiceProtocol,
        Map<String, List<PhysicalQuantumPlan>> byIdempotencyOwner,
        Map<String, List<PhysicalQuantumPlan>> byReversalOwner,
        Map<String, List<PhysicalQuantumPlan>> byImplementationContract,
        Map<String, List<PhysicalQuantumPlan>> byRuntimeContract,
        Map<String, List<PhysicalQuantumPlan>> byExternalImplementationReadiness,
        Map<String, List<PhysicalQuantumPlan>> byExternalImplementationReason,
        Map<String, List<PhysicalQuantumPlan>> byEndpoint,
        Map<String, List<PhysicalQuantumPlan>> byTimeoutMillis,
        Map<String, List<PhysicalQuantumPlan>> byDatasource,
        Map<String, List<PhysicalQuantumPlan>> byBackend) {
}
