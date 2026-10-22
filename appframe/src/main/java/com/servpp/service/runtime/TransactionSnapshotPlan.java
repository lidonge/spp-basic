package com.servpp.service.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public record TransactionSnapshotPlan(String serviceName,
                                      String transactionResourceId,
                                      String objectType,
                                      String repositoryName,
                                      String resolvedRepositoryName,
                                      String repositoryAdapterName,
                                      String storageTableName,
                                      String transactionHistoryTableName,
                                      String transactionArchiveTableName,
                                      String datasourceUrl,
                                      String driverClassName,
                                      Map<String, String> repositoryAdapterConfiguration,
                                      List<String> objectIdPaths,
                                      List<String> effectiveSavePhases,
                                      String snapshotBoundary,
                                      String recoveryRole,
                                      String traceIdColumnName,
                                      String transactionIdColumnName,
                                      String resourceIdColumnName,
                                      String phaseColumnName,
                                      boolean latestSnapshotAvailable,
                                      boolean historyAvailable,
                                      boolean archiveAvailable,
                                      boolean traceQueryAvailable,
                                      boolean resourcePhaseQueryAvailable,
                                      boolean pruneAvailable,
                                      boolean retainLatestAvailable,
                                      boolean archiveOperationAvailable,
                                      String readiness,
                                      List<String> readyReasons,
                                      List<String> missingReasons,
                                      List<String> actions,
                                      String evidencePath,
                                      String executionContract) {
    public TransactionSnapshotPlan(String serviceName,
                                   String transactionResourceId,
                                   String objectType,
                                   String repositoryName,
                                   String resolvedRepositoryName,
                                   String repositoryAdapterName,
                                   String storageTableName,
                                   String transactionHistoryTableName,
                                   String transactionArchiveTableName,
                                   String datasourceUrl,
                                   String driverClassName,
                                   Map<String, String> repositoryAdapterConfiguration,
                                   List<String> objectIdPaths,
                                   List<String> effectiveSavePhases,
                                   String snapshotBoundary,
                                   String recoveryRole,
                                   String traceIdColumnName,
                                   String transactionIdColumnName,
                                   String resourceIdColumnName,
                                   String phaseColumnName,
                                   boolean latestSnapshotAvailable,
                                   boolean historyAvailable,
                                   boolean archiveAvailable,
                                   boolean traceQueryAvailable,
                                   boolean resourcePhaseQueryAvailable,
                                   boolean pruneAvailable,
                                   boolean retainLatestAvailable,
                                   boolean archiveOperationAvailable) {
        this(serviceName,
                transactionResourceId,
                objectType,
                repositoryName,
                resolvedRepositoryName,
                repositoryAdapterName,
                storageTableName,
                transactionHistoryTableName,
                transactionArchiveTableName,
                datasourceUrl,
                driverClassName,
                repositoryAdapterConfiguration,
                objectIdPaths,
                effectiveSavePhases,
                snapshotBoundary,
                recoveryRole,
                traceIdColumnName,
                transactionIdColumnName,
                resourceIdColumnName,
                phaseColumnName,
                latestSnapshotAvailable,
                historyAvailable,
                archiveAvailable,
                traceQueryAvailable,
                resourcePhaseQueryAvailable,
                pruneAvailable,
                retainLatestAvailable,
                archiveOperationAvailable,
                "",
                List.of(),
                List.of(),
                List.of(),
                "",
                "");
    }

    public TransactionSnapshotPlan {
        repositoryAdapterConfiguration = repositoryAdapterConfiguration == null
                ? Map.of()
                : Map.copyOf(repositoryAdapterConfiguration);
        objectIdPaths = objectIdPaths == null ? List.of() : List.copyOf(objectIdPaths);
        effectiveSavePhases = effectiveSavePhases == null ? List.of() : List.copyOf(effectiveSavePhases);
        readiness = normalize(readiness);
        readyReasons = readyReasons == null ? List.of() : List.copyOf(readyReasons);
        missingReasons = missingReasons == null ? List.of() : List.copyOf(missingReasons);
        actions = actions == null ? List.of() : List.copyOf(actions);
        evidencePath = normalize(evidencePath);
        executionContract = normalize(executionContract);
        if (missingReasons.isEmpty()) {
            missingReasons = missingReasons(latestSnapshotAvailable, historyAvailable, archiveAvailable,
                    traceQueryAvailable, resourcePhaseQueryAvailable, pruneAvailable, retainLatestAvailable,
                    archiveOperationAvailable);
        }
        boolean evidenceReady = noMissingEvidenceReasons(missingReasons);
        if (readiness.isBlank()) {
            readiness = evidenceReady
                    ? "TRANSACTION_SNAPSHOT_EVIDENCE_READY"
                    : "TRANSACTION_SNAPSHOT_EVIDENCE_INCOMPLETE";
        }
        if (readyReasons.isEmpty() && "TRANSACTION_SNAPSHOT_EVIDENCE_READY".equals(readiness)) {
            readyReasons = readyReasons(latestSnapshotAvailable, historyAvailable, archiveAvailable,
                    traceQueryAvailable, resourcePhaseQueryAvailable, pruneAvailable, retainLatestAvailable,
                    archiveOperationAvailable);
        }
        if (missingReasons.isEmpty() && "TRANSACTION_SNAPSHOT_EVIDENCE_READY".equals(readiness)) {
            missingReasons = List.of("NO_MISSING_TRANSACTION_SNAPSHOT_EVIDENCE_REASON");
        }
        if (actions.isEmpty()) {
            actions = "TRANSACTION_SNAPSHOT_EVIDENCE_READY".equals(readiness)
                    ? List.of("NO_ACTION_REQUIRED")
                    : actions(missingReasons);
        }
        if (evidencePath.isBlank()) {
            evidencePath = evidencePath(serviceName, transactionResourceId, objectType, snapshotBoundary,
                    recoveryRole);
        }
        if (executionContract.isBlank()) {
            executionContract = executionContract(evidencePath,
                    firstNonBlank(resolvedRepositoryName, repositoryName),
                    storageTableName,
                    transactionHistoryTableName,
                    transactionArchiveTableName,
                    readiness);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstNonBlank(String first, String second) {
        return !normalize(first).isBlank() ? normalize(first) : normalize(second);
    }

    private static boolean noMissingEvidenceReasons(List<String> missingReasons) {
        return missingReasons.isEmpty()
                || missingReasons.equals(List.of("NO_MISSING_TRANSACTION_SNAPSHOT_EVIDENCE_REASON"));
    }

    private static List<String> readyReasons(boolean latestSnapshotAvailable,
                                             boolean historyAvailable,
                                             boolean archiveAvailable,
                                             boolean traceQueryAvailable,
                                             boolean resourcePhaseQueryAvailable,
                                             boolean pruneAvailable,
                                             boolean retainLatestAvailable,
                                             boolean archiveOperationAvailable) {
        List<String> reasons = new ArrayList<>();
        if (latestSnapshotAvailable) {
            reasons.add("latestSnapshot");
        }
        if (historyAvailable) {
            reasons.add("history");
        }
        if (archiveAvailable) {
            reasons.add("archive");
        }
        if (traceQueryAvailable) {
            reasons.add("traceQuery");
        }
        if (resourcePhaseQueryAvailable) {
            reasons.add("resourcePhaseQuery");
        }
        if (pruneAvailable) {
            reasons.add("prune");
        }
        if (retainLatestAvailable) {
            reasons.add("retainLatest");
        }
        if (archiveOperationAvailable) {
            reasons.add("archiveOperation");
        }
        return List.copyOf(reasons);
    }

    private static List<String> missingReasons(boolean latestSnapshotAvailable,
                                               boolean historyAvailable,
                                               boolean archiveAvailable,
                                               boolean traceQueryAvailable,
                                               boolean resourcePhaseQueryAvailable,
                                               boolean pruneAvailable,
                                               boolean retainLatestAvailable,
                                               boolean archiveOperationAvailable) {
        List<String> missing = new ArrayList<>();
        if (!latestSnapshotAvailable) {
            missing.add("latestSnapshot");
        }
        if (!historyAvailable) {
            missing.add("history");
        }
        if (!archiveAvailable) {
            missing.add("archive");
        }
        if (!traceQueryAvailable) {
            missing.add("traceQuery");
        }
        if (!resourcePhaseQueryAvailable) {
            missing.add("resourcePhaseQuery");
        }
        if (!pruneAvailable) {
            missing.add("prune");
        }
        if (!retainLatestAvailable) {
            missing.add("retainLatest");
        }
        if (!archiveOperationAvailable) {
            missing.add("archiveOperation");
        }
        return List.copyOf(missing);
    }

    private static List<String> actions(List<String> missingReasons) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "latestSnapshot" -> actions.add("bindLatestTransactionSnapshotTable");
                case "history" -> actions.add("bindTransactionHistoryTable");
                case "archive" -> actions.add("bindTransactionArchiveTable");
                case "traceQuery" -> actions.add("enableTransactionTraceQuery");
                case "resourcePhaseQuery" -> actions.add("enableTransactionResourcePhaseQuery");
                case "prune" -> actions.add("enableTransactionSnapshotPruneOperation");
                case "retainLatest" -> actions.add("enableTransactionSnapshotRetainLatestOperation");
                case "archiveOperation" -> actions.add("enableTransactionSnapshotArchiveOperation");
                default -> actions.add("completeTransactionSnapshotEvidence");
            }
        }
        return actions.isEmpty() ? List.of("NO_ACTION_REQUIRED") : List.copyOf(actions);
    }

    private static String evidencePath(String serviceName,
                                       String transactionResourceId,
                                       String objectType,
                                       String snapshotBoundary,
                                       String recoveryRole) {
        return key(serviceName, "NO_SERVICE")
                + "->" + key(transactionResourceId, "NO_RESOURCE")
                + ":" + key(objectType, "NO_OBJECT_TYPE")
                + ":" + key(snapshotBoundary, "NO_BOUNDARY")
                + ":" + key(recoveryRole, "NO_RECOVERY_ROLE");
    }

    private static String executionContract(String evidencePath,
                                            String repositoryName,
                                            String storageTableName,
                                            String transactionHistoryTableName,
                                            String transactionArchiveTableName,
                                            String readiness) {
        return key(evidencePath, "NO_EVIDENCE_PATH")
                + "->" + key(repositoryName, "NO_REPOSITORY")
                + "+latest=" + key(storageTableName, "NO_LATEST_TABLE")
                + "+history=" + key(transactionHistoryTableName, "NO_HISTORY_TABLE")
                + "+archive=" + key(transactionArchiveTableName, "NO_ARCHIVE_TABLE")
                + ":" + key(readiness, "UNASSIGNED");
    }

    private static String key(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }
}
