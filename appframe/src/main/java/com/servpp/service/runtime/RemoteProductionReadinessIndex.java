package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record RemoteProductionReadinessIndex(
        List<RemoteProductionReadinessPlan> plans,
        Map<String, List<RemoteProductionReadinessPlan>> byReadiness,
        Map<String, List<RemoteProductionReadinessPlan>> byReadyReason,
        Map<String, List<RemoteProductionReadinessPlan>> byMissingReason,
        Map<String, List<RemoteProductionReadinessPlan>> byReason,
        Map<String, List<RemoteProductionReadinessPlan>> byAction,
        Map<String, List<RemoteProductionReadinessPlan>> byAdapterRole,
        Map<String, List<RemoteProductionReadinessPlan>> byImplementationType,
        Map<String, List<RemoteProductionReadinessPlan>> byImplementationKind,
        Map<String, List<RemoteProductionReadinessPlan>> byEndpoint,
        Map<String, List<RemoteProductionReadinessPlan>> byService,
        Map<String, List<RemoteProductionReadinessPlan>> byProductionContract) {
}
