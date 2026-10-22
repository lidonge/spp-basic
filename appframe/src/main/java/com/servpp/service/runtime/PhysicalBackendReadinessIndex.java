package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record PhysicalBackendReadinessIndex(
        List<PhysicalBackendPlan> plans,
        Map<String, List<PhysicalBackendPlan>> byReadiness,
        Map<String, List<PhysicalBackendPlan>> byReadyReason,
        Map<String, List<PhysicalBackendPlan>> byMissingReason,
        Map<String, List<PhysicalBackendPlan>> byAction,
        Map<String, List<PhysicalBackendPlan>> byBackend,
        Map<String, List<PhysicalBackendPlan>> byImplementationType,
        Map<String, List<PhysicalBackendPlan>> byTransaction,
        Map<String, List<PhysicalBackendPlan>> byDatasource,
        Map<String, List<PhysicalBackendPlan>> byQuantumStore,
        Map<String, List<PhysicalBackendPlan>> byQuantumDatasourceCoordinationReadiness,
        Map<String, List<PhysicalBackendPlan>> byQuantumDatasourceCoordinationMissingReason,
        Map<String, List<PhysicalBackendPlan>> byQuantumDatasourceCoordinationAction,
        Map<String, List<PhysicalBackendPlan>> byQuantumDatasourceCoordinationContract,
        Map<String, List<PhysicalBackendPlan>> byBackendContract,
        Map<String, List<PhysicalBackendPlan>> byRuntimeContract) {
}
