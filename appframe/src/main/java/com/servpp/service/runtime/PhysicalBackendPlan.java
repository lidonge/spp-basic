package com.servpp.service.runtime;

import java.util.ArrayList;
import java.util.List;

public record PhysicalBackendPlan(String backendName,
                                  String adapterName,
                                  boolean adapterAvailable,
                                  String adapterImplementationType,
                                  List<String> supportedTransactions,
                                  String applicationId,
                                  String transactionServiceGroup,
                                  String transactionBridgeName,
                                  String transactionBridgeClassName,
                                  String seataTransactionTimeoutMillis,
                                  boolean seataQuantumSupported,
                                  String datasourceUrl,
                                  String driverClassName,
                                  String quantumStoreName,
                                  String quantumDatasourceUrl,
                                  boolean quantumDatasourceSeparate,
                                  boolean quantumDatasourceCoordinated,
                                  String quantumDatasourceCoordinationReadiness,
                                  String quantumDatasourceCoordinationMissingReason,
                                  String quantumDatasourceCoordinationAction,
                                  String quantumDatasourceCoordinationContract,
                                  String quantumJournalStoreName,
                                  String quantumJournalTableName,
                                  String quantumValueTableName,
                                  String transactionCoordinatorName,
                                  String idempotencyStoreName,
                                  String idempotencyTableName,
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
                                  String backendContract,
                                  String runtimeContract) {
    public PhysicalBackendPlan(String backendName,
                               String adapterName,
                               boolean adapterAvailable,
                               String adapterImplementationType,
                               List<String> supportedTransactions,
                               String applicationId,
                               String transactionServiceGroup,
                               String transactionBridgeName,
                               String transactionBridgeClassName,
                               String seataTransactionTimeoutMillis,
                               boolean seataQuantumSupported,
                               String datasourceUrl,
                               String driverClassName,
                               String quantumStoreName,
                               String quantumDatasourceUrl,
                               boolean quantumDatasourceSeparate,
                               boolean quantumDatasourceCoordinated,
                               String quantumDatasourceCoordinationReadiness,
                               String quantumDatasourceCoordinationMissingReason,
                               String quantumDatasourceCoordinationAction,
                               String quantumDatasourceCoordinationContract,
                               String quantumJournalStoreName,
                               String quantumJournalTableName,
                               String quantumValueTableName,
                               String transactionCoordinatorName,
                               String idempotencyStoreName,
                               String idempotencyTableName,
                               String sagaLogStoreName,
                               String sagaLogTableName,
                               String tccBranchStoreName,
                               String tccBranchTableName,
                               String tccReservationStoreName,
                               String tccReservationTableName,
                               String tccReservationTtlMillis) {
        this(backendName,
                adapterName,
                adapterAvailable,
                adapterImplementationType,
                supportedTransactions,
                applicationId,
                transactionServiceGroup,
                transactionBridgeName,
                transactionBridgeClassName,
                seataTransactionTimeoutMillis,
                seataQuantumSupported,
                datasourceUrl,
                driverClassName,
                quantumStoreName,
                quantumDatasourceUrl,
                quantumDatasourceSeparate,
                quantumDatasourceCoordinated,
                quantumDatasourceCoordinationReadiness,
                quantumDatasourceCoordinationMissingReason,
                quantumDatasourceCoordinationAction,
                quantumDatasourceCoordinationContract,
                quantumJournalStoreName,
                quantumJournalTableName,
                quantumValueTableName,
                transactionCoordinatorName,
                idempotencyStoreName,
                idempotencyTableName,
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

    public PhysicalBackendPlan {
        backendName = normalize(backendName);
        adapterName = normalize(adapterName);
        adapterImplementationType = normalize(adapterImplementationType);
        supportedTransactions = normalizeList(supportedTransactions);
        applicationId = normalize(applicationId);
        transactionServiceGroup = normalize(transactionServiceGroup);
        transactionBridgeName = normalize(transactionBridgeName);
        transactionBridgeClassName = normalize(transactionBridgeClassName);
        seataTransactionTimeoutMillis = normalize(seataTransactionTimeoutMillis);
        datasourceUrl = normalize(datasourceUrl);
        driverClassName = normalize(driverClassName);
        quantumStoreName = normalize(quantumStoreName);
        quantumDatasourceUrl = normalize(quantumDatasourceUrl);
        quantumDatasourceCoordinationReadiness = normalize(quantumDatasourceCoordinationReadiness);
        quantumDatasourceCoordinationMissingReason = normalize(quantumDatasourceCoordinationMissingReason);
        quantumDatasourceCoordinationAction = normalize(quantumDatasourceCoordinationAction);
        quantumDatasourceCoordinationContract = normalize(quantumDatasourceCoordinationContract);
        quantumJournalStoreName = normalize(quantumJournalStoreName);
        quantumJournalTableName = normalize(quantumJournalTableName);
        quantumValueTableName = normalize(quantumValueTableName);
        transactionCoordinatorName = normalize(transactionCoordinatorName);
        idempotencyStoreName = normalize(idempotencyStoreName);
        idempotencyTableName = normalize(idempotencyTableName);
        sagaLogStoreName = normalize(sagaLogStoreName);
        sagaLogTableName = normalize(sagaLogTableName);
        tccBranchStoreName = normalize(tccBranchStoreName);
        tccBranchTableName = normalize(tccBranchTableName);
        tccReservationStoreName = normalize(tccReservationStoreName);
        tccReservationTableName = normalize(tccReservationTableName);
        tccReservationTtlMillis = normalize(tccReservationTtlMillis);
        readiness = normalize(readiness);
        readyReasons = normalizeList(readyReasons);
        missingReasons = normalizeList(missingReasons);
        actions = normalizeList(actions);
        backendContract = normalize(backendContract);
        runtimeContract = normalize(runtimeContract);
        if (readiness.isBlank()) {
            readiness = missingReasons.isEmpty() ? "PHYSICAL_BACKEND_READY" : "PHYSICAL_BACKEND_BLOCKED";
        }
        if (missingReasons.isEmpty() && "PHYSICAL_BACKEND_READY".equals(readiness)) {
            missingReasons = List.of("NO_MISSING_REASON");
        }
        if (actions.isEmpty()) {
            actions = "PHYSICAL_BACKEND_READY".equals(readiness)
                    ? List.of("NO_ACTION_REQUIRED")
                    : List.of("resolvePhysicalBackendReadiness");
        }
        if (backendContract.isBlank()) {
            backendContract = backendContract(backendName, adapterName, adapterImplementationType,
                    supportedTransactions, readiness);
        }
        if (runtimeContract.isBlank()) {
            runtimeContract = runtimeContract(backendName, adapterImplementationType, datasourceUrl,
                    quantumStoreName, quantumDatasourceUrl, readiness);
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

    private static String backendContract(String backendName,
                                          String adapterName,
                                          String adapterImplementationType,
                                          List<String> supportedTransactions,
                                          String readiness) {
        return key(backendName)
                + ":" + key(adapterName)
                + ":" + key(adapterImplementationType)
                + ":" + listKey(supportedTransactions, "NO_TRANSACTION_SUPPORT")
                + ":" + key(readiness);
    }

    private static String runtimeContract(String backendName,
                                          String adapterImplementationType,
                                          String datasourceUrl,
                                          String quantumStoreName,
                                          String quantumDatasourceUrl,
                                          String readiness) {
        return key(backendName)
                + "->" + key(adapterImplementationType)
                + "+datasource=" + key(datasourceUrl)
                + "+quantumStore=" + key(quantumStoreName)
                + "+quantumDatasource=" + key(quantumDatasourceUrl)
                + ":" + key(readiness);
    }

    private static String listKey(List<String> values, String emptyKey) {
        List<String> normalized = values == null
                ? List.of()
                : values.stream()
                        .map(PhysicalBackendPlan::key)
                        .toList();
        return normalized.isEmpty() ? emptyKey : String.join("+", normalized);
    }

    private static String key(String value) {
        return value == null || value.isBlank() ? "UNASSIGNED" : value;
    }
}
