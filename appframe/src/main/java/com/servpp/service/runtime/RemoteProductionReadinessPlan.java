package com.servpp.service.runtime;

import java.util.List;

public record RemoteProductionReadinessPlan(String serviceName,
                                            String adapterImplementationType,
                                            String adapterRole,
                                            boolean adapterAvailable,
                                            boolean adapterExecutable,
                                            boolean referenceImplementation,
                                            boolean externalImplementation,
                                            String adapterName,
                                            String endpoint,
                                            String timeoutMillis,
                                            boolean physicalPlanAvailable,
                                            boolean productionReady,
                                            String reason,
                                            String action,
                                            String readiness,
                                            List<String> readyReasons,
                                            List<String> missingReasons,
                                            List<String> actions,
                                            String implementationKind,
                                            String productionContract) {
    public RemoteProductionReadinessPlan(String serviceName,
                                         String adapterImplementationType,
                                         String adapterRole,
                                         boolean adapterAvailable,
                                         boolean adapterExecutable,
                                         boolean referenceImplementation,
                                         boolean externalImplementation,
                                         String adapterName,
                                         String endpoint,
                                         String timeoutMillis,
                                         boolean physicalPlanAvailable,
                                         boolean productionReady,
                                         String reason,
                                         String action) {
        this(serviceName,
                adapterImplementationType,
                adapterRole,
                adapterAvailable,
                adapterExecutable,
                referenceImplementation,
                externalImplementation,
                adapterName,
                endpoint,
                timeoutMillis,
                physicalPlanAvailable,
                productionReady,
                reason,
                action,
                productionReady ? "REMOTE_PRODUCTION_READY" : "REMOTE_PRODUCTION_NOT_READY",
                productionReady ? List.of("externalImplementation", "adapterAvailable",
                        "adapterExecutable", "externalEndpoint") : List.of(),
                productionReady ? List.of("NO_MISSING_REMOTE_PRODUCTION_REASON")
                        : List.of(reason == null || reason.isBlank() ? "unknown" : reason),
                action == null || action.isBlank() ? List.of() : List.of(action),
                implementationKind(referenceImplementation, externalImplementation),
                productionContract(serviceName, implementationKind(referenceImplementation, externalImplementation),
                        adapterRole, endpoint,
                        productionReady ? "REMOTE_PRODUCTION_READY" : "REMOTE_PRODUCTION_NOT_READY"));
    }

    public RemoteProductionReadinessPlan {
        readyReasons = readyReasons == null ? List.of() : List.copyOf(readyReasons);
        missingReasons = missingReasons == null ? List.of() : List.copyOf(missingReasons);
        actions = actions == null ? List.of() : List.copyOf(actions);
        if (actions.isEmpty() && action != null && !action.isBlank()) {
            actions = List.of(action);
        }
        if (readiness == null || readiness.isBlank()) {
            readiness = productionReady ? "REMOTE_PRODUCTION_READY" : "REMOTE_PRODUCTION_NOT_READY";
        }
        if (implementationKind == null || implementationKind.isBlank()) {
            implementationKind = implementationKind(referenceImplementation, externalImplementation);
        }
        if (productionContract == null || productionContract.isBlank()) {
            productionContract = productionContract(serviceName, implementationKind, adapterRole, endpoint, readiness);
        }
    }

    private static String implementationKind(boolean referenceImplementation, boolean externalImplementation) {
        if (referenceImplementation) {
            return "REFERENCE_IMPLEMENTATION";
        }
        if (externalImplementation) {
            return "EXTERNAL_IMPLEMENTATION";
        }
        return "CUSTOM_REMOTE_IMPLEMENTATION";
    }

    private static String productionContract(String serviceName,
                                             String implementationKind,
                                             String adapterRole,
                                             String endpoint,
                                             String readiness) {
        return key(serviceName)
                + "->" + key(implementationKind)
                + "+" + key(adapterRole)
                + "+endpoint=" + endpointKey(endpoint)
                + ":" + key(readiness);
    }

    private static String endpointKey(String endpoint) {
        return endpoint == null || endpoint.isBlank() ? "NO_ENDPOINT" : endpoint;
    }

    private static String key(String value) {
        return value == null || value.isBlank() ? "UNASSIGNED" : value;
    }
}
