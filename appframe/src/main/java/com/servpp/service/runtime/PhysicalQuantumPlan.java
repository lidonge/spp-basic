package com.servpp.service.runtime;

import java.util.ArrayList;
import java.util.List;

public record PhysicalQuantumPlan(String storeName,
                                  String adapterName,
                                  String adapterRole,
                                  String implementationStrategy,
                                  String accessMode,
                                  String idempotencyMode,
                                  String reversalMode,
                                  String serviceProtocol,
                                  String endpoint,
                                  String timeoutMillis,
                                  List<String> attributeIds,
                                  String datasourceUrl,
                                  String driverClassName,
                                  String journalStoreName,
                                  String journalTableName,
                                  String valueTableName,
                                  boolean localJdbcQuantumStore,
                                  List<String> backendNames,
                                  List<String> backendAdapterNames,
                                  List<String> backendSupportedTransactions,
                                  String readiness,
                                  List<String> readyReasons,
                                  List<String> missingReasons,
                                  List<String> actions,
                                  String idempotencyOwner,
                                  String reversalOwner,
                                  String implementationContract,
                                  String runtimeContract) {
    public PhysicalQuantumPlan(String storeName,
                               String adapterName,
                               String adapterRole,
                               String implementationStrategy,
                               String accessMode,
                               String idempotencyMode,
                               String reversalMode,
                               String endpoint,
                               String timeoutMillis,
                               List<String> attributeIds,
                               String datasourceUrl,
                               String driverClassName,
                               String journalStoreName,
                               String journalTableName,
                               String valueTableName,
                               boolean localJdbcQuantumStore,
                               List<String> backendNames,
                               List<String> backendAdapterNames,
                               List<String> backendSupportedTransactions) {
        this(storeName, adapterName, adapterRole, implementationStrategy, accessMode,
                idempotencyMode, reversalMode,
                "quantumService".equals(adapterName) ? "HTTP_JSON" : "",
                endpoint, timeoutMillis, attributeIds, datasourceUrl, driverClassName,
                journalStoreName, journalTableName, valueTableName, localJdbcQuantumStore,
                backendNames, backendAdapterNames, backendSupportedTransactions);
    }

    public PhysicalQuantumPlan(String storeName,
                               String adapterName,
                               String adapterRole,
                               String implementationStrategy,
                               String accessMode,
                               String idempotencyMode,
                               String reversalMode,
                               String serviceProtocol,
                               String endpoint,
                               String timeoutMillis,
                               List<String> attributeIds,
                               String datasourceUrl,
                               String driverClassName,
                               String journalStoreName,
                               String journalTableName,
                               String valueTableName,
                               boolean localJdbcQuantumStore,
                               List<String> backendNames,
                               List<String> backendAdapterNames,
                               List<String> backendSupportedTransactions) {
        this(storeName,
                adapterName,
                adapterRole,
                implementationStrategy,
                accessMode,
                idempotencyMode,
                reversalMode,
                serviceProtocol,
                endpoint,
                timeoutMillis,
                attributeIds,
                datasourceUrl,
                driverClassName,
                journalStoreName,
                journalTableName,
                valueTableName,
                localJdbcQuantumStore,
                backendNames,
                backendAdapterNames,
                backendSupportedTransactions,
                "",
                List.of(),
                List.of(),
                List.of(),
                "",
                "",
                "",
                "");
    }

    public PhysicalQuantumPlan {
        storeName = normalize(storeName);
        adapterName = normalize(adapterName);
        adapterRole = normalize(adapterRole);
        implementationStrategy = normalize(implementationStrategy);
        accessMode = normalize(accessMode);
        idempotencyMode = normalize(idempotencyMode);
        reversalMode = normalize(reversalMode);
        serviceProtocol = normalize(serviceProtocol);
        if (serviceProtocol.isBlank() && "quantumService".equals(adapterName)) {
            serviceProtocol = "HTTP_JSON";
        }
        endpoint = normalize(endpoint);
        timeoutMillis = normalize(timeoutMillis);
        attributeIds = normalizeList(attributeIds);
        datasourceUrl = normalize(datasourceUrl);
        driverClassName = normalize(driverClassName);
        journalStoreName = normalize(journalStoreName);
        journalTableName = normalize(journalTableName);
        valueTableName = normalize(valueTableName);
        backendNames = normalizeList(backendNames);
        backendAdapterNames = normalizeList(backendAdapterNames);
        backendSupportedTransactions = normalizeList(backendSupportedTransactions);
        readiness = normalize(readiness);
        readyReasons = normalizeList(readyReasons);
        missingReasons = normalizeList(missingReasons);
        actions = normalizeList(actions);
        idempotencyOwner = normalize(idempotencyOwner);
        reversalOwner = normalize(reversalOwner);
        implementationContract = normalize(implementationContract);
        runtimeContract = normalize(runtimeContract);
        if (idempotencyOwner.isBlank()) {
            idempotencyOwner = idempotencyOwner(idempotencyMode);
        }
        if (reversalOwner.isBlank()) {
            reversalOwner = reversalOwner(implementationStrategy);
        }
        if (readiness.isBlank()) {
            readiness = missingReasons.isEmpty() ? "PHYSICAL_QUANTUM_READY" : "PHYSICAL_QUANTUM_BLOCKED";
        }
        if (missingReasons.isEmpty() && "PHYSICAL_QUANTUM_READY".equals(readiness)) {
            missingReasons = List.of("NO_MISSING_REASON");
        }
        if (actions.isEmpty()) {
            actions = "PHYSICAL_QUANTUM_READY".equals(readiness)
                    ? List.of("NO_ACTION_REQUIRED")
                    : List.of("resolvePhysicalQuantumReadiness");
        }
        if (implementationContract.isBlank()) {
            implementationContract = implementationContract(implementationStrategy, accessMode,
                    idempotencyOwner, reversalOwner);
        }
        if (runtimeContract.isBlank()) {
            runtimeContract = runtimeContract(storeName, implementationContract,
                    runtimeBinding(implementationStrategy, serviceProtocol, endpoint, datasourceUrl, backendNames),
                    readiness);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            normalized.add(normalize(value));
        }
        return List.copyOf(normalized);
    }

    private static String idempotencyOwner(String idempotencyMode) {
        if ("SERVICE_IDEMPOTENCY".equals(idempotencyMode)) {
            return "QUANTUM_SERVICE";
        }
        if ("OBJECT_STORE_IDEMPOTENCY".equals(idempotencyMode)) {
            return "OBJECT_STORE";
        }
        if ("FRAMEWORK_IDEMPOTENCY".equals(idempotencyMode)) {
            return "FRAMEWORK";
        }
        return metadataValue(idempotencyMode);
    }

    private static String reversalOwner(String implementationStrategy) {
        if ("QUANTUM_SERVICE".equals(implementationStrategy)) {
            return "QUANTUM_SERVICE";
        }
        if ("TCC_OBJECT_STORE".equals(implementationStrategy)) {
            return "OBJECT_STORE";
        }
        if ("GLOBAL_QUANTUM_STORE".equals(implementationStrategy)) {
            return "GLOBAL_QUANTUM_STORE";
        }
        return metadataValue(implementationStrategy);
    }

    private static String implementationContract(String implementationStrategy,
                                                 String accessMode,
                                                 String idempotencyOwner,
                                                 String reversalOwner) {
        return key(implementationStrategy)
                + "+" + key(accessMode)
                + "+" + key(idempotencyOwner)
                + "+" + key(reversalOwner);
    }

    private static String runtimeContract(String storeName,
                                          String implementationContract,
                                          String runtimeBinding,
                                          String readiness) {
        return key(storeName)
                + "->" + key(implementationContract)
                + "+" + key(runtimeBinding)
                + ":" + key(readiness);
    }

    private static String runtimeBinding(String implementationStrategy,
                                         String serviceProtocol,
                                         String endpoint,
                                         String datasourceUrl,
                                         List<String> backendNames) {
        if ("QUANTUM_SERVICE".equals(implementationStrategy)) {
            return "protocol=" + key(serviceProtocol) + "+endpoint=" + endpointKey(endpoint);
        }
        if ("GLOBAL_QUANTUM_STORE".equals(implementationStrategy)) {
            return "datasource=" + key(datasourceUrl);
        }
        if ("TCC_OBJECT_STORE".equals(implementationStrategy)) {
            return "backend=" + listKey(backendNames, "NO_BACKEND_BINDING");
        }
        if (endpoint != null && !endpoint.isBlank()) {
            return "endpoint=" + endpointKey(endpoint);
        }
        if (datasourceUrl != null && !datasourceUrl.isBlank()) {
            return "datasource=" + key(datasourceUrl);
        }
        return "backend=" + listKey(backendNames, "NO_BACKEND_BINDING");
    }

    private static String listKey(List<String> values, String emptyKey) {
        List<String> normalized = values == null
                ? List.of()
                : values.stream()
                        .map(PhysicalQuantumPlan::key)
                        .toList();
        return normalized.isEmpty() ? emptyKey : String.join("+", normalized);
    }

    private static String endpointKey(String endpoint) {
        return endpoint == null || endpoint.isBlank() ? "NO_ENDPOINT" : endpoint;
    }

    private static String key(String value) {
        return value == null || value.isBlank() ? "UNASSIGNED" : value;
    }

    private static String metadataValue(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
