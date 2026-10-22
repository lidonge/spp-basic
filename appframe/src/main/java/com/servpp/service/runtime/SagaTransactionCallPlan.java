package com.servpp.service.runtime;

import java.util.List;

import com.servpp.service.transaction.TransactionMode;

public record SagaTransactionCallPlan(String sagaServiceName,
                                      String targetServiceName,
                                      boolean targetRegistered,
                                      boolean targetLocal,
                                      boolean targetRemote,
                                      GeneratedServiceKind targetServiceKind,
                                      TransactionMode targetTransactionMode,
                                      boolean localAtomicUpdateTransaction,
                                      int argumentSnapshotRuleCount,
                                      int currentSnapshotRuleCount,
                                      List<String> argumentSnapshotSavePhases,
                                      List<String> rollbackRecoverySavePhases,
                                      List<SagaRecoveryEvidencePlan> argumentRecoveryEvidencePlans,
                                      List<SagaRecoveryEvidencePlan> rollbackRecoveryEvidencePlans,
                                      boolean argumentRecoveryEvidence,
                                      boolean rollbackRecoveryEvidence,
                                      boolean quantumBoundaryRecoveryEvidence,
                                      String quantumBoundaryEvidencePersistence,
                                      String quantumReversalExecution,
                                      List<String> implementationStrategies,
                                      String recoveryBoundary,
                                      String recoveryOwner,
                                      boolean objectSnapshotRequired,
                                      String recoveryReadiness,
                                      List<String> recoveryReadyReasons,
                                      List<String> recoveryMissingReasons,
                                      List<String> recoveryActions,
                                      String callPath,
                                      String recoveryContract) {
    public SagaTransactionCallPlan {
        argumentSnapshotSavePhases = argumentSnapshotSavePhases == null
                ? List.of()
                : List.copyOf(argumentSnapshotSavePhases);
        rollbackRecoverySavePhases = rollbackRecoverySavePhases == null
                ? List.of()
                : List.copyOf(rollbackRecoverySavePhases);
        argumentRecoveryEvidencePlans = argumentRecoveryEvidencePlans == null
                ? List.of()
                : List.copyOf(argumentRecoveryEvidencePlans);
        rollbackRecoveryEvidencePlans = rollbackRecoveryEvidencePlans == null
                ? List.of()
                : List.copyOf(rollbackRecoveryEvidencePlans);
        recoveryReadyReasons = recoveryReadyReasons == null ? List.of() : List.copyOf(recoveryReadyReasons);
        recoveryMissingReasons = recoveryMissingReasons == null ? List.of() : List.copyOf(recoveryMissingReasons);
        recoveryActions = recoveryActions == null ? List.of() : List.copyOf(recoveryActions);
        quantumBoundaryEvidencePersistence = normalize(quantumBoundaryEvidencePersistence);
        quantumReversalExecution = normalize(quantumReversalExecution);
        implementationStrategies = implementationStrategies == null
                ? List.of()
                : List.copyOf(implementationStrategies);
        recoveryBoundary = normalize(recoveryBoundary);
        recoveryOwner = normalize(recoveryOwner);
    }

    public SagaTransactionCallPlan(String sagaServiceName,
                                   String targetServiceName,
                                   boolean targetRegistered,
                                   boolean targetLocal,
                                   boolean targetRemote,
                                   GeneratedServiceKind targetServiceKind,
                                   TransactionMode targetTransactionMode,
                                   boolean localAtomicUpdateTransaction,
                                   int argumentSnapshotRuleCount,
                                   int currentSnapshotRuleCount,
                                   List<String> argumentSnapshotSavePhases,
                                   List<String> rollbackRecoverySavePhases,
                                   List<SagaRecoveryEvidencePlan> argumentRecoveryEvidencePlans,
                                   List<SagaRecoveryEvidencePlan> rollbackRecoveryEvidencePlans,
                                   boolean argumentRecoveryEvidence,
                                   boolean rollbackRecoveryEvidence) {
        this(sagaServiceName, targetServiceName, targetRegistered, targetLocal, targetRemote,
                targetServiceKind, targetTransactionMode, localAtomicUpdateTransaction,
                argumentSnapshotRuleCount, currentSnapshotRuleCount, argumentSnapshotSavePhases,
                rollbackRecoverySavePhases, argumentRecoveryEvidencePlans, rollbackRecoveryEvidencePlans,
                argumentRecoveryEvidence, rollbackRecoveryEvidence, false, "", "",
                List.of(), "OBJECT_SNAPSHOT_STORE", "FRAMEWORK", true,
                null, List.of(), List.of(),
                List.of(), null, null);
    }

    public boolean sourceScenarioTransaction() {
        return targetLocal && !targetRemote && targetServiceKind == GeneratedServiceKind.SCENARIO
                && "SPP_DECLARED_SCENARIO".equals(recoveryBoundary);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
