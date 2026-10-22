package com.servpp.service.runtime;

import java.util.List;

public record TransactionSnapshotRetentionExecutionPlan(String planName,
                                                        String retentionPlanName,
                                                        String automationPlanName,
                                                        String repositoryName,
                                                        String resolvedRepositoryName,
                                                        String serviceName,
                                                        String resourceName,
                                                        long retentionMillis,
                                                        boolean serviceSpecificRetention,
                                                        boolean resourceSpecificRetention,
                                                        String cutoffSource,
                                                        long archiveRetentionMillis,
                                                        boolean resourceSpecificArchiveRetention,
                                                        String archiveCutoffSource,
                                                        String archiveRuntimeOperation,
                                                        String pruneArchiveRuntimeOperation,
                                                        String hotHistoryTableName,
                                                        String coldArchiveTableName,
                                                        String schedulerPropertyPrefix,
                                                        String retentionMillisPropertyName,
                                                        String archiveRetentionMillisPropertyName,
                                                        String readiness,
                                                        List<String> readyReasons,
                                                        List<String> missingReasons,
                                                        List<String> actions,
                                                        String executionContract) {
    public TransactionSnapshotRetentionExecutionPlan {
        readyReasons = readyReasons == null ? List.of() : List.copyOf(readyReasons);
        missingReasons = missingReasons == null ? List.of() : List.copyOf(missingReasons);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
