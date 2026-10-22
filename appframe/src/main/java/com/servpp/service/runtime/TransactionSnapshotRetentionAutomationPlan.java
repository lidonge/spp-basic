package com.servpp.service.runtime;

import java.util.List;

public record TransactionSnapshotRetentionAutomationPlan(String planName,
                                                         String retentionPlanName,
                                                         String repositoryName,
                                                         String resolvedRepositoryName,
                                                         String hotHistoryTableName,
                                                         String coldArchiveTableName,
                                                         List<String> sourceServiceNames,
                                                         boolean enabledByDefault,
                                                         String schedulerPropertyPrefix,
                                                         String enabledPropertyName,
                                                         String fixedDelayPropertyName,
                                                         long defaultFixedDelayMillis,
                                                         String retentionMillisPropertyName,
                                                         long defaultRetentionMillis,
                                                         String archiveRetentionMillisPropertyName,
                                                         long defaultArchiveRetentionMillis,
                                                         String archiveEndpointPath,
                                                         String archiveRuntimeOperation,
                                                         String pruneArchiveRuntimeOperation,
                                                         String pruneRuntimeOperation) {
    public TransactionSnapshotRetentionAutomationPlan {
        sourceServiceNames = sourceServiceNames == null ? List.of() : List.copyOf(sourceServiceNames);
    }
}
