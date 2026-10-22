package com.servpp.service.runtime;

import java.util.List;

public record AppSemanticMigrationPlan(String migrationType,
                                       String targetKind,
                                       String targetName,
                                       String tableName,
                                       String serviceName,
                                       String objectType,
                                       String adapterName,
                                       String adapterRole,
                                       boolean referenceImplementation,
                                       boolean externalImplementation,
                                       String snapshotBoundary,
                                       String recoveryRole,
                                       boolean transactionEvidence,
                                       boolean currentObject,
                                       boolean argumentRecoveryEvidence,
                                       boolean rollbackRecoveryEvidence,
                                       String objectKeySource,
                                       String objectKeyAppDdlIndexName,
                                       boolean objectKeyAppDdlDerived,
                                       boolean objectKeyEscapeHatch,
                                       List<String> appDdlObjectKeyPaths,
                                       List<String> objectKeyColumns,
                                       boolean appDdlObjectKeyPushdown,
                                       List<String> externalObjectKeyRoots,
                                       List<String> paths,
                                       List<String> participantCategories,
                                       String identityKind,
                                       String recommendation,
                                       String repositoryName,
                                       String repositoryAdapterName,
                                       String repositoryStorageTableName,
                                       String repositoryDatasourceUrl,
                                       String repositoryDriverClassName) {
    public AppSemanticMigrationPlan {
        appDdlObjectKeyPaths = appDdlObjectKeyPaths == null ? List.of() : List.copyOf(appDdlObjectKeyPaths);
        objectKeyColumns = objectKeyColumns == null ? List.of() : List.copyOf(objectKeyColumns);
        externalObjectKeyRoots = externalObjectKeyRoots == null ? List.of() : List.copyOf(externalObjectKeyRoots);
        paths = paths == null ? List.of() : List.copyOf(paths);
        participantCategories = participantCategories == null ? List.of() : List.copyOf(participantCategories);
    }

    public AppSemanticMigrationPlan(String migrationType,
                                    String targetKind,
                                    String targetName,
                                    String tableName,
                                    String serviceName,
                                    String objectType,
                                    String adapterName,
                                    String adapterRole,
                                    boolean referenceImplementation,
                                    boolean externalImplementation,
                                    String snapshotBoundary,
                                    String recoveryRole,
                                    boolean transactionEvidence,
                                    boolean currentObject,
                                    boolean argumentRecoveryEvidence,
                                    boolean rollbackRecoveryEvidence,
                                    String objectKeySource,
                                    String objectKeyAppDdlIndexName,
                                    boolean objectKeyAppDdlDerived,
                                    boolean objectKeyEscapeHatch,
                                    List<String> appDdlObjectKeyPaths,
                                    List<String> objectKeyColumns,
                                    boolean appDdlObjectKeyPushdown,
                                    List<String> externalObjectKeyRoots,
                                    List<String> paths,
                                    String recommendation,
                                    String repositoryName,
                                    String repositoryAdapterName,
                                    String repositoryStorageTableName,
                                    String repositoryDatasourceUrl,
                                    String repositoryDriverClassName) {
        this(migrationType, targetKind, targetName, tableName, serviceName, objectType, adapterName, adapterRole,
                referenceImplementation, externalImplementation, snapshotBoundary, recoveryRole, transactionEvidence,
                currentObject, argumentRecoveryEvidence, rollbackRecoveryEvidence, objectKeySource,
                objectKeyAppDdlIndexName, objectKeyAppDdlDerived, objectKeyEscapeHatch, appDdlObjectKeyPaths,
                objectKeyColumns, appDdlObjectKeyPushdown, externalObjectKeyRoots, paths, List.of(), null,
                recommendation, repositoryName, repositoryAdapterName, repositoryStorageTableName,
                repositoryDatasourceUrl, repositoryDriverClassName);
    }

    public AppSemanticMigrationPlan(String migrationType,
                                    String targetKind,
                                    String targetName,
                                    String tableName,
                                    String serviceName,
                                    String objectType,
                                    String adapterName,
                                    String adapterRole,
                                    boolean referenceImplementation,
                                    boolean externalImplementation,
                                    String snapshotBoundary,
                                    String recoveryRole,
                                    boolean transactionEvidence,
                                    boolean currentObject,
                                    boolean argumentRecoveryEvidence,
                                    boolean rollbackRecoveryEvidence,
                                    String objectKeySource,
                                    String objectKeyAppDdlIndexName,
                                    boolean objectKeyAppDdlDerived,
                                    boolean objectKeyEscapeHatch,
                                    List<String> appDdlObjectKeyPaths,
                                      List<String> objectKeyColumns,
                                      boolean appDdlObjectKeyPushdown,
                                      List<String> externalObjectKeyRoots,
                                      List<String> paths,
                                      String recommendation) {
        this(migrationType, targetKind, targetName, tableName, serviceName, objectType, adapterName, adapterRole,
                referenceImplementation, externalImplementation, snapshotBoundary, recoveryRole, transactionEvidence,
                currentObject, argumentRecoveryEvidence, rollbackRecoveryEvidence, objectKeySource,
                objectKeyAppDdlIndexName, objectKeyAppDdlDerived, objectKeyEscapeHatch, appDdlObjectKeyPaths,
                objectKeyColumns, appDdlObjectKeyPushdown, externalObjectKeyRoots, paths, List.of(), null,
                recommendation, null, null, null, null, null);
    }

    public AppSemanticMigrationPlan(String migrationType,
                                    String targetKind,
                                    String targetName,
                                    String tableName,
                                    String serviceName,
                                    String objectType,
                                    String adapterName,
                                    String adapterRole,
                                    boolean referenceImplementation,
                                    boolean externalImplementation,
                                    String snapshotBoundary,
                                    String recoveryRole,
                                    boolean transactionEvidence,
                                    boolean currentObject,
                                    boolean argumentRecoveryEvidence,
                                    boolean rollbackRecoveryEvidence,
                                    String objectKeySource,
                                    String objectKeyAppDdlIndexName,
                                    boolean objectKeyAppDdlDerived,
                                    boolean objectKeyEscapeHatch,
                                    List<String> appDdlObjectKeyPaths,
                                    List<String> objectKeyColumns,
                                    boolean appDdlObjectKeyPushdown,
                                    List<String> externalObjectKeyRoots,
                                    List<String> paths,
                                    List<String> participantCategories,
                                    String recommendation) {
        this(migrationType, targetKind, targetName, tableName, serviceName, objectType, adapterName, adapterRole,
                referenceImplementation, externalImplementation, snapshotBoundary, recoveryRole, transactionEvidence,
                currentObject, argumentRecoveryEvidence, rollbackRecoveryEvidence, objectKeySource,
                objectKeyAppDdlIndexName, objectKeyAppDdlDerived, objectKeyEscapeHatch, appDdlObjectKeyPaths,
                objectKeyColumns, appDdlObjectKeyPushdown, externalObjectKeyRoots, paths, participantCategories,
                null, recommendation, null, null, null, null, null);
    }

    public AppSemanticMigrationPlan(String migrationType,
                                    String targetKind,
                                    String targetName,
                                    String tableName,
                                    String serviceName,
                                    String objectType,
                                    String adapterName,
                                    String adapterRole,
                                    boolean referenceImplementation,
                                    boolean externalImplementation,
                                    String snapshotBoundary,
                                    String recoveryRole,
                                    boolean transactionEvidence,
                                    boolean currentObject,
                                    boolean argumentRecoveryEvidence,
                                    boolean rollbackRecoveryEvidence,
                                    String objectKeySource,
                                    String objectKeyAppDdlIndexName,
                                    boolean objectKeyAppDdlDerived,
                                    boolean objectKeyEscapeHatch,
                                    List<String> appDdlObjectKeyPaths,
                                    List<String> objectKeyColumns,
                                    boolean appDdlObjectKeyPushdown,
                                    List<String> externalObjectKeyRoots,
                                    List<String> paths,
                                    List<String> participantCategories,
                                    String identityKind,
                                    String recommendation) {
        this(migrationType, targetKind, targetName, tableName, serviceName, objectType, adapterName, adapterRole,
                referenceImplementation, externalImplementation, snapshotBoundary, recoveryRole, transactionEvidence,
                currentObject, argumentRecoveryEvidence, rollbackRecoveryEvidence, objectKeySource,
                objectKeyAppDdlIndexName, objectKeyAppDdlDerived, objectKeyEscapeHatch, appDdlObjectKeyPaths,
                objectKeyColumns, appDdlObjectKeyPushdown, externalObjectKeyRoots, paths, participantCategories,
                identityKind, recommendation, null, null, null, null, null);
    }

    public AppSemanticMigrationPlan withIdentityKind(String identityKind) {
        return new AppSemanticMigrationPlan(migrationType, targetKind, targetName, tableName, serviceName, objectType,
                adapterName, adapterRole, referenceImplementation, externalImplementation, snapshotBoundary,
                recoveryRole, transactionEvidence, currentObject, argumentRecoveryEvidence, rollbackRecoveryEvidence,
                objectKeySource, objectKeyAppDdlIndexName, objectKeyAppDdlDerived, objectKeyEscapeHatch,
                appDdlObjectKeyPaths, objectKeyColumns, appDdlObjectKeyPushdown, externalObjectKeyRoots, paths,
                participantCategories, identityKind, recommendation, repositoryName, repositoryAdapterName,
                repositoryStorageTableName, repositoryDatasourceUrl, repositoryDriverClassName);
    }

    public String migrationAction() {
        if (migrationType == null || migrationType.isBlank()) {
            return "-";
        }
        return switch (migrationType) {
            case "OBJECT_BACKED_PRIMARY_KEY" -> "promoteAppDdlPrimaryKeyToPlatformColumn";
            case "APP_DDL_PLATFORM_PRIMARY_KEY" ->
                    "replaceSidecarPrimaryKeyWithBusinessOrCompositionIdentity+keepRuntimeIdentityOutsideSppRole";
            case "OBJECT_BACKED_INDEX" -> "promoteAppDdlIndexToPlatformColumn";
            case "SPP_TECHNICAL_FIELD_PLATFORM_COLUMN" ->
                    "removeTechnicalIdentityFromSppBusinessType+keepOnlyTrueBusinessCredentials";
            case "ROLE_PERFORMER_FIELD" -> "removeRolePerformerField+useTakePerformerIdentity";
            case "ROLE_OBJECT_FIELD" -> "removeRoleObjectField+useRuntimeRoleParticipantIdentity";
            case "ROLE_WITHOUT_PERFORMER" -> "declareRoleTakePerformer";
            case "SERVICE_NON_ROLE_PARTICIPANT" -> serviceNonRoleParticipantMigrationAction();
            case "LEGACY_ASYNC_MODIFIER" -> "replaceLegacyAsycnWithAsync";
            case "REPOSITORY_ESCAPE_HATCH" ->
                    "deriveRepositoryFromAppDdl+pushRepositoryColumnsToAppDdl+clearRepositoryEscapeHatch";
            case "SNAPSHOT_ESCAPE_HATCH" ->
                    "deriveSnapshotObjectKeyFromAppDdl+pushSnapshotObjectKeyColumnsToAppDdl+clearSnapshotEscapeHatch";
            case "CURRENT_SNAPSHOT_CONTEXT" ->
                    "bindCurrentContextToSagaArgument+deriveCurrentContextKeyFromAppDdl";
            case "REMOTE_REFERENCE_ADAPTER" -> "replaceReferenceRemoteAdapter+bindExternalDomainAdapter";
            case "REMOTE_EXTERNAL_ADAPTER" -> "externalRemoteAdapterReady";
            case "QUANTUM_SERVICE_INTERNAL_PROTOCOL" ->
                    "idempotencyBoundary=QUANTUM_SERVICE_INTERNAL+reversalExecution=INPUT_NEGATIVE_DELTA"
                            + "+snapshotBoundary=QUANTUM_SERVICE_AUDIT_EVIDENCE";
            default -> valueOrDash(recommendation);
        };
    }

    private String serviceNonRoleParticipantMigrationAction() {
        if (participantCategories != null && !participantCategories.isEmpty()
                && participantCategories.stream().allMatch("contracts"::equals)) {
            return "CONTRACT_CONTEXT_TO_ROLE_SIGNED_SCENARIO";
        }
        return "migrateServiceParticipantToRole";
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
