package com.servpp.service.runtime;

import java.util.ArrayList;
import java.util.List;

import com.servpp.service.transaction.TransactionMode;

public record QuantumConservationPlan(String serviceName,
                                      GeneratedServiceKind serviceKind,
                                      TransactionMode transactionMode,
                                      String resourceId,
                                      List<String> attributeIds,
                                      int attributeCount,
                                      boolean pairDeclared,
                                      boolean conservationClosureCandidate,
                                      String readiness,
                                      List<String> readyReasons,
                                      List<String> missingReasons,
                                      List<String> actions,
                                      String conservationContract,
                                      List<String> implementationStrategies,
                                      String implementationStrategySet,
                                      String idempotencyOwner,
                                      String reversalOwner,
                                      String snapshotBoundary,
                                      String recoveryOwner,
                                      boolean negativeDeltaReversal,
                                      String runtimeResponsibilityContract) {
    public QuantumConservationPlan(String serviceName,
                                   GeneratedServiceKind serviceKind,
                                   TransactionMode transactionMode,
                                   String resourceId,
                                   List<String> attributeIds,
                                   int attributeCount,
                                   boolean pairDeclared,
                                   boolean conservationClosureCandidate,
                                   String readiness,
                                   List<String> missingReasons,
                                   List<String> actions,
                                   String conservationContract,
                                   List<String> implementationStrategies,
                                   String implementationStrategySet,
                                   String idempotencyOwner,
                                   String reversalOwner,
                                   String snapshotBoundary,
                                   String recoveryOwner,
                                   boolean negativeDeltaReversal,
                                   String runtimeResponsibilityContract) {
        this(serviceName,
                serviceKind,
                transactionMode,
                resourceId,
                attributeIds,
                attributeCount,
                pairDeclared,
                conservationClosureCandidate,
                readiness,
                List.of(),
                missingReasons,
                actions,
                conservationContract,
                implementationStrategies,
                implementationStrategySet,
                idempotencyOwner,
                reversalOwner,
                snapshotBoundary,
                recoveryOwner,
                negativeDeltaReversal,
                runtimeResponsibilityContract);
    }

    public QuantumConservationPlan {
        serviceName = normalize(serviceName);
        resourceId = normalize(resourceId);
        attributeIds = normalizeList(attributeIds);
        readyReasons = normalizeList(readyReasons);
        missingReasons = normalizeList(missingReasons);
        actions = normalizeList(actions);
        conservationContract = normalize(conservationContract);
        implementationStrategies = normalizeList(implementationStrategies);
        implementationStrategySet = normalize(implementationStrategySet);
        idempotencyOwner = normalize(idempotencyOwner);
        reversalOwner = normalize(reversalOwner);
        snapshotBoundary = normalize(snapshotBoundary);
        recoveryOwner = normalize(recoveryOwner);
        runtimeResponsibilityContract = normalize(runtimeResponsibilityContract);
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
}
