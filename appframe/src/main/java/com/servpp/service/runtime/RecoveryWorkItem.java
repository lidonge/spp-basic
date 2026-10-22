package com.servpp.service.runtime;

import java.util.List;

public record RecoveryWorkItem(String kind,
                               String workId,
                               String globalSerialNumber,
                               String serviceName,
                               String status,
                               String message,
                               String createdAt,
                               String suggestedAction,
                               String actionReason,
                               boolean recoveryPayloadAvailable,
                               boolean manualInterventionRequired,
                               boolean expired,
                               int argumentSnapshotRuleCount,
                               List<String> argumentSnapshotSavePhases,
                               List<String> rollbackRecoverySavePhases,
                               TccRecoveryCandidate tccCandidate,
                               RemoteCompensationRecoveryCandidate remoteCompensationCandidate) {
    public RecoveryWorkItem {
        argumentSnapshotSavePhases = argumentSnapshotSavePhases == null
                ? List.of()
                : List.copyOf(argumentSnapshotSavePhases);
        rollbackRecoverySavePhases = rollbackRecoverySavePhases == null
                ? List.of()
                : List.copyOf(rollbackRecoverySavePhases);
    }

    public static RecoveryWorkItem tcc(TccRecoveryCandidate candidate) {
        boolean manualInterventionRequired = !candidate.lifecycleAvailable() || !candidate.recoveryPayloadAvailable();
        String suggestedAction;
        String actionReason;
        if (manualInterventionRequired) {
            suggestedAction = "MANUAL";
            actionReason = !candidate.lifecycleAvailable()
                    ? "TCC lifecycle service is not available."
                    : "TCC recovery payload is not available.";
        } else if (candidate.expiredReservationAvailable()) {
            suggestedAction = "CANCEL";
            actionReason = "TCC reservation has expired.";
        } else {
            suggestedAction = "CONFIRM";
            actionReason = "TCC branch is still trying and has replay payload.";
        }
        return new RecoveryWorkItem("TCC",
                candidate.branch().branchId(),
                candidate.branch().globalSerialNumber(),
                candidate.tryServiceName(),
                candidate.branch().status().name(),
                candidate.branch().message(),
                candidate.branch().updatedAt(),
                suggestedAction,
                actionReason,
                candidate.recoveryPayloadAvailable(),
                manualInterventionRequired,
                candidate.expiredReservationAvailable(),
                candidate.argumentSnapshotRuleCount(),
                candidate.argumentSnapshotSavePhases(),
                candidate.rollbackRecoverySavePhases(),
                candidate,
                null);
    }

    public static RecoveryWorkItem remoteCompensation(RemoteCompensationRecoveryCandidate candidate) {
        String suggestedAction;
        String actionReason;
        if (candidate.manualInterventionRequired()) {
            suggestedAction = "MANUAL";
            actionReason = "Remote compensation policy requires manual intervention.";
        } else if (candidate.recoveryPayloadAvailable()) {
            suggestedAction = "RETRY";
            actionReason = "Remote compensation failed and replay payload is available.";
        } else {
            suggestedAction = "MANUAL";
            actionReason = "Remote compensation replay payload is not available.";
        }
        return new RecoveryWorkItem("REMOTE_COMPENSATION",
                candidate.sagaId(),
                candidate.globalSerialNumber(),
                candidate.serviceName(),
                candidate.status(),
                candidate.message(),
                candidate.createdAt(),
                suggestedAction,
                actionReason,
                candidate.recoveryPayloadAvailable(),
                candidate.manualInterventionRequired(),
                false,
                0,
                List.of(),
                List.of(),
                null,
                candidate);
    }

    public static RecoveryWorkItem nativeSaga(String backendName,
                                              List<String> sagaServiceNames) {
        String normalizedBackend = backendName == null || backendName.isBlank()
                ? "native-saga"
                : backendName.trim();
        List<String> services = sagaServiceNames == null ? List.of() : List.copyOf(sagaServiceNames);
        return new RecoveryWorkItem("NATIVE_SAGA",
                "native-saga:" + normalizedBackend,
                "",
                String.join(",", services),
                "SCAN_READY",
                "Native Saga persistent recovery scan is ready.",
                "",
                "SCAN",
                "Scan stale Seata Saga instances and recover them under an exclusive lease.",
                true,
                false,
                false,
                0,
                List.of(),
                List.of(),
                null,
                null);
    }
}
