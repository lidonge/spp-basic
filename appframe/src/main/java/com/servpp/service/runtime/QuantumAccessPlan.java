package com.servpp.service.runtime;

import java.util.ArrayList;
import java.util.List;

import com.servpp.service.transaction.TransactionMode;

public record QuantumAccessPlan(String serviceName,
                                TransactionMode transactionMode,
                                String transactionResourceId,
                                List<String> attributeIds,
                                List<String> storeNames,
                                List<String> adapterNames,
                                List<String> implementationStrategies,
                                boolean singleImplementationStrategy,
                                boolean mixedImplementationStrategy,
                                List<String> accessModes,
                                List<String> executionOwners,
                                List<String> backendBindingRoles,
                                String idempotencyMode,
                                String idempotencyOwner,
                                String idempotencyBoundary,
                                boolean idempotencyKeyAvailable,
                                List<String> reversalModes,
                                List<String> reversalOwners,
                                List<String> reversalExecutions,
                                String snapshotBoundary,
                                String recoveryOwner,
                                boolean objectSnapshotManaged,
                                boolean objectCompensationManaged,
                                boolean objectRecoveryRequired,
                                boolean objectSnapshotBypassed,
                                boolean objectCompensationBypassed,
                                boolean serviceInternalSnapshotManaged,
                                boolean serviceInternalIdempotencyManaged,
                                boolean serviceInternalReversalManaged,
                                boolean negativeDeltaReversal,
                                boolean serviceReversal,
                                List<String> endpoints,
                                List<String> timeoutMillisValues,
                                List<String> datasourceUrls,
                                boolean accessContractAvailable,
                                List<String> missingContractReasons,
                                String responsibilityReadiness,
                                List<String> responsibilityMissingReasons,
                                List<String> responsibilityActions,
                                String implementationStrategySet,
                                String responsibilityBoundary,
                                String accessContract,
                                String serviceInternalProtocolContract) {
    public QuantumAccessPlan(String serviceName,
                             TransactionMode transactionMode,
                             String transactionResourceId,
                             List<String> attributeIds,
                             List<String> storeNames,
                             List<String> adapterNames,
                             List<String> implementationStrategies,
                             boolean singleImplementationStrategy,
                             boolean mixedImplementationStrategy,
                             List<String> accessModes,
                             List<String> executionOwners,
                             List<String> backendBindingRoles,
                             String idempotencyMode,
                             String idempotencyOwner,
                             String idempotencyBoundary,
                             boolean idempotencyKeyAvailable,
                             List<String> reversalModes,
                             List<String> reversalOwners,
                             List<String> reversalExecutions,
                             String snapshotBoundary,
                             String recoveryOwner,
                             boolean objectSnapshotManaged,
                             boolean objectCompensationManaged,
                             boolean objectRecoveryRequired,
                             boolean objectSnapshotBypassed,
                             boolean objectCompensationBypassed,
                             boolean serviceInternalSnapshotManaged,
                             boolean serviceInternalIdempotencyManaged,
                             boolean serviceInternalReversalManaged,
                             boolean negativeDeltaReversal,
                             boolean serviceReversal,
                             List<String> endpoints,
                             List<String> timeoutMillisValues,
                             List<String> datasourceUrls,
                             boolean accessContractAvailable,
                             List<String> missingContractReasons) {
        this(serviceName,
                transactionMode,
                transactionResourceId,
                attributeIds,
                storeNames,
                adapterNames,
                implementationStrategies,
                singleImplementationStrategy,
                mixedImplementationStrategy,
                accessModes,
                executionOwners,
                backendBindingRoles,
                idempotencyMode,
                idempotencyOwner,
                idempotencyBoundary,
                idempotencyKeyAvailable,
                reversalModes,
                reversalOwners,
                reversalExecutions,
                snapshotBoundary,
                recoveryOwner,
                objectSnapshotManaged,
                objectCompensationManaged,
                objectRecoveryRequired,
                objectSnapshotBypassed,
                objectCompensationBypassed,
                serviceInternalSnapshotManaged,
                serviceInternalIdempotencyManaged,
                serviceInternalReversalManaged,
                negativeDeltaReversal,
                serviceReversal,
                endpoints,
                timeoutMillisValues,
                datasourceUrls,
                accessContractAvailable,
                missingContractReasons,
                "",
                List.of(),
                List.of(),
                "",
                "",
                "",
                "");
    }

    public QuantumAccessPlan {
        serviceName = normalize(serviceName);
        transactionResourceId = normalize(transactionResourceId);
        attributeIds = normalizeList(attributeIds);
        storeNames = normalizeList(storeNames);
        adapterNames = normalizeList(adapterNames);
        implementationStrategies = normalizeList(implementationStrategies);
        accessModes = normalizeList(accessModes);
        executionOwners = normalizeList(executionOwners);
        backendBindingRoles = normalizeList(backendBindingRoles);
        idempotencyMode = normalize(idempotencyMode);
        idempotencyOwner = normalize(idempotencyOwner);
        idempotencyBoundary = normalize(idempotencyBoundary);
        reversalModes = normalizeList(reversalModes);
        reversalOwners = normalizeList(reversalOwners);
        reversalExecutions = normalizeList(reversalExecutions);
        snapshotBoundary = normalize(snapshotBoundary);
        recoveryOwner = normalize(recoveryOwner);
        endpoints = normalizeList(endpoints);
        timeoutMillisValues = normalizeList(timeoutMillisValues);
        datasourceUrls = normalizeList(datasourceUrls);
        missingContractReasons = normalizeList(missingContractReasons);
        responsibilityReadiness = normalize(responsibilityReadiness);
        responsibilityMissingReasons = normalizeList(responsibilityMissingReasons);
        responsibilityActions = normalizeList(responsibilityActions);
        implementationStrategySet = normalize(implementationStrategySet);
        responsibilityBoundary = normalize(responsibilityBoundary);
        accessContract = normalize(accessContract);
        serviceInternalProtocolContract = normalize(serviceInternalProtocolContract);
        if (responsibilityMissingReasons.isEmpty()) {
            responsibilityMissingReasons = responsibilityMissingReasons(accessContractAvailable,
                    idempotencyOwner, idempotencyBoundary, serviceInternalIdempotencyManaged,
                    reversalOwners, reversalExecutions, negativeDeltaReversal, serviceReversal,
                    objectCompensationBypassed, serviceInternalReversalManaged, snapshotBoundary,
                    objectSnapshotBypassed, serviceInternalSnapshotManaged, recoveryOwner);
        }
        if (responsibilityReadiness.isBlank()) {
            responsibilityReadiness = responsibilityMissingReasons.isEmpty()
                    ? "RESPONSIBILITY_COMPLETE"
                    : "RESPONSIBILITY_INCOMPLETE";
        }
        if (responsibilityMissingReasons.isEmpty()
                && "RESPONSIBILITY_COMPLETE".equals(responsibilityReadiness)) {
            responsibilityMissingReasons = List.of("NO_MISSING_RESPONSIBILITY_REASON");
        }
        if (responsibilityActions.isEmpty()) {
            responsibilityActions = "RESPONSIBILITY_COMPLETE".equals(responsibilityReadiness)
                    ? List.of("NO_ACTION_REQUIRED")
                    : responsibilityActions(responsibilityMissingReasons);
        }
        if (implementationStrategySet.isBlank()) {
            implementationStrategySet = listKey(implementationStrategies, "NO_IMPLEMENTATION_STRATEGY");
        }
        if (responsibilityBoundary.isBlank()) {
            responsibilityBoundary = String.join("+",
                    key(idempotencyOwner),
                    listKey(reversalOwners, "NO_REVERSAL_OWNER"),
                    key(snapshotBoundary),
                    key(recoveryOwner));
        }
        if (accessContract.isBlank()) {
            accessContract = accessContract(serviceName, attributeIds, implementationStrategies,
                    idempotencyOwner, reversalOwners, accessContractAvailable);
        }
        if (serviceInternalProtocolContract.isBlank()) {
            serviceInternalProtocolContract = serviceInternalProtocolContract(serviceName,
                    implementationStrategies, serviceInternalIdempotencyManaged,
                    serviceInternalSnapshotManaged, serviceInternalReversalManaged,
                    negativeDeltaReversal, accessContractAvailable);
        }
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

    private static String accessContract(String serviceName,
                                         List<String> attributeIds,
                                         List<String> implementationStrategies,
                                         String idempotencyOwner,
                                         List<String> reversalOwners,
                                         boolean accessContractAvailable) {
        return key(serviceName)
                + ":" + listKey(attributeIds, "NO_QUANTUM_ATTRIBUTE")
                + "->" + listKey(implementationStrategies, "NO_IMPLEMENTATION_STRATEGY")
                + "+" + key(idempotencyOwner)
                + "+" + listKey(reversalOwners, "NO_REVERSAL_OWNER")
                + "+" + (accessContractAvailable ? "ACCESS_CONTRACT_AVAILABLE" : "ACCESS_CONTRACT_MISSING");
    }

    private static String serviceInternalProtocolContract(String serviceName,
                                                          List<String> implementationStrategies,
                                                          boolean serviceInternalIdempotencyManaged,
                                                          boolean serviceInternalSnapshotManaged,
                                                          boolean serviceInternalReversalManaged,
                                                          boolean negativeDeltaReversal,
                                                          boolean accessContractAvailable) {
        boolean quantumService = implementationStrategies != null
                && implementationStrategies.stream().anyMatch("QUANTUM_SERVICE"::equals);
        if (!quantumService) {
            return key(serviceName) + ":NOT_QUANTUM_SERVICE";
        }
        String idempotency = serviceInternalIdempotencyManaged
                ? "SERVICE_IDEMPOTENCY"
                : "MISSING_SERVICE_IDEMPOTENCY";
        String snapshot = serviceInternalSnapshotManaged
                ? "SERVICE_SNAPSHOT"
                : "MISSING_SERVICE_SNAPSHOT";
        String reversal = serviceInternalReversalManaged
                ? "SERVICE_REVERSAL_INTERNAL"
                : "MISSING_SERVICE_REVERSAL_INTERNAL";
        String delta = negativeDeltaReversal
                ? "NEGATIVE_DELTA_REVERSAL"
                : "MISSING_NEGATIVE_DELTA_REVERSAL";
        String contract = accessContractAvailable
                ? "ACCESS_CONTRACT_AVAILABLE"
                : "ACCESS_CONTRACT_MISSING";
        return key(serviceName) + ":" + idempotency + "+" + snapshot + "+" + delta + "+"
                + reversal + "+" + contract;
    }

    private static List<String> responsibilityMissingReasons(boolean accessContractAvailable,
                                                             String idempotencyOwner,
                                                             String idempotencyBoundary,
                                                             boolean serviceInternalIdempotencyManaged,
                                                             List<String> reversalOwners,
                                                             List<String> reversalExecutions,
                                                             boolean negativeDeltaReversal,
                                                             boolean serviceReversal,
                                                             boolean objectCompensationBypassed,
                                                             boolean serviceInternalReversalManaged,
                                                             String snapshotBoundary,
                                                             boolean objectSnapshotBypassed,
                                                             boolean serviceInternalSnapshotManaged,
                                                             String recoveryOwner) {
        List<String> missing = new ArrayList<>();
        if (!accessContractAvailable) {
            missing.add("accessContract");
        }
        if (idempotencyOwner.isBlank() || idempotencyBoundary.isBlank()) {
            missing.add("idempotencyOwner");
        }
        if ("QUANTUM_SERVICE".equals(idempotencyOwner) && !serviceInternalIdempotencyManaged) {
            missing.add("serviceInternalIdempotency");
        }
        if (reversalOwners.isEmpty() || reversalExecutions.isEmpty()) {
            missing.add("reversalOwner");
        }
        if (!negativeDeltaReversal && !serviceReversal) {
            missing.add("reversalContract");
        }
        if (objectCompensationBypassed && !serviceInternalReversalManaged) {
            missing.add("serviceInternalReversal");
        }
        if (snapshotBoundary.isBlank()) {
            missing.add("snapshotBoundary");
        }
        if (objectSnapshotBypassed && !serviceInternalSnapshotManaged) {
            missing.add("serviceInternalSnapshot");
        }
        if (recoveryOwner.isBlank()) {
            missing.add("recoveryOwner");
        }
        return List.copyOf(missing);
    }

    private static List<String> responsibilityActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        List<String> actions = new ArrayList<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "accessContract" -> actions.add("fixQuantumAccessContract");
                case "idempotencyOwner" -> actions.add("declareQuantumIdempotencyOwner");
                case "serviceInternalIdempotency" -> actions.add("idempotencyBoundary=QUANTUM_SERVICE_INTERNAL");
                case "reversalOwner" -> actions.add("declareQuantumReversalOwner");
                case "reversalContract" -> actions.add("declareNegativeDeltaOrServiceReversal");
                case "serviceInternalReversal" -> {
                    actions.add("reversalExecution=INPUT_NEGATIVE_DELTA");
                    actions.add("objectCompensation=false");
                    actions.add("objectRecoveryRequired=false");
                }
                case "snapshotBoundary" -> actions.add("declareQuantumSnapshotBoundary");
                case "serviceInternalSnapshot" -> {
                    actions.add("snapshotBoundary=QUANTUM_SERVICE_AUDIT_EVIDENCE");
                    actions.add("objectSnapshotManaged=false");
                    actions.add("objectRecoveryRequired=false");
                }
                case "recoveryOwner" -> actions.add("declareQuantumRecoveryOwner");
                default -> actions.add("reviewQuantumAccessResponsibility");
            }
        }
        return List.copyOf(actions);
    }

    private static String listKey(List<String> values, String fallback) {
        List<String> keys = values == null
                ? List.of()
                : values.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(QuantumAccessPlan::key)
                        .distinct()
                        .toList();
        return keys.isEmpty() ? fallback : String.join("+", keys);
    }

    private static String key(String value) {
        return value == null || value.isBlank() ? "UNASSIGNED" : value;
    }
}
