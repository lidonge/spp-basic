package com.servpp.service.runtime;

import java.util.List;

public record RecoveryWorkItemPlan(String workItemKind,
                                   boolean recoveryAvailable,
                                   List<String> sourceServiceNames,
                                   List<String> supportedDecisions,
                                   List<String> suggestedActions,
                                   String workItemSource,
                                   String schedulerPropertyPrefix,
                                   boolean schedulerEnabledByDefault,
                                   String schedulerEnabledPropertyName,
                                   String schedulerFixedDelayMillisPropertyName,
                                   long defaultSchedulerFixedDelayMillis,
                                   String manualRecoveryEndpointPath,
                                   String backendName,
                                   String backendAdapterName,
                                   String backendDatasourceUrl,
                                   String backendDriverClassName,
                                   String sagaLogStoreName,
                                   String sagaLogTableName,
                                   String tccBranchStoreName,
                                   String tccBranchTableName,
                                   String tccReservationStoreName,
                                   String tccReservationTableName,
                                   String tccReservationTtlMillis,
                                   String nativeSagaStateMachineInstanceTableName,
                                   String nativeSagaStateInstanceTableName,
                                   String recoveryLeaseTableName,
                                   String readiness,
                                   List<String> readyReasons,
                                   List<String> missingReasons,
                                   List<String> actions,
                                   String executionContract,
                                   List<String> physicalEvidencePaths) {
    public RecoveryWorkItemPlan(String workItemKind,
                                boolean recoveryAvailable,
                                List<String> sourceServiceNames,
                                List<String> supportedDecisions,
                                List<String> suggestedActions,
                                String workItemSource,
                                String schedulerPropertyPrefix,
                                boolean schedulerEnabledByDefault,
                                String schedulerEnabledPropertyName,
                                String schedulerFixedDelayMillisPropertyName,
                                long defaultSchedulerFixedDelayMillis,
                                String manualRecoveryEndpointPath,
                                String backendName,
                                String backendAdapterName,
                                String backendDatasourceUrl,
                                String backendDriverClassName,
                                String sagaLogStoreName,
                                String sagaLogTableName,
                                String tccBranchStoreName,
                                String tccBranchTableName,
                                String tccReservationStoreName,
                                String tccReservationTableName,
                                String tccReservationTtlMillis,
                                String readiness,
                                List<String> readyReasons,
                                List<String> missingReasons,
                                List<String> actions,
                                String executionContract,
                                List<String> physicalEvidencePaths) {
        this(workItemKind,
                recoveryAvailable,
                sourceServiceNames,
                supportedDecisions,
                suggestedActions,
                workItemSource,
                schedulerPropertyPrefix,
                schedulerEnabledByDefault,
                schedulerEnabledPropertyName,
                schedulerFixedDelayMillisPropertyName,
                defaultSchedulerFixedDelayMillis,
                manualRecoveryEndpointPath,
                backendName,
                backendAdapterName,
                backendDatasourceUrl,
                backendDriverClassName,
                sagaLogStoreName,
                sagaLogTableName,
                tccBranchStoreName,
                tccBranchTableName,
                tccReservationStoreName,
                tccReservationTableName,
                tccReservationTtlMillis,
                "",
                "",
                "",
                readiness,
                readyReasons,
                missingReasons,
                actions,
                executionContract,
                physicalEvidencePaths);
    }

    public RecoveryWorkItemPlan {
        sourceServiceNames = sourceServiceNames == null ? List.of() : List.copyOf(sourceServiceNames);
        supportedDecisions = supportedDecisions == null ? List.of() : List.copyOf(supportedDecisions);
        suggestedActions = suggestedActions == null ? List.of() : List.copyOf(suggestedActions);
        nativeSagaStateMachineInstanceTableName =
                normalize(nativeSagaStateMachineInstanceTableName);
        nativeSagaStateInstanceTableName = normalize(nativeSagaStateInstanceTableName);
        recoveryLeaseTableName = normalize(recoveryLeaseTableName);
        readyReasons = readyReasons == null ? List.of() : List.copyOf(readyReasons);
        missingReasons = missingReasons == null ? List.of() : List.copyOf(missingReasons);
        actions = actions == null ? List.of() : List.copyOf(actions);
        physicalEvidencePaths = physicalEvidencePaths == null ? List.of() : List.copyOf(physicalEvidencePaths);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
