package com.servpp.service.runtime;

import java.util.List;

public record SagaRecoveryEvidencePlan(String objectType,
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
    public SagaRecoveryEvidencePlan {
        effectiveSavePhases = effectiveSavePhases == null ? List.of() : List.copyOf(effectiveSavePhases);
        objectIdPaths = objectIdPaths == null ? List.of() : List.copyOf(objectIdPaths);
    }
}
