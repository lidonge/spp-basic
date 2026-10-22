package com.servpp.service.runtime;

import java.util.ArrayList;
import java.util.List;

public class SagaRecoveryEvidenceReview {
    public String sagaServiceName;
    public String targetServiceName;
    public String evidenceRole;
    public String objectType;
    public String ruleSource;
    public String timing;
    public List<String> effectiveSavePhases = new ArrayList<>();
    public String snapshotBoundary;
    public String recoveryRole;
    public List<String> objectIdPaths = new ArrayList<>();
    public String repositoryName;
    public String resolvedRepositoryName;
    public String repositoryAdapterName;
    public boolean repositoryAppDdlTables;
    public String snapshotStorageTableName;
    public String readiness;
    public List<String> readyReasons = new ArrayList<>();
    public List<String> missingReasons = new ArrayList<>();
    public List<String> actions = new ArrayList<>();
    public String evidencePath;
    public String recoveryContract;

    public SagaRecoveryEvidenceReview() {
    }

    public SagaRecoveryEvidenceReview(String sagaServiceName,
                                      String targetServiceName,
                                      String evidenceRole,
                                      String objectType,
                                      String ruleSource,
                                      String timing,
                                      List<String> effectiveSavePhases,
                                      String snapshotBoundary,
                                      String recoveryRole,
                                      List<String> objectIdPaths,
                                      String repositoryName,
                                      String resolvedRepositoryName,
                                      String repositoryAdapterName,
                                      boolean repositoryAppDdlTables,
                                      String snapshotStorageTableName) {
        this(sagaServiceName, targetServiceName, evidenceRole, objectType, ruleSource, timing,
                effectiveSavePhases, snapshotBoundary, recoveryRole, objectIdPaths, repositoryName,
                resolvedRepositoryName, repositoryAdapterName, repositoryAppDdlTables, snapshotStorageTableName,
                "", List.of(), List.of(), List.of(), "", "");
    }

    public SagaRecoveryEvidenceReview(String sagaServiceName,
                                      String targetServiceName,
                                      String evidenceRole,
                                      String objectType,
                                      String ruleSource,
                                      String timing,
                                      List<String> effectiveSavePhases,
                                      String snapshotBoundary,
                                      String recoveryRole,
                                      List<String> objectIdPaths,
                                      String repositoryName,
                                      String resolvedRepositoryName,
                                      String repositoryAdapterName,
                                      boolean repositoryAppDdlTables,
                                      String snapshotStorageTableName,
                                      String readiness,
                                      List<String> readyReasons,
                                      List<String> missingReasons,
                                      List<String> actions,
                                      String evidencePath,
                                      String recoveryContract) {
        this.sagaServiceName = sagaServiceName;
        this.targetServiceName = targetServiceName;
        this.evidenceRole = evidenceRole;
        this.objectType = objectType;
        this.ruleSource = ruleSource;
        this.timing = timing;
        this.effectiveSavePhases = effectiveSavePhases == null
                ? new ArrayList<>()
                : new ArrayList<>(effectiveSavePhases);
        this.snapshotBoundary = snapshotBoundary;
        this.recoveryRole = recoveryRole;
        this.objectIdPaths = objectIdPaths == null ? new ArrayList<>() : new ArrayList<>(objectIdPaths);
        this.repositoryName = repositoryName;
        this.resolvedRepositoryName = resolvedRepositoryName;
        this.repositoryAdapterName = repositoryAdapterName;
        this.repositoryAppDdlTables = repositoryAppDdlTables;
        this.snapshotStorageTableName = snapshotStorageTableName;
        this.readiness = readiness;
        this.readyReasons = readyReasons == null ? new ArrayList<>() : new ArrayList<>(readyReasons);
        this.missingReasons = missingReasons == null ? new ArrayList<>() : new ArrayList<>(missingReasons);
        this.actions = actions == null ? new ArrayList<>() : new ArrayList<>(actions);
        this.evidencePath = evidencePath;
        this.recoveryContract = recoveryContract;
    }
}
