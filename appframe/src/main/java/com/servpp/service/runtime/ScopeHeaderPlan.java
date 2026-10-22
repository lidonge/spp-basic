package com.servpp.service.runtime;

import java.util.List;

public record ScopeHeaderPlan(String scopeName,
                              boolean localScope,
                              boolean remoteScope,
                              String inputHeaderName,
                              String outputHeaderName,
                              List<String> serviceNames,
                              List<String> transactionModes,
                              List<String> transactionServiceNames,
                              List<String> physicalBindingRequiredServiceNames,
                              List<String> physicalBindingAvailableServiceNames,
                              List<String> physicalBackendNames,
                              List<String> physicalBackendAdapterNames,
                              List<String> physicalBackendDatasourceUrls,
                              List<String> physicalBackendDriverClassNames,
                              List<String> physicalBindingBindings,
                              List<String> idempotencyRequiredServiceNames,
                              List<String> idempotencyAvailableServiceNames,
                              List<String> idempotencyRuntimeStoreAvailableServiceNames,
                              List<String> idempotencyBackendNames,
                              List<String> idempotencyTableNames,
                              List<String> idempotencyDatasourceUrls,
                              List<String> idempotencyDriverClassNames,
                              List<String> idempotencyBindings,
                              String readiness,
                              List<String> readyReasons,
                              List<String> missingReasons,
                              List<String> actions,
                              String scopeKind,
                              String boundary,
                              String physicalBindingReadiness,
                              String idempotencyReadiness) {
    public ScopeHeaderPlan {
        serviceNames = serviceNames == null ? List.of() : List.copyOf(serviceNames);
        transactionModes = transactionModes == null ? List.of() : List.copyOf(transactionModes);
        transactionServiceNames = transactionServiceNames == null ? List.of() : List.copyOf(transactionServiceNames);
        physicalBindingRequiredServiceNames = physicalBindingRequiredServiceNames == null
                ? List.of()
                : List.copyOf(physicalBindingRequiredServiceNames);
        physicalBindingAvailableServiceNames = physicalBindingAvailableServiceNames == null
                ? List.of()
                : List.copyOf(physicalBindingAvailableServiceNames);
        physicalBackendNames = physicalBackendNames == null ? List.of() : List.copyOf(physicalBackendNames);
        physicalBackendAdapterNames = physicalBackendAdapterNames == null
                ? List.of()
                : List.copyOf(physicalBackendAdapterNames);
        physicalBackendDatasourceUrls = physicalBackendDatasourceUrls == null
                ? List.of()
                : List.copyOf(physicalBackendDatasourceUrls);
        physicalBackendDriverClassNames = physicalBackendDriverClassNames == null
                ? List.of()
                : List.copyOf(physicalBackendDriverClassNames);
        physicalBindingBindings = physicalBindingBindings == null
                ? List.of()
                : List.copyOf(physicalBindingBindings);
        idempotencyRequiredServiceNames = idempotencyRequiredServiceNames == null
                ? List.of()
                : List.copyOf(idempotencyRequiredServiceNames);
        idempotencyAvailableServiceNames = idempotencyAvailableServiceNames == null
                ? List.of()
                : List.copyOf(idempotencyAvailableServiceNames);
        idempotencyRuntimeStoreAvailableServiceNames = idempotencyRuntimeStoreAvailableServiceNames == null
                ? List.of()
                : List.copyOf(idempotencyRuntimeStoreAvailableServiceNames);
        idempotencyBackendNames = idempotencyBackendNames == null ? List.of() : List.copyOf(idempotencyBackendNames);
        idempotencyTableNames = idempotencyTableNames == null ? List.of() : List.copyOf(idempotencyTableNames);
        idempotencyDatasourceUrls = idempotencyDatasourceUrls == null
                ? List.of()
                : List.copyOf(idempotencyDatasourceUrls);
        idempotencyDriverClassNames = idempotencyDriverClassNames == null
                ? List.of()
                : List.copyOf(idempotencyDriverClassNames);
        idempotencyBindings = idempotencyBindings == null ? List.of() : List.copyOf(idempotencyBindings);
        readyReasons = readyReasons == null ? List.of() : List.copyOf(readyReasons);
        missingReasons = missingReasons == null ? List.of() : List.copyOf(missingReasons);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public ScopeHeaderPlan(String scopeName,
                           boolean localScope,
                           boolean remoteScope,
                           String inputHeaderName,
                           String outputHeaderName,
                           List<String> serviceNames,
                           List<String> transactionModes,
                           List<String> transactionServiceNames,
                           List<String> physicalBindingRequiredServiceNames,
                           List<String> physicalBindingAvailableServiceNames,
                           List<String> physicalBackendNames,
                           List<String> physicalBackendAdapterNames,
                           List<String> physicalBackendDatasourceUrls,
                           List<String> physicalBackendDriverClassNames,
                           List<String> physicalBindingBindings,
                           List<String> idempotencyRequiredServiceNames,
                           List<String> idempotencyAvailableServiceNames,
                           List<String> idempotencyRuntimeStoreAvailableServiceNames,
                           List<String> idempotencyBackendNames,
                           List<String> idempotencyTableNames,
                           List<String> idempotencyDatasourceUrls,
                           List<String> idempotencyDriverClassNames,
                           List<String> idempotencyBindings) {
        this(scopeName, localScope, remoteScope, inputHeaderName, outputHeaderName,
                serviceNames, transactionModes, transactionServiceNames,
                physicalBindingRequiredServiceNames, physicalBindingAvailableServiceNames,
                physicalBackendNames, physicalBackendAdapterNames, physicalBackendDatasourceUrls,
                physicalBackendDriverClassNames, physicalBindingBindings,
                idempotencyRequiredServiceNames, idempotencyAvailableServiceNames,
                idempotencyRuntimeStoreAvailableServiceNames, idempotencyBackendNames,
                idempotencyTableNames, idempotencyDatasourceUrls, idempotencyDriverClassNames,
                idempotencyBindings, null, List.of(), List.of(), List.of(), null, null, null, null);
    }
}
