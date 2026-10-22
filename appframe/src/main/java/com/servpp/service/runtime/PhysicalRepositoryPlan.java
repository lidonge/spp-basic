package com.servpp.service.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record PhysicalRepositoryPlan(String repositoryName,
                                     String adapterName,
                                     String adapterRole,
                                     boolean appDdlTables,
                                     List<String> appDdlTableNames,
                                     String storageTableName,
                                     String transactionHistoryTableName,
                                     String transactionArchiveTableName,
                                     String datasourceUrl,
                                     String driverClassName,
                                     String implementationClass,
                                     boolean localJdbcObjectStore,
                                     boolean localJsonSnapshotStore,
                                     boolean referenceStore,
                                     String readiness,
                                     List<String> readyReasons,
                                     List<String> missingReasons,
                                     List<String> actions,
                                     String repositoryContract,
                                     String runtimeContract) {
    public PhysicalRepositoryPlan(String repositoryName,
                                  String adapterName,
                                  String adapterRole,
                                  boolean appDdlTables,
                                  List<String> appDdlTableNames,
                                  String storageTableName,
                                  String transactionHistoryTableName,
                                  String transactionArchiveTableName,
                                  String datasourceUrl,
                                  String driverClassName,
                                  String implementationClass,
                                  boolean localJdbcObjectStore,
                                  boolean localJsonSnapshotStore,
                                  boolean referenceStore) {
        this(repositoryName,
                adapterName,
                adapterRole,
                appDdlTables,
                appDdlTableNames,
                storageTableName,
                transactionHistoryTableName,
                transactionArchiveTableName,
                datasourceUrl,
                driverClassName,
                implementationClass,
                localJdbcObjectStore,
                localJsonSnapshotStore,
                referenceStore,
                "",
                List.of(),
                List.of(),
                List.of(),
                "",
                "");
    }

    public PhysicalRepositoryPlan {
        repositoryName = normalize(repositoryName);
        adapterName = normalize(adapterName);
        adapterRole = normalize(adapterRole);
        appDdlTableNames = normalizeList(appDdlTableNames);
        storageTableName = normalize(storageTableName);
        transactionHistoryTableName = normalize(transactionHistoryTableName);
        transactionArchiveTableName = normalize(transactionArchiveTableName);
        datasourceUrl = normalize(datasourceUrl);
        driverClassName = normalize(driverClassName);
        implementationClass = normalize(implementationClass);
        readiness = normalize(readiness);
        readyReasons = normalizeList(readyReasons);
        missingReasons = normalizeList(missingReasons);
        actions = normalizeList(actions);
        repositoryContract = normalize(repositoryContract);
        runtimeContract = normalize(runtimeContract);
        if (missingReasons.isEmpty()) {
            missingReasons = missingReasons(repositoryName, adapterName, adapterRole,
                    appDdlTables, appDdlTableNames, storageTableName, transactionHistoryTableName,
                    transactionArchiveTableName, datasourceUrl, driverClassName,
                    localJdbcObjectStore, localJsonSnapshotStore, referenceStore);
        }
        boolean ready = noMissingReasons(missingReasons);
        if (readiness.isBlank()) {
            readiness = ready ? "PHYSICAL_REPOSITORY_READY" : "PHYSICAL_REPOSITORY_BLOCKED";
        }
        if (readyReasons.isEmpty() && "PHYSICAL_REPOSITORY_READY".equals(readiness)) {
            readyReasons = readyReasons(repositoryName, adapterName, adapterRole, appDdlTables,
                    appDdlTableNames, storageTableName, transactionHistoryTableName,
                    transactionArchiveTableName, datasourceUrl, driverClassName, implementationClass,
                    localJdbcObjectStore, localJsonSnapshotStore, referenceStore);
        }
        if (missingReasons.isEmpty() && "PHYSICAL_REPOSITORY_READY".equals(readiness)) {
            missingReasons = List.of("NO_MISSING_REASON");
        }
        if (actions.isEmpty()) {
            actions = "PHYSICAL_REPOSITORY_READY".equals(readiness)
                    ? List.of("NO_ACTION_REQUIRED")
                    : actions(missingReasons);
        }
        if (repositoryContract.isBlank()) {
            repositoryContract = repositoryContract(repositoryName, adapterName, adapterRole,
                    appDdlTables, appDdlTableNames, readiness);
        }
        if (runtimeContract.isBlank()) {
            runtimeContract = runtimeContract(repositoryName, adapterRole, storageTableName,
                    transactionHistoryTableName, transactionArchiveTableName, datasourceUrl, readiness);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(PhysicalRepositoryPlan::normalize)
                .toList();
    }

    private static boolean noMissingReasons(List<String> missingReasons) {
        return missingReasons.isEmpty() || missingReasons.equals(List.of("NO_MISSING_REASON"));
    }

    private static List<String> readyReasons(String repositoryName,
                                             String adapterName,
                                             String adapterRole,
                                             boolean appDdlTables,
                                             List<String> appDdlTableNames,
                                             String storageTableName,
                                             String transactionHistoryTableName,
                                             String transactionArchiveTableName,
                                             String datasourceUrl,
                                             String driverClassName,
                                             String implementationClass,
                                             boolean localJdbcObjectStore,
                                             boolean localJsonSnapshotStore,
                                             boolean referenceStore) {
        List<String> reasons = new ArrayList<>();
        if (!isBlank(repositoryName)) {
            reasons.add("repository");
        }
        if (!isBlank(adapterName)) {
            reasons.add("adapter");
        }
        if (!isBlank(adapterRole)) {
            reasons.add("adapterRole");
        }
        if (localJdbcObjectStore) {
            reasons.add("jdbcObjectRepository");
        }
        if (localJsonSnapshotStore) {
            reasons.add("jsonSnapshotStore");
        }
        if (referenceStore) {
            reasons.add("referenceStore");
        }
        if (appDdlTables) {
            reasons.add("appDdlRepository");
        }
        if (!appDdlTableNames.isEmpty()) {
            reasons.add("appDdlTables");
        }
        if (!isBlank(storageTableName)) {
            reasons.add("storageTable");
        }
        if (!isBlank(transactionHistoryTableName)) {
            reasons.add("transactionHistoryTable");
        }
        if (!isBlank(transactionArchiveTableName)) {
            reasons.add("transactionArchiveTable");
        }
        if (!isBlank(datasourceUrl)) {
            reasons.add("datasource");
        }
        if (!isBlank(driverClassName)) {
            reasons.add("driver");
        }
        if (!isBlank(implementationClass)) {
            reasons.add("implementationClass");
        }
        return List.copyOf(reasons);
    }

    private static List<String> missingReasons(String repositoryName,
                                               String adapterName,
                                               String adapterRole,
                                               boolean appDdlTables,
                                               List<String> appDdlTableNames,
                                               String storageTableName,
                                               String transactionHistoryTableName,
                                               String transactionArchiveTableName,
                                               String datasourceUrl,
                                               String driverClassName,
                                               boolean localJdbcObjectStore,
                                               boolean localJsonSnapshotStore,
                                               boolean referenceStore) {
        List<String> reasons = new ArrayList<>();
        if (isBlank(repositoryName)) {
            reasons.add("repository");
        }
        if (isBlank(adapterName)) {
            reasons.add("adapter");
        }
        if (isBlank(adapterRole)) {
            reasons.add("adapterRole");
        }
        if (localJdbcObjectStore) {
            if (!appDdlTables) {
                reasons.add("appDdlRepository");
            }
            if (appDdlTableNames.isEmpty()) {
                reasons.add("appDdlTables");
            }
            if (isBlank(storageTableName)) {
                reasons.add("storageTable");
            }
            if (isBlank(transactionHistoryTableName)) {
                reasons.add("transactionHistoryTable");
            }
            if (isBlank(transactionArchiveTableName)) {
                reasons.add("transactionArchiveTable");
            }
            if (isBlank(datasourceUrl)) {
                reasons.add("datasource");
            }
            if (isBlank(driverClassName)) {
                reasons.add("driver");
            }
        } else if (localJsonSnapshotStore) {
            if (isBlank(storageTableName)) {
                reasons.add("storageTable");
            }
            if (isBlank(datasourceUrl)) {
                reasons.add("datasource");
            }
            if (isBlank(driverClassName)) {
                reasons.add("driver");
            }
        } else if (!referenceStore) {
            reasons.add("repositoryAdapterCapability");
        }
        return List.copyOf(reasons);
    }

    private static List<String> actions(List<String> missingReasons) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "physicalRepositoryPlan" -> actions.add("generatePhysicalRepositoryPlan");
                case "repository" -> actions.add("declarePhysicalRepository");
                case "adapter" -> actions.add("configureRepositoryAdapter");
                case "adapterRole" -> actions.add("resolveRepositoryAdapterPhysicalBinding");
                case "repositoryAdapterCapability" -> actions.add("configureJdbcObjectRepositoryAdapter");
                case "appDdlRepository" -> actions.add("enableRepositoryAppDdlTables");
                case "appDdlTables" -> actions.add("publishRepositoryAppDdlTableNames");
                case "storageTable" -> actions.add("configureRepositoryStorageTable");
                case "transactionHistoryTable" -> actions.add("configureRepositoryTransactionHistoryTable");
                case "transactionArchiveTable" -> actions.add("configureRepositoryTransactionArchiveTable");
                case "datasource" -> actions.add("configureRepositoryDatasource");
                case "driver" -> actions.add("configureRepositoryDriver");
                default -> actions.add("resolveRepositoryAdapterPhysicalBinding");
            }
        }
        return actions.isEmpty() ? List.of("NO_ACTION_REQUIRED") : List.copyOf(actions);
    }

    private static String repositoryContract(String repositoryName,
                                             String adapterName,
                                             String adapterRole,
                                             boolean appDdlTables,
                                             List<String> appDdlTableNames,
                                             String readiness) {
        return key(repositoryName, "NO_REPOSITORY")
                + ":" + key(adapterName, "NO_ADAPTER")
                + ":" + key(adapterRole, "NO_ADAPTER_ROLE")
                + ":appDdl=" + (appDdlTables ? "APP_DDL_TABLES" : "NO_APP_DDL_TABLES")
                + ":tables=" + listKey(appDdlTableNames, "NO_APP_DDL_TABLE")
                + ":" + key(readiness, "UNASSIGNED");
    }

    private static String runtimeContract(String repositoryName,
                                          String adapterRole,
                                          String storageTableName,
                                          String transactionHistoryTableName,
                                          String transactionArchiveTableName,
                                          String datasourceUrl,
                                          String readiness) {
        return key(repositoryName, "NO_REPOSITORY")
                + "->" + key(adapterRole, "NO_ADAPTER_ROLE")
                + "+storage=" + key(storageTableName, "NO_STORAGE_TABLE")
                + "+history=" + key(transactionHistoryTableName, "NO_TRANSACTION_HISTORY_TABLE")
                + "+archive=" + key(transactionArchiveTableName, "NO_TRANSACTION_ARCHIVE_TABLE")
                + "+datasource=" + key(datasourceUrl, "NO_DATASOURCE")
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
        return isBlank(value) ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
