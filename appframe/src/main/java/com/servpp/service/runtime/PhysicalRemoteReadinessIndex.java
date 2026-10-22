package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record PhysicalRemoteReadinessIndex(
        List<PhysicalRemotePlan> plans,
        Map<String, List<PhysicalRemotePlan>> byReadiness,
        Map<String, List<PhysicalRemotePlan>> byReadyReason,
        Map<String, List<PhysicalRemotePlan>> byMissingReason,
        Map<String, List<PhysicalRemotePlan>> byAction,
        Map<String, List<PhysicalRemotePlan>> byService,
        Map<String, List<PhysicalRemotePlan>> byAdapterName,
        Map<String, List<PhysicalRemotePlan>> byAdapterRole,
        Map<String, List<PhysicalRemotePlan>> byEndpoint,
        Map<String, List<PhysicalRemotePlan>> byRepository,
        Map<String, List<PhysicalRemotePlan>> byImplementationKind,
        Map<String, List<PhysicalRemotePlan>> byImplementationContract,
        Map<String, List<PhysicalRemotePlan>> byRuntimeContract) {
}
