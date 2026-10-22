package com.servpp.service.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.servpp.service.repository.ObjectSnapshotRule;

public record ObjectSnapshotPlan(String serviceName,
                                 String objectType,
                                 String source,
                                 String appDdlTableName,
                                 String appDdlIndexName,
                                 boolean appDdlDerived,
                                 boolean escapeHatch,
                                 String ruleSource,
                                 String timing,
                                 List<String> effectiveSavePhases,
                                 String snapshotBoundary,
                                 String recoveryRole,
                                 boolean transactionEvidence,
                                 boolean currentObject,
                                 boolean argumentRecoveryEvidence,
                                 boolean rollbackRecoveryEvidence,
                                 int argumentIndex,
                                 List<String> objectIdPaths,
                                 List<String> appDdlObjectKeyPaths,
                                 String repositoryName,
                                 String resolvedRepositoryName,
                                 String repositoryAdapterName,
                                 boolean repositoryAppDdlTables,
                                 Map<String, String> repositoryAdapterConfiguration,
                                 String snapshotStorageTableName,
                                 String transactionHistoryTableName,
                                 String transactionArchiveTableName,
                                 List<String> objectKeyColumns,
                                 boolean appDdlObjectKeyPushdown,
                                 List<String> externalObjectKeyRoots,
                                 String objectKeySource,
                                 String objectKeyAppDdlIndexName,
                                 boolean objectKeyAppDdlDerived,
                                 boolean objectKeyEscapeHatch,
                                 String closureReadiness,
                                 List<String> closureReadyReasons,
                                 List<String> closureMissingReasons,
                                 List<String> closureActions,
                                 String snapshotPath,
                                 String closureContract,
                                 String businessObjectClosureReadiness,
                                 List<String> businessObjectClosureReadyReasons,
                                 List<String> businessObjectClosureMissingReasons,
                                 List<String> businessObjectClosureActions,
                                 String businessObjectClosureContract,
                                 ObjectSnapshotRule rule) {
    public ObjectSnapshotPlan(String serviceName,
                              String objectType,
                              String source,
                              String appDdlTableName,
                              String appDdlIndexName,
                              boolean appDdlDerived,
                              boolean escapeHatch,
                              String ruleSource,
                              String timing,
                              List<String> effectiveSavePhases,
                              String snapshotBoundary,
                              String recoveryRole,
                              boolean transactionEvidence,
                              boolean currentObject,
                              boolean argumentRecoveryEvidence,
                              boolean rollbackRecoveryEvidence,
                              int argumentIndex,
                              List<String> objectIdPaths,
                              List<String> appDdlObjectKeyPaths,
                              String repositoryName,
                              String resolvedRepositoryName,
                              String repositoryAdapterName,
                              boolean repositoryAppDdlTables,
                              Map<String, String> repositoryAdapterConfiguration,
                              String snapshotStorageTableName,
                              String transactionHistoryTableName,
                              String transactionArchiveTableName,
                              List<String> objectKeyColumns,
                              boolean appDdlObjectKeyPushdown,
                              List<String> externalObjectKeyRoots,
                              String objectKeySource,
                              String objectKeyAppDdlIndexName,
                              boolean objectKeyAppDdlDerived,
                              boolean objectKeyEscapeHatch,
                              ObjectSnapshotRule rule) {
        this(serviceName, objectType, source, appDdlTableName, appDdlIndexName, appDdlDerived,
                escapeHatch, ruleSource, timing, effectiveSavePhases, snapshotBoundary, recoveryRole,
                transactionEvidence, currentObject, argumentRecoveryEvidence, rollbackRecoveryEvidence,
                argumentIndex, objectIdPaths, appDdlObjectKeyPaths, repositoryName, resolvedRepositoryName,
                repositoryAdapterName, repositoryAppDdlTables, repositoryAdapterConfiguration,
                snapshotStorageTableName, transactionHistoryTableName, transactionArchiveTableName,
                objectKeyColumns, appDdlObjectKeyPushdown, externalObjectKeyRoots, objectKeySource,
                objectKeyAppDdlIndexName, objectKeyAppDdlDerived, objectKeyEscapeHatch,
                "", List.of(), List.of(), List.of(), "", "", "",
                List.of(), List.of(), List.of(), "", rule);
    }

    public ObjectSnapshotPlan {
        objectIdPaths = objectIdPaths == null ? List.of() : List.copyOf(objectIdPaths);
        appDdlObjectKeyPaths = appDdlObjectKeyPaths == null ? List.of() : List.copyOf(appDdlObjectKeyPaths);
        effectiveSavePhases = effectiveSavePhases == null ? List.of() : List.copyOf(effectiveSavePhases);
        repositoryAdapterConfiguration = repositoryAdapterConfiguration == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(repositoryAdapterConfiguration));
        objectKeyColumns = objectKeyColumns == null ? List.of() : List.copyOf(objectKeyColumns);
        externalObjectKeyRoots = externalObjectKeyRoots == null ? List.of() : List.copyOf(externalObjectKeyRoots);
        closureReadyReasons = closureReadyReasons == null ? List.of() : List.copyOf(closureReadyReasons);
        closureMissingReasons = closureMissingReasons == null ? List.of() : List.copyOf(closureMissingReasons);
        closureActions = closureActions == null ? List.of() : List.copyOf(closureActions);
        businessObjectClosureReadyReasons = businessObjectClosureReadyReasons == null
                ? List.of()
                : List.copyOf(businessObjectClosureReadyReasons);
        businessObjectClosureMissingReasons = businessObjectClosureMissingReasons == null
                ? List.of()
                : List.copyOf(businessObjectClosureMissingReasons);
        businessObjectClosureActions = businessObjectClosureActions == null
                ? List.of()
                : List.copyOf(businessObjectClosureActions);
    }
}
