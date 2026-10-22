package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record TransactionSnapshotRetentionPlan(String planName,
                                               String repositoryName,
                                               String resolvedRepositoryName,
                                               String repositoryAdapterName,
                                               String latestSnapshotTableName,
                                               String hotHistoryTableName,
                                               String coldArchiveTableName,
                                               String datasourceUrl,
                                               String driverClassName,
                                               Map<String, String> repositoryAdapterConfiguration,
                                               List<String> sourceServiceNames,
                                               List<String> sourceResourceNames,
                                               Map<String, String> serviceResourceNames,
                                               Map<String, Long> serviceRetentionMillis,
                                               Map<String, Long> resourceRetentionMillis,
                                               Map<String, Long> resourceArchiveRetentionMillis,
                                               List<String> effectiveSavePhases,
                                               String traceIdColumnName,
                                               String transactionIdColumnName,
                                               String resourceIdColumnName,
                                               String phaseColumnName,
                                               boolean hotHistoryAvailable,
                                               boolean coldArchiveAvailable,
                                               boolean traceQueryAvailable,
                                               boolean resourcePhaseQueryAvailable,
                                               String retentionMode,
                                               String transactionQueryEndpointPath,
                                               String archiveQueryEndpointPath,
                                               String archiveEndpointPath,
                                               String pruneRuntimeOperation,
                                               String retainLatestRuntimeOperation) {
    public TransactionSnapshotRetentionPlan {
        repositoryAdapterConfiguration = repositoryAdapterConfiguration == null
                ? Map.of()
                : Map.copyOf(repositoryAdapterConfiguration);
        sourceServiceNames = sourceServiceNames == null ? List.of() : List.copyOf(sourceServiceNames);
        sourceResourceNames = sourceResourceNames == null ? List.of() : List.copyOf(sourceResourceNames);
        serviceResourceNames = serviceResourceNames == null ? Map.of() : Map.copyOf(serviceResourceNames);
        serviceRetentionMillis = serviceRetentionMillis == null ? Map.of() : Map.copyOf(serviceRetentionMillis);
        resourceRetentionMillis = resourceRetentionMillis == null ? Map.of() : Map.copyOf(resourceRetentionMillis);
        resourceArchiveRetentionMillis = resourceArchiveRetentionMillis == null
                ? Map.of()
                : Map.copyOf(resourceArchiveRetentionMillis);
        effectiveSavePhases = effectiveSavePhases == null ? List.of() : List.copyOf(effectiveSavePhases);
    }
}
