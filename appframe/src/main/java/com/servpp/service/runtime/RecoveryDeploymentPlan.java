package com.servpp.service.runtime;

import java.util.List;

public record RecoveryDeploymentPlan(String planName,
                                     boolean unifiedWorkItemView,
                                     List<String> workItemKinds,
                                     List<String> evidenceSources,
                                     boolean tccRecoveryAvailable,
                                     boolean remoteCompensationRecoveryAvailable,
                                     List<String> tccTryServiceNames,
                                     List<String> remoteCompensationSourceServiceNames,
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
                                     String deploymentContract,
                                     String runtimeContract) {
    public RecoveryDeploymentPlan(String planName,
                                  boolean unifiedWorkItemView,
                                  List<String> workItemKinds,
                                  List<String> evidenceSources,
                                  boolean tccRecoveryAvailable,
                                  boolean remoteCompensationRecoveryAvailable,
                                  List<String> tccTryServiceNames,
                                  List<String> remoteCompensationSourceServiceNames,
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
                                  String tccReservationTtlMillis) {
        this(planName,
                unifiedWorkItemView,
                workItemKinds,
                evidenceSources,
                tccRecoveryAvailable,
                remoteCompensationRecoveryAvailable,
                tccTryServiceNames,
                remoteCompensationSourceServiceNames,
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
                List.of(),
                List.of(),
                List.of(),
                "",
                "");
    }

    public RecoveryDeploymentPlan {
        planName = normalize(planName);
        workItemKinds = workItemKinds == null ? List.of() : List.copyOf(workItemKinds);
        evidenceSources = evidenceSources == null ? List.of() : List.copyOf(evidenceSources);
        tccTryServiceNames = tccTryServiceNames == null ? List.of() : List.copyOf(tccTryServiceNames);
        remoteCompensationSourceServiceNames = remoteCompensationSourceServiceNames == null
                ? List.of()
                : List.copyOf(remoteCompensationSourceServiceNames);
        backendName = normalize(backendName);
        backendAdapterName = normalize(backendAdapterName);
        backendDatasourceUrl = normalize(backendDatasourceUrl);
        backendDriverClassName = normalize(backendDriverClassName);
        sagaLogStoreName = normalize(sagaLogStoreName);
        sagaLogTableName = normalize(sagaLogTableName);
        tccBranchStoreName = normalize(tccBranchStoreName);
        tccBranchTableName = normalize(tccBranchTableName);
        tccReservationStoreName = normalize(tccReservationStoreName);
        tccReservationTableName = normalize(tccReservationTableName);
        tccReservationTtlMillis = normalize(tccReservationTtlMillis);
        readiness = normalize(readiness);
        readyReasons = readyReasons == null ? List.of() : List.copyOf(readyReasons);
        missingReasons = missingReasons == null ? List.of() : List.copyOf(missingReasons);
        actions = actions == null ? List.of() : List.copyOf(actions);
        deploymentContract = normalize(deploymentContract);
        runtimeContract = normalize(runtimeContract);
        if (missingReasons.isEmpty()) {
            missingReasons = missingReasons(unifiedWorkItemView, workItemKinds, evidenceSources, backendName,
                    backendDatasourceUrl, backendDriverClassName, tccRecoveryAvailable,
                    remoteCompensationRecoveryAvailable, tccBranchStoreName, tccBranchTableName,
                    tccReservationStoreName, tccReservationTableName, tccReservationTtlMillis,
                    sagaLogStoreName, sagaLogTableName);
        }
        if (readiness.isBlank()) {
            readiness = missingReasons.isEmpty()
                    ? "RECOVERY_DEPLOYMENT_READY"
                    : "RECOVERY_DEPLOYMENT_INCOMPLETE";
        }
        if (readyReasons.isEmpty() && "RECOVERY_DEPLOYMENT_READY".equals(readiness)) {
            readyReasons = readyReasons(unifiedWorkItemView, workItemKinds, evidenceSources, backendName,
                    backendDatasourceUrl, backendDriverClassName, tccRecoveryAvailable,
                    remoteCompensationRecoveryAvailable, tccBranchStoreName, tccBranchTableName,
                    tccReservationStoreName, tccReservationTableName, tccReservationTtlMillis,
                    sagaLogStoreName, sagaLogTableName);
        }
        if (missingReasons.isEmpty() && "RECOVERY_DEPLOYMENT_READY".equals(readiness)) {
            missingReasons = List.of("NO_MISSING_RECOVERY_DEPLOYMENT_REASON");
        }
        if (actions.isEmpty()) {
            actions = "RECOVERY_DEPLOYMENT_READY".equals(readiness)
                    ? List.of("NO_ACTION_REQUIRED")
                    : actions(missingReasons);
        }
        if (deploymentContract.isBlank()) {
            deploymentContract = deploymentContract(planName, workItemKinds, evidenceSources,
                    unifiedWorkItemView, readiness);
        }
        if (runtimeContract.isBlank()) {
            runtimeContract = runtimeContract(planName, backendName, backendDatasourceUrl, sagaLogTableName,
                    tccBranchTableName, tccReservationTableName, tccReservationTtlMillis, readiness);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String deploymentContract(String planName,
                                             List<String> workItemKinds,
                                             List<String> evidenceSources,
                                             boolean unifiedWorkItemView,
                                             String readiness) {
        return key(planName)
                + ":" + listKey(workItemKinds, "NO_WORK_ITEM_KIND")
                + ":" + listKey(evidenceSources, "NO_EVIDENCE_SOURCE")
                + ":" + (unifiedWorkItemView ? "UNIFIED_WORK_ITEM_VIEW" : "NO_UNIFIED_WORK_ITEM_VIEW")
                + ":" + key(readiness);
    }

    private static String runtimeContract(String planName,
                                          String backendName,
                                          String backendDatasourceUrl,
                                          String sagaLogTableName,
                                          String tccBranchTableName,
                                          String tccReservationTableName,
                                          String tccReservationTtlMillis,
                                          String readiness) {
        return key(planName)
                + "->" + key(backendName)
                + "+datasource=" + key(backendDatasourceUrl)
                + "+sagaLog=" + key(sagaLogTableName)
                + "+tccBranch=" + key(tccBranchTableName)
                + "+tccReservation=" + key(tccReservationTableName)
                + "+ttl=" + key(tccReservationTtlMillis)
                + ":" + key(readiness);
    }

    private static List<String> readyReasons(boolean unifiedWorkItemView,
                                             List<String> workItemKinds,
                                             List<String> evidenceSources,
                                             String backendName,
                                             String backendDatasourceUrl,
                                             String backendDriverClassName,
                                             boolean tccRecoveryAvailable,
                                             boolean remoteCompensationRecoveryAvailable,
                                             String tccBranchStoreName,
                                             String tccBranchTableName,
                                             String tccReservationStoreName,
                                             String tccReservationTableName,
                                             String tccReservationTtlMillis,
                                             String sagaLogStoreName,
                                             String sagaLogTableName) {
        java.util.ArrayList<String> reasons = new java.util.ArrayList<>();
        if (unifiedWorkItemView) {
            reasons.add("unifiedWorkItemView");
        }
        if (!workItemKinds.isEmpty()) {
            reasons.add("workItemKinds");
        }
        if (!evidenceSources.isEmpty()) {
            reasons.add("evidenceSources");
        }
        if (!backendName.isBlank() && !backendDatasourceUrl.isBlank() && !backendDriverClassName.isBlank()) {
            reasons.add("backendBinding");
        }
        if (!workItemKinds.contains("TCC")
                || tccEvidenceReady(tccRecoveryAvailable, tccBranchStoreName, tccBranchTableName,
                tccReservationStoreName, tccReservationTableName, tccReservationTtlMillis)) {
            reasons.add("tccRecoveryEvidence");
        }
        if (!workItemKinds.contains("REMOTE_COMPENSATION")
                || remoteCompensationEvidenceReady(remoteCompensationRecoveryAvailable,
                sagaLogStoreName, sagaLogTableName)) {
            reasons.add("remoteCompensationEvidence");
        }
        return List.copyOf(reasons);
    }

    private static List<String> missingReasons(boolean unifiedWorkItemView,
                                               List<String> workItemKinds,
                                               List<String> evidenceSources,
                                               String backendName,
                                               String backendDatasourceUrl,
                                               String backendDriverClassName,
                                               boolean tccRecoveryAvailable,
                                               boolean remoteCompensationRecoveryAvailable,
                                               String tccBranchStoreName,
                                               String tccBranchTableName,
                                               String tccReservationStoreName,
                                               String tccReservationTableName,
                                               String tccReservationTtlMillis,
                                               String sagaLogStoreName,
                                               String sagaLogTableName) {
        java.util.ArrayList<String> missing = new java.util.ArrayList<>();
        if (!unifiedWorkItemView) {
            missing.add("unifiedWorkItemView");
        }
        if (workItemKinds.isEmpty()) {
            missing.add("workItemKinds");
        }
        if (evidenceSources.isEmpty()) {
            missing.add("evidenceSources");
        }
        if (backendName.isBlank() || backendDatasourceUrl.isBlank() || backendDriverClassName.isBlank()) {
            missing.add("backendBinding");
        }
        if (workItemKinds.contains("TCC")
                && !tccEvidenceReady(tccRecoveryAvailable, tccBranchStoreName, tccBranchTableName,
                tccReservationStoreName, tccReservationTableName, tccReservationTtlMillis)) {
            missing.add("tccRecoveryEvidence");
        }
        if (workItemKinds.contains("REMOTE_COMPENSATION")
                && !remoteCompensationEvidenceReady(remoteCompensationRecoveryAvailable,
                sagaLogStoreName, sagaLogTableName)) {
            missing.add("remoteCompensationEvidence");
        }
        return List.copyOf(missing);
    }

    private static boolean tccEvidenceReady(boolean tccRecoveryAvailable,
                                            String tccBranchStoreName,
                                            String tccBranchTableName,
                                            String tccReservationStoreName,
                                            String tccReservationTableName,
                                            String tccReservationTtlMillis) {
        return tccRecoveryAvailable
                && !tccBranchStoreName.isBlank()
                && !tccBranchTableName.isBlank()
                && !tccReservationStoreName.isBlank()
                && !tccReservationTableName.isBlank()
                && !tccReservationTtlMillis.isBlank();
    }

    private static boolean remoteCompensationEvidenceReady(boolean remoteCompensationRecoveryAvailable,
                                                           String sagaLogStoreName,
                                                           String sagaLogTableName) {
        return remoteCompensationRecoveryAvailable
                && !sagaLogStoreName.isBlank()
                && !sagaLogTableName.isBlank();
    }

    private static List<String> actions(List<String> missingReasons) {
        java.util.LinkedHashSet<String> actions = new java.util.LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "unifiedWorkItemView" -> actions.add("enableUnifiedRecoveryWorkItemView");
                case "workItemKinds" -> actions.add("bindRecoveryWorkItemKinds");
                case "evidenceSources" -> actions.add("bindRecoveryEvidenceSources");
                case "backendBinding" -> actions.add("bindRecoveryDeploymentBackend");
                case "tccRecoveryEvidence" -> actions.add("configureTccRecoveryEvidenceStores");
                case "remoteCompensationEvidence" ->
                        actions.add("configureRemoteCompensationRecoveryEvidenceStore");
                default -> actions.add("resolveRecoveryDeploymentBinding");
            }
        }
        return actions.isEmpty() ? List.of("NO_ACTION_REQUIRED") : List.copyOf(actions);
    }

    private static String listKey(List<String> values, String fallback) {
        List<String> keys = values == null
                ? List.of()
                : values.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(RecoveryDeploymentPlan::key)
                        .distinct()
                        .toList();
        return keys.isEmpty() ? fallback : String.join("+", keys);
    }

    private static String key(String value) {
        return value == null || value.isBlank() ? "UNASSIGNED" : value;
    }
}
