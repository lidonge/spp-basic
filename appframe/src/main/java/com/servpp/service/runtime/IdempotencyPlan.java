package com.servpp.service.runtime;

import com.servpp.service.transaction.TransactionMode;

import java.util.List;

public record IdempotencyPlan(String serviceName,
                              GeneratedServiceKind serviceKind,
                              TransactionMode transactionMode,
                              String transactionResourceId,
                              String inputHeaderName,
                              boolean idempotencyKeyRequired,
                              String idempotencyKeyFieldName,
                              String idempotencyKeyPath,
                              boolean idempotencyKeyAvailable,
                              boolean physicalBindingRequired,
                              String backendName,
                              String adapterName,
                              String idempotencyMode,
                              String idempotencyOwner,
                              String idempotencyOwnerStoreNames,
                              String idempotencyOwnerAdapterNames,
                              String idempotencyOwnerAccessModes,
                              String idempotencyOwnerAttributeIds,
                              boolean adapterAvailable,
                              boolean adapterSupportsTransaction,
                              String idempotencyStoreName,
                              String idempotencyTableName,
                              String idempotencyDatasourceUrl,
                              String idempotencyDriverClassName,
                              String readiness,
                              List<String> readyReasons,
                              List<String> missingReasons,
                              List<String> actions,
                              String ownershipBoundary,
                              String physicalRuntimePath,
                              String executionContract) {
    public IdempotencyPlan {
        readyReasons = readyReasons == null ? List.of() : List.copyOf(readyReasons);
        missingReasons = missingReasons == null ? List.of() : List.copyOf(missingReasons);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
