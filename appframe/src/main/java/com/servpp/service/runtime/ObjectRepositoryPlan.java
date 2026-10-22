package com.servpp.service.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record ObjectRepositoryPlan(String serviceName,
                                   String objectType,
                                   String repositoryName,
                                   String resolvedRepositoryName,
                                   String adapterName,
                                   String appDdlTableName,
                                   boolean appDdlTables,
                                   String storageTableName,
                                   String transactionHistoryTableName,
                                   String transactionArchiveTableName,
                                   String datasourceUrl,
                                   String driverClassName,
                                   boolean repositoryQuery,
                                   boolean objectSnapshot,
                                   boolean transactionEvidence,
                                   boolean collectionQuery,
                                   boolean appDdlColumnPushdown,
                                   List<String> keySources,
                                   List<String> objectKeyColumns,
                                   List<String> externalObjectKeyRoots,
                                   String readiness,
                                   List<String> readyReasons,
                                   List<String> missingReasons,
                                   List<String> actions,
                                   String physicalRuntimePath,
                                   String runtimeContract) {
    public ObjectRepositoryPlan(String serviceName,
                                String objectType,
                                String repositoryName,
                                String resolvedRepositoryName,
                                String adapterName,
                                String appDdlTableName,
                                boolean appDdlTables,
                                String storageTableName,
                                String transactionHistoryTableName,
                                String transactionArchiveTableName,
                                String datasourceUrl,
                                String driverClassName,
                                boolean repositoryQuery,
                                boolean objectSnapshot,
                                boolean transactionEvidence,
                                boolean collectionQuery,
                                boolean appDdlColumnPushdown,
                                List<String> keySources,
                                List<String> objectKeyColumns,
                                List<String> externalObjectKeyRoots) {
        this(serviceName,
                objectType,
                repositoryName,
                resolvedRepositoryName,
                adapterName,
                appDdlTableName,
                appDdlTables,
                storageTableName,
                transactionHistoryTableName,
                transactionArchiveTableName,
                datasourceUrl,
                driverClassName,
                repositoryQuery,
                objectSnapshot,
                transactionEvidence,
                collectionQuery,
                appDdlColumnPushdown,
                keySources,
                objectKeyColumns,
                externalObjectKeyRoots,
                "",
                List.of(),
                List.of(),
                List.of(),
                "",
                "");
    }

    public ObjectRepositoryPlan {
        keySources = keySources == null ? List.of() : List.copyOf(keySources);
        objectKeyColumns = objectKeyColumns == null ? List.of() : List.copyOf(objectKeyColumns);
        externalObjectKeyRoots = externalObjectKeyRoots == null ? List.of() : List.copyOf(externalObjectKeyRoots);
        readiness = normalize(readiness);
        readyReasons = readyReasons == null ? List.of() : List.copyOf(readyReasons);
        missingReasons = missingReasons == null ? List.of() : List.copyOf(missingReasons);
        actions = actions == null ? List.of() : List.copyOf(actions);
        physicalRuntimePath = normalize(physicalRuntimePath);
        runtimeContract = normalize(runtimeContract);
        if (missingReasons.isEmpty()) {
            missingReasons = missingReasons(resolvedRepositoryName, adapterName, storageTableName,
                    datasourceUrl, driverClassName, repositoryQuery, appDdlTables, appDdlColumnPushdown,
                    keySources, objectKeyColumns);
        }
        boolean ready = noMissingReasons(missingReasons);
        if (readiness.isBlank()) {
            readiness = ready ? "OBJECT_REPOSITORY_READY" : "OBJECT_REPOSITORY_BLOCKED";
        }
        if (readyReasons.isEmpty() && "OBJECT_REPOSITORY_READY".equals(readiness)) {
            readyReasons = readyReasons(resolvedRepositoryName, adapterName, storageTableName,
                    datasourceUrl, driverClassName, appDdlTables, appDdlColumnPushdown,
                    keySources, objectKeyColumns);
        }
        if (missingReasons.isEmpty() && "OBJECT_REPOSITORY_READY".equals(readiness)) {
            missingReasons = List.of("NO_MISSING_REASON");
        }
        if (actions.isEmpty()) {
            actions = "OBJECT_REPOSITORY_READY".equals(readiness)
                    ? List.of("NO_ACTION_REQUIRED")
                    : actions(missingReasons);
        }
        if (physicalRuntimePath.isBlank()) {
            physicalRuntimePath = physicalRuntimePath(firstNonBlank(resolvedRepositoryName, repositoryName),
                    adapterName, storageTableName);
        }
        if (runtimeContract.isBlank()) {
            runtimeContract = runtimeContract(serviceName, objectType, physicalRuntimePath,
                    appDdlTableName, objectKeyColumns, readiness);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstNonBlank(String first, String second) {
        return !normalize(first).isBlank() ? normalize(first) : normalize(second);
    }

    private static boolean noMissingReasons(List<String> missingReasons) {
        return missingReasons.isEmpty() || missingReasons.equals(List.of("NO_MISSING_REASON"));
    }

    private static List<String> readyReasons(String resolvedRepositoryName,
                                             String adapterName,
                                             String storageTableName,
                                             String datasourceUrl,
                                             String driverClassName,
                                             boolean appDdlTables,
                                             boolean appDdlColumnPushdown,
                                             List<String> keySources,
                                             List<String> objectKeyColumns) {
        List<String> reasons = new ArrayList<>();
        if (!isBlank(resolvedRepositoryName)) {
            reasons.add("repository");
        }
        if (!isBlank(adapterName)) {
            reasons.add("adapter");
        }
        if (!isBlank(storageTableName)) {
            reasons.add("storageTable");
        }
        if (!isBlank(datasourceUrl)) {
            reasons.add("datasource");
        }
        if (!isBlank(driverClassName)) {
            reasons.add("driver");
        }
        if (appDdlTables) {
            reasons.add("appDdlTable");
        }
        if (appDdlColumnPushdown) {
            reasons.add("columnPushdown");
        }
        if (!keySources.isEmpty()) {
            reasons.add("keySource");
        }
        if (!objectKeyColumns.isEmpty()) {
            reasons.add("objectKeyColumns");
        }
        return List.copyOf(reasons);
    }

    private static List<String> missingReasons(String resolvedRepositoryName,
                                               String adapterName,
                                               String storageTableName,
                                               String datasourceUrl,
                                               String driverClassName,
                                               boolean repositoryQuery,
                                               boolean appDdlTables,
                                               boolean appDdlColumnPushdown,
                                               List<String> keySources,
                                               List<String> objectKeyColumns) {
        List<String> reasons = new ArrayList<>();
        if (isBlank(resolvedRepositoryName)) {
            reasons.add("repository");
        }
        if (isBlank(adapterName)) {
            reasons.add("adapter");
        }
        if (isBlank(storageTableName)) {
            reasons.add("storageTable");
        }
        if (isBlank(datasourceUrl)) {
            reasons.add("datasource");
        }
        if (isBlank(driverClassName)) {
            reasons.add("driver");
        }
        if (repositoryQuery) {
            if (!appDdlTables) {
                reasons.add("appDdlTable");
            }
            if (!appDdlColumnPushdown) {
                reasons.add("columnPushdown");
            }
            if (keySources.isEmpty()) {
                reasons.add("keySource");
            }
            if (objectKeyColumns.isEmpty()) {
                reasons.add("objectKeyColumns");
            }
        }
        return List.copyOf(reasons);
    }

    private static List<String> actions(List<String> missingReasons) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "objectRepositoryPlan" -> actions.add("generateObjectRepositoryPlan");
                case "repository" -> actions.add("bindObjectRepository");
                case "adapter" -> actions.add("configureObjectRepositoryAdapter");
                case "storageTable" -> actions.add("configureObjectRepositoryStorageTable");
                case "datasource" -> actions.add("configureObjectRepositoryDatasource");
                case "driver" -> actions.add("configureObjectRepositoryDriver");
                case "appDdlTable" -> actions.add("enableObjectRepositoryAppDdlTable");
                case "columnPushdown" -> actions.add("pushObjectRepositoryColumnsToAppDdl");
                case "keySource" -> actions.add("deriveObjectRepositoryKeySource");
                case "objectKeyColumns" -> actions.add("pushObjectRepositoryKeyColumnsToAppDdl");
                default -> actions.add("reviewObjectRepositoryReadiness");
            }
        }
        return actions.isEmpty() ? List.of("NO_ACTION_REQUIRED") : List.copyOf(actions);
    }

    private static String physicalRuntimePath(String repositoryName,
                                              String adapterName,
                                              String storageTableName) {
        return key(repositoryName, "NO_REPOSITORY")
                + ":" + key(adapterName, "NO_ADAPTER")
                + "." + key(storageTableName, "NO_STORAGE_TABLE");
    }

    private static String runtimeContract(String serviceName,
                                          String objectType,
                                          String physicalRuntimePath,
                                          String appDdlTableName,
                                          List<String> objectKeyColumns,
                                          String readiness) {
        return key(serviceName, "NO_SERVICE")
                + ":" + key(objectType, "NO_OBJECT_TYPE")
                + "->" + key(physicalRuntimePath, "NO_PHYSICAL_RUNTIME_PATH")
                + "+appDdl=" + key(appDdlTableName, "NO_APP_DDL_TABLE")
                + "+keys=" + listKey(objectKeyColumns, "NO_OBJECT_KEY_COLUMNS")
                + ":" + key(readiness, "UNASSIGNED");
    }

    private static String listKey(List<String> values, String fallback) {
        List<String> keys = values == null
                ? List.of()
                : values.stream()
                        .filter(value -> !isBlank(value))
                        .map(value -> key(value, "UNASSIGNED"))
                        .distinct()
                        .toList();
        return keys.isEmpty() ? fallback : String.join("+", keys);
    }

    private static String key(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
