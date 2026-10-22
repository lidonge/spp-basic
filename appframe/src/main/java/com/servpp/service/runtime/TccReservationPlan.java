package com.servpp.service.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TccReservationPlan(String serviceName,
                                 String resourceId,
                                 String actionName,
                                 String sourceAttributeId,
                                 String reservationAttributeId,
                                 String sourceQuantumStoreName,
                                 String sourceQuantumStoreAdapterName,
                                 Map<String, String> sourceQuantumStoreConfiguration,
                                 String sourceQuantumDatasourceUrl,
                                 String sourceQuantumJournalTableName,
                                 String sourceQuantumValueTableName,
                                 boolean sourceQuantumAttribute,
                                 String targetObjectType,
                                 String targetObjectPath,
                                 String targetTableName,
                                 String targetColumnName,
                                 String targetRepositoryName,
                                 String targetResolvedRepositoryName,
                                 String targetRepositoryAdapterName,
                                 boolean targetRepositoryAppDdlTables,
                                 Map<String, String> targetRepositoryAdapterConfiguration,
                                 String targetRepositoryStorageTableName,
                                 String targetRepositoryDatasourceUrl,
                                 boolean targetAppDdlColumnPushdown,
                                 boolean targetPlatformColumn,
                                 boolean targetObjectBackedColumn,
                                 boolean targetPrimaryKeyColumn,
                                 String readiness,
                                 List<String> readyReasons,
                                 List<String> missingReasons,
                                 List<String> actions,
                                 String reservationBoundary,
                                 String physicalReservationPath,
                                 String reservationContract) {
    public TccReservationPlan {
        sourceQuantumStoreConfiguration = sourceQuantumStoreConfiguration == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(sourceQuantumStoreConfiguration));
        targetRepositoryAdapterConfiguration = targetRepositoryAdapterConfiguration == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(targetRepositoryAdapterConfiguration));
        readyReasons = readyReasons == null ? List.of() : List.copyOf(readyReasons);
        missingReasons = missingReasons == null ? List.of() : List.copyOf(missingReasons);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public TccReservationPlan(String serviceName,
                              String resourceId,
                              String actionName,
                              String sourceAttributeId,
                              String reservationAttributeId,
                              String sourceQuantumStoreName,
                              String sourceQuantumStoreAdapterName,
                              Map<String, String> sourceQuantumStoreConfiguration,
                              String sourceQuantumDatasourceUrl,
                              String sourceQuantumJournalTableName,
                              String sourceQuantumValueTableName,
                              boolean sourceQuantumAttribute,
                              String targetObjectType,
                              String targetObjectPath,
                              String targetTableName,
                              String targetColumnName,
                              String targetRepositoryName,
                              String targetResolvedRepositoryName,
                              String targetRepositoryAdapterName,
                              boolean targetRepositoryAppDdlTables,
                              Map<String, String> targetRepositoryAdapterConfiguration,
                              String targetRepositoryStorageTableName,
                              String targetRepositoryDatasourceUrl,
                              boolean targetAppDdlColumnPushdown,
                              boolean targetPlatformColumn,
                              boolean targetObjectBackedColumn,
                              boolean targetPrimaryKeyColumn) {
        this(serviceName, resourceId, actionName, sourceAttributeId, reservationAttributeId,
                sourceQuantumStoreName, sourceQuantumStoreAdapterName, sourceQuantumStoreConfiguration,
                sourceQuantumDatasourceUrl, sourceQuantumJournalTableName, sourceQuantumValueTableName,
                sourceQuantumAttribute, targetObjectType, targetObjectPath, targetTableName, targetColumnName,
                targetRepositoryName, targetResolvedRepositoryName, targetRepositoryAdapterName,
                targetRepositoryAppDdlTables, targetRepositoryAdapterConfiguration,
                targetRepositoryStorageTableName, targetRepositoryDatasourceUrl,
                targetAppDdlColumnPushdown, targetPlatformColumn, targetObjectBackedColumn,
                targetPrimaryKeyColumn, null, List.of(), List.of(), List.of(), null, null, null);
    }
}
