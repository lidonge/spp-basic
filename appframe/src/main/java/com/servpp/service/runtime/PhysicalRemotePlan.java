package com.servpp.service.runtime;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public record PhysicalRemotePlan(String serviceName,
                                 String adapterName,
                                 String adapterRole,
                                 boolean adapterExecutable,
                                 boolean referenceImplementation,
                                 boolean externalImplementation,
                                 String endpoint,
                                 String timeoutMillis,
                                 String objectType,
                                 List<String> objectIdPaths,
                                 List<String> repositoryObjectIdPaths,
                                 String repositoryName,
                                 String repositoryAdapterName,
                                 String repositoryAdapterRole,
                                 boolean repositoryAppDdlTables,
                                 String repositoryStorageTableName,
                                 String repositoryDatasourceUrl,
                                 String repositoryDriverClassName,
                                 String compensationMaxAttempts,
                                 String compensationBackoffMillis,
                                 String compensationManualInterventionRequired,
                                 String readiness,
                                 List<String> readyReasons,
                                 List<String> missingReasons,
                                 List<String> actions,
                                 String implementationContract,
                                 String runtimeContract) {
    public PhysicalRemotePlan(String serviceName,
                              String adapterName,
                              String adapterRole,
                              boolean adapterExecutable,
                              boolean referenceImplementation,
                              boolean externalImplementation,
                              String endpoint,
                              String timeoutMillis,
                              String objectType,
                              List<String> objectIdPaths,
                              List<String> repositoryObjectIdPaths,
                              String repositoryName,
                              String repositoryAdapterName,
                              String repositoryAdapterRole,
                              boolean repositoryAppDdlTables,
                              String repositoryStorageTableName,
                              String repositoryDatasourceUrl,
                              String repositoryDriverClassName,
                              String compensationMaxAttempts,
                              String compensationBackoffMillis,
                              String compensationManualInterventionRequired) {
        this(serviceName,
                adapterName,
                adapterRole,
                adapterExecutable,
                referenceImplementation,
                externalImplementation,
                endpoint,
                timeoutMillis,
                objectType,
                objectIdPaths,
                repositoryObjectIdPaths,
                repositoryName,
                repositoryAdapterName,
                repositoryAdapterRole,
                repositoryAppDdlTables,
                repositoryStorageTableName,
                repositoryDatasourceUrl,
                repositoryDriverClassName,
                compensationMaxAttempts,
                compensationBackoffMillis,
                compensationManualInterventionRequired,
                "",
                List.of(),
                List.of(),
                List.of(),
                "",
                "");
    }

    public PhysicalRemotePlan {
        serviceName = normalize(serviceName);
        adapterName = normalize(adapterName);
        adapterRole = normalize(adapterRole);
        endpoint = normalize(endpoint);
        timeoutMillis = normalize(timeoutMillis);
        objectType = normalize(objectType);
        objectIdPaths = normalizeList(objectIdPaths);
        repositoryObjectIdPaths = normalizeList(repositoryObjectIdPaths);
        repositoryName = normalize(repositoryName);
        repositoryAdapterName = normalize(repositoryAdapterName);
        repositoryAdapterRole = normalize(repositoryAdapterRole);
        repositoryStorageTableName = normalize(repositoryStorageTableName);
        repositoryDatasourceUrl = normalize(repositoryDatasourceUrl);
        repositoryDriverClassName = normalize(repositoryDriverClassName);
        compensationMaxAttempts = normalize(compensationMaxAttempts);
        compensationBackoffMillis = normalize(compensationBackoffMillis);
        compensationManualInterventionRequired = normalize(compensationManualInterventionRequired);
        readiness = normalize(readiness);
        readyReasons = normalizeList(readyReasons);
        missingReasons = normalizeList(missingReasons);
        actions = normalizeList(actions);
        implementationContract = normalize(implementationContract);
        runtimeContract = normalize(runtimeContract);
        if (missingReasons.isEmpty()) {
            missingReasons = missingReasons(serviceName, adapterName, adapterRole, adapterExecutable,
                    referenceImplementation, externalImplementation, endpoint, timeoutMillis, objectType,
                    objectIdPaths, repositoryName, repositoryAdapterName, repositoryAdapterRole,
                    repositoryAppDdlTables, repositoryStorageTableName, repositoryDatasourceUrl,
                    repositoryDriverClassName);
        }
        boolean ready = noMissingReasons(missingReasons);
        if (readiness.isBlank()) {
            readiness = ready ? "PHYSICAL_REMOTE_READY" : "PHYSICAL_REMOTE_BLOCKED";
        }
        if (readyReasons.isEmpty() && "PHYSICAL_REMOTE_READY".equals(readiness)) {
            readyReasons = readyReasons(serviceName, adapterName, adapterRole, adapterExecutable,
                    referenceImplementation, externalImplementation, endpoint, timeoutMillis, objectType,
                    objectIdPaths, repositoryName, repositoryAdapterName, repositoryAdapterRole,
                    repositoryAppDdlTables, repositoryStorageTableName, repositoryDatasourceUrl,
                    repositoryDriverClassName);
        }
        if (missingReasons.isEmpty() && "PHYSICAL_REMOTE_READY".equals(readiness)) {
            missingReasons = List.of("NO_MISSING_REASON");
        }
        if (actions.isEmpty()) {
            actions = "PHYSICAL_REMOTE_READY".equals(readiness)
                    ? List.of("NO_ACTION_REQUIRED")
                    : actions(missingReasons);
        }
        if (implementationContract.isBlank()) {
            implementationContract = implementationContract(adapterName, adapterRole,
                    implementationKind(referenceImplementation, externalImplementation),
                    adapterExecutable, readiness);
        }
        if (runtimeContract.isBlank()) {
            runtimeContract = runtimeContract(serviceName, implementationContract,
                    runtimeBinding(referenceImplementation, externalImplementation, endpoint,
                            timeoutMillis, objectType, objectIdPaths, repositoryName,
                            repositoryAdapterRole, repositoryStorageTableName, repositoryDatasourceUrl),
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

    private static boolean noMissingReasons(List<String> missingReasons) {
        return missingReasons.isEmpty() || missingReasons.equals(List.of("NO_MISSING_REASON"));
    }

    private static List<String> readyReasons(String serviceName,
                                             String adapterName,
                                             String adapterRole,
                                             boolean adapterExecutable,
                                             boolean referenceImplementation,
                                             boolean externalImplementation,
                                             String endpoint,
                                             String timeoutMillis,
                                             String objectType,
                                             List<String> objectIdPaths,
                                             String repositoryName,
                                             String repositoryAdapterName,
                                             String repositoryAdapterRole,
                                             boolean repositoryAppDdlTables,
                                             String repositoryStorageTableName,
                                             String repositoryDatasourceUrl,
                                             String repositoryDriverClassName) {
        List<String> reasons = new ArrayList<>();
        addReadyReason(reasons, !isBlank(serviceName), "service");
        addReadyReason(reasons, !isBlank(adapterName), "adapter");
        addReadyReason(reasons, !isBlank(adapterRole), "adapterRole");
        addReadyReason(reasons, adapterExecutable, "adapterExecutable");
        if (referenceImplementation) {
            reasons.add("referenceImplementation");
            addReadyReason(reasons, !isBlank(objectType), "objectType");
            addReadyReason(reasons, !objectIdPaths.isEmpty(), "objectIdPath");
            addReadyReason(reasons, !isBlank(repositoryName), "repository");
            addReadyReason(reasons, !isBlank(repositoryAdapterName), "repositoryAdapter");
            addReadyReason(reasons, !isBlank(repositoryAdapterRole), "repositoryAdapterRole");
            addReadyReason(reasons, repositoryAppDdlTables, "repositoryAppDdl");
            addReadyReason(reasons, !isBlank(repositoryStorageTableName), "repositoryStorageTable");
            addReadyReason(reasons, !isBlank(repositoryDatasourceUrl), "repositoryDatasource");
            addReadyReason(reasons, !isBlank(repositoryDriverClassName), "repositoryDriver");
        }
        if (externalImplementation) {
            reasons.add("externalImplementation");
            addReadyReason(reasons, !isBlank(endpoint), "endpoint");
            addReadyReason(reasons, isBlank(endpoint) || isAbsoluteHttpEndpoint(endpoint),
                    "absoluteHttpEndpoint");
            addReadyReason(reasons, isBlank(timeoutMillis) || isPositiveLong(timeoutMillis),
                    "positiveTimeout");
            if (externalEndpointReady(endpoint)) {
                reasons.add("productionEndpoint");
            }
        }
        return List.copyOf(reasons);
    }

    private static void addReadyReason(List<String> reasons, boolean ready, String reason) {
        if (ready) {
            reasons.add(reason);
        }
    }

    private static List<String> missingReasons(String serviceName,
                                               String adapterName,
                                               String adapterRole,
                                               boolean adapterExecutable,
                                               boolean referenceImplementation,
                                               boolean externalImplementation,
                                               String endpoint,
                                               String timeoutMillis,
                                               String objectType,
                                               List<String> objectIdPaths,
                                               String repositoryName,
                                               String repositoryAdapterName,
                                               String repositoryAdapterRole,
                                               boolean repositoryAppDdlTables,
                                               String repositoryStorageTableName,
                                               String repositoryDatasourceUrl,
                                               String repositoryDriverClassName) {
        List<String> reasons = new ArrayList<>();
        if (isBlank(serviceName)) {
            reasons.add("service");
        }
        if (isBlank(adapterName)) {
            reasons.add("adapter");
        }
        if (isBlank(adapterRole)) {
            reasons.add("adapterRole");
        }
        if (!adapterExecutable) {
            reasons.add("adapterExecutable");
        }
        if (referenceImplementation) {
            addReferenceMissingReasons(objectType, objectIdPaths, repositoryName, repositoryAdapterName,
                    repositoryAdapterRole, repositoryAppDdlTables, repositoryStorageTableName,
                    repositoryDatasourceUrl, repositoryDriverClassName, reasons);
        } else if (externalImplementation) {
            addExternalMissingReasons(endpoint, timeoutMillis, reasons);
        } else {
            reasons.add("implementationKind");
        }
        return List.copyOf(reasons);
    }

    private static void addReferenceMissingReasons(String objectType,
                                                   List<String> objectIdPaths,
                                                   String repositoryName,
                                                   String repositoryAdapterName,
                                                   String repositoryAdapterRole,
                                                   boolean repositoryAppDdlTables,
                                                   String repositoryStorageTableName,
                                                   String repositoryDatasourceUrl,
                                                   String repositoryDriverClassName,
                                                   List<String> reasons) {
        if (isBlank(objectType)) {
            reasons.add("referenceObjectType");
        }
        if (objectIdPaths.isEmpty()) {
            reasons.add("referenceObjectIdPath");
        }
        if (isBlank(repositoryName)) {
            reasons.add("referenceRepository");
        }
        if (isBlank(repositoryAdapterName)) {
            reasons.add("referenceRepositoryAdapter");
        }
        if (isBlank(repositoryAdapterRole)) {
            reasons.add("referenceRepositoryAdapterRole");
        }
        if (!repositoryAppDdlTables) {
            reasons.add("referenceRepositoryAppDdl");
        }
        if (isBlank(repositoryStorageTableName)) {
            reasons.add("referenceRepositoryStorageTable");
        }
        if (isBlank(repositoryDatasourceUrl)) {
            reasons.add("referenceRepositoryDatasource");
        }
        if (isBlank(repositoryDriverClassName)) {
            reasons.add("referenceRepositoryDriver");
        }
    }

    private static void addExternalMissingReasons(String endpoint,
                                                  String timeoutMillis,
                                                  List<String> reasons) {
        String endpointReason = externalEndpointReadinessReason(endpoint);
        if (!"-".equals(endpointReason)) {
            reasons.add(endpointReason);
        }
        if (!isBlank(timeoutMillis) && !isPositiveLong(timeoutMillis)) {
            reasons.add("timeoutInvalid");
        }
    }

    private static List<String> actions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()
                || missingReasons.equals(List.of("NO_MISSING_REASON"))) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            actions.add(action(reason));
        }
        return List.copyOf(actions);
    }

    private static String action(String missingReason) {
        if (isBlank(missingReason)) {
            return "resolvePhysicalRemoteBinding";
        }
        return switch (missingReason) {
            case "physicalRemotePlan" -> "generatePhysicalRemotePlan";
            case "service" -> "bindRemoteService";
            case "adapter" -> "configureRemoteAdapter";
            case "adapterRole" -> "resolveRemoteAdapterRole";
            case "adapterExecutable" -> "enableExecutableRemoteAdapter";
            case "implementationKind" -> "chooseRemoteImplementationKind";
            case "referenceObjectType" -> "configureReferenceRemoteObjectType";
            case "referenceObjectIdPath" -> "configureReferenceRemoteObjectIdPath";
            case "referenceRepository" -> "bindReferenceRemoteRepository";
            case "referenceRepositoryAdapter" -> "configureReferenceRemoteRepositoryAdapter";
            case "referenceRepositoryAdapterRole" -> "resolveReferenceRemoteRepositoryAdapterRole";
            case "referenceRepositoryAppDdl" -> "enableReferenceRemoteRepositoryAppDdl";
            case "referenceRepositoryStorageTable" -> "configureReferenceRemoteRepositoryStorageTable";
            case "referenceRepositoryDatasource" -> "configureReferenceRemoteRepositoryDatasource";
            case "referenceRepositoryDriver" -> "configureReferenceRemoteRepositoryDriver";
            case "endpoint" -> "configureRemoteEndpoint";
            case "endpointInvalid" -> "configureAbsoluteHttpRemoteEndpoint";
            case "developmentEndpoint" -> "replaceDevelopmentRemoteEndpoint";
            case "placeholderEndpoint" -> "replaceRemoteEndpointPlaceholder";
            case "timeoutInvalid" -> "configurePositiveRemoteTimeout";
            default -> "resolvePhysicalRemoteReadiness";
        };
    }

    private static String implementationContract(String adapterName,
                                                 String adapterRole,
                                                 String implementationKind,
                                                 boolean adapterExecutable,
                                                 String readiness) {
        return key(adapterName)
                + ":" + key(adapterRole)
                + ":" + key(implementationKind)
                + ":executable=" + (adapterExecutable ? "ADAPTER_EXECUTABLE" : "ADAPTER_NOT_EXECUTABLE")
                + ":" + key(readiness);
    }

    private static String runtimeContract(String serviceName,
                                          String implementationContract,
                                          String runtimeBinding,
                                          String readiness) {
        return key(serviceName)
                + "->" + key(implementationContract)
                + "+" + key(runtimeBinding)
                + ":" + key(readiness);
    }

    private static String runtimeBinding(boolean referenceImplementation,
                                         boolean externalImplementation,
                                         String endpoint,
                                         String timeoutMillis,
                                         String objectType,
                                         List<String> objectIdPaths,
                                         String repositoryName,
                                         String repositoryAdapterRole,
                                         String repositoryStorageTableName,
                                         String repositoryDatasourceUrl) {
        if (externalImplementation) {
            return "endpoint=" + endpointKey(endpoint)
                    + "+timeout=" + key(timeoutMillis);
        }
        if (referenceImplementation) {
            return "object=" + key(objectType)
                    + "+objectIds=" + listKey(objectIdPaths, "NO_OBJECT_ID_PATH")
                    + "+repository=" + key(repositoryName)
                    + "+repositoryRole=" + key(repositoryAdapterRole)
                    + "+storage=" + key(repositoryStorageTableName)
                    + "+datasource=" + key(repositoryDatasourceUrl);
        }
        if (!isBlank(endpoint)) {
            return "endpoint=" + endpointKey(endpoint)
                    + "+timeout=" + key(timeoutMillis);
        }
        return "repository=" + key(repositoryName)
                + "+repositoryRole=" + key(repositoryAdapterRole);
    }

    private static String implementationKind(boolean referenceImplementation,
                                             boolean externalImplementation) {
        if (externalImplementation) {
            return "EXTERNAL_IMPLEMENTATION";
        }
        if (referenceImplementation) {
            return "REFERENCE_IMPLEMENTATION";
        }
        return "CUSTOM_IMPLEMENTATION";
    }

    private static boolean externalEndpointReady(String endpoint) {
        return isAbsoluteHttpEndpoint(endpoint)
                && !developmentEndpoint(endpoint)
                && !placeholderEndpoint(endpoint);
    }

    private static String externalEndpointReadinessReason(String endpoint) {
        if (isBlank(endpoint)) {
            return "endpoint";
        }
        if (!isAbsoluteHttpEndpoint(endpoint)) {
            return "endpointInvalid";
        }
        if (developmentEndpoint(endpoint)) {
            return "developmentEndpoint";
        }
        if (placeholderEndpoint(endpoint)) {
            return "placeholderEndpoint";
        }
        return "-";
    }

    private static boolean developmentEndpoint(String endpoint) {
        if (isBlank(endpoint)) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (isBlank(host)) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return "localhost".equals(normalizedHost)
                    || "127.0.0.1".equals(normalizedHost)
                    || "0.0.0.0".equals(normalizedHost)
                    || "::1".equals(normalizedHost);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean placeholderEndpoint(String endpoint) {
        if (isBlank(endpoint)) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (isBlank(host)) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return "example.test".equals(normalizedHost)
                    || normalizedHost.endsWith(".example.test")
                    || "example.com".equals(normalizedHost)
                    || normalizedHost.endsWith(".example.com");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isAbsoluteHttpEndpoint(String endpoint) {
        if (isBlank(endpoint)) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            return uri.isAbsolute()
                    && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    && !isBlank(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isPositiveLong(String value) {
        if (isBlank(value)) {
            return false;
        }
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String listKey(List<String> values, String fallback) {
        List<String> keys = values == null
                ? List.of()
                : values.stream()
                        .filter(value -> !isBlank(value))
                        .map(PhysicalRemotePlan::key)
                        .distinct()
                        .toList();
        return keys.isEmpty() ? fallback : String.join("+", keys);
    }

    private static String endpointKey(String endpoint) {
        return isBlank(endpoint) ? "NO_ENDPOINT" : endpoint;
    }

    private static String key(String value) {
        return isBlank(value) ? "UNASSIGNED" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
