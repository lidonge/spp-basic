package com.servpp.service.runtime;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.servpp.service.repository.ObjectSnapshotRule;
import com.servpp.service.repository.RepositoryHydratingServiceProxy.HydrationRule;
import com.servpp.service.transaction.TccReservationRule;
import com.servpp.service.transaction.TransactionMode;

public record GeneratedServiceCatalog(List<GeneratedServiceMetadata> services,
                                      Map<String, List<String>> servicesByMode,
                                      Map<String, List<String>> servicesByResource,
                                      Map<String, List<String>> tccLifecycleRules,
                                      Map<String, List<TccReservationRule>> tccReservationRules,
                                      List<TccReservationPlan> tccReservationPlans,
                                      Map<String, List<TccReservationPlan>> tccReservationPlansByService,
                                      Map<String, List<TccReservationPlan>>
                                              tccReservationPlansBySourceQuantumStore,
                                      Map<String, List<TccReservationPlan>>
                                              tccReservationPlansByTargetRepository,
                                      Map<String, List<TccReservationPlan>>
                                              tccReservationPlansByTargetObjectType,
                                      Map<String, List<TccReservationPlan>> tccReservationPlansByTargetColumn,
                                      Map<String, List<TccReservationPlan>>
                                              tccReservationPlansBySourceQuantumAttribute,
                                      Map<String, List<TccReservationPlan>>
                                              tccReservationPlansByTargetColumnKind,
                                      Map<String, List<TccReservationPlan>>
                                              tccReservationPlansByTargetColumnPushdown,
                                      Map<String, List<TccReservationPlan>> tccReservationPlansByReadiness,
                                      List<SagaTransactionCallPlan> sagaTransactionCallPlans,
                                      Map<String, List<SagaTransactionCallPlan>> sagaTransactionCallPlansBySaga,
                                      Map<String, List<SagaTransactionCallPlan>>
                                              sagaTransactionCallPlansByTargetService,
                                      Map<String, List<SagaTransactionCallPlan>>
                                              sagaTransactionCallPlansByRecoveryClosure,
                                      List<SagaRecoveryEvidenceReview> sagaRecoveryEvidencePlans,
                                      Map<String, List<SagaRecoveryEvidenceReview>>
                                              sagaRecoveryEvidencePlansBySaga,
                                      Map<String, List<SagaRecoveryEvidenceReview>>
                                              sagaRecoveryEvidencePlansByTargetService,
                                      Map<String, List<SagaRecoveryEvidenceReview>>
                                              sagaRecoveryEvidencePlansByObjectType,
                                      List<CurrentSnapshotContextPlan> currentSnapshotContextPlans,
                                      Map<String, List<CurrentSnapshotContextPlan>>
                                              currentSnapshotContextPlansBySaga,
                                      Map<String, List<CurrentSnapshotContextPlan>>
                                              currentSnapshotContextPlansByTargetService,
                                      Map<String, List<CurrentSnapshotContextPlan>>
                                              currentSnapshotContextPlansByObjectType,
                                      Map<String, List<CurrentSnapshotContextPlan>>
                                              currentSnapshotContextPlansByContextObjectSource,
                                      Map<String, List<CurrentSnapshotContextPlan>>
                                              currentSnapshotContextPlansByObjectKeySource,
                                      Map<String, List<CurrentSnapshotContextPlan>>
                                              currentSnapshotContextPlansByAppDdlIndex,
                                      Map<String, List<CurrentSnapshotContextPlan>>
                                              currentSnapshotContextPlansByResolution,
                                      Map<String, List<CurrentSnapshotContextPlan>>
                                              currentSnapshotContextPlansByKeyPushdown,
                                      Map<String, List<CurrentSnapshotContextPlan>>
                                              currentSnapshotContextPlansByClosureReadiness,
                                      Map<String, List<String>> compensationRules,
                                      Map<String, List<String>> compensationRulesByTarget,
                                      Map<String, List<String>> remoteCompensationRules,
                                      Map<String, List<String>> remoteCompensationRulesByTarget,
                                      Map<String, List<String>> tccLifecycleRulesByTarget,
                                      List<TransactionReversalRule> transactionReversalRules,
                                      Map<String, List<TransactionReversalRule>> transactionReversalRulesByKind,
                                      Map<String, List<TransactionReversalRule>> transactionReversalRulesBySource,
                                      Map<String, List<TransactionReversalRule>> transactionReversalRulesByTarget,
                                      Map<String, List<TransactionReversalRule>> transactionReversalRulesByPhase,
                                      Map<String, List<TransactionReversalRule>> transactionReversalRulesByReadiness,
                                      Map<String, List<ObjectSnapshotRule>> objectSnapshotRules,
                                      List<ObjectSnapshotPlan> objectSnapshotPlans,
                                      Map<String, List<ObjectSnapshotPlan>> objectSnapshotPlansByService,
                                      Map<String, List<ObjectSnapshotPlan>> objectSnapshotPlansByBoundary,
                                      Map<String, List<ObjectSnapshotPlan>> objectSnapshotPlansByRecoveryRole,
                                      Map<String, List<ObjectSnapshotPlan>> objectSnapshotPlansByObjectType,
                                      Map<String, List<ObjectSnapshotPlan>> objectSnapshotPlansByRepository,
                                      Map<String, List<ObjectSnapshotPlan>> objectSnapshotPlansBySource,
                                      Map<String, List<ObjectSnapshotPlan>> objectSnapshotPlansByObjectKeySource,
                                      Map<String, List<ObjectSnapshotPlan>> objectSnapshotPlansByStorageTable,
                                      Map<String, List<ObjectSnapshotPlan>>
                                              objectSnapshotPlansByExternalObjectKeyRoot,
                                      Map<String, List<ObjectSnapshotPlan>> objectSnapshotPlansByAppDdlDerivation,
                                      Map<String, List<ObjectSnapshotPlan>> objectSnapshotPlansByEscapeHatch,
                                      Map<String, List<ObjectSnapshotPlan>> objectSnapshotPlansByObjectKeyPushdown,
                                      Map<String, List<ObjectSnapshotPlan>>
                                              objectSnapshotPlansByObjectKeyDerivation,
                                      Map<String, List<ObjectSnapshotPlan>> objectSnapshotPlansByClosureReadiness,
                                      List<TransactionSnapshotPlan> transactionSnapshotPlans,
                                      Map<String, List<TransactionSnapshotPlan>> transactionSnapshotPlansByService,
                                      Map<String, List<TransactionSnapshotPlan>> transactionSnapshotPlansByResource,
                                      List<TransactionSnapshotRetentionPlan> transactionSnapshotRetentionPlans,
                                      Map<String, TransactionSnapshotRetentionPlan>
                                              transactionSnapshotRetentionPlansByPlan,
                                      Map<String, List<TransactionSnapshotRetentionPlan>>
                                              transactionSnapshotRetentionPlansByRepository,
                                      Map<String, List<TransactionSnapshotRetentionPlan>>
                                              transactionSnapshotRetentionPlansByLatestTable,
                                      Map<String, List<TransactionSnapshotRetentionPlan>>
                                              transactionSnapshotRetentionPlansByHotHistoryTable,
                                      Map<String, List<TransactionSnapshotRetentionPlan>>
                                              transactionSnapshotRetentionPlansByColdArchiveTable,
                                      Map<String, List<TransactionSnapshotRetentionPlan>>
                                              transactionSnapshotRetentionPlansByMode,
                                      Map<String, List<TransactionSnapshotRetentionPlan>>
                                              transactionSnapshotRetentionPlansByReadiness,
                                      List<TransactionSnapshotRetentionAutomationPlan>
                                              transactionSnapshotRetentionAutomationPlans,
                                      Map<String, TransactionSnapshotRetentionAutomationPlan>
                                              transactionSnapshotRetentionAutomationPlansByPlan,
                                      Map<String, List<TransactionSnapshotRetentionAutomationPlan>>
                                              transactionSnapshotRetentionAutomationPlansBySchedulerPrefix,
                                      Map<String, List<TransactionSnapshotRetentionAutomationPlan>>
                                              transactionSnapshotRetentionAutomationPlansByEnabledDefault,
                                      Map<String, List<TransactionSnapshotRetentionAutomationPlan>>
                                              transactionSnapshotRetentionAutomationPlansByReadiness,
                                      List<TransactionSnapshotRetentionExecutionPlan>
                                              transactionSnapshotRetentionExecutionPlans,
                                      Map<String, TransactionSnapshotRetentionExecutionPlan>
                                              transactionSnapshotRetentionExecutionPlansByPlan,
                                      Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
                                              transactionSnapshotRetentionExecutionPlansByService,
                                      Map<String, TransactionSnapshotRetentionExecutionPlan>
                                              transactionSnapshotRetentionExecutionPlansByResource,
                                      Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
                                              transactionSnapshotRetentionExecutionPlansByRetentionSource,
                                      Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
                                              transactionSnapshotRetentionExecutionPlansByArchiveRetentionSource,
                                      Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
                                              transactionSnapshotRetentionExecutionPlansByRetentionWindow,
                                      Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
                                              transactionSnapshotRetentionExecutionPlansByArchiveRetentionWindow,
                                      Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
                                              transactionSnapshotRetentionExecutionPlansByReadiness,
                                      Map<String, List<HydrationRule>> repositoryHydrationRules,
                                      List<RepositoryQueryPlan> repositoryQueryPlans,
                                      Map<String, List<RepositoryQueryPlan>> repositoryQueryPlansByService,
                                      Map<String, List<RepositoryQueryPlan>> repositoryQueryPlansByLookupKind,
                                      Map<String, List<RepositoryQueryPlan>> repositoryQueryPlansByObjectType,
                                      Map<String, List<RepositoryQueryPlan>>
                                              repositoryQueryPlansByAggregateRelation,
                                      Map<String, List<RepositoryQueryPlan>> repositoryQueryPlansByRepository,
                                      Map<String, List<RepositoryQueryPlan>> repositoryQueryPlansBySource,
                                      Map<String, List<RepositoryQueryPlan>>
                                              repositoryQueryPlansByExternalAggregateRoot,
                                      Map<String, List<RepositoryQueryPlan>>
                                              repositoryQueryPlansByAppDdlDerivation,
                                      Map<String, List<RepositoryQueryPlan>>
                                              repositoryQueryPlansByEscapeHatch,
                                      Map<String, List<RepositoryQueryPlan>>
                                              repositoryQueryPlansByColumnPushdown,
                                      Map<String, List<RepositoryQueryPlan>>
                                              repositoryQueryPlansByReplacementReadiness,
                                      List<ObjectRepositoryPlan> objectRepositoryPlans,
                                      Map<String, List<ObjectRepositoryPlan>> objectRepositoryPlansByService,
                                      Map<String, List<ObjectRepositoryPlan>> objectRepositoryPlansByRepository,
                                      Map<String, List<ObjectRepositoryPlan>> objectRepositoryPlansByObjectType,
                                      Map<String, List<ObjectRepositoryPlan>> objectRepositoryPlansByAdapter,
                                      Map<String, List<ObjectRepositoryPlan>> objectRepositoryPlansByStorageTable,
                                      Map<String, List<ObjectRepositoryPlan>> objectRepositoryPlansByRole,
                                      Map<String, List<ObjectRepositoryPlan>> objectRepositoryPlansByAppDdlTable,
                                      Map<String, List<ObjectRepositoryPlan>>
                                              objectRepositoryPlansByAppDdlTableAvailability,
                                      Map<String, List<ObjectRepositoryPlan>> objectRepositoryPlansByKeySource,
                                      Map<String, List<ObjectRepositoryPlan>> objectRepositoryPlansByColumnPushdown,
                                      Map<String, List<ObjectRepositoryPlan>> objectRepositoryPlansByReadiness,
                                      List<RepositoryMigrationPlan> repositoryMigrationPlans,
                                      Map<String, RepositoryMigrationPlan> repositoryMigrationPlansByPlan,
                                      Map<String, RepositoryMigrationPlan> repositoryMigrationPlansByRepository,
                                      Map<String, List<RepositoryMigrationPlan>>
                                              repositoryMigrationPlansByLookupKind,
                                      Map<String, List<RepositoryMigrationPlan>>
                                              repositoryMigrationPlansByAggregateRelation,
                                      Map<String, List<RepositoryMigrationPlan>>
                                              repositoryMigrationPlansByReplacementSource,
                                      Map<String, List<RepositoryMigrationPlan>>
                                              repositoryMigrationPlansByRecommendation,
                                      Map<String, List<RepositoryMigrationPlan>>
                                              repositoryMigrationPlansByEscapeHatch,
                                      Map<String, List<RepositoryMigrationPlan>>
                                              repositoryMigrationPlansByColumnPushdown,
                                      Map<String, List<RepositoryMigrationPlan>>
                                              repositoryMigrationPlansByReadiness,
                                      AppDdlCatalog appDdl,
                                      List<RoleParticipantPlan> roleParticipantPlans,
                                      Map<String, List<RoleParticipantPlan>> roleParticipantPlansByRole,
                                      Map<String, List<RoleParticipantPlan>>
                                              roleParticipantPlansByParticipationKind,
                                      Map<String, List<RoleParticipantPlan>> roleParticipantPlansByPerformerType,
                                      Map<String, List<RoleParticipantPlan>>
                                              roleParticipantPlansByIdentityReadiness,
                                      Map<String, List<RoleParticipantPlan>>
                                              roleParticipantPlansByPlatformColumn,
                                      List<RoleParticipantRequestBindingPlan> roleParticipantRequestBindingPlans,
                                      Map<String, List<RoleParticipantRequestBindingPlan>>
                                              roleParticipantRequestBindingPlansByService,
                                      Map<String, List<RoleParticipantRequestBindingPlan>>
                                              roleParticipantRequestBindingPlansByRole,
                                      Map<String, List<RoleParticipantRequestBindingPlan>>
                                              roleParticipantRequestBindingPlansByRequest,
                                      Map<String, List<RoleParticipantRequestBindingPlan>>
                                              roleParticipantRequestBindingPlansByCompleteness,
                                      List<ContractSignerPlan> contractSignerPlans,
                                      Map<String, List<ContractSignerPlan>> contractSignerPlansByContract,
                                      Map<String, List<ContractSignerPlan>> contractSignerPlansBySignerRole,
                                      PhysicalRuntimeCatalog physicalRuntime,
                                      List<PhysicalBackendPlan> physicalBackendPlans,
                                      Map<String, PhysicalBackendPlan> physicalBackendPlansByBackend,
                                      Map<String, List<PhysicalBackendPlan>>
                                              physicalBackendPlansByImplementationType,
                                      List<PhysicalRepositoryPlan> physicalRepositoryPlans,
                                      Map<String, PhysicalRepositoryPlan> physicalRepositoryPlansByRepository,
                                      Map<String, List<PhysicalRepositoryPlan>>
                                              physicalRepositoryPlansByAdapterRole,
                                      List<PhysicalRemotePlan> physicalRemotePlans,
                                      Map<String, PhysicalRemotePlan> physicalRemotePlansByService,
                                      Map<String, List<PhysicalRemotePlan>> physicalRemotePlansByAdapterRole,
                                      Map<String, List<PhysicalRemotePlan>> physicalRemotePlansByAdapterName,
                                      Map<String, List<PhysicalRemotePlan>> physicalRemotePlansByEndpoint,
                                      Map<String, List<PhysicalRemotePlan>> physicalRemotePlansByRepository,
                                      Map<String, List<PhysicalRemotePlan>>
                                              physicalRemotePlansByImplementationReadiness,
                                      List<PhysicalQuantumPlan> physicalQuantumPlans,
                                      Map<String, PhysicalQuantumPlan> physicalQuantumPlansByStore,
                                      Map<String, List<PhysicalQuantumPlan>>
                                              physicalQuantumPlansByImplementationStrategy,
                                      Map<String, List<PhysicalQuantumPlan>> physicalQuantumPlansByAccessMode,
                                      Map<String, List<PhysicalQuantumPlan>> physicalQuantumPlansByAdapterRole,
                                      Map<String, List<PhysicalQuantumPlan>> physicalQuantumPlansByEndpoint,
                                      Map<String, List<PhysicalQuantumPlan>> physicalQuantumPlansByDatasource,
                                      Map<String, List<PhysicalQuantumPlan>> physicalQuantumPlansByBackend,
                                      Map<String, List<PhysicalQuantumPlan>>
                                              physicalQuantumPlansByImplementationReadiness,
                                      List<QuantumAccessPlan> quantumAccessPlans,
                                      Map<String, List<QuantumAccessPlan>> quantumAccessPlansByService,
                                      Map<String, List<QuantumAccessPlan>>
                                              quantumAccessPlansByImplementationStrategy,
                                      Map<String, List<QuantumAccessPlan>> quantumAccessPlansByStore,
                                      Map<String, List<QuantumAccessPlan>> quantumAccessPlansByEndpoint,
                                      Map<String, List<QuantumAccessPlan>> quantumAccessPlansByDatasource,
                                      Map<String, List<QuantumAccessPlan>> quantumAccessPlansByIdempotencyOwner,
                                      Map<String, List<QuantumAccessPlan>> quantumAccessPlansByReversalMode,
                                      Map<String, List<QuantumAccessPlan>> quantumAccessPlansByRecoveryOwner,
                                      Map<String, List<QuantumAccessPlan>> quantumAccessPlansBySnapshotBoundary,
                                      Map<String, List<QuantumAccessPlan>>
                                              quantumAccessPlansByAccessContractAvailability,
                                      Map<String, List<QuantumAccessPlan>> quantumAccessPlansByObjectSnapshotBypass,
                                      Map<String, List<QuantumAccessPlan>>
                                              quantumAccessPlansByObjectCompensationBypass,
                                      Map<String, List<QuantumAccessPlan>>
                                              quantumAccessPlansByServiceInternalIdempotency,
                                      Map<String, List<QuantumAccessPlan>>
                                              quantumAccessPlansByServiceInternalSnapshot,
                                      Map<String, List<QuantumAccessPlan>>
                                              quantumAccessPlansByServiceInternalReversal,
                                      Map<String, List<QuantumAccessPlan>>
                                              quantumAccessPlansByNegativeDeltaReversal,
                                      Map<String, List<QuantumAccessPlan>>
                                              quantumAccessPlansByServiceProtocolReadiness,
                                      List<RecoveryDeploymentPlan> recoveryDeploymentPlans,
                                      Map<String, RecoveryDeploymentPlan> recoveryDeploymentPlansByPlan,
                                      Map<String, List<RecoveryDeploymentPlan>> recoveryDeploymentPlansByBackend,
                                      Map<String, List<RecoveryDeploymentPlan>> recoveryDeploymentPlansByDatasource,
                                      Map<String, List<RecoveryDeploymentPlan>>
                                              recoveryDeploymentPlansByWorkItemKind,
                                      Map<String, List<RecoveryDeploymentPlan>>
                                              recoveryDeploymentPlansByEvidenceSource,
                                      Map<String, List<RecoveryDeploymentPlan>>
                                              recoveryDeploymentPlansByUnifiedView,
                                      Map<String, List<RecoveryDeploymentPlan>> recoveryDeploymentPlansByReadiness,
                                      List<RecoveryWorkItemPlan> recoveryWorkItemPlans,
                                      Map<String, RecoveryWorkItemPlan> recoveryWorkItemPlansByKind,
                                      Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansBySourceService,
                                      Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansByManualEndpoint,
                                      Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansBySchedulerPrefix,
                                      Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansByWorkItemSource,
                                      Map<String, List<RecoveryWorkItemPlan>>
                                              recoveryWorkItemPlansBySupportedDecision,
                                      Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansBySuggestedAction,
                                      Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansByReadiness,
                                      List<PhysicalBackendAssignment> physicalBackendAssignments,
                                      Map<String, PhysicalBackendAssignment> physicalBackendAssignmentsByService,
                                      Map<String, List<PhysicalBackendAssignment>>
                                              physicalBackendAssignmentsByBackend,
                                      Map<String, List<PhysicalBackendAssignment>>
                                              physicalBackendAssignmentsByTransactionKind,
                                      List<String> physicalBackendValidationErrors,
                                      List<RemoteServiceAssignment> remoteServiceAssignments,
                                      Map<String, RemoteServiceAssignment> remoteServiceAssignmentsByService,
                                      Map<String, List<RemoteServiceAssignment>>
                                              remoteServiceAssignmentsByAdapterRole,
                                      Map<String, List<RemoteServiceAssignment>>
                                              remoteServiceAssignmentsByImplementationType,
                                      Map<String, List<RemoteServiceAssignment>>
                                              remoteServiceAssignmentsByImplementationReadiness,
                                      Map<String, List<RemoteServiceAssignment>>
                                              remoteServiceAssignmentsByExecutionReadiness,
                                      List<String> remoteServiceValidationErrors,
                                      List<String> remoteCompensationValidationErrors,
                                      List<String> snapshotValidationErrors,
                                      List<String> tccLifecycleValidationErrors,
                                      List<String> repositoryValidationErrors,
                                      List<String> compensationValidationErrors,
                                      List<String> repositoryAdapterValidationErrors,
                                      List<String> quantumStoreValidationErrors,
                                      List<String> idempotencyValidationErrors,
                                      List<IdempotencyPlan> idempotencyPlans,
                                      Map<String, IdempotencyPlan> idempotencyPlansByService,
                                      Map<String, List<IdempotencyPlan>> idempotencyPlansByOwner,
                                      Map<String, List<IdempotencyPlan>> idempotencyPlansByBackend,
                                      Map<String, List<IdempotencyPlan>> idempotencyPlansByStore,
                                      Map<String, List<IdempotencyPlan>> idempotencyPlansByKeyAvailability,
                                      Map<String, List<IdempotencyPlan>>
                                              idempotencyPlansByRuntimeStoreAvailability,
                                      Map<String, List<IdempotencyPlan>> idempotencyPlansByBoundaryReadiness,
                                      List<ScopeHeaderPlan> scopeHeaderPlans,
                                      Map<String, ScopeHeaderPlan> scopeHeaderPlansByScope,
                                      Map<String, ScopeHeaderPlan> scopeHeaderPlansByService,
                                      Map<String, List<ScopeHeaderPlan>> scopeHeaderPlansByPhysicalBackend,
                                      Map<String, List<ScopeHeaderPlan>> scopeHeaderPlansByIdempotencyBackend,
                                      Map<String, List<ScopeHeaderPlan>> scopeHeaderPlansByScopeKind,
                                      Map<String, List<ScopeHeaderPlan>> scopeHeaderPlansByBoundary,
                                      Map<String, List<ScopeHeaderPlan>>
                                              scopeHeaderPlansByPhysicalBindingReadiness,
                                      Map<String, List<ScopeHeaderPlan>> scopeHeaderPlansByIdempotencyReadiness,
                                      List<ServiceParticipantRoleOnlyMigrationActionPlan>
                                              serviceParticipantRoleOnlyMigrationActionPlans,
                                      Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>>
                                              serviceParticipantRoleOnlyMigrationActionsByService,
                                      Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>>
                                              serviceParticipantRoleOnlyMigrationActionsByAction,
                                      Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>>
                                              serviceParticipantRoleOnlyMigrationActionsByParticipantCategory,
                                      Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>>
                                              serviceParticipantRoleOnlyMigrationActionsByReadiness,
                                      Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>>
                                              serviceParticipantRoleOnlyMigrationActionsByTargetRole,
                                      Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>>
                                              serviceParticipantRoleOnlyMigrationActionsByReadinessReason,
                                      List<DeploymentValidationPlan> deploymentValidationPlans,
                                      Map<String, DeploymentValidationPlan> deploymentValidationPlansByCategory,
                                      List<AppSemanticMigrationPlan> generatedAppSemanticMigrationPlans,
                                      AppSemanticMigrationPlanIndex generatedAppSemanticMigrationPlanIndex,
                                      GeneratedAppSemanticMigrationSummary generatedAppSemanticMigrationSummary,
                                      List<AppSemanticMigrationSummaryPlan> generatedAppSemanticMigrationSummaryPlans,
                                      Map<String, AppSemanticMigrationSummaryPlan>
                                              generatedAppSemanticMigrationSummaryPlansByType,
                                      Map<String, List<AppSemanticMigrationSummaryPlan>>
                                              generatedAppSemanticMigrationSummaryPlansByParticipantCategory,
                                      Map<String, List<AppSemanticMigrationSummaryPlan>>
                                              generatedAppSemanticMigrationSummaryPlansByIdentityKind,
                                      List<AppSemanticMigrationPlan> appSemanticMigrationPlans,
                                      AppSemanticMigrationPlanIndex appSemanticMigrationPlanIndex,
                                      List<AppSemanticMigrationSummaryPlan> appSemanticMigrationSummaryPlans,
                                      Map<String, AppSemanticMigrationSummaryPlan> appSemanticMigrationSummaryPlansByType,
                                      Map<String, List<AppSemanticMigrationSummaryPlan>>
                                              appSemanticMigrationSummaryPlansByParticipantCategory,
                                      Map<String, List<AppSemanticMigrationSummaryPlan>>
                                              appSemanticMigrationSummaryPlansByIdentityKind,
                                      List<String> appSemanticMigrationWarnings,
                                      List<String> deploymentValidationErrors) {
    @JsonProperty(value = "sagaTransactionCallPlansByMissingRecoveryReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaTransactionCallPlan>> sagaTransactionCallPlansByMissingRecoveryReason() {
        if (sagaTransactionCallPlans == null || sagaTransactionCallPlans.isEmpty()) {
            return Map.of();
        }
        Map<String, List<SagaTransactionCallPlan>> plansByReason = new LinkedHashMap<>();
        for (SagaTransactionCallPlan plan : sagaTransactionCallPlans) {
            SagaTransactionCallPlan enrichedPlan = enrichSagaTransactionCallPlan(plan);
            if (enrichedPlan == null) {
                continue;
            }
            List<String> missingReasons = safeList(enrichedPlan.recoveryMissingReasons());
            if ("RECOVERY_CLOSED".equals(enrichedPlan.recoveryReadiness())) {
                continue;
            }
            for (String reason : missingReasons) {
                if (reason == null || reason.isBlank()) {
                    continue;
                }
                plansByReason.computeIfAbsent(reason, ignored -> new ArrayList<>()).add(enrichedPlan);
            }
        }
        return immutableListMap(plansByReason);
    }

    @JsonProperty(value = "sagaTransactionCallPlansByReadyReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaTransactionCallPlan>> sagaTransactionCallPlansByReadyReason() {
        return sagaTransactionRecoveryClosureIndex().byReadyReason();
    }

    @JsonProperty(value = "sagaTransactionCallPlansByMissingReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaTransactionCallPlan>> sagaTransactionCallPlansByMissingReason() {
        return sagaTransactionRecoveryClosureIndex().byMissingReason();
    }

    @JsonProperty(value = "sagaTransactionCallPlansByAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaTransactionCallPlan>> sagaTransactionCallPlansByAction() {
        return sagaTransactionRecoveryClosureIndex().byAction();
    }

    @JsonProperty(value = "repositoryQueryPlansByIdentitySource", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryQueryPlan>> repositoryQueryPlansByIdentitySource() {
        Map<String, List<RepositoryQueryPlan>> plansByIdentitySource = new LinkedHashMap<>();
        for (RepositoryQueryPlan plan : safeList(repositoryQueryPlans)) {
            if (plan == null) {
                continue;
            }
            List<String> identitySources = safeList(plan.keyPlans()).stream()
                    .map(RepositoryQueryKeyPlan::identitySource)
                    .filter(source -> source != null && !source.isBlank())
                    .distinct()
                    .toList();
            if (identitySources.isEmpty()) {
                addToIndex(plansByIdentitySource, "NO_IDENTITY_SOURCE", plan);
                continue;
            }
            for (String identitySource : identitySources) {
                addToIndex(plansByIdentitySource, indexKey(identitySource), plan);
            }
        }
        return immutableListMap(plansByIdentitySource);
    }

    @JsonProperty(value = "repositoryQueryPlansByRepositoryBindingSource",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryQueryPlan>> repositoryQueryPlansByRepositoryBindingSource() {
        Map<String, List<RepositoryQueryPlan>> plansByBindingSource = new LinkedHashMap<>();
        for (RepositoryQueryPlan plan : safeList(repositoryQueryPlans)) {
            if (plan == null) {
                continue;
            }
            addToIndex(plansByBindingSource, repositoryBindingSource(plan), plan);
        }
        return immutableListMap(plansByBindingSource);
    }

    @JsonProperty(value = "repositoryQueryPlansByExternalAggregateRootSet",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryQueryPlan>> repositoryQueryPlansByExternalAggregateRootSet() {
        Map<String, List<RepositoryQueryPlan>> plansByRootSet = new LinkedHashMap<>();
        for (RepositoryQueryPlan plan : safeList(repositoryQueryPlans)) {
            if (plan == null) {
                continue;
            }
            List<String> roots = safeList(plan.externalAggregateRoots()).stream()
                    .filter(root -> root != null && !root.isBlank())
                    .toList();
            addToIndex(plansByRootSet,
                    roots.isEmpty() ? "LOCAL_AGGREGATE" : String.join("+", roots),
                    plan);
        }
        return immutableListMap(plansByRootSet);
    }

    @JsonProperty(value = "repositoryQueryPlansByExecutableReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryQueryPlan>> repositoryQueryPlansByExecutableReadiness() {
        Map<String, List<RepositoryQueryPlan>> plansByReadiness = new LinkedHashMap<>();
        for (RepositoryQueryPlan plan : safeList(repositoryQueryPlans)) {
            if (plan != null) {
                addToIndex(plansByReadiness, indexKey(plan.executionReadiness()), plan);
            }
        }
        return immutableListMap(plansByReadiness);
    }

    @JsonProperty(value = "repositoryQueryPlansByExecutionContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryQueryPlan>> repositoryQueryPlansByExecutionContract() {
        Map<String, List<RepositoryQueryPlan>> plansByContract = new LinkedHashMap<>();
        for (RepositoryQueryPlan plan : safeList(repositoryQueryPlans)) {
            if (plan != null) {
                addToIndex(plansByContract, indexKey(plan.executionContract()), plan);
            }
        }
        return immutableListMap(plansByContract);
    }

    private static String repositoryBindingSource(RepositoryQueryPlan plan) {
        if (!isBlank(plan.repositoryName())) {
            return "APP_EXPLICIT_REPOSITORY";
        }
        if (!isBlank(plan.resolvedRepositoryName())) {
            return "PHY_RESOLVED_REPOSITORY";
        }
        return "REPOSITORY_UNRESOLVED";
    }

    static boolean repositoryCompositionContextMatches(AppDdlCompositionIndexPlan composition,
                                                       ScenarioContractContextPlan context) {
        if (composition == null || context == null || !context.contextComplete()) {
            return false;
        }
        if (isBlank(composition.roleType()) || !safeList(context.signerRoleTypes()).contains(composition.roleType())) {
            return false;
        }
        return safeList(composition.signerContextContracts()).isEmpty()
                || safeList(composition.signerContextContracts()).contains(context.executionContract());
    }

    static List<String> repositoryCompositionQueryMissingReasons(AppDdlCompositionIndexPlan composition,
                                                                 ScenarioContractContextPlan context) {
        List<String> reasons = new ArrayList<>();
        if (composition == null) {
            reasons.add("compositionIndex");
            return List.copyOf(reasons);
        }
        if (isBlank(composition.namedIndexName())) {
            reasons.add("appDdlNamedIndex");
        }
        if (safeList(composition.compositionColumns()).isEmpty()) {
            reasons.add("compositionColumn");
        }
        if (safeList(composition.rolePerformerColumns()).isEmpty()
                || safeList(composition.performerTypes()).isEmpty()) {
            reasons.add("rolePerformerIdentity");
        }
        if (context == null || !context.contextComplete()) {
            reasons.add("roleSignedContractContext");
        }
        if (context != null && !safeList(context.signerRoleTypes()).contains(composition.roleType())) {
            reasons.add("roleOnlyBoundary");
        }
        return List.copyOf(reasons);
    }

    static List<String> repositoryCompositionQueryActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "compositionIndex" -> actions.add("declareAppDdlCompositionIndex");
                case "appDdlNamedIndex", "compositionColumn" -> actions.add("declareAppDdlNamedCompositionIndex");
                case "rolePerformerIdentity" -> actions.add("bindRolePerformerIdentity");
                case "roleSignedContractContext", "roleOnlyBoundary" ->
                        actions.add("bindRepositoryQueryToRoleSignedScenarioContext");
                default -> actions.add("resolveRepositoryCompositionQueryReadiness");
            }
        }
        return List.copyOf(actions);
    }

    static boolean repositoryCompositionQueryExecutable(RepositoryCompositionQueryPlan plan) {
        return plan != null
                && "REPOSITORY_COMPOSITION_QUERY_READY".equals(plan.readiness())
                && plan.collectionQuery()
                && plan.appDdlIndexCoverage()
                && plan.roleOnlyBoundary()
                && "SCENARIO_CONTRACT_CONTEXT".equals(plan.compositionIdentitySource())
                && "ROLE_PARTICIPANT_IDENTITY".equals(plan.performerIdentitySource());
    }

    static String repositoryCompositionQueryExecutableReadiness(RepositoryCompositionQueryPlan plan) {
        return repositoryCompositionQueryExecutable(plan)
                ? "REPOSITORY_COMPOSITION_QUERY_EXECUTABLE"
                : "REPOSITORY_COMPOSITION_QUERY_NOT_EXECUTABLE";
    }

    @JsonProperty(value = "repositoryCompositionQueryIndex", access = JsonProperty.Access.READ_ONLY)
    public RepositoryCompositionQueryIndex repositoryCompositionQueryIndex() {
        List<RepositoryCompositionQueryPlan> plans = repositoryCompositionQueryPlans();
        if (plans.isEmpty()) {
            return new RepositoryCompositionQueryIndex(List.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of());
        }
        Map<String, List<RepositoryCompositionQueryPlan>> byRoleType = new LinkedHashMap<>();
        Map<String, List<RepositoryCompositionQueryPlan>> byNamedIndex = new LinkedHashMap<>();
        Map<String, List<RepositoryCompositionQueryPlan>> byContract = new LinkedHashMap<>();
        Map<String, List<RepositoryCompositionQueryPlan>> byScenario = new LinkedHashMap<>();
        Map<String, List<RepositoryCompositionQueryPlan>> bySignerRoleSet = new LinkedHashMap<>();
        Map<String, List<RepositoryCompositionQueryPlan>> byCompositionColumn = new LinkedHashMap<>();
        Map<String, List<RepositoryCompositionQueryPlan>> byRolePerformerColumn = new LinkedHashMap<>();
        Map<String, List<RepositoryCompositionQueryPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<RepositoryCompositionQueryPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<RepositoryCompositionQueryPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<RepositoryCompositionQueryPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<RepositoryCompositionQueryPlan>> byQueryContract = new LinkedHashMap<>();
        Map<String, List<RepositoryCompositionQueryPlan>> byExecutableReadiness = new LinkedHashMap<>();
        Map<String, List<RepositoryCompositionQueryPlan>> byExecutionContract = new LinkedHashMap<>();
        for (RepositoryCompositionQueryPlan plan : plans) {
            addToIndex(byRoleType, indexKey(plan.roleType()), plan);
            addToIndex(byNamedIndex, indexKey(plan.namedIndexName()), plan);
            addToIndex(byContract, indexKey(plan.contractType()), plan);
            addToIndex(byScenario, indexKey(plan.scenarioName()), plan);
            addToIndex(bySignerRoleSet, indexKey(plan.signerRoleSet()), plan);
            addListIndexValues(byCompositionColumn, plan.compositionColumns(), plan, "NO_COMPOSITION_COLUMN");
            addListIndexValues(byRolePerformerColumn, plan.rolePerformerColumns(), plan,
                    "NO_ROLE_PERFORMER_COLUMN");
            addToIndex(byReadiness, indexKey(plan.readiness()), plan);
            addListIndexValues(byReadyReason, plan.readyReasons(), plan, "NO_READY_REASON");
            addListIndexValues(byMissingReason, plan.missingReasons(), plan, "NO_MISSING_REASON");
            addListIndexValues(byAction, plan.actions(), plan, "NO_ACTION_REQUIRED");
            addToIndex(byQueryContract, indexKey(plan.queryContract()), plan);
            addToIndex(byExecutableReadiness, indexKey(repositoryCompositionQueryExecutableReadiness(plan)), plan);
            addToIndex(byExecutionContract, indexKey(plan.executionContract()), plan);
        }
        return new RepositoryCompositionQueryIndex(plans,
                immutableListMap(byRoleType),
                immutableListMap(byNamedIndex),
                immutableListMap(byContract),
                immutableListMap(byScenario),
                immutableListMap(bySignerRoleSet),
                immutableListMap(byCompositionColumn),
                immutableListMap(byRolePerformerColumn),
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byQueryContract),
                immutableListMap(byExecutableReadiness),
                immutableListMap(byExecutionContract));
    }

    @JsonProperty(value = "repositoryCompositionQueryPlans", access = JsonProperty.Access.READ_ONLY)
    public List<RepositoryCompositionQueryPlan> repositoryCompositionQueryPlans() {
        return repositoryCompositionQueryPlans(appDdlCompositionIndexPlans(), scenarioContractContextPlans());
    }

    static List<RepositoryCompositionQueryPlan> repositoryCompositionQueryPlans(
            List<AppDdlCompositionIndexPlan> compositionPlans,
            List<ScenarioContractContextPlan> contextPlans) {
        if (compositionPlans == null || compositionPlans.isEmpty()) {
            return List.of();
        }
        List<RepositoryCompositionQueryPlan> plans = new ArrayList<>();
        for (AppDdlCompositionIndexPlan composition : compositionPlans) {
            for (ScenarioContractContextPlan context : safeList(contextPlans)) {
                if (!repositoryCompositionContextMatches(composition, context)) {
                    continue;
                }
                List<String> missingReasons = repositoryCompositionQueryMissingReasons(composition, context);
                List<String> readyReasons = missingReasons.isEmpty()
                        ? List.of("compositionColumn", "appDdlNamedIndex", "rolePerformerIdentity",
                                "roleSignedContractContext", "roleOnlyBoundary")
                        : List.of();
                List<String> actions = missingReasons.isEmpty()
                        ? List.of("NO_ACTION_REQUIRED")
                        : repositoryCompositionQueryActions(missingReasons);
                String readiness = missingReasons.isEmpty()
                        ? "REPOSITORY_COMPOSITION_QUERY_READY"
                        : "REPOSITORY_COMPOSITION_QUERY_INCOMPLETE";
                boolean executable = missingReasons.isEmpty();
                plans.add(new RepositoryCompositionQueryPlan(
                        composition.roleType(),
                        composition.tableName(),
                        composition.namedIndexName(),
                        context.scenarioName(),
                        context.contractType(),
                        context.contractParameterKey(),
                        context.signerRoleTypes(),
                        context.signerRoleSet(),
                        composition.compositionColumns(),
                        composition.rolePerformerColumns(),
                        composition.performerTypes(),
                        "COLLECTION_BY_COMPOSITION_AND_ROLE_PERFORMER",
                        "SCENARIO_CONTRACT_CONTEXT",
                        "ROLE_PARTICIPANT_IDENTITY",
                        true,
                        true,
                        true,
                        readiness,
                        readyReasons,
                        missingReasons,
                        actions,
                        "",
                        executable,
                        ""));
            }
        }
        return List.copyOf(plans);
    }

    @JsonProperty(value = "repositoryCompositionQueryPlansByRoleType", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryCompositionQueryPlan>> repositoryCompositionQueryPlansByRoleType() {
        return repositoryCompositionQueryIndex().byRoleType();
    }

    @JsonProperty(value = "repositoryCompositionQueryPlansByNamedIndex", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryCompositionQueryPlan>> repositoryCompositionQueryPlansByNamedIndex() {
        return repositoryCompositionQueryIndex().byNamedIndex();
    }

    @JsonProperty(value = "repositoryCompositionQueryPlansByContract", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryCompositionQueryPlan>> repositoryCompositionQueryPlansByContract() {
        return repositoryCompositionQueryIndex().byContract();
    }

    @JsonProperty(value = "repositoryCompositionQueryPlansByScenario", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryCompositionQueryPlan>> repositoryCompositionQueryPlansByScenario() {
        return repositoryCompositionQueryIndex().byScenario();
    }

    @JsonProperty(value = "repositoryCompositionQueryPlansBySignerRoleSet", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryCompositionQueryPlan>> repositoryCompositionQueryPlansBySignerRoleSet() {
        return repositoryCompositionQueryIndex().bySignerRoleSet();
    }

    @JsonProperty(value = "repositoryCompositionQueryPlansByCompositionColumn",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryCompositionQueryPlan>> repositoryCompositionQueryPlansByCompositionColumn() {
        return repositoryCompositionQueryIndex().byCompositionColumn();
    }

    @JsonProperty(value = "repositoryCompositionQueryPlansByRolePerformerColumn",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryCompositionQueryPlan>> repositoryCompositionQueryPlansByRolePerformerColumn() {
        return repositoryCompositionQueryIndex().byRolePerformerColumn();
    }

    @JsonProperty(value = "repositoryCompositionQueryPlansByReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryCompositionQueryPlan>> repositoryCompositionQueryPlansByReadiness() {
        return repositoryCompositionQueryIndex().byReadiness();
    }

    @JsonProperty(value = "repositoryCompositionQueryPlansByReadyReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryCompositionQueryPlan>> repositoryCompositionQueryPlansByReadyReason() {
        return repositoryCompositionQueryIndex().byReadyReason();
    }

    @JsonProperty(value = "repositoryCompositionQueryPlansByMissingReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryCompositionQueryPlan>> repositoryCompositionQueryPlansByMissingReason() {
        return repositoryCompositionQueryIndex().byMissingReason();
    }

    @JsonProperty(value = "repositoryCompositionQueryPlansByAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryCompositionQueryPlan>> repositoryCompositionQueryPlansByAction() {
        return repositoryCompositionQueryIndex().byAction();
    }

    @JsonProperty(value = "repositoryCompositionQueryPlansByQueryContract", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryCompositionQueryPlan>> repositoryCompositionQueryPlansByQueryContract() {
        return repositoryCompositionQueryIndex().byQueryContract();
    }

    @JsonProperty(value = "repositoryCompositionQueryPlansByExecutableReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryCompositionQueryPlan>> repositoryCompositionQueryPlansByExecutableReadiness() {
        return repositoryCompositionQueryIndex().byExecutableReadiness();
    }

    @JsonProperty(value = "repositoryCompositionQueryPlansByExecutionContract", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryCompositionQueryPlan>> repositoryCompositionQueryPlansByExecutionContract() {
        return repositoryCompositionQueryIndex().byExecutionContract();
    }

    @JsonProperty(value = "roleBusinessModelMigrationIndex", access = JsonProperty.Access.READ_ONLY)
    public RoleBusinessModelMigrationIndex roleBusinessModelMigrationIndex() {
        List<AppSemanticMigrationPlan> plans = computeRoleBusinessModelMigrationPlans();
        if (plans.isEmpty()) {
            return new RoleBusinessModelMigrationIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<AppSemanticMigrationPlan>> byRole = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byPath = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byMigrationType = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byTargetKind = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byParticipantCategory = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byRoleMigrationType = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byRoleModelContract = new LinkedHashMap<>();
        for (AppSemanticMigrationPlan plan : plans) {
            String role = roleBusinessModelRole(plan);
            String migrationType = indexKey(plan.migrationType());
            String action = indexKey(plan.migrationAction());
            addToIndex(byRole, role, plan);
            addToIndex(byMigrationType, migrationType, plan);
            addToIndex(byAction, action, plan);
            addToIndex(byTargetKind, indexKey(plan.targetKind()), plan);
            addListValuesToIndex(byParticipantCategory, plan.participantCategories(),
                    "NO_PARTICIPANT_CATEGORY", plan);
            addToIndex(byRoleMigrationType, role + ":" + migrationType, plan);
            addToIndex(byRoleModelContract, role + ":" + migrationType + ":" + action, plan);
            for (String path : safeList(plan.paths())) {
                if (path != null && !path.isBlank()) {
                    addToIndex(byPath, path, plan);
                }
            }
        }
        return new RoleBusinessModelMigrationIndex(plans,
                immutableListMap(byRole),
                immutableListMap(byPath),
                immutableListMap(byMigrationType),
                immutableListMap(byAction),
                immutableListMap(byTargetKind),
                immutableListMap(byParticipantCategory),
                immutableListMap(byRoleMigrationType),
                immutableListMap(byRoleModelContract));
    }

    private List<AppSemanticMigrationPlan> computeRoleBusinessModelMigrationPlans() {
        List<AppSemanticMigrationPlan> plans = new ArrayList<>();
        addRoleBusinessModelMigrationPlans(plans, generatedAppSemanticMigrationPlans);
        addRoleBusinessModelMigrationPlans(plans, appSemanticMigrationPlans);
        return List.copyOf(plans);
    }

    @JsonProperty(value = "roleBusinessModelMigrationPlans",
            access = JsonProperty.Access.READ_ONLY)
    public List<AppSemanticMigrationPlan> roleBusinessModelMigrationPlans() {
        return roleBusinessModelMigrationIndex().plans();
    }

    @JsonProperty(value = "roleBusinessModelMigrationPlansByRole",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> roleBusinessModelMigrationPlansByRole() {
        return roleBusinessModelMigrationIndex().byRole();
    }

    @JsonProperty(value = "roleBusinessModelMigrationPlansByPath",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> roleBusinessModelMigrationPlansByPath() {
        return roleBusinessModelMigrationIndex().byPath();
    }

    @JsonProperty(value = "roleBusinessModelMigrationPlansByMigrationType",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> roleBusinessModelMigrationPlansByMigrationType() {
        return roleBusinessModelMigrationIndex().byMigrationType();
    }

    @JsonProperty(value = "roleBusinessModelMigrationPlansByAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> roleBusinessModelMigrationPlansByAction() {
        return roleBusinessModelMigrationIndex().byAction();
    }

    @JsonProperty(value = "roleBusinessModelMigrationPlansByTargetKind",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> roleBusinessModelMigrationPlansByTargetKind() {
        return roleBusinessModelMigrationIndex().byTargetKind();
    }

    @JsonProperty(value = "roleBusinessModelMigrationPlansByParticipantCategory",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> roleBusinessModelMigrationPlansByParticipantCategory() {
        return roleBusinessModelMigrationIndex().byParticipantCategory();
    }

    @JsonProperty(value = "roleBusinessModelMigrationPlansByRoleMigrationType",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> roleBusinessModelMigrationPlansByRoleMigrationType() {
        return roleBusinessModelMigrationIndex().byRoleMigrationType();
    }

    @JsonProperty(value = "roleBusinessModelMigrationPlansByRoleModelContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> roleBusinessModelMigrationPlansByRoleModelContract() {
        return roleBusinessModelMigrationIndex().byRoleModelContract();
    }

    private static void addRoleBusinessModelMigrationPlans(List<AppSemanticMigrationPlan> plans,
                                                           List<AppSemanticMigrationPlan> candidates) {
        for (AppSemanticMigrationPlan plan : safeList(candidates)) {
            if (plan == null || !RoleBusinessModelClosureSupport.roleBusinessModelMigrationType(plan.migrationType())
                    || plans.contains(plan)) {
                continue;
            }
            plans.add(plan);
        }
    }

    private static String roleBusinessModelRole(AppSemanticMigrationPlan plan) {
        return RoleBusinessModelClosureSupport.roleBusinessModelRole(plan);
    }

    @JsonProperty(value = "roleBusinessModelClosureIndex", access = JsonProperty.Access.READ_ONLY)
    public RoleBusinessModelClosureIndex roleBusinessModelClosureIndex() {
        List<RoleBusinessModelClosurePlan> plans = roleBusinessModelClosurePlans();
        if (plans.isEmpty()) {
            return new RoleBusinessModelClosureIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<RoleBusinessModelClosurePlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<RoleBusinessModelClosurePlan>> byOpenReason = new LinkedHashMap<>();
        Map<String, List<RoleBusinessModelClosurePlan>> byAction = new LinkedHashMap<>();
        Map<String, List<RoleBusinessModelClosurePlan>> byRole = new LinkedHashMap<>();
        Map<String, List<RoleBusinessModelClosurePlan>> byParticipationKind = new LinkedHashMap<>();
        Map<String, List<RoleBusinessModelClosurePlan>> byPerformerTypeSet = new LinkedHashMap<>();
        Map<String, List<RoleBusinessModelClosurePlan>> byRoleModelContract = new LinkedHashMap<>();
        for (RoleBusinessModelClosurePlan plan : plans) {
            addToIndex(byReadiness, indexKey(plan.readiness()), plan);
            addListIndexValues(byOpenReason, plan.openReasons(), plan, "NO_OPEN_REASON");
            addListIndexValues(byAction, plan.actions(), plan, "NO_ACTION_REQUIRED");
            addToIndex(byRole, indexKey(plan.roleType()), plan);
            addToIndex(byParticipationKind, indexKey(plan.participationKind()), plan);
            addToIndex(byPerformerTypeSet, RoleBusinessModelClosureSupport.performerSetKey(plan.performerTypes()), plan);
            addToIndex(byRoleModelContract, indexKey(plan.roleModelContract()), plan);
        }
        return new RoleBusinessModelClosureIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byOpenReason),
                immutableListMap(byAction),
                immutableListMap(byRole),
                immutableListMap(byParticipationKind),
                immutableListMap(byPerformerTypeSet),
                immutableListMap(byRoleModelContract));
    }

    private List<RoleBusinessModelClosurePlan> roleBusinessModelClosurePlans() {
        return RoleBusinessModelClosureSupport.plans(roleParticipantPlans, roleBusinessModelMigrationPlans());
    }

    @JsonProperty(value = "roleBusinessModelClosurePlansByReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleBusinessModelClosurePlan>> roleBusinessModelClosurePlansByReadiness() {
        return roleBusinessModelClosureIndex().byReadiness();
    }

    @JsonProperty(value = "roleBusinessModelClosurePlansByOpenReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleBusinessModelClosurePlan>> roleBusinessModelClosurePlansByOpenReason() {
        return roleBusinessModelClosureIndex().byOpenReason();
    }

    @JsonProperty(value = "roleBusinessModelClosurePlansByAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleBusinessModelClosurePlan>> roleBusinessModelClosurePlansByAction() {
        return roleBusinessModelClosureIndex().byAction();
    }

    @JsonProperty(value = "roleBusinessModelClosurePlansByRole",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleBusinessModelClosurePlan>> roleBusinessModelClosurePlansByRole() {
        return roleBusinessModelClosureIndex().byRole();
    }

    @JsonProperty(value = "roleBusinessModelClosurePlansByParticipationKind",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleBusinessModelClosurePlan>> roleBusinessModelClosurePlansByParticipationKind() {
        return roleBusinessModelClosureIndex().byParticipationKind();
    }

    @JsonProperty(value = "roleBusinessModelClosurePlansByPerformerTypeSet",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleBusinessModelClosurePlan>> roleBusinessModelClosurePlansByPerformerTypeSet() {
        return roleBusinessModelClosureIndex().byPerformerTypeSet();
    }

    @JsonProperty(value = "roleBusinessModelClosurePlansByRoleModelContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleBusinessModelClosurePlan>> roleBusinessModelClosurePlansByRoleModelContract() {
        return roleBusinessModelClosureIndex().byRoleModelContract();
    }

    @JsonProperty(value = "sagaRecoveryEvidenceReadinessIndex", access = JsonProperty.Access.READ_ONLY)
    public SagaRecoveryEvidenceReadinessIndex sagaRecoveryEvidenceReadinessIndex() {
        List<SagaRecoveryEvidenceReview> plans = safeList(sagaRecoveryEvidencePlans).stream()
                .filter(plan -> plan != null)
                .map(GeneratedServiceCatalog::enrichSagaRecoveryEvidenceReview)
                .toList();
        if (plans.isEmpty()) {
            return new SagaRecoveryEvidenceReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<SagaRecoveryEvidenceReview>> byReadiness = new LinkedHashMap<>();
        Map<String, List<SagaRecoveryEvidenceReview>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<SagaRecoveryEvidenceReview>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<SagaRecoveryEvidenceReview>> byAction = new LinkedHashMap<>();
        Map<String, List<SagaRecoveryEvidenceReview>> bySaga = new LinkedHashMap<>();
        Map<String, List<SagaRecoveryEvidenceReview>> byTargetService = new LinkedHashMap<>();
        Map<String, List<SagaRecoveryEvidenceReview>> byEvidenceRole = new LinkedHashMap<>();
        Map<String, List<SagaRecoveryEvidenceReview>> byEvidencePath = new LinkedHashMap<>();
        Map<String, List<SagaRecoveryEvidenceReview>> byObjectType = new LinkedHashMap<>();
        Map<String, List<SagaRecoveryEvidenceReview>> byRepository = new LinkedHashMap<>();
        Map<String, List<SagaRecoveryEvidenceReview>> bySnapshotBoundary = new LinkedHashMap<>();
        Map<String, List<SagaRecoveryEvidenceReview>> byRecoveryContract = new LinkedHashMap<>();
        for (SagaRecoveryEvidenceReview plan : plans) {
            if ("SAGA_RECOVERY_EVIDENCE_READY".equals(plan.readiness)) {
                addToIndex(byReadiness, plan.readiness, plan);
                for (String reason : safeList(plan.readyReasons)) {
                    addToIndex(byReadyReason, indexKey(reason), plan);
                }
                for (String reason : safeList(plan.missingReasons)) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.actions)) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            } else {
                addToIndex(byReadiness, indexKey(plan.readiness), plan);
                for (String reason : safeList(plan.missingReasons)) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.actions)) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            }
            addToIndex(bySaga, indexKey(plan.sagaServiceName), plan);
            addToIndex(byTargetService, indexKey(plan.targetServiceName), plan);
            addToIndex(byEvidenceRole, indexKey(plan.evidenceRole), plan);
            addToIndex(byEvidencePath, indexKey(plan.evidencePath), plan);
            addToIndex(byObjectType, indexKey(plan.objectType), plan);
            addToIndex(byRepository, indexKey(firstNonBlank(plan.resolvedRepositoryName, plan.repositoryName)), plan);
            addToIndex(bySnapshotBoundary, indexKey(plan.snapshotBoundary), plan);
            addToIndex(byRecoveryContract, indexKey(plan.recoveryContract), plan);
        }
        return new SagaRecoveryEvidenceReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(bySaga),
                immutableListMap(byTargetService),
                immutableListMap(byEvidenceRole),
                immutableListMap(byEvidencePath),
                immutableListMap(byObjectType),
                immutableListMap(byRepository),
                immutableListMap(bySnapshotBoundary),
                immutableListMap(byRecoveryContract));
    }

    @JsonProperty(value = "sagaRecoveryEvidencePlansByReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaRecoveryEvidenceReview>> sagaRecoveryEvidencePlansByReadiness() {
        return sagaRecoveryEvidenceReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "sagaRecoveryEvidencePlansByReadyReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaRecoveryEvidenceReview>> sagaRecoveryEvidencePlansByReadyReason() {
        return sagaRecoveryEvidenceReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "sagaRecoveryEvidencePlansByMissingReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaRecoveryEvidenceReview>> sagaRecoveryEvidencePlansByMissingReason() {
        return sagaRecoveryEvidenceReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "sagaRecoveryEvidencePlansByAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaRecoveryEvidenceReview>> sagaRecoveryEvidencePlansByAction() {
        return sagaRecoveryEvidenceReadinessIndex().byAction();
    }

    @JsonProperty(value = "sagaRecoveryEvidencePlansByEvidenceRole", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaRecoveryEvidenceReview>> sagaRecoveryEvidencePlansByEvidenceRole() {
        return sagaRecoveryEvidenceReadinessIndex().byEvidenceRole();
    }

    @JsonProperty(value = "sagaRecoveryEvidencePlansByEvidencePath", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaRecoveryEvidenceReview>> sagaRecoveryEvidencePlansByEvidencePath() {
        return sagaRecoveryEvidenceReadinessIndex().byEvidencePath();
    }

    @JsonProperty(value = "sagaRecoveryEvidencePlansByRepository", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaRecoveryEvidenceReview>> sagaRecoveryEvidencePlansByRepository() {
        return sagaRecoveryEvidenceReadinessIndex().byRepository();
    }

    @JsonProperty(value = "sagaRecoveryEvidencePlansBySnapshotBoundary", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaRecoveryEvidenceReview>> sagaRecoveryEvidencePlansBySnapshotBoundary() {
        return sagaRecoveryEvidenceReadinessIndex().bySnapshotBoundary();
    }

    @JsonProperty(value = "sagaRecoveryEvidencePlansByRecoveryContract", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaRecoveryEvidenceReview>> sagaRecoveryEvidencePlansByRecoveryContract() {
        return sagaRecoveryEvidenceReadinessIndex().byRecoveryContract();
    }

    @JsonProperty(value = "derivedCalculationPlans", access = JsonProperty.Access.READ_ONLY)
    public List<DerivedCalculationPlan> derivedCalculationPlans() {
        List<DerivedCalculationPlan> plans = new ArrayList<>();
        for (GeneratedServiceMetadata item : safeList(services)) {
            if (item == null || item.calculationAssignments() == null
                    || item.calculationAssignments().isEmpty()) {
                continue;
            }
            for (CalculationAssignment assignment : item.calculationAssignments()) {
                if (assignment == null) {
                    continue;
                }
                plans.add(new DerivedCalculationPlan(
                        item.serviceName(),
                        item.serviceKind(),
                        item.transactionMode(),
                        item.transactionResourceId(),
                        assignment.targetPath(),
                        safeList(assignment.sourcePaths()),
                        assignment.expression(),
                        safeList(item.serviceParameterTypes()),
                        safeList(item.serviceParameterNames()),
                        derivedCalculationReady(item, assignment)
                                ? "DERIVED_CALCULATION_READY"
                                : "DERIVED_CALCULATION_INCOMPLETE",
                        List.of(),
                        List.of(),
                        List.of(),
                        ""));
            }
        }
        return plans.stream()
                .map(GeneratedServiceCatalog::enrichDerivedCalculationPlan)
                .toList();
    }

    @JsonProperty(value = "derivedCalculationReadinessIndex", access = JsonProperty.Access.READ_ONLY)
    public DerivedCalculationReadinessIndex derivedCalculationReadinessIndex() {
        List<DerivedCalculationPlan> plans = derivedCalculationPlans();
        Map<String, List<DerivedCalculationPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<DerivedCalculationPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<DerivedCalculationPlan>> byService = new LinkedHashMap<>();
        Map<String, List<DerivedCalculationPlan>> byTargetPath = new LinkedHashMap<>();
        Map<String, List<DerivedCalculationPlan>> bySourcePath = new LinkedHashMap<>();
        Map<String, List<DerivedCalculationPlan>> byExpression = new LinkedHashMap<>();
        Map<String, List<DerivedCalculationPlan>> byTransactionResource = new LinkedHashMap<>();
        Map<String, List<DerivedCalculationPlan>> byExecutionContract = new LinkedHashMap<>();
        for (DerivedCalculationPlan plan : plans) {
            addToIndex(byReadiness, indexKey(plan.readiness()), plan);
            addListIndexValues(byReadyReason, plan.readyReasons(), plan,
                    "NO_READY_DERIVED_CALCULATION_REASON");
            addToIndex(byService, indexKey(plan.serviceName()), plan);
            addToIndex(byTargetPath, indexKey(plan.targetPath()), plan);
            for (String sourcePath : safeList(plan.sourcePaths())) {
                addToIndex(bySourcePath, indexKey(sourcePath), plan);
            }
            addToIndex(byExpression, indexKey(plan.expression()), plan);
            addToIndex(byTransactionResource, indexKey(plan.transactionResourceId()), plan);
            addToIndex(byExecutionContract, indexKey(plan.executionContract()), plan);
        }
        return new DerivedCalculationReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byService),
                immutableListMap(byTargetPath),
                immutableListMap(bySourcePath),
                immutableListMap(byExpression),
                immutableListMap(byTransactionResource),
                immutableListMap(byExecutionContract));
    }

    @JsonProperty(value = "derivedCalculationPlansByReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedCalculationPlan>> derivedCalculationPlansByReadiness() {
        return derivedCalculationReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "derivedCalculationPlansByReadyReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedCalculationPlan>> derivedCalculationPlansByReadyReason() {
        return derivedCalculationReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "derivedCalculationPlansByService", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedCalculationPlan>> derivedCalculationPlansByService() {
        return derivedCalculationReadinessIndex().byService();
    }

    @JsonProperty(value = "derivedCalculationPlansByTargetPath", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedCalculationPlan>> derivedCalculationPlansByTargetPath() {
        return derivedCalculationReadinessIndex().byTargetPath();
    }

    @JsonProperty(value = "derivedCalculationPlansBySourcePath", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedCalculationPlan>> derivedCalculationPlansBySourcePath() {
        return derivedCalculationReadinessIndex().bySourcePath();
    }

    @JsonProperty(value = "derivedCalculationPlansByExpression", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedCalculationPlan>> derivedCalculationPlansByExpression() {
        return derivedCalculationReadinessIndex().byExpression();
    }

    @JsonProperty(value = "derivedCalculationPlansByTransactionResource", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedCalculationPlan>> derivedCalculationPlansByTransactionResource() {
        return derivedCalculationReadinessIndex().byTransactionResource();
    }

    @JsonProperty(value = "derivedCalculationPlansByExecutionContract", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedCalculationPlan>> derivedCalculationPlansByExecutionContract() {
        return derivedCalculationReadinessIndex().byExecutionContract();
    }

    private static String derivedCalculationExecutionContractKey(DerivedCalculationPlan plan) {
        if (plan == null) {
            return "NO_DERIVED_CALCULATION_CONTRACT";
        }
        String sourcePathSet = safeList(plan.sourcePaths()).isEmpty()
                ? "NO_SOURCE_PATH"
                : String.join("+", plan.sourcePaths());
        return indexKey(plan.serviceName()) + ":" + indexKey(plan.targetPath())
                + "<-" + sourcePathSet
                + ":" + indexKey(plan.transactionResourceId())
                + ":" + indexKey(plan.transactionMode() == null ? null : plan.transactionMode().name())
                + ":" + indexKey(plan.readiness());
    }

    private static DerivedCalculationPlan enrichDerivedCalculationPlan(DerivedCalculationPlan plan) {
        if (plan == null) {
            return null;
        }
        List<String> missingReasons = safeList(plan.missingReasons()).isEmpty()
                ? derivedCalculationMissingReasons(plan)
                : plan.missingReasons();
        String readiness = firstNonBlank(plan.readiness(),
                missingReasons.isEmpty()
                        || (missingReasons.size() == 1
                        && "NO_MISSING_DERIVED_CALCULATION_REASON".equals(missingReasons.get(0)))
                        ? "DERIVED_CALCULATION_READY"
                        : "DERIVED_CALCULATION_INCOMPLETE");
        List<String> readyReasons = safeList(plan.readyReasons()).isEmpty()
                && "DERIVED_CALCULATION_READY".equals(readiness)
                ? derivedCalculationReadyReasons(plan)
                : plan.readyReasons();
        List<String> normalizedMissingReasons = missingReasons.isEmpty()
                ? List.of("NO_MISSING_DERIVED_CALCULATION_REASON")
                : missingReasons;
        List<String> actions = safeList(plan.actions()).isEmpty()
                ? (missingReasons.isEmpty() ? List.of("NO_ACTION_REQUIRED") : derivedCalculationActions(missingReasons))
                : plan.actions();
        String executionContract = firstNonBlank(plan.executionContract(),
                derivedCalculationExecutionContractKey(new DerivedCalculationPlan(plan.serviceName(),
                        plan.serviceKind(),
                        plan.transactionMode(),
                        plan.transactionResourceId(),
                        plan.targetPath(),
                        plan.sourcePaths(),
                        plan.expression(),
                        plan.serviceParameterTypes(),
                        plan.serviceParameterNames(),
                        readiness,
                        readyReasons,
                        normalizedMissingReasons,
                        actions,
                        "")));
        return new DerivedCalculationPlan(plan.serviceName(),
                plan.serviceKind(),
                plan.transactionMode(),
                plan.transactionResourceId(),
                plan.targetPath(),
                plan.sourcePaths(),
                plan.expression(),
                plan.serviceParameterTypes(),
                plan.serviceParameterNames(),
                readiness,
                readyReasons,
                normalizedMissingReasons,
                actions,
                executionContract);
    }

    private static List<String> derivedCalculationReadyReasons(DerivedCalculationPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        if (plan.serviceKind() == GeneratedServiceKind.ATOMIC_CALCULATE) {
            reasons.add("calculateService");
        }
        if (!isBlank(plan.targetPath())) {
            reasons.add("targetPath");
        }
        if (!isBlank(plan.expression())) {
            reasons.add("expression");
        }
        if (!safeList(plan.sourcePaths()).isEmpty()) {
            reasons.add("sourcePaths");
        }
        if (!isBlank(plan.transactionResourceId())) {
            reasons.add("transactionResource");
        }
        return List.copyOf(reasons);
    }

    private static List<String> derivedCalculationMissingReasons(DerivedCalculationPlan plan) {
        if (plan == null) {
            return List.of("calculationPlan");
        }
        List<String> reasons = new ArrayList<>();
        if (plan.serviceKind() != GeneratedServiceKind.ATOMIC_CALCULATE) {
            reasons.add("calculateService");
        }
        if (isBlank(plan.targetPath())) {
            reasons.add("targetPath");
        }
        if (isBlank(plan.expression())) {
            reasons.add("expression");
        }
        return List.copyOf(reasons);
    }

    private static List<String> derivedCalculationActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "calculationPlan" -> actions.add("registerDerivedCalculationPlan");
                case "calculateService" -> actions.add("declareAtomicCalculateService");
                case "targetPath" -> actions.add("declareDerivedCalculationTarget");
                case "expression" -> actions.add("declareDerivedCalculationExpression");
                default -> actions.add("reviewDerivedCalculationPlan");
            }
        }
        return List.copyOf(actions);
    }

    @JsonProperty(value = "derivedBusinessFactIndex", access = JsonProperty.Access.READ_ONLY)
    public DerivedBusinessFactIndex derivedBusinessFactIndex() {
        List<DerivedBusinessFactPlan> plans = derivedBusinessFactPlans();
        Map<String, List<DerivedBusinessFactPlan>> byFactKind = new LinkedHashMap<>();
        Map<String, List<DerivedBusinessFactPlan>> byTargetOwnerType = new LinkedHashMap<>();
        Map<String, List<DerivedBusinessFactPlan>> byTargetOwnerCategory = new LinkedHashMap<>();
        Map<String, List<DerivedBusinessFactPlan>> byTargetPath = new LinkedHashMap<>();
        Map<String, List<DerivedBusinessFactPlan>> bySourcePath = new LinkedHashMap<>();
        Map<String, List<DerivedBusinessFactPlan>> bySourceOwnerType = new LinkedHashMap<>();
        Map<String, List<DerivedBusinessFactPlan>> byService = new LinkedHashMap<>();
        Map<String, List<DerivedBusinessFactPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<DerivedBusinessFactPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<DerivedBusinessFactPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<DerivedBusinessFactPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<DerivedBusinessFactPlan>> byExpression = new LinkedHashMap<>();
        Map<String, List<DerivedBusinessFactPlan>> byTransactionResource = new LinkedHashMap<>();
        Map<String, List<DerivedBusinessFactPlan>> byBusinessFactKindSet = new LinkedHashMap<>();
        Map<String, List<DerivedBusinessFactPlan>> byBusinessFactContract = new LinkedHashMap<>();
        for (DerivedBusinessFactPlan plan : plans) {
            for (String kind : safeList(plan.businessFactKinds())) {
                addToIndex(byFactKind, indexKey(kind), plan);
            }
            addToIndex(byTargetOwnerType, indexKey(plan.targetOwnerType()), plan);
            addToIndex(byTargetOwnerCategory, indexKey(plan.targetOwnerCategory()), plan);
            addToIndex(byTargetPath, indexKey(plan.targetPath()), plan);
            for (String sourcePath : safeList(plan.sourcePaths())) {
                addToIndex(bySourcePath, indexKey(sourcePath), plan);
            }
            for (String sourceOwnerType : safeList(plan.sourceOwnerTypes())) {
                addToIndex(bySourceOwnerType, indexKey(sourceOwnerType), plan);
            }
            addToIndex(byService, indexKey(plan.serviceName()), plan);
            addToIndex(byReadiness, indexKey(plan.readiness()), plan);
            for (String reason : safeList(plan.readyReasons())) {
                addToIndex(byReadyReason, indexKey(reason), plan);
            }
            for (String reason : safeList(plan.missingReasons())) {
                addToIndex(byMissingReason, indexKey(reason), plan);
            }
            for (String action : safeList(plan.actions())) {
                addToIndex(byAction, indexKey(action), plan);
            }
            addToIndex(byExpression, indexKey(plan.expression()), plan);
            addToIndex(byTransactionResource, indexKey(plan.transactionResourceId()), plan);
            addToIndex(byBusinessFactKindSet,
                    indexKey(String.join("+", safeList(plan.businessFactKinds()))), plan);
            addToIndex(byBusinessFactContract, indexKey(plan.businessFactContract()), plan);
        }
        return new DerivedBusinessFactIndex(plans,
                immutableListMap(byFactKind),
                immutableListMap(byTargetOwnerType),
                immutableListMap(byTargetOwnerCategory),
                immutableListMap(byTargetPath),
                immutableListMap(bySourcePath),
                immutableListMap(bySourceOwnerType),
                immutableListMap(byService),
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byExpression),
                immutableListMap(byTransactionResource),
                immutableListMap(byBusinessFactKindSet),
                immutableListMap(byBusinessFactContract));
    }

    @JsonProperty(value = "derivedBusinessFactPlansByFactKind", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedBusinessFactPlan>> derivedBusinessFactPlansByFactKind() {
        return derivedBusinessFactIndex().byFactKind();
    }

    @JsonProperty(value = "derivedBusinessFactPlansByTargetOwnerType", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedBusinessFactPlan>> derivedBusinessFactPlansByTargetOwnerType() {
        return derivedBusinessFactIndex().byTargetOwnerType();
    }

    @JsonProperty(value = "derivedBusinessFactPlansByTargetOwnerCategory", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedBusinessFactPlan>> derivedBusinessFactPlansByTargetOwnerCategory() {
        return derivedBusinessFactIndex().byTargetOwnerCategory();
    }

    @JsonProperty(value = "derivedBusinessFactPlansByTargetPath", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedBusinessFactPlan>> derivedBusinessFactPlansByTargetPath() {
        return derivedBusinessFactIndex().byTargetPath();
    }

    @JsonProperty(value = "derivedBusinessFactPlansBySourcePath", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedBusinessFactPlan>> derivedBusinessFactPlansBySourcePath() {
        return derivedBusinessFactIndex().bySourcePath();
    }

    @JsonProperty(value = "derivedBusinessFactPlansBySourceOwnerType", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedBusinessFactPlan>> derivedBusinessFactPlansBySourceOwnerType() {
        return derivedBusinessFactIndex().bySourceOwnerType();
    }

    @JsonProperty(value = "derivedBusinessFactPlansByService", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedBusinessFactPlan>> derivedBusinessFactPlansByService() {
        return derivedBusinessFactIndex().byService();
    }

    @JsonProperty(value = "derivedBusinessFactPlansByReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedBusinessFactPlan>> derivedBusinessFactPlansByReadiness() {
        return derivedBusinessFactIndex().byReadiness();
    }

    @JsonProperty(value = "derivedBusinessFactPlansByReadyReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedBusinessFactPlan>> derivedBusinessFactPlansByReadyReason() {
        return derivedBusinessFactIndex().byReadyReason();
    }

    @JsonProperty(value = "derivedBusinessFactPlansByMissingReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedBusinessFactPlan>> derivedBusinessFactPlansByMissingReason() {
        return derivedBusinessFactIndex().byMissingReason();
    }

    @JsonProperty(value = "derivedBusinessFactPlansByAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedBusinessFactPlan>> derivedBusinessFactPlansByAction() {
        return derivedBusinessFactIndex().byAction();
    }

    @JsonProperty(value = "derivedBusinessFactPlansByExpression", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedBusinessFactPlan>> derivedBusinessFactPlansByExpression() {
        return derivedBusinessFactIndex().byExpression();
    }

    @JsonProperty(value = "derivedBusinessFactPlansByTransactionResource", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedBusinessFactPlan>> derivedBusinessFactPlansByTransactionResource() {
        return derivedBusinessFactIndex().byTransactionResource();
    }

    @JsonProperty(value = "derivedBusinessFactPlansByBusinessFactKindSet",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedBusinessFactPlan>> derivedBusinessFactPlansByBusinessFactKindSet() {
        return derivedBusinessFactIndex().byBusinessFactKindSet();
    }

    @JsonProperty(value = "derivedBusinessFactPlansByBusinessFactContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DerivedBusinessFactPlan>> derivedBusinessFactPlansByBusinessFactContract() {
        return derivedBusinessFactIndex().byBusinessFactContract();
    }

    @JsonProperty(value = "derivedBusinessFactPlans", access = JsonProperty.Access.READ_ONLY)
    public List<DerivedBusinessFactPlan> derivedBusinessFactPlans() {
        List<DerivedBusinessFactPlan> plans = new ArrayList<>();
        for (DerivedCalculationPlan calculation : derivedCalculationPlans()) {
            String targetOwnerType = ownerType(calculation.targetPath());
            String targetOwnerCategory = appDdl().tableForBusinessType(targetOwnerType)
                    .map(AppDdlCatalog.Table::category)
                    .filter(value -> !value.isBlank())
                    .orElse("unknown");
            List<String> sourceOwnerTypes = ownerTypes(calculation.sourcePaths());
            List<String> factKinds = businessFactKinds(calculation.targetPath(), calculation.sourcePaths(),
                    targetOwnerCategory);
            DerivedBusinessFactPlan plan = new DerivedBusinessFactPlan(
                    calculation.serviceName(),
                    calculation.targetPath(),
                    targetOwnerType,
                    targetOwnerCategory,
                    safeList(calculation.sourcePaths()),
                    sourceOwnerTypes,
                    factKinds,
                    calculation.expression(),
                    calculation.transactionMode(),
                    calculation.transactionResourceId(),
                    calculation.readiness(),
                    derivedBusinessFactContract(calculation, targetOwnerType, targetOwnerCategory, factKinds),
                    List.of(),
                    List.of(),
                    List.of());
            plans.add(enrichDerivedBusinessFactPlan(plan));
        }
        return List.copyOf(plans);
    }

    private static DerivedBusinessFactPlan enrichDerivedBusinessFactPlan(DerivedBusinessFactPlan plan) {
        if (plan == null) {
            return null;
        }
        List<String> missingReasons = safeList(plan.missingReasons()).isEmpty()
                ? derivedBusinessFactMissingReasons(plan)
                : plan.missingReasons();
        String readiness = firstNonBlank(plan.readiness(),
                missingReasons.isEmpty()
                        || (missingReasons.size() == 1
                        && "NO_MISSING_DERIVED_BUSINESS_FACT_REASON".equals(missingReasons.get(0)))
                        ? "DERIVED_BUSINESS_FACT_READY"
                        : "DERIVED_BUSINESS_FACT_INCOMPLETE");
        List<String> readyReasons = safeList(plan.readyReasons()).isEmpty()
                && isDerivedBusinessFactReady(readiness)
                ? derivedBusinessFactReadyReasons(plan)
                : plan.readyReasons();
        List<String> normalizedMissingReasons = missingReasons.isEmpty()
                ? List.of("NO_MISSING_DERIVED_BUSINESS_FACT_REASON")
                : missingReasons;
        List<String> actions = safeList(plan.actions()).isEmpty()
                ? (missingReasons.isEmpty() ? List.of("NO_ACTION_REQUIRED")
                : derivedBusinessFactActions(missingReasons))
                : plan.actions();
        String businessFactContract = firstNonBlank(plan.businessFactContract(),
                derivedBusinessFactContract(plan, readiness));
        return new DerivedBusinessFactPlan(plan.serviceName(),
                plan.targetPath(),
                plan.targetOwnerType(),
                plan.targetOwnerCategory(),
                safeList(plan.sourcePaths()),
                safeList(plan.sourceOwnerTypes()),
                safeList(plan.businessFactKinds()),
                plan.expression(),
                plan.transactionMode(),
                plan.transactionResourceId(),
                readiness,
                businessFactContract,
                readyReasons,
                normalizedMissingReasons,
                actions);
    }

    private static boolean isDerivedBusinessFactReady(String readiness) {
        return "DERIVED_CALCULATION_READY".equals(readiness)
                || "DERIVED_BUSINESS_FACT_READY".equals(readiness);
    }

    private static List<String> derivedBusinessFactReadyReasons(DerivedBusinessFactPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        if (!isBlank(plan.serviceName())) {
            reasons.add("service");
        }
        if (!isBlank(plan.targetPath())) {
            reasons.add("targetPath");
        }
        if (!isBlank(plan.targetOwnerType())) {
            reasons.add("targetOwnerType");
        }
        if (!isBlank(plan.targetOwnerCategory())) {
            reasons.add("targetOwnerCategory");
        }
        if (!safeList(plan.sourcePaths()).isEmpty()) {
            reasons.add("sourcePaths");
        }
        if (!safeList(plan.sourceOwnerTypes()).isEmpty()) {
            reasons.add("sourceOwnerTypes");
        }
        if (!safeList(plan.businessFactKinds()).isEmpty()) {
            reasons.add("businessFactKinds");
        }
        if (!isBlank(plan.expression())) {
            reasons.add("expression");
        }
        if (!isBlank(plan.transactionResourceId())) {
            reasons.add("transactionResource");
        }
        if (!isBlank(plan.businessFactContract())) {
            reasons.add("businessFactContract");
        }
        return List.copyOf(reasons);
    }

    private static List<String> derivedBusinessFactMissingReasons(DerivedBusinessFactPlan plan) {
        if (plan == null) {
            return List.of("derivedBusinessFactPlan");
        }
        List<String> reasons = new ArrayList<>();
        if (isBlank(plan.serviceName())) {
            reasons.add("service");
        }
        if (isBlank(plan.targetPath())) {
            reasons.add("targetPath");
        }
        if (isBlank(plan.targetOwnerType())) {
            reasons.add("targetOwnerType");
        }
        if (isBlank(plan.targetOwnerCategory()) || "unknown".equals(plan.targetOwnerCategory())) {
            reasons.add("targetOwnerCategory");
        }
        if (safeList(plan.businessFactKinds()).isEmpty()) {
            reasons.add("businessFactKinds");
        }
        if (isBlank(plan.expression())) {
            reasons.add("expression");
        }
        return List.copyOf(reasons);
    }

    private static List<String> derivedBusinessFactActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "derivedBusinessFactPlan" -> actions.add("registerDerivedBusinessFactPlan");
                case "service" -> actions.add("declareDerivedBusinessFactService");
                case "targetPath" -> actions.add("declareDerivedBusinessFactTarget");
                case "targetOwnerType", "targetOwnerCategory" -> actions.add("bindDerivedBusinessFactOwner");
                case "businessFactKinds" -> actions.add("classifyDerivedBusinessFactKind");
                case "expression" -> actions.add("declareDerivedBusinessFactExpression");
                default -> actions.add("reviewDerivedBusinessFactPlan");
            }
        }
        return List.copyOf(actions);
    }

    private static List<String> ownerTypes(List<String> paths) {
        List<String> ownerTypes = new ArrayList<>();
        for (String path : safeList(paths)) {
            String ownerType = ownerType(path);
            if (!ownerType.isBlank() && !ownerTypes.contains(ownerType)) {
                ownerTypes.add(ownerType);
            }
        }
        return List.copyOf(ownerTypes);
    }

    private static String ownerType(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int dot = path.indexOf('.');
        return dot <= 0 ? path.trim() : path.substring(0, dot).trim();
    }

    private static String fieldName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int dot = path.lastIndexOf('.');
        return dot < 0 || dot == path.length() - 1 ? path.trim() : path.substring(dot + 1).trim();
    }

    private static List<String> businessFactKinds(String targetPath, List<String> sourcePaths,
                                                 String targetOwnerCategory) {
        List<String> kinds = new ArrayList<>();
        if ("roles".equals(targetOwnerCategory)) {
            kinds.add("ROLE_PARTICIPANT_FACT");
        } else if ("contracts".equals(targetOwnerCategory)) {
            kinds.add("CONTRACT_FACT");
        } else if (!targetOwnerCategory.isBlank()) {
            kinds.add(targetOwnerCategory.toUpperCase(Locale.ROOT) + "_FACT");
        } else {
            kinds.add("UNKNOWN_FACT");
        }
        String fieldName = fieldName(targetPath);
        if ("unitPrice".equals(fieldName)) {
            kinds.add("LOCKED_UNIT_PRICE");
        } else if ("lineAmount".equals(fieldName)) {
            kinds.add("LINE_AMOUNT");
        } else if ("totalQuantity".equals(fieldName)
                || fieldName.endsWith("Amount")
                || fieldName.endsWith("Quantity")) {
            kinds.add("CONTRACT_AMOUNT_FACT");
        }
        if ("requestedQty".equals(fieldName) || containsFieldPath(sourcePaths, "requestedQty")) {
            kinds.add("REQUESTED_QUANTITY");
        }
        if ("orderQty".equals(fieldName) || containsFieldPath(sourcePaths, "orderQty")) {
            kinds.add("ORDERED_QUANTUM_QUANTITY");
        }
        return List.copyOf(kinds);
    }

    private static boolean containsFieldPath(List<String> paths, String fieldName) {
        for (String path : safeList(paths)) {
            if (fieldName.equals(fieldName(path))) {
                return true;
            }
        }
        return false;
    }

    private static String derivedBusinessFactContract(DerivedCalculationPlan calculation,
                                                      String targetOwnerType,
                                                      String targetOwnerCategory,
                                                      List<String> factKinds) {
        return indexKey(calculation.serviceName()) + ":" + indexKey(calculation.targetPath())
                + ":" + indexKey(targetOwnerType)
                + ":" + indexKey(targetOwnerCategory)
                + ":" + (safeList(factKinds).isEmpty() ? "NO_FACT_KIND" : String.join("+", factKinds))
                + ":" + indexKey(calculation.readiness());
    }

    private static String derivedBusinessFactContract(DerivedBusinessFactPlan plan, String readiness) {
        if (plan == null) {
            return "NO_DERIVED_BUSINESS_FACT_CONTRACT";
        }
        return indexKey(plan.serviceName()) + ":" + indexKey(plan.targetPath())
                + ":" + indexKey(plan.targetOwnerType())
                + ":" + indexKey(plan.targetOwnerCategory())
                + ":" + (safeList(plan.businessFactKinds()).isEmpty()
                ? "NO_FACT_KIND"
                : String.join("+", plan.businessFactKinds()))
                + ":" + indexKey(readiness);
    }

    @JsonProperty(value = "transactionReversalRules")
    public List<TransactionReversalRule> transactionReversalRules() {
        return safeList(transactionReversalRules).stream()
                .filter(rule -> rule != null)
                .peek(GeneratedServiceCatalog::enrichTransactionReversalRule)
                .toList();
    }

    @JsonProperty(value = "transactionReversalReadinessIndex", access = JsonProperty.Access.READ_ONLY)
    public TransactionReversalReadinessIndex transactionReversalReadinessIndex() {
        List<TransactionReversalRule> rules = transactionReversalRules();
        if (rules.isEmpty()) {
            return new TransactionReversalReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of());
        }
        Map<String, List<TransactionReversalRule>> byReadiness = new LinkedHashMap<>();
        Map<String, List<TransactionReversalRule>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<TransactionReversalRule>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<TransactionReversalRule>> byAction = new LinkedHashMap<>();
        Map<String, List<TransactionReversalRule>> byKind = new LinkedHashMap<>();
        Map<String, List<TransactionReversalRule>> bySource = new LinkedHashMap<>();
        Map<String, List<TransactionReversalRule>> byTarget = new LinkedHashMap<>();
        Map<String, List<TransactionReversalRule>> byReversalPath = new LinkedHashMap<>();
        Map<String, List<TransactionReversalRule>> byPhase = new LinkedHashMap<>();
        Map<String, List<TransactionReversalRule>> byExecutionContract = new LinkedHashMap<>();
        for (TransactionReversalRule rule : rules) {
            addToIndex(byReadiness, indexKey(rule.readiness), rule);
            for (String reason : transactionReversalRuleReadyReasons(rule)) {
                addToIndex(byReadyReason, indexKey(reason), rule);
            }
            for (String reason : transactionReversalRuleMissingReasons(rule)) {
                addToIndex(byMissingReason, indexKey(reason), rule);
            }
            for (String action : transactionReversalRuleActions(rule)) {
                addToIndex(byAction, indexKey(action), rule);
            }
            addToIndex(byKind, indexKey(rule.ruleKind), rule);
            addToIndex(bySource, indexKey(rule.sourceServiceName), rule);
            addToIndex(byTarget, indexKey(rule.targetServiceName), rule);
            addToIndex(byReversalPath, transactionReversalPath(rule), rule);
            addToIndex(byPhase, indexKey(rule.phaseName), rule);
            addToIndex(byExecutionContract, transactionReversalExecutionContractKey(rule), rule);
        }
        return new TransactionReversalReadinessIndex(rules,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byKind),
                immutableListMap(bySource),
                immutableListMap(byTarget),
                immutableListMap(byReversalPath),
                immutableListMap(byPhase),
                immutableListMap(byExecutionContract));
    }

    @JsonProperty(value = "transactionReversalRulesByKind", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionReversalRule>> transactionReversalRulesByKind() {
        return transactionReversalReadinessIndex().byKind();
    }

    @JsonProperty(value = "transactionReversalRulesBySource", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionReversalRule>> transactionReversalRulesBySource() {
        return transactionReversalReadinessIndex().bySource();
    }

    @JsonProperty(value = "transactionReversalRulesByTarget", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionReversalRule>> transactionReversalRulesByTarget() {
        return transactionReversalReadinessIndex().byTarget();
    }

    @JsonProperty(value = "transactionReversalRulesByPhase", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionReversalRule>> transactionReversalRulesByPhase() {
        return transactionReversalReadinessIndex().byPhase();
    }

    @JsonProperty(value = "transactionReversalRulesByReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionReversalRule>> transactionReversalRulesByReadiness() {
        return transactionReversalReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "transactionReversalRulesByReadyReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionReversalRule>> transactionReversalRulesByReadyReason() {
        return transactionReversalReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "transactionReversalRulesByMissingReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionReversalRule>> transactionReversalRulesByMissingReason() {
        return transactionReversalReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "transactionReversalRulesByAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionReversalRule>> transactionReversalRulesByAction() {
        return transactionReversalReadinessIndex().byAction();
    }

    @JsonProperty(value = "transactionReversalRulesByReversalPath", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionReversalRule>> transactionReversalRulesByReversalPath() {
        return transactionReversalReadinessIndex().byReversalPath();
    }

    @JsonProperty(value = "transactionReversalRulesByExecutionContract", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionReversalRule>> transactionReversalRulesByExecutionContract() {
        return transactionReversalReadinessIndex().byExecutionContract();
    }

    @JsonProperty(value = "tccReservationReadinessIndex", access = JsonProperty.Access.READ_ONLY)
    public TccReservationReadinessIndex tccReservationReadinessIndex() {
        List<TccReservationPlan> plans = safeList(tccReservationPlans).stream()
                .filter(plan -> plan != null)
                .map(GeneratedServiceCatalog::enrichTccReservationPlan)
                .toList();
        if (plans.isEmpty()) {
            return new TccReservationReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of());
        }
        Map<String, List<TccReservationPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<TccReservationPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<TccReservationPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<TccReservationPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<TccReservationPlan>> byService = new LinkedHashMap<>();
        Map<String, List<TccReservationPlan>> byResource = new LinkedHashMap<>();
        Map<String, List<TccReservationPlan>> bySourceQuantumStore = new LinkedHashMap<>();
        Map<String, List<TccReservationPlan>> bySourceImplementationStrategy = new LinkedHashMap<>();
        Map<String, List<TccReservationPlan>> byTargetRepository = new LinkedHashMap<>();
        Map<String, List<TccReservationPlan>> byTargetColumn = new LinkedHashMap<>();
        Map<String, List<TccReservationPlan>> byTargetColumnKind = new LinkedHashMap<>();
        Map<String, List<TccReservationPlan>> byReservationBoundary = new LinkedHashMap<>();
        Map<String, List<TccReservationPlan>> byPhysicalReservationPath = new LinkedHashMap<>();
        Map<String, List<TccReservationPlan>> byReservationContract = new LinkedHashMap<>();
        for (TccReservationPlan plan : plans) {
            String readiness = isBlank(plan.readiness()) ? "TCC_RESERVATION_INCOMPLETE" : plan.readiness();
            List<String> missingReasons = safeList(plan.missingReasons());
            if ("TCC_RESERVATION_READY".equals(readiness)) {
                addToIndex(byReadiness, readiness, plan);
                for (String reason : safeList(plan.readyReasons())) {
                    addToIndex(byReadyReason, indexKey(reason), plan);
                }
                for (String reason : missingReasons) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.actions())) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            } else {
                addToIndex(byReadiness, readiness, plan);
                for (String reason : missingReasons) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.actions())) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            }
            addToIndex(byService, indexKey(plan.serviceName()), plan);
            addToIndex(byResource, indexKey(plan.resourceId()), plan);
            addToIndex(bySourceQuantumStore, indexKey(plan.sourceQuantumStoreName()), plan);
            addToIndex(bySourceImplementationStrategy, tccReservationSourceImplementationStrategy(plan), plan);
            addToIndex(byTargetRepository,
                    indexKey(firstNonBlank(plan.targetResolvedRepositoryName(), plan.targetRepositoryName())), plan);
            addToIndex(byTargetColumn, indexKey(plan.targetColumnName()), plan);
            addToIndex(byTargetColumnKind, tccReservationTargetColumnKind(plan), plan);
            addToIndex(byReservationBoundary, indexKey(plan.reservationBoundary()), plan);
            addToIndex(byPhysicalReservationPath, indexKey(plan.physicalReservationPath()), plan);
            addToIndex(byReservationContract, indexKey(plan.reservationContract()), plan);
        }
        return new TccReservationReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byService),
                immutableListMap(byResource),
                immutableListMap(bySourceQuantumStore),
                immutableListMap(bySourceImplementationStrategy),
                immutableListMap(byTargetRepository),
                immutableListMap(byTargetColumn),
                immutableListMap(byTargetColumnKind),
                immutableListMap(byReservationBoundary),
                immutableListMap(byPhysicalReservationPath),
                immutableListMap(byReservationContract));
    }

    @JsonProperty(value = "tccReservationPlansByReadyReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TccReservationPlan>> tccReservationPlansByReadyReason() {
        return tccReservationReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "tccReservationPlansByMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TccReservationPlan>> tccReservationPlansByMissingReason() {
        return tccReservationReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "tccReservationPlansByAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TccReservationPlan>> tccReservationPlansByAction() {
        return tccReservationReadinessIndex().byAction();
    }

    @JsonProperty(value = "tccReservationPlansByResource",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TccReservationPlan>> tccReservationPlansByResource() {
        return tccReservationReadinessIndex().byResource();
    }

    @JsonProperty(value = "tccReservationPlansBySourceImplementationStrategy",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TccReservationPlan>> tccReservationPlansBySourceImplementationStrategy() {
        return tccReservationReadinessIndex().bySourceImplementationStrategy();
    }

    @JsonProperty(value = "tccReservationPlansByReservationBoundary",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TccReservationPlan>> tccReservationPlansByReservationBoundary() {
        return tccReservationReadinessIndex().byReservationBoundary();
    }

    @JsonProperty(value = "tccReservationPlansByPhysicalReservationPath",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TccReservationPlan>> tccReservationPlansByPhysicalReservationPath() {
        return tccReservationReadinessIndex().byPhysicalReservationPath();
    }

    @JsonProperty(value = "tccReservationPlansByReservationContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TccReservationPlan>> tccReservationPlansByReservationContract() {
        return tccReservationReadinessIndex().byReservationContract();
    }

    @JsonProperty(value = "objectSnapshotClosureIndex", access = JsonProperty.Access.READ_ONLY)
    public ObjectSnapshotClosureIndex objectSnapshotClosureIndex() {
        List<ObjectSnapshotPlan> plans = safeList(objectSnapshotPlans).stream()
                .filter(plan -> plan != null)
                .filter(plan -> !plan.transactionEvidence())
                .map(GeneratedServiceCatalog::enrichObjectSnapshotPlan)
                .toList();
        if (plans.isEmpty()) {
            return new ObjectSnapshotClosureIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of());
        }
        Map<String, List<ObjectSnapshotPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byService = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byObjectType = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> bySnapshotPath = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byBoundary = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byRecoveryRole = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byRepository = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byObjectKeySource = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byObjectKeyColumnSet = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byClosureContract = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byStorageTable = new LinkedHashMap<>();
        for (ObjectSnapshotPlan plan : plans) {
            if ("SNAPSHOT_CLOSED".equals(plan.closureReadiness())) {
                addToIndex(byReadiness, plan.closureReadiness(), plan);
                for (String reason : safeList(plan.closureReadyReasons())) {
                    addToIndex(byReadyReason, indexKey(reason), plan);
                }
                for (String reason : safeList(plan.closureMissingReasons())) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.closureActions())) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            } else {
                addToIndex(byReadiness, indexKey(plan.closureReadiness()), plan);
                for (String reason : safeList(plan.closureMissingReasons())) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.closureActions())) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            }
            addToIndex(byService, indexKey(plan.serviceName()), plan);
            addToIndex(byObjectType, indexKey(plan.objectType()), plan);
            addToIndex(bySnapshotPath, indexKey(plan.snapshotPath()), plan);
            addToIndex(byBoundary, indexKey(plan.snapshotBoundary()), plan);
            addToIndex(byRecoveryRole, indexKey(plan.recoveryRole()), plan);
            addToIndex(byRepository,
                    indexKey(firstNonBlank(plan.resolvedRepositoryName(), plan.repositoryName())), plan);
            addToIndex(byObjectKeySource, indexKey(plan.objectKeySource()), plan);
            addToIndex(byObjectKeyColumnSet, objectKeyColumnSetKey(plan), plan);
            addToIndex(byClosureContract, indexKey(plan.closureContract()), plan);
            addToIndex(byStorageTable, indexKey(plan.snapshotStorageTableName()), plan);
        }
        return new ObjectSnapshotClosureIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byService),
                immutableListMap(byObjectType),
                immutableListMap(bySnapshotPath),
                immutableListMap(byBoundary),
                immutableListMap(byRecoveryRole),
                immutableListMap(byRepository),
                immutableListMap(byObjectKeySource),
                immutableListMap(byObjectKeyColumnSet),
                immutableListMap(byClosureContract),
                immutableListMap(byStorageTable));
    }

    @JsonProperty(value = "objectSnapshotClosurePlansByReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> objectSnapshotClosurePlansByReadiness() {
        return objectSnapshotClosureIndex().byReadiness();
    }

    @JsonProperty(value = "objectSnapshotClosurePlansByReadyReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> objectSnapshotClosurePlansByReadyReason() {
        return objectSnapshotClosureIndex().byReadyReason();
    }

    @JsonProperty(value = "objectSnapshotClosurePlansByMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> objectSnapshotClosurePlansByMissingReason() {
        return objectSnapshotClosureIndex().byMissingReason();
    }

    @JsonProperty(value = "objectSnapshotClosurePlansByAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> objectSnapshotClosurePlansByAction() {
        return objectSnapshotClosureIndex().byAction();
    }

    @JsonProperty(value = "objectSnapshotClosurePlansByService",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> objectSnapshotClosurePlansByService() {
        return objectSnapshotClosureIndex().byService();
    }

    @JsonProperty(value = "objectSnapshotClosurePlansByObjectType",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> objectSnapshotClosurePlansByObjectType() {
        return objectSnapshotClosureIndex().byObjectType();
    }

    @JsonProperty(value = "objectSnapshotClosurePlansBySnapshotPath",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> objectSnapshotClosurePlansBySnapshotPath() {
        return objectSnapshotClosureIndex().bySnapshotPath();
    }

    @JsonProperty(value = "objectSnapshotClosurePlansByBoundary",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> objectSnapshotClosurePlansByBoundary() {
        return objectSnapshotClosureIndex().byBoundary();
    }

    @JsonProperty(value = "objectSnapshotClosurePlansByRecoveryRole",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> objectSnapshotClosurePlansByRecoveryRole() {
        return objectSnapshotClosureIndex().byRecoveryRole();
    }

    @JsonProperty(value = "objectSnapshotClosurePlansByRepository",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> objectSnapshotClosurePlansByRepository() {
        return objectSnapshotClosureIndex().byRepository();
    }

    @JsonProperty(value = "objectSnapshotClosurePlansByObjectKeySource",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> objectSnapshotClosurePlansByObjectKeySource() {
        return objectSnapshotClosureIndex().byObjectKeySource();
    }

    @JsonProperty(value = "objectSnapshotClosurePlansByObjectKeyColumnSet",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> objectSnapshotClosurePlansByObjectKeyColumnSet() {
        return objectSnapshotClosureIndex().byObjectKeyColumnSet();
    }

    @JsonProperty(value = "objectSnapshotClosurePlansByClosureContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> objectSnapshotClosurePlansByClosureContract() {
        return objectSnapshotClosureIndex().byClosureContract();
    }

    @JsonProperty(value = "objectSnapshotClosurePlansByStorageTable",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> objectSnapshotClosurePlansByStorageTable() {
        return objectSnapshotClosureIndex().byStorageTable();
    }

    @JsonProperty(value = "businessObjectSnapshotClosureIndex", access = JsonProperty.Access.READ_ONLY)
    public ObjectSnapshotClosureIndex businessObjectSnapshotClosureIndex() {
        List<ObjectSnapshotPlan> plans = safeList(objectSnapshotPlans).stream()
                .filter(plan -> plan != null)
                .filter(plan -> !plan.transactionEvidence())
                .map(GeneratedServiceCatalog::enrichObjectSnapshotPlan)
                .toList();
        if (plans.isEmpty()) {
            return new ObjectSnapshotClosureIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of());
        }
        Map<String, List<ObjectSnapshotPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byService = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byObjectType = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> bySnapshotPath = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byBoundary = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byRecoveryRole = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byRepository = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byObjectKeySource = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byObjectKeyColumnSet = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byClosureContract = new LinkedHashMap<>();
        Map<String, List<ObjectSnapshotPlan>> byStorageTable = new LinkedHashMap<>();
        for (ObjectSnapshotPlan plan : plans) {
            if ("BUSINESS_OBJECT_SNAPSHOT_CLOSED".equals(plan.businessObjectClosureReadiness())) {
                addToIndex(byReadiness, plan.businessObjectClosureReadiness(), plan);
                for (String reason : safeList(plan.businessObjectClosureReadyReasons())) {
                    addToIndex(byReadyReason, indexKey(reason), plan);
                }
                for (String reason : safeList(plan.businessObjectClosureMissingReasons())) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.businessObjectClosureActions())) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            } else {
                addToIndex(byReadiness, indexKey(plan.businessObjectClosureReadiness()), plan);
                for (String reason : safeList(plan.businessObjectClosureMissingReasons())) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.businessObjectClosureActions())) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            }
            addToIndex(byService, indexKey(plan.serviceName()), plan);
            addToIndex(byObjectType, indexKey(plan.objectType()), plan);
            addToIndex(bySnapshotPath, indexKey(plan.snapshotPath()), plan);
            addToIndex(byBoundary, indexKey(plan.snapshotBoundary()), plan);
            addToIndex(byRecoveryRole, indexKey(plan.recoveryRole()), plan);
            addToIndex(byRepository,
                    indexKey(firstNonBlank(plan.resolvedRepositoryName(), plan.repositoryName())), plan);
            addToIndex(byObjectKeySource, indexKey(plan.objectKeySource()), plan);
            addToIndex(byObjectKeyColumnSet, objectKeyColumnSetKey(plan), plan);
            addToIndex(byClosureContract, indexKey(plan.businessObjectClosureContract()), plan);
            addToIndex(byStorageTable, indexKey(plan.snapshotStorageTableName()), plan);
        }
        return new ObjectSnapshotClosureIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byService),
                immutableListMap(byObjectType),
                immutableListMap(bySnapshotPath),
                immutableListMap(byBoundary),
                immutableListMap(byRecoveryRole),
                immutableListMap(byRepository),
                immutableListMap(byObjectKeySource),
                immutableListMap(byObjectKeyColumnSet),
                immutableListMap(byClosureContract),
                immutableListMap(byStorageTable));
    }

    @JsonProperty(value = "businessObjectSnapshotClosurePlansByReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> businessObjectSnapshotClosurePlansByReadiness() {
        return businessObjectSnapshotClosureIndex().byReadiness();
    }

    @JsonProperty(value = "businessObjectSnapshotClosurePlansByReadyReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> businessObjectSnapshotClosurePlansByReadyReason() {
        return businessObjectSnapshotClosureIndex().byReadyReason();
    }

    @JsonProperty(value = "businessObjectSnapshotClosurePlansByMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> businessObjectSnapshotClosurePlansByMissingReason() {
        return businessObjectSnapshotClosureIndex().byMissingReason();
    }

    @JsonProperty(value = "businessObjectSnapshotClosurePlansByAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> businessObjectSnapshotClosurePlansByAction() {
        return businessObjectSnapshotClosureIndex().byAction();
    }

    @JsonProperty(value = "businessObjectSnapshotClosurePlansByService",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> businessObjectSnapshotClosurePlansByService() {
        return businessObjectSnapshotClosureIndex().byService();
    }

    @JsonProperty(value = "businessObjectSnapshotClosurePlansByObjectType",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> businessObjectSnapshotClosurePlansByObjectType() {
        return businessObjectSnapshotClosureIndex().byObjectType();
    }

    @JsonProperty(value = "businessObjectSnapshotClosurePlansBySnapshotPath",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> businessObjectSnapshotClosurePlansBySnapshotPath() {
        return businessObjectSnapshotClosureIndex().bySnapshotPath();
    }

    @JsonProperty(value = "businessObjectSnapshotClosurePlansByBoundary",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> businessObjectSnapshotClosurePlansByBoundary() {
        return businessObjectSnapshotClosureIndex().byBoundary();
    }

    @JsonProperty(value = "businessObjectSnapshotClosurePlansByRecoveryRole",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> businessObjectSnapshotClosurePlansByRecoveryRole() {
        return businessObjectSnapshotClosureIndex().byRecoveryRole();
    }

    @JsonProperty(value = "businessObjectSnapshotClosurePlansByRepository",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> businessObjectSnapshotClosurePlansByRepository() {
        return businessObjectSnapshotClosureIndex().byRepository();
    }

    @JsonProperty(value = "businessObjectSnapshotClosurePlansByObjectKeySource",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> businessObjectSnapshotClosurePlansByObjectKeySource() {
        return businessObjectSnapshotClosureIndex().byObjectKeySource();
    }

    @JsonProperty(value = "businessObjectSnapshotClosurePlansByObjectKeyColumnSet",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> businessObjectSnapshotClosurePlansByObjectKeyColumnSet() {
        return businessObjectSnapshotClosureIndex().byObjectKeyColumnSet();
    }

    @JsonProperty(value = "businessObjectSnapshotClosurePlansByClosureContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> businessObjectSnapshotClosurePlansByClosureContract() {
        return businessObjectSnapshotClosureIndex().byClosureContract();
    }

    @JsonProperty(value = "businessObjectSnapshotClosurePlansByStorageTable",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectSnapshotPlan>> businessObjectSnapshotClosurePlansByStorageTable() {
        return businessObjectSnapshotClosureIndex().byStorageTable();
    }

    @JsonProperty(value = "currentSnapshotContextReadinessIndex", access = JsonProperty.Access.READ_ONLY)
    public CurrentSnapshotContextReadinessIndex currentSnapshotContextReadinessIndex() {
        List<CurrentSnapshotContextPlan> plans = safeList(currentSnapshotContextPlans).stream()
                .filter(plan -> plan != null)
                .map(GeneratedServiceCatalog::enrichCurrentSnapshotContextPlan)
                .toList();
        if (plans.isEmpty()) {
            return new CurrentSnapshotContextReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of());
        }
        Map<String, List<CurrentSnapshotContextPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<CurrentSnapshotContextPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<CurrentSnapshotContextPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<CurrentSnapshotContextPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<CurrentSnapshotContextPlan>> bySaga = new LinkedHashMap<>();
        Map<String, List<CurrentSnapshotContextPlan>> byTargetService = new LinkedHashMap<>();
        Map<String, List<CurrentSnapshotContextPlan>> byObjectType = new LinkedHashMap<>();
        Map<String, List<CurrentSnapshotContextPlan>> byContextPath = new LinkedHashMap<>();
        Map<String, List<CurrentSnapshotContextPlan>> byContextObjectSource = new LinkedHashMap<>();
        Map<String, List<CurrentSnapshotContextPlan>> byResolution = new LinkedHashMap<>();
        Map<String, List<CurrentSnapshotContextPlan>> byKeyPushdown = new LinkedHashMap<>();
        Map<String, List<CurrentSnapshotContextPlan>> byObjectKeySource = new LinkedHashMap<>();
        Map<String, List<CurrentSnapshotContextPlan>> byObjectKeyColumnSet = new LinkedHashMap<>();
        Map<String, List<CurrentSnapshotContextPlan>> byClosureContract = new LinkedHashMap<>();
        Map<String, List<CurrentSnapshotContextPlan>> byAppDdlIndex = new LinkedHashMap<>();
        for (CurrentSnapshotContextPlan plan : plans) {
            if ("CURRENT_CONTEXT_CLOSED".equals(plan.readiness())) {
                addToIndex(byReadiness, plan.readiness(), plan);
                for (String reason : safeList(plan.readyReasons())) {
                    addToIndex(byReadyReason, indexKey(reason), plan);
                }
                for (String reason : safeList(plan.missingReasons())) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.actions())) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            } else {
                addToIndex(byReadiness, indexKey(plan.readiness()), plan);
                for (String reason : safeList(plan.missingReasons())) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.actions())) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            }
            addToIndex(bySaga, indexKey(plan.sagaServiceName()), plan);
            addToIndex(byTargetService, indexKey(plan.targetServiceName()), plan);
            addToIndex(byObjectType, indexKey(plan.objectType()), plan);
            addToIndex(byContextPath, currentSnapshotContextPath(plan), plan);
            addToIndex(byContextObjectSource, indexKey(plan.contextObjectSource()), plan);
            if ("LOCAL_SAGA_PARAMETER".equals(plan.contextObjectSource())) {
                addToIndex(byContextObjectSource, "SAGA_PARAMETER", plan);
            }
            addToIndex(byResolution, plan.resolvedFromSagaParameter()
                    ? "RESOLVED_FROM_SAGA_PARAMETER"
                    : "MISSING_SAGA_PARAMETER_RESOLUTION", plan);
            addToIndex(byResolution, plan.ambiguousSagaParameter()
                    ? "AMBIGUOUS_SAGA_PARAMETER"
                    : "SAGA_PARAMETER_UNAMBIGUOUS", plan);
            addToIndex(byKeyPushdown, plan.appDdlObjectKeyPushdown()
                    ? "APP_DDL_KEY_PUSHDOWN"
                    : "MISSING_APP_DDL_KEY_PUSHDOWN", plan);
            addToIndex(byKeyPushdown, plan.objectKeyAppDdlDerived()
                    ? "APP_DDL_KEY_DERIVED"
                    : "MISSING_APP_DDL_KEY_DERIVATION", plan);
            addToIndex(byObjectKeySource, indexKey(plan.objectKeySource()), plan);
            addToIndex(byObjectKeyColumnSet, currentContextObjectKeyColumnSetKey(plan), plan);
            addToIndex(byClosureContract, indexKey(plan.closureContract()), plan);
            addToIndex(byAppDdlIndex, indexKey(firstNonBlank(
                    plan.objectKeyAppDdlIndexName(), "NO_APP_DDL_INDEX")), plan);
        }
        return new CurrentSnapshotContextReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(bySaga),
                immutableListMap(byTargetService),
                immutableListMap(byObjectType),
                immutableListMap(byContextPath),
                immutableListMap(byContextObjectSource),
                immutableListMap(byResolution),
                immutableListMap(byKeyPushdown),
                immutableListMap(byObjectKeySource),
                immutableListMap(byObjectKeyColumnSet),
                immutableListMap(byClosureContract),
                immutableListMap(byAppDdlIndex));
    }

    @JsonProperty(value = "currentSnapshotContextPlansByReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<CurrentSnapshotContextPlan>> currentSnapshotContextPlansByReadiness() {
        return currentSnapshotContextReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "currentSnapshotContextPlansByReadyReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<CurrentSnapshotContextPlan>> currentSnapshotContextPlansByReadyReason() {
        return currentSnapshotContextReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "currentSnapshotContextPlansByMissingReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<CurrentSnapshotContextPlan>> currentSnapshotContextPlansByMissingReason() {
        return currentSnapshotContextReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "currentSnapshotContextPlansByAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<CurrentSnapshotContextPlan>> currentSnapshotContextPlansByAction() {
        return currentSnapshotContextReadinessIndex().byAction();
    }

    @JsonProperty(value = "currentSnapshotContextPlansBySaga", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<CurrentSnapshotContextPlan>> currentSnapshotContextPlansBySaga() {
        return currentSnapshotContextReadinessIndex().bySaga();
    }

    @JsonProperty(value = "currentSnapshotContextPlansByTargetService",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<CurrentSnapshotContextPlan>> currentSnapshotContextPlansByTargetService() {
        return currentSnapshotContextReadinessIndex().byTargetService();
    }

    @JsonProperty(value = "currentSnapshotContextPlansByObjectType",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<CurrentSnapshotContextPlan>> currentSnapshotContextPlansByObjectType() {
        return currentSnapshotContextReadinessIndex().byObjectType();
    }

    @JsonProperty(value = "currentSnapshotContextPlansByContextPath", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<CurrentSnapshotContextPlan>> currentSnapshotContextPlansByContextPath() {
        return currentSnapshotContextReadinessIndex().byContextPath();
    }

    @JsonProperty(value = "currentSnapshotContextPlansByContextObjectSource",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<CurrentSnapshotContextPlan>> currentSnapshotContextPlansByContextObjectSource() {
        return currentSnapshotContextReadinessIndex().byContextObjectSource();
    }

    @JsonProperty(value = "currentSnapshotContextPlansByResolution",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<CurrentSnapshotContextPlan>> currentSnapshotContextPlansByResolution() {
        return currentSnapshotContextReadinessIndex().byResolution();
    }

    @JsonProperty(value = "currentSnapshotContextPlansByKeyPushdown",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<CurrentSnapshotContextPlan>> currentSnapshotContextPlansByKeyPushdown() {
        return currentSnapshotContextReadinessIndex().byKeyPushdown();
    }

    @JsonProperty(value = "currentSnapshotContextPlansByObjectKeySource",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<CurrentSnapshotContextPlan>> currentSnapshotContextPlansByObjectKeySource() {
        return currentSnapshotContextReadinessIndex().byObjectKeySource();
    }

    @JsonProperty(value = "currentSnapshotContextPlansByObjectKeyColumnSet",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<CurrentSnapshotContextPlan>> currentSnapshotContextPlansByObjectKeyColumnSet() {
        return currentSnapshotContextReadinessIndex().byObjectKeyColumnSet();
    }

    @JsonProperty(value = "currentSnapshotContextPlansByClosureContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<CurrentSnapshotContextPlan>> currentSnapshotContextPlansByClosureContract() {
        return currentSnapshotContextReadinessIndex().byClosureContract();
    }

    @JsonProperty(value = "currentSnapshotContextPlansByAppDdlIndex",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<CurrentSnapshotContextPlan>> currentSnapshotContextPlansByAppDdlIndex() {
        return currentSnapshotContextReadinessIndex().byAppDdlIndex();
    }

    @JsonProperty(value = "currentSnapshotContextPlansByClosureReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<CurrentSnapshotContextPlan>> currentSnapshotContextPlansByClosureReadiness() {
        return currentSnapshotContextReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "roleParticipantReadinessIndex", access = JsonProperty.Access.READ_ONLY)
    public RoleParticipantReadinessIndex roleParticipantReadinessIndex() {
        List<RoleParticipantPlan> plans = safeList(roleParticipantPlans).stream()
                .filter(plan -> plan != null)
                .map(GeneratedServiceCatalog::enrichRoleParticipantPlan)
                .toList();
        if (plans.isEmpty()) {
            return new RoleParticipantReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<RoleParticipantPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<RoleParticipantPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<RoleParticipantPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<RoleParticipantPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<RoleParticipantPlan>> byRole = new LinkedHashMap<>();
        Map<String, List<RoleParticipantPlan>> byParticipationKind = new LinkedHashMap<>();
        Map<String, List<RoleParticipantPlan>> byJointPerformer = new LinkedHashMap<>();
        Map<String, List<RoleParticipantPlan>> byPerformerCount = new LinkedHashMap<>();
        Map<String, List<RoleParticipantPlan>> byPerformerType = new LinkedHashMap<>();
        Map<String, List<RoleParticipantPlan>> byPerformerTypeSet = new LinkedHashMap<>();
        Map<String, List<RoleParticipantPlan>> byPlatformColumn = new LinkedHashMap<>();
        Map<String, List<RoleParticipantPlan>> byPlatformColumnSet = new LinkedHashMap<>();
        Map<String, List<RoleParticipantPlan>> byCompatRoleKey = new LinkedHashMap<>();
        for (RoleParticipantPlan plan : plans) {
            String readiness = isBlank(plan.readiness()) ? "ROLE_PARTICIPANT_INCOMPLETE" : plan.readiness();
            List<String> missingReasons = safeList(plan.missingReasons());
            if ("ROLE_PARTICIPANT_READY".equals(readiness)) {
                addToIndex(byReadiness, readiness, plan);
                for (String reason : safeList(plan.readyReasons())) {
                    addToIndex(byReadyReason, indexKey(reason), plan);
                }
                for (String reason : missingReasons) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.actions())) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            } else {
                addToIndex(byReadiness, readiness, plan);
                for (String reason : missingReasons) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.actions())) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            }
            addToIndex(byRole, indexKey(plan.roleType()), plan);
            addToIndex(byParticipationKind, indexKey(plan.participationKind()), plan);
            addToIndex(byJointPerformer, plan.jointPerformer() ? "JOINT_PERFORMER" : "SINGLE_PERFORMER", plan);
            addToIndex(byPerformerCount, String.valueOf(plan.performerCount()), plan);
            addListIndexValues(byPerformerType, plan.performerTypes(), plan, "NO_PERFORMER_TYPE");
            addToIndex(byPerformerTypeSet, indexKey(plan.performerTypeSet()), plan);
            addListIndexValues(byPlatformColumn, plan.platformColumns(), plan, "NO_PLATFORM_COLUMN");
            addToIndex(byPlatformColumnSet, indexKey(plan.platformColumnSet()), plan);
            addListIndexValues(byCompatRoleKey, plan.compatRoleKeyPaths(), plan, "NO_COMPAT_ROLE_KEY");
        }
        return new RoleParticipantReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byRole),
                immutableListMap(byParticipationKind),
                immutableListMap(byJointPerformer),
                immutableListMap(byPerformerCount),
                immutableListMap(byPerformerType),
                immutableListMap(byPerformerTypeSet),
                immutableListMap(byPlatformColumn),
                immutableListMap(byPlatformColumnSet),
                immutableListMap(byCompatRoleKey));
    }

    @JsonProperty(value = "roleParticipantPlansByPerformerTypeSet", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleParticipantPlan>> roleParticipantPlansByPerformerTypeSet() {
        return roleParticipantReadinessIndex().byPerformerTypeSet();
    }

    @JsonProperty(value = "roleParticipantPlansByPlatformColumnSet", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleParticipantPlan>> roleParticipantPlansByPlatformColumnSet() {
        return roleParticipantReadinessIndex().byPlatformColumnSet();
    }

    @JsonProperty(value = "roleParticipantPlansByJointPerformer", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleParticipantPlan>> roleParticipantPlansByJointPerformer() {
        return roleParticipantReadinessIndex().byJointPerformer();
    }

    @JsonProperty(value = "roleParticipantPlansByPerformerCount", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleParticipantPlan>> roleParticipantPlansByPerformerCount() {
        return roleParticipantReadinessIndex().byPerformerCount();
    }

    @JsonProperty(value = "roleParticipantPlansByCompatRoleKey", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleParticipantPlan>> roleParticipantPlansByCompatRoleKey() {
        return roleParticipantReadinessIndex().byCompatRoleKey();
    }

    @JsonProperty(value = "roleParticipantPlansByReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleParticipantPlan>> roleParticipantPlansByReadiness() {
        return roleParticipantReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "roleParticipantPlansByReadyReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleParticipantPlan>> roleParticipantPlansByReadyReason() {
        return roleParticipantReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "roleParticipantPlansByMissingReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleParticipantPlan>> roleParticipantPlansByMissingReason() {
        return roleParticipantReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "roleParticipantPlansByAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleParticipantPlan>> roleParticipantPlansByAction() {
        return roleParticipantReadinessIndex().byAction();
    }

    @JsonProperty(value = "appDdlCompositionIndex", access = JsonProperty.Access.READ_ONLY)
    public AppDdlCompositionIndex appDdlCompositionIndex() {
        List<AppDdlCompositionIndexPlan> plans = appDdlCompositionIndexPlans();
        if (plans.isEmpty()) {
            return new AppDdlCompositionIndex(List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<AppDdlCompositionIndexPlan>> byTableName = new LinkedHashMap<>();
        Map<String, List<AppDdlCompositionIndexPlan>> byRoleType = new LinkedHashMap<>();
        Map<String, List<AppDdlCompositionIndexPlan>> byNamedIndex = new LinkedHashMap<>();
        Map<String, List<AppDdlCompositionIndexPlan>> byCompositionColumn = new LinkedHashMap<>();
        Map<String, List<AppDdlCompositionIndexPlan>> byRolePerformerColumn = new LinkedHashMap<>();
        Map<String, List<AppDdlCompositionIndexPlan>> byPerformerType = new LinkedHashMap<>();
        Map<String, List<AppDdlCompositionIndexPlan>> bySignerContextContract = new LinkedHashMap<>();
        Map<String, List<AppDdlCompositionIndexPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<AppDdlCompositionIndexPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<AppDdlCompositionIndexPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<AppDdlCompositionIndexPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<AppDdlCompositionIndexPlan>> byCompositionContract = new LinkedHashMap<>();
        for (AppDdlCompositionIndexPlan plan : plans) {
            addToIndex(byTableName, indexKey(plan.tableName()), plan);
            addToIndex(byRoleType, indexKey(plan.roleType()), plan);
            addToIndex(byNamedIndex, indexKey(plan.namedIndexName()), plan);
            addListIndexValues(byCompositionColumn, plan.compositionColumns(), plan, "NO_COMPOSITION_COLUMN");
            addListIndexValues(byRolePerformerColumn, plan.rolePerformerColumns(), plan,
                    "NO_ROLE_PERFORMER_COLUMN");
            addListIndexValues(byPerformerType, plan.performerTypes(), plan, "NO_PERFORMER_TYPE");
            addListIndexValues(bySignerContextContract, plan.signerContextContracts(), plan,
                    "NO_SIGNER_CONTEXT_CONTRACT");
            addToIndex(byReadiness, indexKey(plan.readiness()), plan);
            addListIndexValues(byReadyReason, plan.readyReasons(), plan, "NO_READY_REASON");
            addListIndexValues(byMissingReason, plan.missingReasons(), plan, "NO_MISSING_REASON");
            addListIndexValues(byAction, plan.actions(), plan, "NO_ACTION_REQUIRED");
            addToIndex(byCompositionContract, indexKey(plan.compositionContract()), plan);
        }
        return new AppDdlCompositionIndex(plans,
                immutableListMap(byTableName),
                immutableListMap(byRoleType),
                immutableListMap(byNamedIndex),
                immutableListMap(byCompositionColumn),
                immutableListMap(byRolePerformerColumn),
                immutableListMap(byPerformerType),
                immutableListMap(bySignerContextContract),
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byCompositionContract));
    }

    @JsonProperty(value = "appDdlCompositionIndexPlans", access = JsonProperty.Access.READ_ONLY)
    public List<AppDdlCompositionIndexPlan> appDdlCompositionIndexPlans() {
        return appDdlCompositionIndexPlans(appDdl, roleParticipantPlans, scenarioContractContextPlans());
    }

    static List<AppDdlCompositionIndexPlan> appDdlCompositionIndexPlans(
            AppDdlCatalog appDdl,
            List<RoleParticipantPlan> roleParticipantPlans,
            List<ScenarioContractContextPlan> scenarioContractContextPlans) {
        if (appDdl == null || appDdl.tables().isEmpty()) {
            return List.of();
        }
        List<AppDdlCompositionIndexPlan> plans = new ArrayList<>();
        List<RoleParticipantPlan> enrichedRoleParticipants = safeList(roleParticipantPlans).stream()
                .filter(plan -> plan != null)
                .map(GeneratedServiceCatalog::enrichRoleParticipantPlan)
                .toList();
        for (AppDdlCatalog.Table table : appDdl.tables()) {
            if (!"roles".equals(table.category()) || table.platformPrimaryKeys().isEmpty()
                    || table.indexPathsByName().isEmpty()) {
                continue;
            }
            RoleParticipantPlan roleParticipant = roleParticipantForTable(table, enrichedRoleParticipants);
            List<String> rolePerformerColumns = roleParticipant == null
                    ? List.of()
                    : roleParticipant.platformColumns();
            List<String> compositionColumns = table.platformPrimaryKeys().stream()
                    .filter(column -> !rolePerformerColumns.contains(column))
                    .toList();
            if (compositionColumns.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, List<String>> entry : table.indexPathsByName().entrySet()) {
                List<String> indexColumns = normalizeCompositionIndexColumns(table, entry.getValue());
                if (indexColumns.isEmpty() || !compositionColumns.containsAll(indexColumns)) {
                    continue;
                }
                List<String> signerContextContracts =
                        signerContextContracts(table.businessType(), scenarioContractContextPlans);
                List<String> missingReasons = appDdlCompositionIndexMissingReasons(roleParticipant,
                        signerContextContracts);
                List<String> readyReasons = missingReasons.isEmpty()
                        ? List.of("compositionColumn", "rolePerformerIdentity", "roleSignedContractContext")
                        : List.of();
                List<String> actions = missingReasons.isEmpty()
                        ? List.of("NO_ACTION_REQUIRED")
                        : appDdlCompositionIndexActions(missingReasons);
                plans.add(new AppDdlCompositionIndexPlan(
                        table.name(),
                        table.businessType(),
                        entry.getKey(),
                        indexColumns,
                        table.platformPrimaryKeys(),
                        compositionColumns,
                        rolePerformerColumns,
                        roleParticipant == null ? List.of() : roleParticipant.performerTypes(),
                        signerContextContracts,
                        missingReasons.isEmpty()
                                ? "COMPOSITION_INDEX_READY_FOR_ROLE_SIGNED_CONTEXT"
                                : "COMPOSITION_INDEX_INCOMPLETE",
                        readyReasons,
                        missingReasons,
                        actions,
                        ""));
            }
        }
        return List.copyOf(plans);
    }

    @JsonProperty(value = "appDdlCompositionIndexPlansByTableName", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppDdlCompositionIndexPlan>> appDdlCompositionIndexPlansByTableName() {
        return appDdlCompositionIndex().byTableName();
    }

    @JsonProperty(value = "appDdlCompositionIndexPlansByRoleType", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppDdlCompositionIndexPlan>> appDdlCompositionIndexPlansByRoleType() {
        return appDdlCompositionIndex().byRoleType();
    }

    @JsonProperty(value = "appDdlCompositionIndexPlansByNamedIndex", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppDdlCompositionIndexPlan>> appDdlCompositionIndexPlansByNamedIndex() {
        return appDdlCompositionIndex().byNamedIndex();
    }

    @JsonProperty(value = "appDdlCompositionIndexPlansByCompositionColumn",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppDdlCompositionIndexPlan>> appDdlCompositionIndexPlansByCompositionColumn() {
        return appDdlCompositionIndex().byCompositionColumn();
    }

    @JsonProperty(value = "appDdlCompositionIndexPlansByRolePerformerColumn",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppDdlCompositionIndexPlan>> appDdlCompositionIndexPlansByRolePerformerColumn() {
        return appDdlCompositionIndex().byRolePerformerColumn();
    }

    @JsonProperty(value = "appDdlCompositionIndexPlansByPerformerType",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppDdlCompositionIndexPlan>> appDdlCompositionIndexPlansByPerformerType() {
        return appDdlCompositionIndex().byPerformerType();
    }

    @JsonProperty(value = "appDdlCompositionIndexPlansBySignerContextContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppDdlCompositionIndexPlan>> appDdlCompositionIndexPlansBySignerContextContract() {
        return appDdlCompositionIndex().bySignerContextContract();
    }

    @JsonProperty(value = "appDdlCompositionIndexPlansByReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppDdlCompositionIndexPlan>> appDdlCompositionIndexPlansByReadiness() {
        return appDdlCompositionIndex().byReadiness();
    }

    @JsonProperty(value = "appDdlCompositionIndexPlansByReadyReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppDdlCompositionIndexPlan>> appDdlCompositionIndexPlansByReadyReason() {
        return appDdlCompositionIndex().byReadyReason();
    }

    @JsonProperty(value = "appDdlCompositionIndexPlansByMissingReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppDdlCompositionIndexPlan>> appDdlCompositionIndexPlansByMissingReason() {
        return appDdlCompositionIndex().byMissingReason();
    }

    @JsonProperty(value = "appDdlCompositionIndexPlansByAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppDdlCompositionIndexPlan>> appDdlCompositionIndexPlansByAction() {
        return appDdlCompositionIndex().byAction();
    }

    @JsonProperty(value = "appDdlCompositionIndexPlansByCompositionContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppDdlCompositionIndexPlan>> appDdlCompositionIndexPlansByCompositionContract() {
        return appDdlCompositionIndex().byCompositionContract();
    }

    private static RoleParticipantPlan roleParticipantForTable(AppDdlCatalog.Table table,
                                                               List<RoleParticipantPlan> roleParticipants) {
        if (table == null || roleParticipants == null || roleParticipants.isEmpty()) {
            return null;
        }
        return roleParticipants.stream()
                .filter(plan -> plan != null
                        && (table.name().equals(plan.roleTableName())
                        || table.businessType().equals(plan.roleType())))
                .findFirst()
                .orElse(null);
    }

    private static List<String> normalizeCompositionIndexColumns(AppDdlCatalog.Table table,
                                                                 List<String> indexPaths) {
        if (table == null || indexPaths == null || indexPaths.isEmpty()) {
            return List.of();
        }
        List<String> columns = new ArrayList<>();
        for (String path : indexPaths) {
            String column = normalizeCompositionIndexColumn(table.businessType(), path, table.columns());
            if (column == null || column.isBlank()) {
                return List.of();
            }
            columns.add(column);
        }
        return List.copyOf(columns);
    }

    private static String normalizeCompositionIndexColumn(String businessType, String path,
                                                          List<AppDdlCatalog.Column> columns) {
        if (path == null || path.isBlank()) {
            return "";
        }
        if (columns != null) {
            for (AppDdlCatalog.Column column : columns) {
                if (column.name().equals(path)) {
                    return column.name();
                }
            }
        }
        String prefix = businessType == null || businessType.isBlank() ? "" : businessType + ".";
        String relativePath = path.startsWith(prefix) ? path.substring(prefix.length()) : path;
        if (columns != null) {
            for (AppDdlCatalog.Column column : columns) {
                if (column.name().equals(relativePath)) {
                    return column.name();
                }
            }
        }
        return relativePath;
    }

    private List<String> signerContextContracts(String roleType) {
        return signerContextContracts(roleType, scenarioContractContextPlans());
    }

    private static List<String> signerContextContracts(String roleType,
                                                       List<ScenarioContractContextPlan> contextPlans) {
        if (roleType == null || roleType.isBlank()) {
            return List.of();
        }
        return safeList(contextPlans).stream()
                .filter(plan -> plan != null
                        && plan.contextComplete()
                        && safeList(plan.signerRoleTypes()).contains(roleType))
                .map(ScenarioContractContextPlan::executionContract)
                .distinct()
                .toList();
    }

    private static List<String> appDdlCompositionIndexMissingReasons(RoleParticipantPlan roleParticipant,
                                                                     List<String> signerContextContracts) {
        List<String> reasons = new ArrayList<>();
        if (roleParticipant == null || !roleParticipant.complete() || roleParticipant.platformColumns().isEmpty()) {
            reasons.add("rolePerformerIdentity");
        }
        if (signerContextContracts == null || signerContextContracts.isEmpty()) {
            reasons.add("roleSignedContractContext");
        }
        return List.copyOf(reasons);
    }

    private static List<String> appDdlCompositionIndexActions(List<String> missingReasons) {
        List<String> actions = new ArrayList<>();
        for (String reason : safeList(missingReasons)) {
            switch (reason) {
                case "rolePerformerIdentity" -> actions.add("declareTakeRolePerformerIdentity");
                case "roleSignedContractContext" -> actions.add("routeCompositionQueryThroughRoleSignedScenario");
                default -> actions.add("completeAppDdlCompositionIndexEvidence");
            }
        }
        return actions.isEmpty() ? List.of("NO_ACTION_REQUIRED") : List.copyOf(actions);
    }

    @JsonProperty(value = "roleParticipantRequestBindingReadinessIndex",
            access = JsonProperty.Access.READ_ONLY)
    public RoleParticipantRequestBindingReadinessIndex roleParticipantRequestBindingReadinessIndex() {
        List<RoleParticipantRequestBindingPlan> plans = safeList(roleParticipantRequestBindingPlans).stream()
                .filter(plan -> plan != null)
                .map(GeneratedServiceCatalog::enrichRoleParticipantRequestBindingPlan)
                .toList();
        if (plans.isEmpty()) {
            return new RoleParticipantRequestBindingReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<RoleParticipantRequestBindingPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<RoleParticipantRequestBindingPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<RoleParticipantRequestBindingPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<RoleParticipantRequestBindingPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<RoleParticipantRequestBindingPlan>> byService = new LinkedHashMap<>();
        Map<String, List<RoleParticipantRequestBindingPlan>> byRole = new LinkedHashMap<>();
        Map<String, List<RoleParticipantRequestBindingPlan>> byRequest = new LinkedHashMap<>();
        Map<String, List<RoleParticipantRequestBindingPlan>> byParameterPath = new LinkedHashMap<>();
        Map<String, List<RoleParticipantRequestBindingPlan>> byBindingContract = new LinkedHashMap<>();
        Map<String, List<RoleParticipantRequestBindingPlan>> byIdentityBindingSource = new LinkedHashMap<>();
        Map<String, List<RoleParticipantRequestBindingPlan>> bySidecarField = new LinkedHashMap<>();
        Map<String, List<RoleParticipantRequestBindingPlan>> byMissingSidecarField = new LinkedHashMap<>();
        Map<String, List<RoleParticipantRequestBindingPlan>> byPerformerType = new LinkedHashMap<>();
        for (RoleParticipantRequestBindingPlan plan : plans) {
            if ("REQUEST_SIDECAR_READY".equals(plan.readiness())) {
                addToIndex(byReadiness, plan.readiness(), plan);
                for (String reason : safeList(plan.readyReasons())) {
                    addToIndex(byReadyReason, indexKey(reason), plan);
                }
                for (String reason : safeList(plan.missingReasons())) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.actions())) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            } else {
                addToIndex(byReadiness, indexKey(plan.readiness()), plan);
                for (String reason : safeList(plan.missingReasons())) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.actions())) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            }
            addToIndex(byService, indexKey(plan.serviceName()), plan);
            addToIndex(byRole, indexKey(plan.roleType()), plan);
            addToIndex(byRequest, indexKey(plan.requestTypeName()), plan);
            addToIndex(byParameterPath, roleParticipantRequestBindingParameterPath(plan), plan);
            addToIndex(byBindingContract, indexKey(plan.bindingContract()), plan);
            addToIndex(byIdentityBindingSource, indexKey(plan.identityBindingSource()), plan);
            addListIndexValues(bySidecarField, plan.sidecarFieldNames(), plan, "NO_SIDECAR_FIELD");
            addListIndexValues(byMissingSidecarField, plan.missingSidecarFieldNames(), plan,
                    "NO_MISSING_SIDECAR_FIELD");
            addListIndexValues(byPerformerType, plan.performerTypes(), plan, "NO_PERFORMER_TYPE");
        }
        return new RoleParticipantRequestBindingReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byService),
                immutableListMap(byRole),
                immutableListMap(byRequest),
                immutableListMap(byParameterPath),
                immutableListMap(byBindingContract),
                immutableListMap(byIdentityBindingSource),
                immutableListMap(bySidecarField),
                immutableListMap(byMissingSidecarField),
                immutableListMap(byPerformerType));
    }

    @JsonProperty(value = "roleParticipantRequestBindingPlansByParameterPath",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleParticipantRequestBindingPlan>>
            roleParticipantRequestBindingPlansByParameterPath() {
        return roleParticipantRequestBindingReadinessIndex().byParameterPath();
    }

    @JsonProperty(value = "roleParticipantRequestBindingPlansByBindingContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleParticipantRequestBindingPlan>>
            roleParticipantRequestBindingPlansByBindingContract() {
        return roleParticipantRequestBindingReadinessIndex().byBindingContract();
    }

    @JsonProperty(value = "roleParticipantRequestBindingPlansByIdentityBindingSource",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleParticipantRequestBindingPlan>>
            roleParticipantRequestBindingPlansByIdentityBindingSource() {
        return roleParticipantRequestBindingReadinessIndex().byIdentityBindingSource();
    }

    @JsonProperty(value = "roleParticipantRequestBindingPlansBySidecarField",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleParticipantRequestBindingPlan>>
            roleParticipantRequestBindingPlansBySidecarField() {
        return roleParticipantRequestBindingReadinessIndex().bySidecarField();
    }

    @JsonProperty(value = "roleParticipantRequestBindingPlansByMissingSidecarField",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleParticipantRequestBindingPlan>>
            roleParticipantRequestBindingPlansByMissingSidecarField() {
        return roleParticipantRequestBindingReadinessIndex().byMissingSidecarField();
    }

    @JsonProperty(value = "roleParticipantRequestBindingPlansByPerformerType",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleParticipantRequestBindingPlan>>
            roleParticipantRequestBindingPlansByPerformerType() {
        return roleParticipantRequestBindingReadinessIndex().byPerformerType();
    }

    @JsonProperty(value = "roleParticipantRequestBindingPlansByReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleParticipantRequestBindingPlan>>
            roleParticipantRequestBindingPlansByReadiness() {
        return roleParticipantRequestBindingReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "roleParticipantRequestBindingPlansByReadyReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleParticipantRequestBindingPlan>>
            roleParticipantRequestBindingPlansByReadyReason() {
        return roleParticipantRequestBindingReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "roleParticipantRequestBindingPlansByMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleParticipantRequestBindingPlan>>
            roleParticipantRequestBindingPlansByMissingReason() {
        return roleParticipantRequestBindingReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "roleParticipantRequestBindingPlansByAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RoleParticipantRequestBindingPlan>>
            roleParticipantRequestBindingPlansByAction() {
        return roleParticipantRequestBindingReadinessIndex().byAction();
    }

    private static String roleParticipantRequestBindingContractKey(
            RoleParticipantRequestBindingPlan plan, List<String> missingReasons) {
        if (plan == null) {
            return "NO_BINDING_CONTRACT";
        }
        String parameterPath = roleParticipantRequestBindingParameterPath(plan);
        String requestType = indexKey(plan.requestTypeName());
        String readiness = safeList(missingReasons).isEmpty()
                ? "REQUEST_SIDECAR_READY"
                : "REQUEST_SIDECAR_INCOMPLETE";
        if ("NO_PARAMETER_PATH".equals(parameterPath) && "NONE".equals(requestType)) {
            return "NO_BINDING_CONTRACT";
        }
        return parameterPath + "->" + requestType + ":" + readiness;
    }

    private static RoleParticipantRequestBindingPlan enrichRoleParticipantRequestBindingPlan(
            RoleParticipantRequestBindingPlan plan) {
        if (plan == null) {
            return null;
        }
        List<String> missingReasons = safeList(plan.missingReasons()).isEmpty()
                ? roleParticipantRequestBindingMissingReasons(plan)
                : safeList(plan.missingReasons());
        String readiness = !isBlank(plan.readiness())
                ? plan.readiness()
                : (missingReasons.isEmpty() ? "REQUEST_SIDECAR_READY" : "REQUEST_SIDECAR_INCOMPLETE");
        List<String> readyReasons = safeList(plan.readyReasons()).isEmpty()
                && "REQUEST_SIDECAR_READY".equals(readiness)
                ? roleParticipantRequestBindingReadyReasons(plan)
                : safeList(plan.readyReasons());
        if (missingReasons.isEmpty() && "REQUEST_SIDECAR_READY".equals(readiness)) {
            missingReasons = List.of("NO_MISSING_REQUEST_BINDING_REASON");
        }
        List<String> actions = safeList(plan.actions()).isEmpty()
                ? roleParticipantRequestBindingActions("REQUEST_SIDECAR_READY".equals(readiness)
                        ? List.of()
                        : missingReasons)
                : safeList(plan.actions());
        String bindingContract = !isBlank(plan.bindingContract())
                ? plan.bindingContract()
                : roleParticipantRequestBindingContractKey(plan,
                        "REQUEST_SIDECAR_READY".equals(readiness) ? List.of() : missingReasons);
        return new RoleParticipantRequestBindingPlan(plan.serviceName(),
                plan.roleType(),
                plan.roleParameterName(),
                plan.performerTypes(),
                plan.requestTypeName(),
                plan.sidecarFieldNames(),
                plan.missingSidecarFieldNames(),
                plan.identityBindingSource(),
                plan.requestTypeAvailable(),
                plan.complete(),
                readiness,
                readyReasons,
                missingReasons,
                actions,
                bindingContract);
    }

    private static String roleParticipantRequestBindingParameterPath(RoleParticipantRequestBindingPlan plan) {
        if (plan == null || plan.serviceName() == null || plan.serviceName().isBlank()
                || plan.roleParameterName() == null || plan.roleParameterName().isBlank()
                || plan.roleType() == null || plan.roleType().isBlank()) {
            return "NO_PARAMETER_PATH";
        }
        return plan.serviceName() + "." + plan.roleParameterName() + ":" + plan.roleType();
    }

    private static String performerTypeSetKey(List<String> performerTypes) {
        List<String> performers = safeList(performerTypes).stream()
                .filter(performer -> performer != null && !performer.isBlank())
                .distinct()
                .toList();
        return performers.isEmpty() ? "NO_PERFORMER_TYPE" : String.join("+", performers);
    }

    private static String platformColumnSetKey(List<String> platformColumns) {
        List<String> columns = safeList(platformColumns).stream()
                .filter(column -> column != null && !column.isBlank())
                .distinct()
                .toList();
        return columns.isEmpty() ? "NO_PLATFORM_COLUMN" : String.join("+", columns);
    }

    @JsonProperty(value = "idempotencyPlans")
    public List<IdempotencyPlan> idempotencyPlans() {
        return safeList(idempotencyPlans).stream()
                .filter(plan -> plan != null)
                .map(GeneratedServiceCatalog::enrichIdempotencyPlan)
                .toList();
    }

    @JsonProperty(value = "idempotencyPlansByService")
    public Map<String, IdempotencyPlan> idempotencyPlansByService() {
        Map<String, IdempotencyPlan> byService = new LinkedHashMap<>();
        for (IdempotencyPlan plan : idempotencyPlans()) {
            byService.putIfAbsent(indexKey(plan.serviceName()), plan);
        }
        return Map.copyOf(byService);
    }

    @JsonProperty(value = "idempotencyReadinessIndex", access = JsonProperty.Access.READ_ONLY)
    public IdempotencyReadinessIndex idempotencyReadinessIndex() {
        List<IdempotencyPlan> plans = idempotencyPlans();
        if (plans.isEmpty()) {
            return new IdempotencyReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of());
        }
        Map<String, List<IdempotencyPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<IdempotencyPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<IdempotencyPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<IdempotencyPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<IdempotencyPlan>> byService = new LinkedHashMap<>();
        Map<String, List<IdempotencyPlan>> byIdempotencyKeyPath = new LinkedHashMap<>();
        Map<String, List<IdempotencyPlan>> byOwner = new LinkedHashMap<>();
        Map<String, List<IdempotencyPlan>> byMode = new LinkedHashMap<>();
        Map<String, List<IdempotencyPlan>> byBoundary = new LinkedHashMap<>();
        Map<String, List<IdempotencyPlan>> byOwnershipBoundary = new LinkedHashMap<>();
        Map<String, List<IdempotencyPlan>> byBackend = new LinkedHashMap<>();
        Map<String, List<IdempotencyPlan>> byStore = new LinkedHashMap<>();
        Map<String, List<IdempotencyPlan>> byPhysicalRuntimePath = new LinkedHashMap<>();
        Map<String, List<IdempotencyPlan>> byExecutionContract = new LinkedHashMap<>();
        for (IdempotencyPlan plan : plans) {
            addToIndex(byReadiness, indexKey(plan.readiness()), plan);
            for (String reason : safeList(plan.readyReasons())) {
                addToIndex(byReadyReason, indexKey(reason), plan);
            }
            for (String reason : safeList(plan.missingReasons())) {
                addToIndex(byMissingReason, indexKey(reason), plan);
            }
            for (String action : safeList(plan.actions())) {
                addToIndex(byAction, indexKey(action), plan);
            }
            addToIndex(byService, indexKey(plan.serviceName()), plan);
            addToIndex(byIdempotencyKeyPath, idempotencyKeyPathKey(plan), plan);
            addToIndex(byOwner, idempotencyOwnerKey(plan), plan);
            addToIndex(byMode, idempotencyModeKey(plan), plan);
            addToIndex(byBoundary, idempotencyBoundaryKey(plan), plan);
            addToIndex(byOwnershipBoundary, indexKey(plan.ownershipBoundary()), plan);
            addToIndex(byBackend, indexKey(plan.backendName()), plan);
            addToIndex(byStore, idempotencyStoreKey(plan), plan);
            addToIndex(byPhysicalRuntimePath, indexKey(plan.physicalRuntimePath()), plan);
            addToIndex(byExecutionContract, indexKey(plan.executionContract()), plan);
        }
        return new IdempotencyReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byService),
                immutableListMap(byIdempotencyKeyPath),
                immutableListMap(byOwner),
                immutableListMap(byMode),
                immutableListMap(byBoundary),
                immutableListMap(byOwnershipBoundary),
                immutableListMap(byBackend),
                immutableListMap(byStore),
                immutableListMap(byPhysicalRuntimePath),
                immutableListMap(byExecutionContract));
    }

    @JsonProperty(value = "idempotencyPlansByReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<IdempotencyPlan>> idempotencyPlansByReadiness() {
        return idempotencyReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "idempotencyPlansByReadyReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<IdempotencyPlan>> idempotencyPlansByReadyReason() {
        return idempotencyReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "idempotencyPlansByMissingReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<IdempotencyPlan>> idempotencyPlansByMissingReason() {
        return idempotencyReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "idempotencyPlansByAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<IdempotencyPlan>> idempotencyPlansByAction() {
        return idempotencyReadinessIndex().byAction();
    }

    @JsonProperty(value = "idempotencyPlansByKeyPath", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<IdempotencyPlan>> idempotencyPlansByKeyPath() {
        return idempotencyReadinessIndex().byIdempotencyKeyPath();
    }

    @JsonProperty(value = "idempotencyPlansByMode", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<IdempotencyPlan>> idempotencyPlansByMode() {
        return idempotencyReadinessIndex().byMode();
    }

    @JsonProperty(value = "idempotencyPlansByBoundary", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<IdempotencyPlan>> idempotencyPlansByBoundary() {
        return idempotencyReadinessIndex().byBoundary();
    }

    @JsonProperty(value = "idempotencyPlansByOwner", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<IdempotencyPlan>> idempotencyPlansByOwner() {
        return idempotencyReadinessIndex().byOwner();
    }

    @JsonProperty(value = "idempotencyPlansByBackend", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<IdempotencyPlan>> idempotencyPlansByBackend() {
        return idempotencyReadinessIndex().byBackend();
    }

    @JsonProperty(value = "idempotencyPlansByStore", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<IdempotencyPlan>> idempotencyPlansByStore() {
        return idempotencyReadinessIndex().byStore();
    }

    @JsonProperty(value = "idempotencyPlansByOwnershipBoundary", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<IdempotencyPlan>> idempotencyPlansByOwnershipBoundary() {
        return idempotencyReadinessIndex().byOwnershipBoundary();
    }

    @JsonProperty(value = "idempotencyPlansByPhysicalRuntimePath", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<IdempotencyPlan>> idempotencyPlansByPhysicalRuntimePath() {
        return idempotencyReadinessIndex().byPhysicalRuntimePath();
    }

    @JsonProperty(value = "idempotencyPlansByExecutionContract", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<IdempotencyPlan>> idempotencyPlansByExecutionContract() {
        return idempotencyReadinessIndex().byExecutionContract();
    }

    @JsonProperty(value = "idempotencyPlansByKeyAvailability", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<IdempotencyPlan>> idempotencyPlansByKeyAvailability() {
        Map<String, List<IdempotencyPlan>> byKeyAvailability = new LinkedHashMap<>();
        for (IdempotencyPlan plan : idempotencyPlans()) {
            addToIndex(byKeyAvailability,
                    plan.idempotencyKeyAvailable() ? "KEY_AVAILABLE" : "KEY_MISSING",
                    plan);
        }
        return immutableListMap(byKeyAvailability);
    }

    @JsonProperty(value = "idempotencyPlansByRuntimeStoreAvailability", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<IdempotencyPlan>> idempotencyPlansByRuntimeStoreAvailability() {
        Map<String, List<IdempotencyPlan>> byRuntimeStoreAvailability = new LinkedHashMap<>();
        for (IdempotencyPlan plan : idempotencyPlans()) {
            addToIndex(byRuntimeStoreAvailability,
                    idempotencyRuntimeAvailable(plan) ? "RUNTIME_STORE_AVAILABLE" : "RUNTIME_STORE_MISSING",
                    plan);
        }
        return immutableListMap(byRuntimeStoreAvailability);
    }

    @JsonProperty(value = "idempotencyPlansByBoundaryReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<IdempotencyPlan>> idempotencyPlansByBoundaryReadiness() {
        Map<String, List<IdempotencyPlan>> byBoundaryReadiness = new LinkedHashMap<>();
        for (IdempotencyPlan plan : idempotencyPlans()) {
            addToIndex(byBoundaryReadiness,
                    idempotencyBoundaryAvailable(plan)
                            ? "IDEMPOTENCY_BOUNDARY_READY"
                            : "IDEMPOTENCY_BOUNDARY_BLOCKED",
                    plan);
        }
        return immutableListMap(byBoundaryReadiness);
    }

    @JsonProperty(value = "scopeHeaderReadinessIndex", access = JsonProperty.Access.READ_ONLY)
    public ScopeHeaderReadinessIndex scopeHeaderReadinessIndex() {
        List<ScopeHeaderPlan> plans = safeList(scopeHeaderPlans).stream()
                .filter(plan -> plan != null)
                .map(GeneratedServiceCatalog::enrichScopeHeaderPlan)
                .toList();
        if (plans.isEmpty()) {
            return new ScopeHeaderReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<ScopeHeaderPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<ScopeHeaderPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<ScopeHeaderPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<ScopeHeaderPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<ScopeHeaderPlan>> byScope = new LinkedHashMap<>();
        Map<String, List<ScopeHeaderPlan>> byService = new LinkedHashMap<>();
        Map<String, List<ScopeHeaderPlan>> byScopeKind = new LinkedHashMap<>();
        Map<String, List<ScopeHeaderPlan>> byBoundary = new LinkedHashMap<>();
        Map<String, List<ScopeHeaderPlan>> byPhysicalBackend = new LinkedHashMap<>();
        Map<String, List<ScopeHeaderPlan>> byIdempotencyBackend = new LinkedHashMap<>();
        Map<String, List<ScopeHeaderPlan>> byPhysicalBinding = new LinkedHashMap<>();
        Map<String, List<ScopeHeaderPlan>> byIdempotencyBinding = new LinkedHashMap<>();
        for (ScopeHeaderPlan plan : plans) {
            String readiness = isBlank(plan.readiness()) ? "SCOPE_HEADER_INCOMPLETE" : plan.readiness();
            List<String> missingReasons = safeList(plan.missingReasons());
            if ("SCOPE_HEADER_READY".equals(readiness)) {
                addToIndex(byReadiness, readiness, plan);
                for (String reason : safeList(plan.readyReasons())) {
                    addToIndex(byReadyReason, indexKey(reason), plan);
                }
                for (String reason : missingReasons) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.actions())) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            } else {
                addToIndex(byReadiness, readiness, plan);
                for (String reason : missingReasons) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.actions())) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            }
            addToIndex(byScope, indexKey(plan.scopeName()), plan);
            for (String serviceName : plan.serviceNames()) {
                addToIndex(byService, indexKey(serviceName), plan);
            }
            addToIndex(byScopeKind, indexKey(plan.scopeKind()), plan);
            addToIndex(byBoundary, indexKey(plan.boundary()), plan);
            addListIndexValues(byPhysicalBackend, plan.physicalBackendNames(), plan, "NO_PHYSICAL_BACKEND");
            addListIndexValues(byIdempotencyBackend, plan.idempotencyBackendNames(), plan,
                    "NO_IDEMPOTENCY_BACKEND");
            addListIndexValues(byPhysicalBinding, plan.physicalBindingBindings(), plan,
                    "NO_PHYSICAL_BINDING");
            addListIndexValues(byIdempotencyBinding, plan.idempotencyBindings(), plan,
                    "NO_IDEMPOTENCY_BINDING");
        }
        return new ScopeHeaderReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byScope),
                immutableListMap(byService),
                immutableListMap(byScopeKind),
                immutableListMap(byBoundary),
                immutableListMap(byPhysicalBackend),
                immutableListMap(byIdempotencyBackend),
                immutableListMap(byPhysicalBinding),
                immutableListMap(byIdempotencyBinding));
    }

    @JsonProperty(value = "scopeHeaderReadinessPlansByReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScopeHeaderPlan>> scopeHeaderReadinessPlansByReadiness() {
        return scopeHeaderReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "scopeHeaderReadinessPlansByReadyReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScopeHeaderPlan>> scopeHeaderReadinessPlansByReadyReason() {
        return scopeHeaderReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "scopeHeaderReadinessPlansByMissingReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScopeHeaderPlan>> scopeHeaderReadinessPlansByMissingReason() {
        return scopeHeaderReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "scopeHeaderReadinessPlansByAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScopeHeaderPlan>> scopeHeaderReadinessPlansByAction() {
        return scopeHeaderReadinessIndex().byAction();
    }

    @JsonProperty(value = "scopeHeaderReadinessPlansByScope", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScopeHeaderPlan>> scopeHeaderReadinessPlansByScope() {
        return scopeHeaderReadinessIndex().byScope();
    }

    @JsonProperty(value = "scopeHeaderReadinessPlansByService", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScopeHeaderPlan>> scopeHeaderReadinessPlansByService() {
        return scopeHeaderReadinessIndex().byService();
    }

    @JsonProperty(value = "scopeHeaderReadinessPlansByScopeKind", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScopeHeaderPlan>> scopeHeaderReadinessPlansByScopeKind() {
        return scopeHeaderReadinessIndex().byScopeKind();
    }

    @JsonProperty(value = "scopeHeaderReadinessPlansByBoundary", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScopeHeaderPlan>> scopeHeaderReadinessPlansByBoundary() {
        return scopeHeaderReadinessIndex().byBoundary();
    }

    @JsonProperty(value = "scopeHeaderReadinessPlansByPhysicalBackend", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScopeHeaderPlan>> scopeHeaderReadinessPlansByPhysicalBackend() {
        return scopeHeaderReadinessIndex().byPhysicalBackend();
    }

    @JsonProperty(value = "scopeHeaderReadinessPlansByIdempotencyBackend", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScopeHeaderPlan>> scopeHeaderReadinessPlansByIdempotencyBackend() {
        return scopeHeaderReadinessIndex().byIdempotencyBackend();
    }

    @JsonProperty(value = "scopeHeaderReadinessPlansByPhysicalBinding", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScopeHeaderPlan>> scopeHeaderReadinessPlansByPhysicalBinding() {
        return scopeHeaderReadinessIndex().byPhysicalBinding();
    }

    @JsonProperty(value = "scopeHeaderReadinessPlansByIdempotencyBinding", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScopeHeaderPlan>> scopeHeaderReadinessPlansByIdempotencyBinding() {
        return scopeHeaderReadinessIndex().byIdempotencyBinding();
    }

    @JsonProperty(value = "sagaTransactionRecoveryClosureIndex", access = JsonProperty.Access.READ_ONLY)
    public SagaTransactionRecoveryClosureIndex sagaTransactionRecoveryClosureIndex() {
        List<SagaTransactionCallPlan> plans = safeList(sagaTransactionCallPlans).stream()
                .filter(plan -> plan != null)
                .map(GeneratedServiceCatalog::enrichSagaTransactionCallPlan)
                .toList();
        if (plans.isEmpty()) {
            return new SagaTransactionRecoveryClosureIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<SagaTransactionCallPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<SagaTransactionCallPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<SagaTransactionCallPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<SagaTransactionCallPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<SagaTransactionCallPlan>> bySaga = new LinkedHashMap<>();
        Map<String, List<SagaTransactionCallPlan>> byTargetService = new LinkedHashMap<>();
        Map<String, List<SagaTransactionCallPlan>> byCall = new LinkedHashMap<>();
        Map<String, List<SagaTransactionCallPlan>> byTargetTransactionMode = new LinkedHashMap<>();
        Map<String, List<SagaTransactionCallPlan>> byRecoveryContract = new LinkedHashMap<>();
        Map<String, List<SagaTransactionCallPlan>> byEvidenceObjectType = new LinkedHashMap<>();
        Map<String, List<SagaTransactionCallPlan>> byImplementationStrategy = new LinkedHashMap<>();
        Map<String, List<SagaTransactionCallPlan>> byRecoveryBoundary = new LinkedHashMap<>();
        Map<String, List<SagaTransactionCallPlan>> byRecoveryOwner = new LinkedHashMap<>();
        Map<String, List<SagaTransactionCallPlan>> byObjectSnapshotResponsibility = new LinkedHashMap<>();
        for (SagaTransactionCallPlan plan : plans) {
            String readiness = isBlank(plan.recoveryReadiness()) ? "RECOVERY_OPEN" : plan.recoveryReadiness();
            List<String> missingReasons = safeList(plan.recoveryMissingReasons());
            if ("RECOVERY_CLOSED".equals(readiness)) {
                addToIndex(byReadiness, readiness, plan);
                for (String reason : safeList(plan.recoveryReadyReasons())) {
                    addToIndex(byReadyReason, indexKey(reason), plan);
                }
                for (String reason : missingReasons) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.recoveryActions())) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            } else {
                addToIndex(byReadiness, readiness, plan);
                for (String reason : missingReasons) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.recoveryActions())) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            }
            addToIndex(bySaga, indexKey(plan.sagaServiceName()), plan);
            addToIndex(byTargetService, indexKey(plan.targetServiceName()), plan);
            addToIndex(byCall, indexKey(plan.callPath()), plan);
            addToIndex(byTargetTransactionMode,
                    plan.targetTransactionMode() == null ? "NO_TRANSACTION_MODE" : plan.targetTransactionMode().name(),
                    plan);
            addToIndex(byRecoveryContract, indexKey(plan.recoveryContract()), plan);
            addSagaRecoveryEvidenceObjectTypes(byEvidenceObjectType, plan);
            List<String> implementationStrategies = safeList(plan.implementationStrategies());
            if (implementationStrategies.isEmpty()) {
                addToIndex(byImplementationStrategy, "NO_QUANTUM_IMPLEMENTATION", plan);
            } else {
                for (String implementationStrategy : implementationStrategies) {
                    addToIndex(byImplementationStrategy, indexKey(implementationStrategy), plan);
                }
            }
            addToIndex(byRecoveryBoundary, indexKey(plan.recoveryBoundary()), plan);
            addToIndex(byRecoveryOwner, indexKey(plan.recoveryOwner()), plan);
            addToIndex(byObjectSnapshotResponsibility,
                    plan.objectSnapshotRequired()
                            ? "OBJECT_SNAPSHOT_REQUIRED"
                            : "BOUNDARY_MANAGED_RECOVERY",
                    plan);
        }
        return new SagaTransactionRecoveryClosureIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(bySaga),
                immutableListMap(byTargetService),
                immutableListMap(byCall),
                immutableListMap(byTargetTransactionMode),
                immutableListMap(byRecoveryContract),
                immutableListMap(byEvidenceObjectType),
                immutableListMap(byImplementationStrategy),
                immutableListMap(byRecoveryBoundary),
                immutableListMap(byRecoveryOwner),
                immutableListMap(byObjectSnapshotResponsibility));
    }

    @JsonProperty(value = "sagaTransactionRecoveryClosurePlansByReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaTransactionCallPlan>> sagaTransactionRecoveryClosurePlansByReadiness() {
        return sagaTransactionRecoveryClosureIndex().byReadiness();
    }

    @JsonProperty(value = "sagaTransactionRecoveryClosurePlansByReadyReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaTransactionCallPlan>> sagaTransactionRecoveryClosurePlansByReadyReason() {
        return sagaTransactionRecoveryClosureIndex().byReadyReason();
    }

    @JsonProperty(value = "sagaTransactionRecoveryClosurePlansByMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaTransactionCallPlan>> sagaTransactionRecoveryClosurePlansByMissingReason() {
        return sagaTransactionRecoveryClosureIndex().byMissingReason();
    }

    @JsonProperty(value = "sagaTransactionRecoveryClosurePlansByAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaTransactionCallPlan>> sagaTransactionRecoveryClosurePlansByAction() {
        return sagaTransactionRecoveryClosureIndex().byAction();
    }

    @JsonProperty(value = "sagaTransactionRecoveryClosurePlansBySaga",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaTransactionCallPlan>> sagaTransactionRecoveryClosurePlansBySaga() {
        return sagaTransactionRecoveryClosureIndex().bySaga();
    }

    @JsonProperty(value = "sagaTransactionRecoveryClosurePlansByTargetService",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaTransactionCallPlan>> sagaTransactionRecoveryClosurePlansByTargetService() {
        return sagaTransactionRecoveryClosureIndex().byTargetService();
    }

    @JsonProperty(value = "sagaTransactionRecoveryClosurePlansByCall",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaTransactionCallPlan>> sagaTransactionRecoveryClosurePlansByCall() {
        return sagaTransactionRecoveryClosureIndex().byCall();
    }

    @JsonProperty(value = "sagaTransactionRecoveryClosurePlansByTargetTransactionMode",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaTransactionCallPlan>> sagaTransactionRecoveryClosurePlansByTargetTransactionMode() {
        return sagaTransactionRecoveryClosureIndex().byTargetTransactionMode();
    }

    @JsonProperty(value = "sagaTransactionRecoveryClosurePlansByRecoveryContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaTransactionCallPlan>> sagaTransactionRecoveryClosurePlansByRecoveryContract() {
        return sagaTransactionRecoveryClosureIndex().byRecoveryContract();
    }

    @JsonProperty(value = "sagaTransactionRecoveryClosurePlansByEvidenceObjectType",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaTransactionCallPlan>> sagaTransactionRecoveryClosurePlansByEvidenceObjectType() {
        return sagaTransactionRecoveryClosureIndex().byEvidenceObjectType();
    }

    @JsonProperty(value = "sagaTransactionRecoveryClosurePlansByImplementationStrategy",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaTransactionCallPlan>>
            sagaTransactionRecoveryClosurePlansByImplementationStrategy() {
        return sagaTransactionRecoveryClosureIndex().byImplementationStrategy();
    }

    @JsonProperty(value = "sagaTransactionRecoveryClosurePlansByRecoveryBoundary",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaTransactionCallPlan>> sagaTransactionRecoveryClosurePlansByRecoveryBoundary() {
        return sagaTransactionRecoveryClosureIndex().byRecoveryBoundary();
    }

    @JsonProperty(value = "sagaTransactionRecoveryClosurePlansByRecoveryOwner",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaTransactionCallPlan>> sagaTransactionRecoveryClosurePlansByRecoveryOwner() {
        return sagaTransactionRecoveryClosureIndex().byRecoveryOwner();
    }

    @JsonProperty(value = "sagaTransactionRecoveryClosurePlansByObjectSnapshotResponsibility",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<SagaTransactionCallPlan>>
            sagaTransactionRecoveryClosurePlansByObjectSnapshotResponsibility() {
        return sagaTransactionRecoveryClosureIndex().byObjectSnapshotResponsibility();
    }

    @JsonProperty(value = "appDdlTableIndex", access = JsonProperty.Access.READ_ONLY)
    public AppDdlTableIndex appDdlTableIndex() {
        if (appDdl == null || appDdl.tables().isEmpty()) {
            return new AppDdlTableIndex(Map.of(), Map.of(), Map.of());
        }
        Map<String, AppDdlCatalog.Table> byBusinessType = new java.util.LinkedHashMap<>();
        Map<String, List<AppDdlCatalog.Table>> byCategory = new java.util.LinkedHashMap<>();
        for (AppDdlCatalog.Table table : appDdl.tables()) {
            if (table.businessType() != null && !table.businessType().isBlank()) {
                byBusinessType.put(table.businessType(), table);
            }
            String category = table.category() == null || table.category().isBlank()
                    ? "UNASSIGNED"
                    : table.category();
            byCategory.computeIfAbsent(category, ignored -> new java.util.ArrayList<>()).add(table);
        }
        return new AppDdlTableIndex(appDdl.byName(),
                Map.copyOf(byBusinessType),
                immutableListMap(byCategory));
    }

    @JsonProperty(value = "appDdlTablesByName", access = JsonProperty.Access.READ_ONLY)
    public Map<String, AppDdlCatalog.Table> appDdlTablesByName() {
        return appDdlTableIndex().byName();
    }

    @JsonProperty(value = "appDdlTablesByBusinessType", access = JsonProperty.Access.READ_ONLY)
    public Map<String, AppDdlCatalog.Table> appDdlTablesByBusinessType() {
        return appDdlTableIndex().byBusinessType();
    }

    @JsonProperty(value = "appDdlTablesByCategory", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppDdlCatalog.Table>> appDdlTablesByCategory() {
        return appDdlTableIndex().byCategory();
    }

    @JsonProperty(value = "businessCredentialIndex", access = JsonProperty.Access.READ_ONLY)
    public BusinessCredentialIndex businessCredentialIndex() {
        if (appDdl == null || appDdl.tables().isEmpty()) {
            return new BusinessCredentialIndex(List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        List<BusinessCredentialPlan> plans = new ArrayList<>();
        for (AppDdlCatalog.Table table : appDdl.tables()) {
            for (String columnName : table.businessCredentialColumns()) {
                if (columnName == null || columnName.isBlank()) {
                    continue;
                }
                plans.add(new BusinessCredentialPlan(table.name(),
                        table.businessType(),
                        table.category(),
                        columnName,
                        businessCredentialPath(table.businessType(), columnName),
                        table.primaryKeys().contains(columnName),
                        table.platformColumns().contains(columnName)));
            }
        }
        if (plans.isEmpty()) {
            return new BusinessCredentialIndex(List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<BusinessCredentialPlan>> byTableName = new LinkedHashMap<>();
        Map<String, List<BusinessCredentialPlan>> byBusinessType = new LinkedHashMap<>();
        Map<String, List<BusinessCredentialPlan>> byCategory = new LinkedHashMap<>();
        Map<String, List<BusinessCredentialPlan>> byColumn = new LinkedHashMap<>();
        Map<String, List<BusinessCredentialPlan>> byPath = new LinkedHashMap<>();
        for (BusinessCredentialPlan plan : plans) {
            addToIndex(byTableName, indexKey(plan.tableName()), plan);
            addToIndex(byBusinessType, indexKey(plan.businessType()), plan);
            addToIndex(byCategory, indexKey(plan.category()), plan);
            addToIndex(byColumn, indexKey(plan.columnName()), plan);
            addToIndex(byPath, indexKey(plan.path()), plan);
        }
        return new BusinessCredentialIndex(List.copyOf(plans),
                immutableListMap(byTableName),
                immutableListMap(byBusinessType),
                immutableListMap(byCategory),
                immutableListMap(byColumn),
                immutableListMap(byPath));
    }

    @JsonProperty(value = "businessCredentialPlans", access = JsonProperty.Access.READ_ONLY)
    public List<BusinessCredentialPlan> businessCredentialPlans() {
        return businessCredentialIndex().plans();
    }

    @JsonProperty(value = "businessCredentialPlansByBusinessType", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<BusinessCredentialPlan>> businessCredentialPlansByBusinessType() {
        return businessCredentialIndex().byBusinessType();
    }

    @JsonProperty(value = "businessCredentialPlansByTableName", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<BusinessCredentialPlan>> businessCredentialPlansByTableName() {
        return businessCredentialIndex().byTableName();
    }

    @JsonProperty(value = "businessCredentialPlansByPath", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<BusinessCredentialPlan>> businessCredentialPlansByPath() {
        return businessCredentialIndex().byPath();
    }

    @JsonProperty(value = "appDdlTablePhysicalizationIndex", access = JsonProperty.Access.READ_ONLY)
    public AppDdlTablePhysicalizationIndex appDdlTablePhysicalizationIndex() {
        if (appDdl == null || appDdl.tables().isEmpty()) {
            return new AppDdlTablePhysicalizationIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        List<AppDdlTablePhysicalizationPlan> plans = appDdl.tables().stream()
                .map(table -> appDdlTablePhysicalizationPlan(table,
                        appDdlPhysicalRepositoriesForTable(table.name()),
                        objectRepositoryPlansForAppDdlTable(table.name())))
                .toList();
        Map<String, List<AppDdlTablePhysicalizationPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<AppDdlTablePhysicalizationPlan>> byCategory = new LinkedHashMap<>();
        Map<String, List<AppDdlTablePhysicalizationPlan>> byBusinessType = new LinkedHashMap<>();
        Map<String, List<AppDdlTablePhysicalizationPlan>> byRepository = new LinkedHashMap<>();
        Map<String, List<AppDdlTablePhysicalizationPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<AppDdlTablePhysicalizationPlan>> byAction = new LinkedHashMap<>();
        for (AppDdlTablePhysicalizationPlan plan : plans) {
            addToIndex(byReadiness, plan.missingReasons().isEmpty()
                    ? "APP_DDL_TABLE_READY"
                    : "APP_DDL_TABLE_BLOCKED", plan);
            addToIndex(byCategory, indexKey(plan.category()), plan);
            addToIndex(byBusinessType, indexKey(plan.businessType()), plan);
            if (plan.repositoryNames().isEmpty()) {
                addToIndex(byRepository, "NO_REPOSITORY", plan);
            } else {
                for (String repositoryName : plan.repositoryNames()) {
                    addToIndex(byRepository, indexKey(repositoryName), plan);
                }
            }
            if (plan.missingReasons().isEmpty()) {
                addToIndex(byMissingReason, "NO_MISSING_REASON", plan);
                addToIndex(byAction, "NO_ACTION_REQUIRED", plan);
            } else {
                for (String reason : plan.missingReasons()) {
                    addToIndex(byMissingReason, reason, plan);
                }
                for (String action : plan.actions()) {
                    addToIndex(byAction, action, plan);
                }
            }
        }
        return new AppDdlTablePhysicalizationIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byCategory),
                immutableListMap(byBusinessType),
                immutableListMap(byRepository),
                immutableListMap(byMissingReason),
                immutableListMap(byAction));
    }

    @JsonProperty(value = "appDdlTablePhysicalizationPlansByReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppDdlTablePhysicalizationPlan>> appDdlTablePhysicalizationPlansByReadiness() {
        return appDdlTablePhysicalizationIndex().byReadiness();
    }

    @JsonProperty(value = "appDdlTablePhysicalizationPlansByCategory",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppDdlTablePhysicalizationPlan>> appDdlTablePhysicalizationPlansByCategory() {
        return appDdlTablePhysicalizationIndex().byCategory();
    }

    @JsonProperty(value = "appDdlTablePhysicalizationPlansByBusinessType",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppDdlTablePhysicalizationPlan>> appDdlTablePhysicalizationPlansByBusinessType() {
        return appDdlTablePhysicalizationIndex().byBusinessType();
    }

    @JsonProperty(value = "appDdlTablePhysicalizationPlansByRepository",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppDdlTablePhysicalizationPlan>> appDdlTablePhysicalizationPlansByRepository() {
        return appDdlTablePhysicalizationIndex().byRepository();
    }

    @JsonProperty(value = "appDdlTablePhysicalizationPlansByMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppDdlTablePhysicalizationPlan>> appDdlTablePhysicalizationPlansByMissingReason() {
        return appDdlTablePhysicalizationIndex().byMissingReason();
    }

    @JsonProperty(value = "appDdlTablePhysicalizationPlansByAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppDdlTablePhysicalizationPlan>> appDdlTablePhysicalizationPlansByAction() {
        return appDdlTablePhysicalizationIndex().byAction();
    }

    @JsonProperty(value = "physicalSourceFileIndex", access = JsonProperty.Access.READ_ONLY)
    public PhysicalSourceFileIndex physicalSourceFileIndex() {
        if (physicalRuntime == null || physicalRuntime.physicalSourceFiles().isEmpty()) {
            return new PhysicalSourceFileIndex(List.of(), List.of(), List.of(), Map.of(),
                    List.of(), Map.of());
        }
        List<String> all = new java.util.ArrayList<>();
        List<String> shared = new java.util.ArrayList<>();
        List<String> profile = new java.util.ArrayList<>();
        Map<String, List<String>> byRole = new java.util.LinkedHashMap<>();
        for (String sourceFile : physicalRuntime.physicalSourceFiles()) {
            if (sourceFile == null || sourceFile.isBlank()) {
                continue;
            }
            all.add(sourceFile);
            if (physicalSourceFileMatchesProfile(sourceFile, physicalRuntime.physicalProfile())) {
                profile.add(sourceFile);
                byRole.computeIfAbsent("PROFILE", ignored -> new java.util.ArrayList<>()).add(sourceFile);
            } else {
                shared.add(sourceFile);
                byRole.computeIfAbsent("SHARED", ignored -> new java.util.ArrayList<>()).add(sourceFile);
            }
        }
        String sourceSelectionContract = physicalSourceSelectionContract(all, shared, profile);
        Map<String, List<String>> bySourceSelectionContract = new LinkedHashMap<>();
        bySourceSelectionContract.put(sourceSelectionContract,
                physicalSourceSelectionEvidence(all, shared, profile));
        return new PhysicalSourceFileIndex(List.copyOf(all),
                List.copyOf(shared),
                List.copyOf(profile),
                immutableListMap(byRole),
                List.of(sourceSelectionContract),
                immutableStringListMap(bySourceSelectionContract));
    }

    @JsonProperty(value = "physicalSourceFiles", access = JsonProperty.Access.READ_ONLY)
    public List<String> physicalSourceFiles() {
        return physicalSourceFileIndex().all();
    }

    @JsonProperty(value = "sharedPhysicalSourceFiles", access = JsonProperty.Access.READ_ONLY)
    public List<String> sharedPhysicalSourceFiles() {
        return physicalSourceFileIndex().shared();
    }

    @JsonProperty(value = "profilePhysicalSourceFiles", access = JsonProperty.Access.READ_ONLY)
    public List<String> profilePhysicalSourceFiles() {
        return physicalSourceFileIndex().profile();
    }

    @JsonProperty(value = "physicalSourceFilesByRole", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<String>> physicalSourceFilesByRole() {
        return physicalSourceFileIndex().byRole();
    }

    @JsonProperty(value = "physicalSourceSelectionContracts", access = JsonProperty.Access.READ_ONLY)
    public List<String> physicalSourceSelectionContracts() {
        return physicalSourceFileIndex().sourceSelectionContracts();
    }

    @JsonProperty(value = "physicalSourceFilesBySourceSelectionContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<String>> physicalSourceFilesBySourceSelectionContract() {
        return physicalSourceFileIndex().bySourceSelectionContract();
    }

    @JsonProperty(value = "physicalProfileStrictConfigIndex", access = JsonProperty.Access.READ_ONLY)
    public PhysicalProfileStrictConfigIndex physicalProfileStrictConfigIndex() {
        DeploymentValidationPlan plan = deploymentValidationPlansByCategory == null
                ? null
                : deploymentValidationPlansByCategory.get("PHYSICAL_PROFILE");
        if (plan == null || plan.metadata() == null || plan.metadata().isEmpty()) {
            return new PhysicalProfileStrictConfigIndex(List.of(), List.of(), List.of(),
                    List.of(), List.of(), Map.of(), Map.of(), List.of(), Map.of());
        }
        List<String> configuredKeys = csvValues(plan.metadata().get("physicalProfileStrictConfigKeys"));
        List<String> enabledKeys =
                csvValues(plan.metadata().get("physicalProfileStrictEffectiveEnabledKeys"));
        List<String> disabledKeys =
                csvValues(plan.metadata().get("physicalProfileStrictEffectiveDisabledKeys"));
        List<String> configuredBindings =
                csvValues(plan.metadata().get("physicalProfileStrictConfigBindings"));
        List<String> effectiveBindings =
                csvValues(plan.metadata().get("physicalProfileStrictEffectiveBindings"));
        Map<String, List<String>> keysByEffect = new LinkedHashMap<>();
        keysByEffect.put("ENABLED", enabledKeys);
        keysByEffect.put("DISABLED", disabledKeys);
        keysByEffect.put("CONFIGURED", configuredKeys);
        Map<String, List<String>> bindingsByEffect = new LinkedHashMap<>();
        bindingsByEffect.put("ENABLED", effectiveBindings.stream()
                .filter(value -> value.endsWith("=true"))
                .toList());
        bindingsByEffect.put("DISABLED", effectiveBindings.stream()
                .filter(value -> value.endsWith("=false"))
                .toList());
        bindingsByEffect.put("CONFIGURED", configuredBindings);
        String strictContract = physicalProfileStrictContract(plan.metadata(),
                configuredKeys,
                enabledKeys,
                disabledKeys);
        Map<String, List<String>> byStrictContract = new LinkedHashMap<>();
        byStrictContract.put(strictContract,
                physicalProfileStrictContractEvidence(plan.metadata(),
                        configuredBindings,
                        effectiveBindings));
        return new PhysicalProfileStrictConfigIndex(configuredKeys,
                enabledKeys,
                disabledKeys,
                configuredBindings,
                effectiveBindings,
                immutableStringListMap(keysByEffect),
                immutableStringListMap(bindingsByEffect),
                List.of(strictContract),
                immutableStringListMap(byStrictContract));
    }

    @JsonProperty(value = "technicalFieldPlatformColumnIndex", access = JsonProperty.Access.READ_ONLY)
    public TechnicalFieldPlatformColumnIndex technicalFieldPlatformColumnIndex() {
        Map<String, AppSemanticMigrationPlan> byKey = new LinkedHashMap<>();
        addTechnicalFieldPlatformColumnPlans(byKey, generatedAppSemanticMigrationPlans);
        addTechnicalFieldPlatformColumnPlans(byKey, appSemanticMigrationPlans);
        if (byKey.isEmpty()) {
            return new TechnicalFieldPlatformColumnIndex(List.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        List<AppSemanticMigrationPlan> plans = List.copyOf(byKey.values());
        Map<String, List<AppSemanticMigrationPlan>> byObjectType = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byTableName = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byPath = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byPlatformColumn = new LinkedHashMap<>();
        for (AppSemanticMigrationPlan plan : plans) {
            addToIndex(byObjectType, indexKey(plan.objectType()), plan);
            addToIndex(byTableName, indexKey(plan.tableName()), plan);
            for (String path : plan.paths()) {
                addToIndex(byPath, indexKey(path), plan);
            }
            for (String column : technicalFieldPlatformColumns(plan)) {
                addToIndex(byPlatformColumn, indexKey(column), plan);
            }
        }
        return new TechnicalFieldPlatformColumnIndex(plans,
                immutableListMap(byObjectType),
                immutableListMap(byTableName),
                immutableListMap(byPath),
                immutableListMap(byPlatformColumn));
    }

    @JsonProperty(value = "technicalFieldPlatformColumnPlansByObjectType",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> technicalFieldPlatformColumnPlansByObjectType() {
        return technicalFieldPlatformColumnIndex().byObjectType();
    }

    @JsonProperty(value = "technicalFieldPlatformColumnPlansByTableName",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> technicalFieldPlatformColumnPlansByTableName() {
        return technicalFieldPlatformColumnIndex().byTableName();
    }

    @JsonProperty(value = "technicalFieldPlatformColumnPlansByPath", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> technicalFieldPlatformColumnPlansByPath() {
        return technicalFieldPlatformColumnIndex().byPath();
    }

    @JsonProperty(value = "technicalFieldPlatformColumnPlansByPlatformColumn",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> technicalFieldPlatformColumnPlansByPlatformColumn() {
        return technicalFieldPlatformColumnIndex().byPlatformColumn();
    }

    private static String businessCredentialPath(String businessType, String columnName) {
        if (businessType == null || businessType.isBlank()) {
            return columnName == null ? "" : columnName;
        }
        if (columnName == null || columnName.isBlank()) {
            return businessType;
        }
        return businessType + "." + columnName;
    }

    @JsonProperty(value = "appDdlPlatformPrimaryKeyIndex", access = JsonProperty.Access.READ_ONLY)
    public AppDdlPlatformPrimaryKeyIndex appDdlPlatformPrimaryKeyIndex() {
        Map<String, AppSemanticMigrationPlan> byKey = new LinkedHashMap<>();
        addAppDdlPlatformPrimaryKeyPlans(byKey, generatedAppSemanticMigrationPlans);
        addAppDdlPlatformPrimaryKeyPlans(byKey, appSemanticMigrationPlans);
        if (byKey.isEmpty()) {
            return new AppDdlPlatformPrimaryKeyIndex(List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        List<AppSemanticMigrationPlan> plans = List.copyOf(byKey.values());
        Map<String, List<AppSemanticMigrationPlan>> byIdentityKind = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byObjectType = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byTableName = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byPath = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byPlatformColumn = new LinkedHashMap<>();
        for (AppSemanticMigrationPlan plan : plans) {
            addToIndex(byIdentityKind, indexKey(plan.identityKind()), plan);
            addToIndex(byObjectType, indexKey(plan.objectType()), plan);
            addToIndex(byTableName, indexKey(plan.tableName()), plan);
            for (String path : plan.paths()) {
                addToIndex(byPath, indexKey(path), plan);
            }
            for (String column : migrationPlanPlatformColumns(plan)) {
                addToIndex(byPlatformColumn, indexKey(column), plan);
            }
        }
        return new AppDdlPlatformPrimaryKeyIndex(plans,
                immutableListMap(byIdentityKind),
                immutableListMap(byObjectType),
                immutableListMap(byTableName),
                immutableListMap(byPath),
                immutableListMap(byPlatformColumn));
    }

    @JsonProperty(value = "appDdlPlatformPrimaryKeyPlansByIdentityKind",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> appDdlPlatformPrimaryKeyPlansByIdentityKind() {
        return appDdlPlatformPrimaryKeyIndex().byIdentityKind();
    }

    @JsonProperty(value = "appDdlPlatformPrimaryKeyPlansByObjectType",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> appDdlPlatformPrimaryKeyPlansByObjectType() {
        return appDdlPlatformPrimaryKeyIndex().byObjectType();
    }

    @JsonProperty(value = "appDdlPlatformPrimaryKeyPlansByTableName",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> appDdlPlatformPrimaryKeyPlansByTableName() {
        return appDdlPlatformPrimaryKeyIndex().byTableName();
    }

    @JsonProperty(value = "appDdlPlatformPrimaryKeyPlansByPath", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> appDdlPlatformPrimaryKeyPlansByPath() {
        return appDdlPlatformPrimaryKeyIndex().byPath();
    }

    @JsonProperty(value = "appDdlPlatformPrimaryKeyPlansByPlatformColumn",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> appDdlPlatformPrimaryKeyPlansByPlatformColumn() {
        return appDdlPlatformPrimaryKeyIndex().byPlatformColumn();
    }

    @JsonProperty(value = "quantumServiceInternalProtocolMigrationIndex",
            access = JsonProperty.Access.READ_ONLY)
    public QuantumServiceInternalProtocolMigrationIndex quantumServiceInternalProtocolMigrationIndex() {
        Map<String, AppSemanticMigrationPlan> byKey = new LinkedHashMap<>();
        addQuantumServiceInternalProtocolPlans(byKey, generatedAppSemanticMigrationPlans);
        addQuantumServiceInternalProtocolPlans(byKey, appSemanticMigrationPlans);
        if (byKey.isEmpty()) {
            return new QuantumServiceInternalProtocolMigrationIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        List<AppSemanticMigrationPlan> plans = List.copyOf(byKey.values());
        Map<String, List<AppSemanticMigrationPlan>> byService = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> bySource = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byAttributePath = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> bySnapshotBoundary = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byRecoveryRole = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byAdapter = new LinkedHashMap<>();
        for (AppSemanticMigrationPlan plan : plans) {
            addToIndex(byService, indexKey(plan.serviceName()), plan);
            if (plan.participantCategories().isEmpty()) {
                addToIndex(bySource, "NO_PROTOCOL_SOURCE", plan);
            } else {
                for (String source : plan.participantCategories()) {
                    addToIndex(bySource, indexKey(source), plan);
                }
            }
            if (plan.paths().isEmpty()) {
                addToIndex(byAttributePath, "NO_ATTRIBUTE_PATH", plan);
            } else {
                for (String path : plan.paths()) {
                    addToIndex(byAttributePath, indexKey(path), plan);
                }
            }
            addToIndex(bySnapshotBoundary, indexKey(plan.snapshotBoundary()), plan);
            addToIndex(byRecoveryRole, indexKey(plan.recoveryRole()), plan);
            addToIndex(byAdapter, indexKey(plan.adapterName()), plan);
        }
        return new QuantumServiceInternalProtocolMigrationIndex(plans,
                immutableListMap(byService),
                immutableListMap(bySource),
                immutableListMap(byAttributePath),
                immutableListMap(bySnapshotBoundary),
                immutableListMap(byRecoveryRole),
                immutableListMap(byAdapter));
    }

    @JsonProperty(value = "quantumServiceInternalProtocolMigrationPlansByService",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> quantumServiceInternalProtocolMigrationPlansByService() {
        return quantumServiceInternalProtocolMigrationIndex().byService();
    }

    @JsonProperty(value = "quantumServiceInternalProtocolMigrationPlansBySource",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> quantumServiceInternalProtocolMigrationPlansBySource() {
        return quantumServiceInternalProtocolMigrationIndex().bySource();
    }

    @JsonProperty(value = "quantumServiceInternalProtocolMigrationPlansByAttributePath",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> quantumServiceInternalProtocolMigrationPlansByAttributePath() {
        return quantumServiceInternalProtocolMigrationIndex().byAttributePath();
    }

    @JsonProperty(value = "quantumServiceInternalProtocolMigrationPlansBySnapshotBoundary",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>>
            quantumServiceInternalProtocolMigrationPlansBySnapshotBoundary() {
        return quantumServiceInternalProtocolMigrationIndex().bySnapshotBoundary();
    }

    @JsonProperty(value = "quantumServiceInternalProtocolMigrationPlansByRecoveryRole",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> quantumServiceInternalProtocolMigrationPlansByRecoveryRole() {
        return quantumServiceInternalProtocolMigrationIndex().byRecoveryRole();
    }

    @JsonProperty(value = "quantumServiceInternalProtocolMigrationPlansByAdapter",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> quantumServiceInternalProtocolMigrationPlansByAdapter() {
        return quantumServiceInternalProtocolMigrationIndex().byAdapter();
    }

    @JsonProperty(value = "repositoryReplacementReadinessIndex",
            access = JsonProperty.Access.READ_ONLY)
    public RepositoryReplacementReadinessIndex repositoryReplacementReadinessIndex() {
        List<RepositoryQueryPlan> plans = List.copyOf(safeList(repositoryQueryPlans));
        if (plans.isEmpty()) {
            return new RepositoryReplacementReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<RepositoryQueryPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<RepositoryQueryPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<RepositoryQueryPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<RepositoryQueryPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<RepositoryQueryPlan>> bySource = new LinkedHashMap<>();
        Map<String, List<RepositoryQueryPlan>> byRepository = new LinkedHashMap<>();
        Map<String, List<RepositoryQueryPlan>> byAccessContract = new LinkedHashMap<>();
        for (RepositoryQueryPlan plan : plans) {
            List<String> readyReasons = repositoryReplacementReadyReasons(plan);
            List<String> missingReasons = repositoryReplacementMissingReasons(plan);
            String readiness = repositoryReplacementReady(plan)
                    ? "REPLACEMENT_READY"
                    : "REPLACEMENT_MISSING";
            addToIndex(byReadiness, readiness, plan);
            if (readyReasons.isEmpty()) {
                addToIndex(byReadyReason, "NO_READY_REASON", plan);
            } else {
                for (String reason : readyReasons) {
                    addToIndex(byReadyReason, reason, plan);
                }
            }
            if (missingReasons.isEmpty()) {
                addToIndex(byMissingReason, "NO_MISSING_REASON", plan);
                addToIndex(byAction, "NO_ACTION_REQUIRED", plan);
            } else {
                for (String reason : missingReasons) {
                    addToIndex(byMissingReason, reason, plan);
                }
                for (String action : repositoryReplacementActions(missingReasons)) {
                    addToIndex(byAction, action, plan);
                }
            }
            addToIndex(bySource, indexKey(plan.source()), plan);
            addToIndex(byRepository, indexKey(plan.resolvedRepositoryName()), plan);
            addToIndex(byAccessContract, repositoryReplacementAccessContractKey(plan, readiness), plan);
        }
        return new RepositoryReplacementReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(bySource),
                immutableListMap(byRepository),
                immutableListMap(byAccessContract));
    }

    @JsonProperty(value = "repositoryReplacementPlansByReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryQueryPlan>> repositoryReplacementPlansByReadiness() {
        return repositoryReplacementReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "repositoryReplacementPlansByReadyReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryQueryPlan>> repositoryReplacementPlansByReadyReason() {
        return repositoryReplacementReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "repositoryReplacementPlansByMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryQueryPlan>> repositoryReplacementPlansByMissingReason() {
        return repositoryReplacementReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "repositoryReplacementPlansByAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryQueryPlan>> repositoryReplacementPlansByAction() {
        return repositoryReplacementReadinessIndex().byAction();
    }

    @JsonProperty(value = "repositoryReplacementPlansBySource",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryQueryPlan>> repositoryReplacementPlansBySource() {
        return repositoryReplacementReadinessIndex().bySource();
    }

    @JsonProperty(value = "repositoryReplacementPlansByRepository",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryQueryPlan>> repositoryReplacementPlansByRepository() {
        return repositoryReplacementReadinessIndex().byRepository();
    }

    @JsonProperty(value = "repositoryReplacementPlansByAccessContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryQueryPlan>> repositoryReplacementPlansByAccessContract() {
        return repositoryReplacementReadinessIndex().byAccessContract();
    }

    @JsonProperty(value = "repositoryMigrationReadinessIndex",
            access = JsonProperty.Access.READ_ONLY)
    public RepositoryMigrationReadinessIndex repositoryMigrationReadinessIndex() {
        List<RepositoryMigrationPlan> plans = List.copyOf(safeList(repositoryMigrationPlans));
        if (plans.isEmpty()) {
            return new RepositoryMigrationReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of());
        }
        Map<String, List<RepositoryMigrationPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<RepositoryMigrationPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<RepositoryMigrationPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<RepositoryMigrationPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<RepositoryMigrationPlan>> byRepository = new LinkedHashMap<>();
        Map<String, List<RepositoryMigrationPlan>> byReplacementSource = new LinkedHashMap<>();
        Map<String, List<RepositoryMigrationPlan>> byRecommendation = new LinkedHashMap<>();
        Map<String, List<RepositoryMigrationPlan>> byLookupKind = new LinkedHashMap<>();
        Map<String, List<RepositoryMigrationPlan>> byAggregateRelation = new LinkedHashMap<>();
        Map<String, List<RepositoryMigrationPlan>> byMigrationContract = new LinkedHashMap<>();
        for (RepositoryMigrationPlan plan : plans) {
            List<String> readyReasons = repositoryMigrationReadyReasons(plan);
            List<String> missingReasons = repositoryMigrationMissingReasons(plan);
            String readiness = repositoryMigrationReady(plan)
                    ? "REPOSITORY_MIGRATION_READY"
                    : "REPOSITORY_MIGRATION_BLOCKED";
            addToIndex(byReadiness, readiness, plan);
            if (readyReasons.isEmpty()) {
                addToIndex(byReadyReason, "NO_READY_REASON", plan);
            } else {
                for (String reason : readyReasons) {
                    addToIndex(byReadyReason, reason, plan);
                }
            }
            if (missingReasons.isEmpty()) {
                addToIndex(byMissingReason, "NO_MISSING_REASON", plan);
                addToIndex(byAction, "NO_ACTION_REQUIRED", plan);
            } else {
                for (String reason : missingReasons) {
                    addToIndex(byMissingReason, reason, plan);
                }
                for (String action : repositoryMigrationActions(missingReasons)) {
                    addToIndex(byAction, action, plan);
                }
            }
            addToIndex(byRepository, indexKey(firstNonBlank(plan.resolvedRepositoryName(), plan.repositoryName())),
                    plan);
            addListIndexValues(byReplacementSource, plan.replacementSources(), plan, "NO_REPLACEMENT_SOURCE");
            addToIndex(byRecommendation, indexKey(plan.recommendation()), plan);
            addListIndexValues(byLookupKind, plan.repositoryLookupKinds(), plan, "NO_LOOKUP_KIND");
            addListIndexValues(byAggregateRelation, plan.aggregateRelationKinds(), plan, "NO_AGGREGATE_RELATION");
            addToIndex(byMigrationContract, repositoryMigrationContractKey(plan, readiness), plan);
        }
        return new RepositoryMigrationReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byRepository),
                immutableListMap(byReplacementSource),
                immutableListMap(byRecommendation),
                immutableListMap(byLookupKind),
                immutableListMap(byAggregateRelation),
                immutableListMap(byMigrationContract));
    }

    @JsonProperty(value = "repositoryMigrationPlansByReadyReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryMigrationPlan>> repositoryMigrationPlansByReadyReason() {
        return repositoryMigrationReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "repositoryMigrationPlansByMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryMigrationPlan>> repositoryMigrationPlansByMissingReason() {
        return repositoryMigrationReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "repositoryMigrationPlansByAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryMigrationPlan>> repositoryMigrationPlansByAction() {
        return repositoryMigrationReadinessIndex().byAction();
    }

    @JsonProperty(value = "repositoryMigrationPlansByMigrationContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RepositoryMigrationPlan>> repositoryMigrationPlansByMigrationContract() {
        return repositoryMigrationReadinessIndex().byMigrationContract();
    }

    @JsonProperty(value = "objectRepositoryReadinessIndex",
            access = JsonProperty.Access.READ_ONLY)
    public ObjectRepositoryReadinessIndex objectRepositoryReadinessIndex() {
        List<ObjectRepositoryPlan> plans = List.copyOf(safeList(objectRepositoryPlans));
        if (plans.isEmpty()) {
            return new ObjectRepositoryReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<ObjectRepositoryPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<ObjectRepositoryPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<ObjectRepositoryPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<ObjectRepositoryPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<ObjectRepositoryPlan>> byService = new LinkedHashMap<>();
        Map<String, List<ObjectRepositoryPlan>> byRepository = new LinkedHashMap<>();
        Map<String, List<ObjectRepositoryPlan>> byObjectType = new LinkedHashMap<>();
        Map<String, List<ObjectRepositoryPlan>> byRole = new LinkedHashMap<>();
        Map<String, List<ObjectRepositoryPlan>> byKeySource = new LinkedHashMap<>();
        Map<String, List<ObjectRepositoryPlan>> byObjectKeyColumnSet = new LinkedHashMap<>();
        Map<String, List<ObjectRepositoryPlan>> byStorageTable = new LinkedHashMap<>();
        Map<String, List<ObjectRepositoryPlan>> byRuntimeContract = new LinkedHashMap<>();
        for (ObjectRepositoryPlan plan : plans) {
            addToIndex(byReadiness, indexKey(plan.readiness()), plan);
            addListIndexValues(byReadyReason, plan.readyReasons(), plan, "NO_READY_REASON");
            addListIndexValues(byMissingReason, plan.missingReasons(), plan, "NO_MISSING_REASON");
            addListIndexValues(byAction, plan.actions(), plan, "NO_ACTION_REQUIRED");
            addToIndex(byService, indexKey(plan.serviceName()), plan);
            addToIndex(byRepository, indexKey(plan.resolvedRepositoryName()), plan);
            addToIndex(byObjectType, indexKey(plan.objectType()), plan);
            addListIndexValues(byRole, objectRepositoryRoles(plan), plan, "UNUSED_REPOSITORY_PLAN");
            addListIndexValues(byKeySource, plan.keySources(), plan, "NO_KEY_SOURCE");
            addToIndex(byObjectKeyColumnSet, objectRepositoryKeyColumnSetKey(plan), plan);
            addToIndex(byStorageTable, indexKey(plan.storageTableName()), plan);
            addToIndex(byRuntimeContract, indexKey(plan.runtimeContract()), plan);
        }
        return new ObjectRepositoryReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byService),
                immutableListMap(byRepository),
                immutableListMap(byObjectType),
                immutableListMap(byRole),
                immutableListMap(byKeySource),
                immutableListMap(byObjectKeyColumnSet),
                immutableListMap(byStorageTable),
                immutableListMap(byRuntimeContract));
    }

    @JsonProperty(value = "objectRepositoryPlansByReadyReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectRepositoryPlan>> objectRepositoryPlansByReadyReason() {
        return objectRepositoryReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "objectRepositoryPlansByMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectRepositoryPlan>> objectRepositoryPlansByMissingReason() {
        return objectRepositoryReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "objectRepositoryPlansByAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectRepositoryPlan>> objectRepositoryPlansByAction() {
        return objectRepositoryReadinessIndex().byAction();
    }

    @JsonProperty(value = "objectRepositoryPlansByObjectKeyColumnSet",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectRepositoryPlan>> objectRepositoryPlansByObjectKeyColumnSet() {
        return objectRepositoryReadinessIndex().byObjectKeyColumnSet();
    }

    @JsonProperty(value = "objectRepositoryPlansByRuntimeContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ObjectRepositoryPlan>> objectRepositoryPlansByRuntimeContract() {
        return objectRepositoryReadinessIndex().byRuntimeContract();
    }

    @JsonProperty(value = "physicalRepositoryReadinessIndex",
            access = JsonProperty.Access.READ_ONLY)
    public PhysicalRepositoryReadinessIndex physicalRepositoryReadinessIndex() {
        List<PhysicalRepositoryPlan> plans = List.copyOf(safeList(physicalRepositoryPlans));
        if (plans.isEmpty()) {
            return new PhysicalRepositoryReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<PhysicalRepositoryPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<PhysicalRepositoryPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<PhysicalRepositoryPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<PhysicalRepositoryPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<PhysicalRepositoryPlan>> byRepository = new LinkedHashMap<>();
        Map<String, List<PhysicalRepositoryPlan>> byAdapterRole = new LinkedHashMap<>();
        Map<String, List<PhysicalRepositoryPlan>> byAppDdlTable = new LinkedHashMap<>();
        Map<String, List<PhysicalRepositoryPlan>> byStorageTable = new LinkedHashMap<>();
        Map<String, List<PhysicalRepositoryPlan>> byDatasource = new LinkedHashMap<>();
        Map<String, List<PhysicalRepositoryPlan>> byRepositoryContract = new LinkedHashMap<>();
        Map<String, List<PhysicalRepositoryPlan>> byRuntimeContract = new LinkedHashMap<>();
        for (PhysicalRepositoryPlan plan : plans) {
            addToIndex(byReadiness, indexKey(plan.readiness()), plan);
            addListIndexValues(byReadyReason, plan.readyReasons(), plan, "NO_READY_REASON");
            addListIndexValues(byMissingReason, plan.missingReasons(), plan, "NO_MISSING_REASON");
            addListIndexValues(byAction, plan.actions(), plan, "NO_ACTION_REQUIRED");
            addToIndex(byRepository, indexKey(plan.repositoryName()), plan);
            addToIndex(byAdapterRole, indexKey(plan.adapterRole()), plan);
            addListIndexValues(byAppDdlTable, plan.appDdlTableNames(), plan, "NO_APP_DDL_TABLE");
            addToIndex(byStorageTable, indexKey(plan.storageTableName()), plan);
            addToIndex(byDatasource, indexKey(plan.datasourceUrl()), plan);
            addToIndex(byRepositoryContract, indexKey(plan.repositoryContract()), plan);
            addToIndex(byRuntimeContract, indexKey(plan.runtimeContract()), plan);
        }
        return new PhysicalRepositoryReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byRepository),
                immutableListMap(byAdapterRole),
                immutableListMap(byAppDdlTable),
                immutableListMap(byStorageTable),
                immutableListMap(byDatasource),
                immutableListMap(byRepositoryContract),
                immutableListMap(byRuntimeContract));
    }

    @JsonProperty(value = "physicalRepositoryPlansByReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalRepositoryPlan>> physicalRepositoryPlansByReadiness() {
        return physicalRepositoryReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "physicalRepositoryPlansByReadyReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalRepositoryPlan>> physicalRepositoryPlansByReadyReason() {
        return physicalRepositoryReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "physicalRepositoryPlansByMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalRepositoryPlan>> physicalRepositoryPlansByMissingReason() {
        return physicalRepositoryReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "physicalRepositoryPlansByAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalRepositoryPlan>> physicalRepositoryPlansByAction() {
        return physicalRepositoryReadinessIndex().byAction();
    }

    @JsonProperty(value = "physicalRepositoryPlansByAppDdlTable",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalRepositoryPlan>> physicalRepositoryPlansByAppDdlTable() {
        return physicalRepositoryReadinessIndex().byAppDdlTable();
    }

    @JsonProperty(value = "physicalRepositoryPlansByStorageTable",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalRepositoryPlan>> physicalRepositoryPlansByStorageTable() {
        return physicalRepositoryReadinessIndex().byStorageTable();
    }

    @JsonProperty(value = "physicalRepositoryPlansByDatasource",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalRepositoryPlan>> physicalRepositoryPlansByDatasource() {
        return physicalRepositoryReadinessIndex().byDatasource();
    }

    @JsonProperty(value = "physicalRepositoryPlansByRepositoryContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalRepositoryPlan>> physicalRepositoryPlansByRepositoryContract() {
        return physicalRepositoryReadinessIndex().byRepositoryContract();
    }

    @JsonProperty(value = "physicalRepositoryPlansByRuntimeContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalRepositoryPlan>> physicalRepositoryPlansByRuntimeContract() {
        return physicalRepositoryReadinessIndex().byRuntimeContract();
    }

    @JsonProperty(value = "physicalBackendReadinessIndex",
            access = JsonProperty.Access.READ_ONLY)
    public PhysicalBackendReadinessIndex physicalBackendReadinessIndex() {
        List<PhysicalBackendPlan> plans = safeList(physicalBackendPlans).stream()
                .filter(plan -> plan != null)
                .map(GeneratedServiceCatalog::enrichPhysicalBackendPlan)
                .toList();
        if (plans.isEmpty()) {
            return new PhysicalBackendReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of());
        }
        Map<String, List<PhysicalBackendPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<PhysicalBackendPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<PhysicalBackendPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<PhysicalBackendPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<PhysicalBackendPlan>> byBackend = new LinkedHashMap<>();
        Map<String, List<PhysicalBackendPlan>> byImplementationType = new LinkedHashMap<>();
        Map<String, List<PhysicalBackendPlan>> byTransaction = new LinkedHashMap<>();
        Map<String, List<PhysicalBackendPlan>> byDatasource = new LinkedHashMap<>();
        Map<String, List<PhysicalBackendPlan>> byQuantumStore = new LinkedHashMap<>();
        Map<String, List<PhysicalBackendPlan>> byQuantumDatasourceCoordinationReadiness =
                new LinkedHashMap<>();
        Map<String, List<PhysicalBackendPlan>> byQuantumDatasourceCoordinationMissingReason =
                new LinkedHashMap<>();
        Map<String, List<PhysicalBackendPlan>> byQuantumDatasourceCoordinationAction =
                new LinkedHashMap<>();
        Map<String, List<PhysicalBackendPlan>> byQuantumDatasourceCoordinationContract =
                new LinkedHashMap<>();
        Map<String, List<PhysicalBackendPlan>> byBackendContract = new LinkedHashMap<>();
        Map<String, List<PhysicalBackendPlan>> byRuntimeContract = new LinkedHashMap<>();
        for (PhysicalBackendPlan plan : plans) {
            List<String> readyReasons = safeList(plan.readyReasons());
            List<String> missingReasons = safeList(plan.missingReasons());
            addToIndex(byReadiness, indexKey(plan.readiness()), plan);
            if (readyReasons.isEmpty()) {
                addToIndex(byReadyReason, "NO_READY_REASON", plan);
            } else {
                for (String reason : readyReasons) {
                    addToIndex(byReadyReason, reason, plan);
                }
            }
            for (String reason : missingReasons) {
                addToIndex(byMissingReason, indexKey(reason), plan);
            }
            for (String action : safeList(plan.actions())) {
                addToIndex(byAction, indexKey(action), plan);
            }
            addToIndex(byBackend, indexKey(plan.backendName()), plan);
            addToIndex(byImplementationType, indexKey(plan.adapterImplementationType()), plan);
            addListIndexValues(byTransaction, plan.supportedTransactions(), plan, "NO_TRANSACTION_SUPPORT");
            addToIndex(byDatasource, indexKey(plan.datasourceUrl()), plan);
            addToIndex(byQuantumStore, indexKey(plan.quantumStoreName()), plan);
            addToIndex(byQuantumDatasourceCoordinationReadiness,
                    indexKey(plan.quantumDatasourceCoordinationReadiness()), plan);
            addToIndex(byQuantumDatasourceCoordinationMissingReason,
                    indexKey(plan.quantumDatasourceCoordinationMissingReason()), plan);
            addToIndex(byQuantumDatasourceCoordinationAction,
                    indexKey(plan.quantumDatasourceCoordinationAction()), plan);
            addToIndex(byQuantumDatasourceCoordinationContract,
                    indexKey(plan.quantumDatasourceCoordinationContract()), plan);
            addToIndex(byBackendContract, indexKey(plan.backendContract()), plan);
            addToIndex(byRuntimeContract, indexKey(plan.runtimeContract()), plan);
        }
        return new PhysicalBackendReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byBackend),
                immutableListMap(byImplementationType),
                immutableListMap(byTransaction),
                immutableListMap(byDatasource),
                immutableListMap(byQuantumStore),
                immutableListMap(byQuantumDatasourceCoordinationReadiness),
                immutableListMap(byQuantumDatasourceCoordinationMissingReason),
                immutableListMap(byQuantumDatasourceCoordinationAction),
                immutableListMap(byQuantumDatasourceCoordinationContract),
                immutableListMap(byBackendContract),
                immutableListMap(byRuntimeContract));
    }

    @JsonProperty(value = "physicalBackendPlansByReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalBackendPlan>> physicalBackendPlansByReadiness() {
        return physicalBackendReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "physicalBackendPlansByReadyReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalBackendPlan>> physicalBackendPlansByReadyReason() {
        return physicalBackendReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "physicalBackendPlansByMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalBackendPlan>> physicalBackendPlansByMissingReason() {
        return physicalBackendReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "physicalBackendPlansByAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalBackendPlan>> physicalBackendPlansByAction() {
        return physicalBackendReadinessIndex().byAction();
    }

    @JsonProperty(value = "physicalBackendPlansByQuantumStore",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalBackendPlan>> physicalBackendPlansByQuantumStore() {
        return physicalBackendReadinessIndex().byQuantumStore();
    }

    @JsonProperty(value = "physicalBackendPlansByQuantumDatasourceCoordinationReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalBackendPlan>> physicalBackendPlansByQuantumDatasourceCoordinationReadiness() {
        return physicalBackendReadinessIndex().byQuantumDatasourceCoordinationReadiness();
    }

    @JsonProperty(value = "physicalBackendPlansByQuantumDatasourceCoordinationMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalBackendPlan>> physicalBackendPlansByQuantumDatasourceCoordinationMissingReason() {
        return physicalBackendReadinessIndex().byQuantumDatasourceCoordinationMissingReason();
    }

    @JsonProperty(value = "physicalBackendPlansByQuantumDatasourceCoordinationAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalBackendPlan>> physicalBackendPlansByQuantumDatasourceCoordinationAction() {
        return physicalBackendReadinessIndex().byQuantumDatasourceCoordinationAction();
    }

    @JsonProperty(value = "physicalBackendPlansByQuantumDatasourceCoordinationContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalBackendPlan>> physicalBackendPlansByQuantumDatasourceCoordinationContract() {
        return physicalBackendReadinessIndex().byQuantumDatasourceCoordinationContract();
    }

    @JsonProperty(value = "physicalBackendPlansByBackendContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalBackendPlan>> physicalBackendPlansByBackendContract() {
        return physicalBackendReadinessIndex().byBackendContract();
    }

    @JsonProperty(value = "physicalBackendPlansByRuntimeContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalBackendPlan>> physicalBackendPlansByRuntimeContract() {
        return physicalBackendReadinessIndex().byRuntimeContract();
    }

    @JsonProperty(value = "physicalBackendAssignmentsByBindingScope", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalBackendAssignment>> physicalBackendAssignmentsByBindingScope() {
        return physicalBackendAssignmentsBySingleKey(PhysicalBackendAssignment::bindingScope);
    }

    @JsonProperty(value = "physicalBackendAssignmentsByResolution", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalBackendAssignment>> physicalBackendAssignmentsByResolution() {
        return physicalBackendAssignmentsBySingleKey(assignment -> assignment.resolved()
                ? "RESOLVED"
                : "UNRESOLVED");
    }

    @JsonProperty(value = "physicalBackendAssignmentsByAdapterReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalBackendAssignment>> physicalBackendAssignmentsByAdapterReadiness() {
        return physicalBackendAssignmentsBySingleKey(assignment -> assignment.adapterAvailable()
                ? "ADAPTER_AVAILABLE"
                : "ADAPTER_UNAVAILABLE");
    }

    @JsonProperty(value = "physicalBackendAssignmentsByTransactionSupport", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalBackendAssignment>> physicalBackendAssignmentsByTransactionSupport() {
        return physicalBackendAssignmentsBySingleKey(assignment -> assignment.adapterSupportsTransaction()
                ? "TRANSACTION_SUPPORTED"
                : "TRANSACTION_UNSUPPORTED");
    }

    @JsonProperty(value = "physicalBackendAssignmentsByExecutionContract", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalBackendAssignment>> physicalBackendAssignmentsByExecutionContract() {
        return physicalBackendAssignmentsBySingleKey(GeneratedServiceCatalog::physicalBackendAssignmentExecutionContract);
    }

    private Map<String, List<PhysicalBackendAssignment>> physicalBackendAssignmentsBySingleKey(
            Function<PhysicalBackendAssignment, String> keyMapper) {
        Map<String, List<PhysicalBackendAssignment>> assignmentsByKey = new LinkedHashMap<>();
        for (PhysicalBackendAssignment assignment : safeList(physicalBackendAssignments)) {
            if (assignment == null) {
                continue;
            }
            addToIndex(assignmentsByKey, indexKey(keyMapper.apply(assignment)), assignment);
        }
        return immutableListMap(assignmentsByKey);
    }

    private static String physicalBackendAssignmentExecutionContract(PhysicalBackendAssignment assignment) {
        if (assignment == null) {
            return "UNASSIGNED";
        }
        return indexKey(assignment.serviceName())
                + ":" + indexKey(assignment.transactionKind())
                + ":" + indexKey(assignment.targetName())
                + "->" + indexKey(assignment.backendName())
                + "+" + indexKey(assignment.adapterImplementationType())
                + "+" + indexKey(assignment.bindingScope())
                + "+" + (assignment.resolved() ? "RESOLVED" : "UNRESOLVED")
                + "+" + (assignment.adapterAvailable() ? "ADAPTER_AVAILABLE" : "ADAPTER_UNAVAILABLE")
                + "+" + (assignment.adapterSupportsTransaction()
                        ? "TRANSACTION_SUPPORTED"
                        : "TRANSACTION_UNSUPPORTED");
    }

    @JsonProperty(value = "physicalRemoteReadinessIndex",
            access = JsonProperty.Access.READ_ONLY)
    public PhysicalRemoteReadinessIndex physicalRemoteReadinessIndex() {
        List<PhysicalRemotePlan> plans = List.copyOf(safeList(physicalRemotePlans));
        if (plans.isEmpty()) {
            return new PhysicalRemoteReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<PhysicalRemotePlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<PhysicalRemotePlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<PhysicalRemotePlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<PhysicalRemotePlan>> byAction = new LinkedHashMap<>();
        Map<String, List<PhysicalRemotePlan>> byService = new LinkedHashMap<>();
        Map<String, List<PhysicalRemotePlan>> byAdapterName = new LinkedHashMap<>();
        Map<String, List<PhysicalRemotePlan>> byAdapterRole = new LinkedHashMap<>();
        Map<String, List<PhysicalRemotePlan>> byEndpoint = new LinkedHashMap<>();
        Map<String, List<PhysicalRemotePlan>> byRepository = new LinkedHashMap<>();
        Map<String, List<PhysicalRemotePlan>> byImplementationKind = new LinkedHashMap<>();
        Map<String, List<PhysicalRemotePlan>> byImplementationContract = new LinkedHashMap<>();
        Map<String, List<PhysicalRemotePlan>> byRuntimeContract = new LinkedHashMap<>();
        for (PhysicalRemotePlan plan : plans) {
            addToIndex(byReadiness, indexKey(plan.readiness()), plan);
            addListIndexValues(byReadyReason, plan.readyReasons(), plan, "NO_READY_REASON");
            addListIndexValues(byMissingReason, plan.missingReasons(), plan, "NO_MISSING_REASON");
            addListIndexValues(byAction, plan.actions(), plan, "NO_ACTION_REQUIRED");
            addToIndex(byService, indexKey(plan.serviceName()), plan);
            addToIndex(byAdapterName, indexKey(plan.adapterName()), plan);
            addToIndex(byAdapterRole, indexKey(plan.adapterRole()), plan);
            addToIndex(byEndpoint, endpointIndexKey(plan.endpoint()), plan);
            addToIndex(byRepository, indexKey(plan.repositoryName()), plan);
            addToIndex(byImplementationKind, physicalRemoteImplementationKind(plan), plan);
            addToIndex(byImplementationContract, indexKey(plan.implementationContract()), plan);
            addToIndex(byRuntimeContract, indexKey(plan.runtimeContract()), plan);
        }
        return new PhysicalRemoteReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byService),
                immutableListMap(byAdapterName),
                immutableListMap(byAdapterRole),
                immutableListMap(byEndpoint),
                immutableListMap(byRepository),
                immutableListMap(byImplementationKind),
                immutableListMap(byImplementationContract),
                immutableListMap(byRuntimeContract));
    }

    @JsonProperty(value = "physicalRemotePlansByReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalRemotePlan>> physicalRemotePlansByReadiness() {
        return physicalRemoteReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "physicalRemotePlansByReadyReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalRemotePlan>> physicalRemotePlansByReadyReason() {
        return physicalRemoteReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "physicalRemotePlansByMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalRemotePlan>> physicalRemotePlansByMissingReason() {
        return physicalRemoteReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "physicalRemotePlansByAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalRemotePlan>> physicalRemotePlansByAction() {
        return physicalRemoteReadinessIndex().byAction();
    }

    @JsonProperty(value = "physicalRemotePlansByImplementationContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalRemotePlan>> physicalRemotePlansByImplementationContract() {
        return physicalRemoteReadinessIndex().byImplementationContract();
    }

    @JsonProperty(value = "physicalRemotePlansByRuntimeContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalRemotePlan>> physicalRemotePlansByRuntimeContract() {
        return physicalRemoteReadinessIndex().byRuntimeContract();
    }

    @JsonProperty(value = "physicalQuantumReadinessIndex",
            access = JsonProperty.Access.READ_ONLY)
    public PhysicalQuantumReadinessIndex physicalQuantumReadinessIndex() {
        List<PhysicalQuantumPlan> plans = safeList(physicalQuantumPlans).stream()
                .filter(plan -> plan != null)
                .map(GeneratedServiceCatalog::enrichPhysicalQuantumPlan)
                .toList();
        if (plans.isEmpty()) {
            return new PhysicalQuantumReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<PhysicalQuantumPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<PhysicalQuantumPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<PhysicalQuantumPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<PhysicalQuantumPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<PhysicalQuantumPlan>> byStore = new LinkedHashMap<>();
        Map<String, List<PhysicalQuantumPlan>> byImplementationStrategy = new LinkedHashMap<>();
        Map<String, List<PhysicalQuantumPlan>> byAccessMode = new LinkedHashMap<>();
        Map<String, List<PhysicalQuantumPlan>> byServiceProtocol = new LinkedHashMap<>();
        Map<String, List<PhysicalQuantumPlan>> byIdempotencyOwner = new LinkedHashMap<>();
        Map<String, List<PhysicalQuantumPlan>> byReversalOwner = new LinkedHashMap<>();
        Map<String, List<PhysicalQuantumPlan>> byImplementationContract = new LinkedHashMap<>();
        Map<String, List<PhysicalQuantumPlan>> byRuntimeContract = new LinkedHashMap<>();
        Map<String, List<PhysicalQuantumPlan>> byExternalImplementationReadiness = new LinkedHashMap<>();
        Map<String, List<PhysicalQuantumPlan>> byExternalImplementationReason = new LinkedHashMap<>();
        Map<String, List<PhysicalQuantumPlan>> byEndpoint = new LinkedHashMap<>();
        Map<String, List<PhysicalQuantumPlan>> byTimeoutMillis = new LinkedHashMap<>();
        Map<String, List<PhysicalQuantumPlan>> byDatasource = new LinkedHashMap<>();
        Map<String, List<PhysicalQuantumPlan>> byBackend = new LinkedHashMap<>();
        for (PhysicalQuantumPlan plan : plans) {
            List<String> readyReasons = safeList(plan.readyReasons());
            List<String> missingReasons = safeList(plan.missingReasons());
            addToIndex(byReadiness, indexKey(plan.readiness()), plan);
            if (readyReasons.isEmpty()) {
                addToIndex(byReadyReason, "NO_READY_REASON", plan);
            } else {
                for (String reason : readyReasons) {
                    addToIndex(byReadyReason, reason, plan);
                }
            }
            for (String reason : missingReasons) {
                addToIndex(byMissingReason, indexKey(reason), plan);
            }
            for (String action : safeList(plan.actions())) {
                addToIndex(byAction, indexKey(action), plan);
            }
            addToIndex(byStore, indexKey(plan.storeName()), plan);
            addToIndex(byImplementationStrategy, indexKey(plan.implementationStrategy()), plan);
            addToIndex(byAccessMode, indexKey(plan.accessMode()), plan);
            addToIndex(byServiceProtocol, indexKey(plan.serviceProtocol()), plan);
            addToIndex(byIdempotencyOwner, indexKey(plan.idempotencyOwner()), plan);
            addToIndex(byReversalOwner, indexKey(plan.reversalOwner()), plan);
            addToIndex(byImplementationContract, indexKey(plan.implementationContract()), plan);
            addToIndex(byRuntimeContract, indexKey(plan.runtimeContract()), plan);
            if (isQuantumServicePlan(plan)) {
                addToIndex(byExternalImplementationReadiness,
                        quantumServiceExternalImplementationReady(plan)
                                ? "PRODUCTION_READY"
                                : "CONTRACT_ONLY",
                        plan);
                addToIndex(byExternalImplementationReason,
                        quantumServiceExternalImplementationReason(plan), plan);
            }
            addToIndex(byEndpoint, endpointIndexKey(plan.endpoint()), plan);
            addToIndex(byTimeoutMillis, isBlank(plan.timeoutMillis()) ? "NO_TIMEOUT" : plan.timeoutMillis(), plan);
            addToIndex(byDatasource, indexKey(plan.datasourceUrl()), plan);
            addListIndexValues(byBackend, plan.backendNames(), plan, "NO_BACKEND_BINDING");
        }
        return new PhysicalQuantumReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byStore),
                immutableListMap(byImplementationStrategy),
                immutableListMap(byAccessMode),
                immutableListMap(byServiceProtocol),
                immutableListMap(byIdempotencyOwner),
                immutableListMap(byReversalOwner),
                immutableListMap(byImplementationContract),
                immutableListMap(byRuntimeContract),
                immutableListMap(byExternalImplementationReadiness),
                immutableListMap(byExternalImplementationReason),
                immutableListMap(byEndpoint),
                immutableListMap(byTimeoutMillis),
                immutableListMap(byDatasource),
                immutableListMap(byBackend));
    }

    @JsonProperty(value = "physicalQuantumPlansByIdempotencyOwner",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalQuantumPlan>> physicalQuantumPlansByIdempotencyOwner() {
        return physicalQuantumReadinessIndex().byIdempotencyOwner();
    }

    @JsonProperty(value = "physicalQuantumPlansByServiceProtocol",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalQuantumPlan>> physicalQuantumPlansByServiceProtocol() {
        return physicalQuantumReadinessIndex().byServiceProtocol();
    }

    @JsonProperty(value = "physicalQuantumPlansByReversalOwner",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalQuantumPlan>> physicalQuantumPlansByReversalOwner() {
        return physicalQuantumReadinessIndex().byReversalOwner();
    }

    @JsonProperty(value = "physicalQuantumPlansByImplementationContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalQuantumPlan>> physicalQuantumPlansByImplementationContract() {
        return physicalQuantumReadinessIndex().byImplementationContract();
    }

    @JsonProperty(value = "physicalQuantumPlansByRuntimeContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalQuantumPlan>> physicalQuantumPlansByRuntimeContract() {
        return physicalQuantumReadinessIndex().byRuntimeContract();
    }

    @JsonProperty(value = "physicalQuantumPlansByTimeoutMillis",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalQuantumPlan>> physicalQuantumPlansByTimeoutMillis() {
        return physicalQuantumReadinessIndex().byTimeoutMillis();
    }

    @JsonProperty(value = "physicalQuantumPlansByReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalQuantumPlan>> physicalQuantumPlansByReadiness() {
        return physicalQuantumReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "physicalQuantumPlansByReadyReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalQuantumPlan>> physicalQuantumPlansByReadyReason() {
        return physicalQuantumReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "physicalQuantumPlansByMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalQuantumPlan>> physicalQuantumPlansByMissingReason() {
        return physicalQuantumReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "physicalQuantumPlansByAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalQuantumPlan>> physicalQuantumPlansByAction() {
        return physicalQuantumReadinessIndex().byAction();
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionReadinessIndex",
            access = JsonProperty.Access.READ_ONLY)
    public TransactionSnapshotRetentionExecutionReadinessIndex
            transactionSnapshotRetentionExecutionReadinessIndex() {
        List<TransactionSnapshotRetentionExecutionPlan> plans = transactionSnapshotRetentionExecutionPlans();
        if (plans.isEmpty()) {
            return new TransactionSnapshotRetentionExecutionReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byResource = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byRepository = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byAutomationPlan = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> bySchedulerPrefix = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byRetentionSource = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byArchiveRetentionSource =
                new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byArchiveOperation = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byPruneArchiveOperation = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byStorageTier = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byExecutionContract =
                new LinkedHashMap<>();
        for (TransactionSnapshotRetentionExecutionPlan plan : plans) {
            addToIndex(byReadiness, indexKey(plan.readiness()), plan);
            if (safeList(plan.readyReasons()).isEmpty()) {
                addToIndex(byReadyReason, "NO_READY_REASON", plan);
            }
            for (String reason : safeList(plan.readyReasons())) {
                addToIndex(byReadyReason, indexKey(reason), plan);
            }
            for (String reason : safeList(plan.missingReasons())) {
                addToIndex(byMissingReason, indexKey(reason), plan);
            }
            for (String action : safeList(plan.actions())) {
                addToIndex(byAction, indexKey(action), plan);
            }
            addToIndex(byResource, indexKey(plan.resourceName()), plan);
            addToIndex(byRepository,
                    indexKey(firstNonBlank(plan.resolvedRepositoryName(), plan.repositoryName())),
                    plan);
            addToIndex(byAutomationPlan, indexKey(plan.automationPlanName()), plan);
            addToIndex(bySchedulerPrefix, indexKey(plan.schedulerPropertyPrefix()), plan);
            addToIndex(byRetentionSource, indexKey(plan.cutoffSource()), plan);
            if ("defaultRetentionMillis".equals(plan.cutoffSource())) {
                addToIndex(byRetentionSource, "repositoryDefaultRetentionMillis", plan);
            }
            addToIndex(byArchiveRetentionSource, indexKey(plan.archiveCutoffSource()), plan);
            addToIndex(byArchiveOperation, indexKey(plan.archiveRuntimeOperation()), plan);
            addToIndex(byPruneArchiveOperation, indexKey(plan.pruneArchiveRuntimeOperation()), plan);
            addToIndex(byStorageTier, retentionExecutionStorageTierKey(plan), plan);
            addToIndex(byExecutionContract, indexKey(plan.executionContract()), plan);
        }
        return new TransactionSnapshotRetentionExecutionReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byResource),
                immutableListMap(byRepository),
                immutableListMap(byAutomationPlan),
                immutableListMap(bySchedulerPrefix),
                immutableListMap(byRetentionSource),
                immutableListMap(byArchiveRetentionSource),
                immutableListMap(byArchiveOperation),
                immutableListMap(byPruneArchiveOperation),
                immutableListMap(byStorageTier),
                immutableListMap(byExecutionContract));
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionPlans")
    public List<TransactionSnapshotRetentionExecutionPlan> transactionSnapshotRetentionExecutionPlans() {
        return safeList(transactionSnapshotRetentionExecutionPlans).stream()
                .filter(plan -> plan != null)
                .map(GeneratedServiceCatalog::enrichTransactionSnapshotRetentionExecutionPlan)
                .toList();
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionPlansByPlan")
    public Map<String, TransactionSnapshotRetentionExecutionPlan> transactionSnapshotRetentionExecutionPlansByPlan() {
        Map<String, TransactionSnapshotRetentionExecutionPlan> byPlan = new LinkedHashMap<>();
        for (TransactionSnapshotRetentionExecutionPlan plan : transactionSnapshotRetentionExecutionPlans()) {
            byPlan.putIfAbsent(indexKey(plan.planName()), plan);
        }
        return Map.copyOf(byPlan);
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionPlansByService")
    public Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
            transactionSnapshotRetentionExecutionPlansByService() {
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byService = new LinkedHashMap<>();
        for (TransactionSnapshotRetentionExecutionPlan plan : transactionSnapshotRetentionExecutionPlans()) {
            addToIndex(byService, indexKey(plan.serviceName()), plan);
        }
        return immutableListMap(byService);
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionPlansByResource")
    public Map<String, TransactionSnapshotRetentionExecutionPlan>
            transactionSnapshotRetentionExecutionPlansByResource() {
        Map<String, TransactionSnapshotRetentionExecutionPlan> byResource = new LinkedHashMap<>();
        for (TransactionSnapshotRetentionExecutionPlan plan : transactionSnapshotRetentionExecutionPlans()) {
            byResource.putIfAbsent(indexKey(plan.resourceName()), plan);
        }
        return Map.copyOf(byResource);
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionPlansByRetentionWindow")
    public Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
            transactionSnapshotRetentionExecutionPlansByRetentionWindow() {
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byWindow = new LinkedHashMap<>();
        for (TransactionSnapshotRetentionExecutionPlan plan : transactionSnapshotRetentionExecutionPlans()) {
            addToIndex(byWindow, plan.retentionMillis() + "ms", plan);
        }
        return immutableListMap(byWindow);
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionPlansByArchiveRetentionWindow")
    public Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
            transactionSnapshotRetentionExecutionPlansByArchiveRetentionWindow() {
        Map<String, List<TransactionSnapshotRetentionExecutionPlan>> byWindow = new LinkedHashMap<>();
        for (TransactionSnapshotRetentionExecutionPlan plan : transactionSnapshotRetentionExecutionPlans()) {
            addToIndex(byWindow, plan.archiveRetentionMillis() + "ms", plan);
        }
        return immutableListMap(byWindow);
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionPlansByReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
            transactionSnapshotRetentionExecutionPlansByReadiness() {
        return transactionSnapshotRetentionExecutionReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionPlansByReadyReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
            transactionSnapshotRetentionExecutionPlansByReadyReason() {
        return transactionSnapshotRetentionExecutionReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionPlansByMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
            transactionSnapshotRetentionExecutionPlansByMissingReason() {
        return transactionSnapshotRetentionExecutionReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionPlansByAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
            transactionSnapshotRetentionExecutionPlansByAction() {
        return transactionSnapshotRetentionExecutionReadinessIndex().byAction();
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionPlansByRepository",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
            transactionSnapshotRetentionExecutionPlansByRepository() {
        return transactionSnapshotRetentionExecutionReadinessIndex().byRepository();
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionPlansByAutomationPlan",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
            transactionSnapshotRetentionExecutionPlansByAutomationPlan() {
        return transactionSnapshotRetentionExecutionReadinessIndex().byAutomationPlan();
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionPlansBySchedulerPrefix",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
            transactionSnapshotRetentionExecutionPlansBySchedulerPrefix() {
        return transactionSnapshotRetentionExecutionReadinessIndex().bySchedulerPrefix();
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionPlansByRetentionSource",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
            transactionSnapshotRetentionExecutionPlansByRetentionSource() {
        return transactionSnapshotRetentionExecutionReadinessIndex().byRetentionSource();
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionPlansByArchiveRetentionSource",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
            transactionSnapshotRetentionExecutionPlansByArchiveRetentionSource() {
        return transactionSnapshotRetentionExecutionReadinessIndex().byArchiveRetentionSource();
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionPlansByArchiveOperation",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
            transactionSnapshotRetentionExecutionPlansByArchiveOperation() {
        return transactionSnapshotRetentionExecutionReadinessIndex().byArchiveOperation();
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionPlansByPruneArchiveOperation",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
            transactionSnapshotRetentionExecutionPlansByPruneArchiveOperation() {
        return transactionSnapshotRetentionExecutionReadinessIndex().byPruneArchiveOperation();
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionPlansByStorageTier",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
            transactionSnapshotRetentionExecutionPlansByStorageTier() {
        return transactionSnapshotRetentionExecutionReadinessIndex().byStorageTier();
    }

    @JsonProperty(value = "transactionSnapshotRetentionExecutionPlansByExecutionContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotRetentionExecutionPlan>>
            transactionSnapshotRetentionExecutionPlansByExecutionContract() {
        return transactionSnapshotRetentionExecutionReadinessIndex().byExecutionContract();
    }

    @JsonProperty(value = "transactionSnapshotEvidenceReadinessIndex",
            access = JsonProperty.Access.READ_ONLY)
    public TransactionSnapshotEvidenceReadinessIndex transactionSnapshotEvidenceReadinessIndex() {
        List<TransactionSnapshotPlan> plans = safeList(transactionSnapshotPlans).stream()
                .filter(plan -> plan != null)
                .toList();
        if (plans.isEmpty()) {
            return new TransactionSnapshotEvidenceReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<TransactionSnapshotPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotPlan>> byService = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotPlan>> byResource = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotPlan>> byRepository = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotPlan>> byObjectType = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotPlan>> byEvidencePath = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotPlan>> byLatestTable = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotPlan>> byHistoryTable = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotPlan>> byArchiveTable = new LinkedHashMap<>();
        Map<String, List<TransactionSnapshotPlan>> byExecutionContract = new LinkedHashMap<>();
        for (TransactionSnapshotPlan plan : plans) {
            addToIndex(byReadiness, indexKey(plan.readiness()), plan);
            addListIndexValues(byReadyReason, plan.readyReasons(), plan, "NO_READY_REASON");
            addListIndexValues(byMissingReason, plan.missingReasons(), plan,
                    "NO_MISSING_TRANSACTION_SNAPSHOT_EVIDENCE_REASON");
            addListIndexValues(byAction, plan.actions(), plan, "NO_ACTION_REQUIRED");
            addToIndex(byService, indexKey(plan.serviceName()), plan);
            addToIndex(byResource, indexKey(plan.transactionResourceId()), plan);
            addToIndex(byRepository,
                    indexKey(firstNonBlank(plan.resolvedRepositoryName(), plan.repositoryName())),
                    plan);
            addToIndex(byObjectType, indexKey(plan.objectType()), plan);
            addToIndex(byEvidencePath, indexKey(plan.evidencePath()), plan);
            addToIndex(byLatestTable, indexKey(plan.storageTableName()), plan);
            addToIndex(byHistoryTable, indexKey(plan.transactionHistoryTableName()), plan);
            addToIndex(byArchiveTable, indexKey(plan.transactionArchiveTableName()), plan);
            addToIndex(byExecutionContract, indexKey(plan.executionContract()), plan);
        }
        return new TransactionSnapshotEvidenceReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byService),
                immutableListMap(byResource),
                immutableListMap(byRepository),
                immutableListMap(byObjectType),
                immutableListMap(byEvidencePath),
                immutableListMap(byLatestTable),
                immutableListMap(byHistoryTable),
                immutableListMap(byArchiveTable),
                immutableListMap(byExecutionContract));
    }

    @JsonProperty(value = "transactionSnapshotEvidencePlansByReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotPlan>> transactionSnapshotEvidencePlansByReadiness() {
        return transactionSnapshotEvidenceReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "transactionSnapshotEvidencePlansByReadyReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotPlan>> transactionSnapshotEvidencePlansByReadyReason() {
        return transactionSnapshotEvidenceReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "transactionSnapshotEvidencePlansByMissingReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotPlan>> transactionSnapshotEvidencePlansByMissingReason() {
        return transactionSnapshotEvidenceReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "transactionSnapshotEvidencePlansByAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotPlan>> transactionSnapshotEvidencePlansByAction() {
        return transactionSnapshotEvidenceReadinessIndex().byAction();
    }

    @JsonProperty(value = "transactionSnapshotEvidencePlansByService", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotPlan>> transactionSnapshotEvidencePlansByService() {
        return transactionSnapshotEvidenceReadinessIndex().byService();
    }

    @JsonProperty(value = "transactionSnapshotEvidencePlansByResource", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotPlan>> transactionSnapshotEvidencePlansByResource() {
        return transactionSnapshotEvidenceReadinessIndex().byResource();
    }

    @JsonProperty(value = "transactionSnapshotEvidencePlansByRepository", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotPlan>> transactionSnapshotEvidencePlansByRepository() {
        return transactionSnapshotEvidenceReadinessIndex().byRepository();
    }

    @JsonProperty(value = "transactionSnapshotEvidencePlansByObjectType", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotPlan>> transactionSnapshotEvidencePlansByObjectType() {
        return transactionSnapshotEvidenceReadinessIndex().byObjectType();
    }

    @JsonProperty(value = "transactionSnapshotEvidencePlansByEvidencePath", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotPlan>> transactionSnapshotEvidencePlansByEvidencePath() {
        return transactionSnapshotEvidenceReadinessIndex().byEvidencePath();
    }

    @JsonProperty(value = "transactionSnapshotEvidencePlansByLatestTable", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotPlan>> transactionSnapshotEvidencePlansByLatestTable() {
        return transactionSnapshotEvidenceReadinessIndex().byLatestTable();
    }

    @JsonProperty(value = "transactionSnapshotEvidencePlansByHistoryTable", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotPlan>> transactionSnapshotEvidencePlansByHistoryTable() {
        return transactionSnapshotEvidenceReadinessIndex().byHistoryTable();
    }

    @JsonProperty(value = "transactionSnapshotEvidencePlansByArchiveTable", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotPlan>> transactionSnapshotEvidencePlansByArchiveTable() {
        return transactionSnapshotEvidenceReadinessIndex().byArchiveTable();
    }

    @JsonProperty(value = "transactionSnapshotEvidencePlansByExecutionContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<TransactionSnapshotPlan>> transactionSnapshotEvidencePlansByExecutionContract() {
        return transactionSnapshotEvidenceReadinessIndex().byExecutionContract();
    }

    @JsonProperty(value = "transactionModes", access = JsonProperty.Access.READ_ONLY)
    public Map<String, TransactionMode> transactionModes() {
        Map<String, TransactionMode> byService = new java.util.LinkedHashMap<>();
        for (GeneratedServiceMetadata service : services) {
            if (service.transactionMode() != null) {
                byService.put(service.serviceName(), service.transactionMode());
            }
        }
        return Map.copyOf(byService);
    }

    @JsonProperty(value = "validationErrorsByCategory", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<String>> validationErrorsByCategory() {
        Map<String, List<String>> errors = new java.util.LinkedHashMap<>();
        errors.put("PHYSICAL_BACKEND", safeList(physicalBackendValidationErrors));
        errors.put("REMOTE_SERVICE", safeList(remoteServiceValidationErrors));
        errors.put("REMOTE_COMPENSATION", safeList(remoteCompensationValidationErrors));
        errors.put("SNAPSHOT", safeList(snapshotValidationErrors));
        errors.put("TCC_LIFECYCLE", safeList(tccLifecycleValidationErrors));
        errors.put("REPOSITORY", safeList(repositoryValidationErrors));
        errors.put("REPOSITORY_ADAPTER", safeList(repositoryAdapterValidationErrors));
        errors.put("QUANTUM_STORE", safeList(quantumStoreValidationErrors));
        errors.put("IDEMPOTENCY", safeList(idempotencyValidationErrors));
        errors.put("COMPENSATION", safeList(compensationValidationErrors));
        errors.put("DEPLOYMENT", safeList(deploymentValidationErrors));
        return Map.copyOf(errors);
    }

    @JsonProperty(value = "validationErrorCountsByCategory", access = JsonProperty.Access.READ_ONLY)
    public Map<String, Integer> validationErrorCountsByCategory() {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : validationErrorsByCategory().entrySet()) {
            counts.put(entry.getKey(), entry.getValue().size());
        }
        return Map.copyOf(counts);
    }

    @JsonProperty(value = "validationErrorCount", access = JsonProperty.Access.READ_ONLY)
    public int validationErrorCount() {
        return validationErrorCountsByCategory().values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    @JsonProperty(value = "validationErrorCategories", access = JsonProperty.Access.READ_ONLY)
    public List<String> validationErrorCategories() {
        return validationErrorsByCategory().entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();
    }

    @JsonProperty(value = "deploymentValidationPlansByReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DeploymentValidationPlan>> deploymentValidationPlansByReadiness() {
        Map<String, List<DeploymentValidationPlan>> byReadiness = new java.util.LinkedHashMap<>();
        for (DeploymentValidationPlan plan : deploymentValidationPlans) {
            String key = plan.passed()
                    ? "DEPLOYMENT_VALIDATION_PASSED"
                    : "DEPLOYMENT_VALIDATION_FAILED";
            byReadiness.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(plan);
        }
        return immutableListMap(byReadiness);
    }

    @JsonProperty(value = "deploymentValidationReadinessIndex", access = JsonProperty.Access.READ_ONLY)
    public DeploymentValidationReadinessIndex deploymentValidationReadinessIndex() {
        List<DeploymentValidationPlan> plans = safeList(deploymentValidationPlans).stream()
                .filter(plan -> plan != null)
                .toList();
        if (plans.isEmpty()) {
            return new DeploymentValidationReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<DeploymentValidationPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<DeploymentValidationPlan>> byCategory = new LinkedHashMap<>();
        Map<String, List<DeploymentValidationPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<DeploymentValidationPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<DeploymentValidationPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<String>> categoriesByMetadataKey = new LinkedHashMap<>();
        for (DeploymentValidationPlan plan : plans) {
            addToIndex(byCategory, indexKey(plan.category()), plan);
            String readiness = plan.passed()
                    ? "DEPLOYMENT_VALIDATION_PASSED"
                    : "DEPLOYMENT_VALIDATION_FAILED";
            addToIndex(byReadiness, readiness, plan);
            for (String metadataKey : plan.metadata().keySet()) {
                addToStringIndex(categoriesByMetadataKey, indexKey(metadataKey), indexKey(plan.category()));
            }
            if (plan.passed()) {
                addToIndex(byReadyReason, "categoryPassed", plan);
                addToIndex(byReadyReason, "noErrors", plan);
                addToIndex(byMissingReason, "NO_MISSING_DEPLOYMENT_VALIDATION_REASON", plan);
                addToIndex(byAction, "NO_ACTION_REQUIRED", plan);
            } else {
                addToIndex(byMissingReason, "categoryFailed", plan);
                for (String reason : deploymentValidationMissingReasons(plan)) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : deploymentValidationActions(plan)) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            }
        }
        return new DeploymentValidationReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byCategory),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableStringListMap(categoriesByMetadataKey));
    }

    @JsonProperty(value = "deploymentValidationReadinessPlansByReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DeploymentValidationPlan>> deploymentValidationReadinessPlansByReadiness() {
        return deploymentValidationReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "deploymentValidationReadinessPlansByCategory", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DeploymentValidationPlan>> deploymentValidationReadinessPlansByCategory() {
        return deploymentValidationReadinessIndex().byCategory();
    }

    @JsonProperty(value = "deploymentValidationReadinessPlansByReadyReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DeploymentValidationPlan>> deploymentValidationReadinessPlansByReadyReason() {
        return deploymentValidationReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "deploymentValidationReadinessPlansByMissingReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DeploymentValidationPlan>> deploymentValidationReadinessPlansByMissingReason() {
        return deploymentValidationReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "deploymentValidationReadinessPlansByAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<DeploymentValidationPlan>> deploymentValidationReadinessPlansByAction() {
        return deploymentValidationReadinessIndex().byAction();
    }

    @JsonProperty(value = "deploymentValidationCategoriesByMetadataKey", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<String>> deploymentValidationCategoriesByMetadataKey() {
        return deploymentValidationReadinessIndex().categoriesByMetadataKey();
    }

    @JsonProperty(value = "generatedAppSemanticMigrationPlansByType", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> generatedAppSemanticMigrationPlansByType() {
        return generatedAppSemanticMigrationPlanIndex().byType();
    }

    @JsonProperty(value = "generatedAppSemanticMigrationPlansByService", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> generatedAppSemanticMigrationPlansByService() {
        return generatedAppSemanticMigrationPlanIndex().byService();
    }

    @JsonProperty(value = "generatedAppSemanticMigrationPlansByRecommendation",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> generatedAppSemanticMigrationPlansByRecommendation() {
        return generatedAppSemanticMigrationPlanIndex().byRecommendation();
    }

    @JsonProperty(value = "generatedAppSemanticMigrationPlansByAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> generatedAppSemanticMigrationPlansByAction() {
        return generatedAppSemanticMigrationPlanIndex().byAction();
    }

    @JsonProperty(value = "generatedAppSemanticMigrationPlansByTargetKind",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> generatedAppSemanticMigrationPlansByTargetKind() {
        return generatedAppSemanticMigrationPlanIndex().byTargetKind();
    }

    @JsonProperty(value = "generatedAppSemanticMigrationPlansByAdapterRole",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> generatedAppSemanticMigrationPlansByAdapterRole() {
        return generatedAppSemanticMigrationPlanIndex().byAdapterRole();
    }

    @JsonProperty(value = "generatedAppSemanticMigrationPlansByParticipantCategory",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> generatedAppSemanticMigrationPlansByParticipantCategory() {
        return generatedAppSemanticMigrationPlanIndex().byParticipantCategory();
    }

    @JsonProperty(value = "generatedAppSemanticMigrationPlansByRoleOnlyReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> generatedAppSemanticMigrationPlansByRoleOnlyReadiness() {
        return generatedAppSemanticMigrationPlanIndex().byRoleOnlyReadiness();
    }

    @JsonProperty(value = "generatedAppSemanticMigrationPlansByPlatformPrimaryKeyIdentityKind",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>>
            generatedAppSemanticMigrationPlansByPlatformPrimaryKeyIdentityKind() {
        return generatedAppSemanticMigrationPlanIndex().byPlatformPrimaryKeyIdentityKind();
    }

    @JsonProperty(value = "generatedAppSemanticMigrationPlansByRemoteImplementationKind",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> generatedAppSemanticMigrationPlansByRemoteImplementationKind() {
        return generatedAppSemanticMigrationPlanIndex().byRemoteImplementationKind();
    }

    @JsonProperty(value = "generatedAppSemanticMigrationPlansByRemoteExternalReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> generatedAppSemanticMigrationPlansByRemoteExternalReadiness() {
        return generatedAppSemanticMigrationPlanIndex().byRemoteExternalReadiness();
    }

    @JsonProperty(value = "generatedAppSemanticMigrationPlansByMigrationContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> generatedAppSemanticMigrationPlansByMigrationContract() {
        return generatedAppSemanticMigrationPlanIndex().byMigrationContract();
    }

    @JsonProperty(value = "appSemanticMigrationPlansByType", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> appSemanticMigrationPlansByType() {
        return appSemanticMigrationPlanIndex().byType();
    }

    @JsonProperty(value = "appSemanticMigrationPlansByService", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> appSemanticMigrationPlansByService() {
        return appSemanticMigrationPlanIndex().byService();
    }

    @JsonProperty(value = "appSemanticMigrationPlansByRecommendation", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> appSemanticMigrationPlansByRecommendation() {
        return appSemanticMigrationPlanIndex().byRecommendation();
    }

    @JsonProperty(value = "appSemanticMigrationPlansByAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> appSemanticMigrationPlansByAction() {
        return appSemanticMigrationPlanIndex().byAction();
    }

    @JsonProperty(value = "appSemanticMigrationPlansByTargetKind", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> appSemanticMigrationPlansByTargetKind() {
        return appSemanticMigrationPlanIndex().byTargetKind();
    }

    @JsonProperty(value = "appSemanticMigrationPlansByAdapterRole", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> appSemanticMigrationPlansByAdapterRole() {
        return appSemanticMigrationPlanIndex().byAdapterRole();
    }

    @JsonProperty(value = "appSemanticMigrationPlansByParticipantCategory",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> appSemanticMigrationPlansByParticipantCategory() {
        return appSemanticMigrationPlanIndex().byParticipantCategory();
    }

    @JsonProperty(value = "appSemanticMigrationPlansByRoleOnlyReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> appSemanticMigrationPlansByRoleOnlyReadiness() {
        return appSemanticMigrationPlanIndex().byRoleOnlyReadiness();
    }

    @JsonProperty(value = "appSemanticMigrationPlansByPlatformPrimaryKeyIdentityKind",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> appSemanticMigrationPlansByPlatformPrimaryKeyIdentityKind() {
        return appSemanticMigrationPlanIndex().byPlatformPrimaryKeyIdentityKind();
    }

    @JsonProperty(value = "appSemanticMigrationPlansByRemoteImplementationKind",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> appSemanticMigrationPlansByRemoteImplementationKind() {
        return appSemanticMigrationPlanIndex().byRemoteImplementationKind();
    }

    @JsonProperty(value = "appSemanticMigrationPlansByRemoteExternalReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> appSemanticMigrationPlansByRemoteExternalReadiness() {
        return appSemanticMigrationPlanIndex().byRemoteExternalReadiness();
    }

    @JsonProperty(value = "appSemanticMigrationPlansByMigrationContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> appSemanticMigrationPlansByMigrationContract() {
        return appSemanticMigrationPlanIndex().byMigrationContract();
    }

    @JsonProperty(value = "generatedAppSemanticMigrationPlansBySuppressedReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> generatedAppSemanticMigrationPlansBySuppressedReason() {
        Map<String, List<AppSemanticMigrationPlan>> byReason = new java.util.LinkedHashMap<>();
        java.util.Set<String> externalRemoteServiceNames = new java.util.LinkedHashSet<>();
        java.util.Set<String> transactionSnapshotServiceNames = new java.util.LinkedHashSet<>();
        java.util.Set<String> closedCurrentContextKeys = new java.util.LinkedHashSet<>();
        for (AppSemanticMigrationPlan plan : safeList(appSemanticMigrationPlans)) {
            if (plan == null) {
                continue;
            }
            if ("REMOTE_EXTERNAL_ADAPTER".equals(plan.migrationType())
                    && plan.serviceName() != null && !plan.serviceName().isBlank()) {
                externalRemoteServiceNames.add(plan.serviceName());
            }
            if (closedCurrentSnapshotContext(plan)) {
                closedCurrentContextKeys.add(currentSnapshotContextKey(plan));
            }
        }
        for (TransactionSnapshotPlan plan : safeList(transactionSnapshotPlans)) {
            if (plan != null && plan.serviceName() != null && !plan.serviceName().isBlank()) {
                transactionSnapshotServiceNames.add(plan.serviceName());
            }
        }
        for (AppSemanticMigrationPlan plan : safeList(generatedAppSemanticMigrationPlans)) {
            String reason = suppressedGeneratedAppSemanticMigrationReason(plan, externalRemoteServiceNames,
                    transactionSnapshotServiceNames, closedCurrentContextKeys);
            if (!reason.isBlank()) {
                byReason.computeIfAbsent(reason, ignored -> new java.util.ArrayList<>()).add(plan);
            }
        }
        return immutableListMap(byReason);
    }

    @JsonProperty(value = "remainingAppSemanticMigrationPlansBySourceClass",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> remainingAppSemanticMigrationPlansBySourceClass() {
        Map<String, List<AppSemanticMigrationPlan>> bySourceClass = new java.util.LinkedHashMap<>();
        bySourceClass.put("runtimeOnly", new java.util.ArrayList<>());
        bySourceClass.put("activeGeneratedOnly", new java.util.ArrayList<>());
        bySourceClass.put("runtimeAndActiveGenerated", new java.util.ArrayList<>());
        Map<String, AppSemanticMigrationPlan> plansByKey = remainingAppSemanticMigrationPlansByKey();
        Map<String, java.util.Set<String>> sourcesByKey = remainingAppSemanticMigrationSourcesByKey();
        for (Map.Entry<String, AppSemanticMigrationPlan> entry : plansByKey.entrySet()) {
            java.util.Set<String> sources = sourcesByKey.getOrDefault(entry.getKey(), java.util.Set.of());
            bySourceClass
                    .computeIfAbsent(remainingAppSemanticMigrationSourceClass(sources),
                            ignored -> new java.util.ArrayList<>())
                    .add(entry.getValue());
        }
        return immutableListMap(bySourceClass);
    }

    @JsonProperty(value = "remainingAppSemanticMigrationPlans",
            access = JsonProperty.Access.READ_ONLY)
    public List<AppSemanticMigrationPlan> remainingAppSemanticMigrationPlans() {
        return List.copyOf(remainingAppSemanticMigrationPlansByKey().values());
    }

    @JsonProperty(value = "remainingAppSemanticMigrationPlanIndex",
            access = JsonProperty.Access.READ_ONLY)
    public AppSemanticMigrationPlanIndex remainingAppSemanticMigrationPlanIndex() {
        List<AppSemanticMigrationPlan> remainingPlans = remainingAppSemanticMigrationPlans();
        return new AppSemanticMigrationPlanIndex(
                appSemanticMigrationPlansBySingleKey(remainingPlans, AppSemanticMigrationPlan::migrationType),
                appSemanticMigrationPlansBySingleKey(remainingPlans, AppSemanticMigrationPlan::serviceName),
                appSemanticMigrationPlansBySingleKey(remainingPlans, AppSemanticMigrationPlan::recommendation),
                appSemanticMigrationPlansBySingleKey(remainingPlans, AppSemanticMigrationPlan::migrationAction),
                appSemanticMigrationPlansBySingleKey(remainingPlans, AppSemanticMigrationPlan::targetKind),
                appSemanticMigrationPlansBySingleKey(remainingPlans, AppSemanticMigrationPlan::adapterRole),
                appSemanticMigrationPlansByParticipantCategory(remainingPlans),
                appSemanticMigrationPlansByRoleOnlyReadiness(remainingPlans,
                        serviceParticipantRoleOnlyMigrationActionPlans),
                appSemanticMigrationPlansByPlatformPrimaryKeyIdentityKind(remainingPlans, roleParticipantPlans),
                appSemanticMigrationPlansByRemoteImplementationKind(remainingPlans),
                appSemanticMigrationPlansByRemoteExternalReadiness(remainingPlans),
                appSemanticMigrationPlansByMigrationContract(remainingPlans,
                        serviceParticipantRoleOnlyMigrationActionPlans));
    }

    @JsonProperty(value = "remainingAppSemanticMigrationPlansByType", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> remainingAppSemanticMigrationPlansByType() {
        return remainingAppSemanticMigrationPlanIndex().byType();
    }

    @JsonProperty(value = "remainingAppSemanticMigrationPlansByService", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> remainingAppSemanticMigrationPlansByService() {
        return remainingAppSemanticMigrationPlanIndex().byService();
    }

    @JsonProperty(value = "remainingAppSemanticMigrationPlansByRecommendation",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> remainingAppSemanticMigrationPlansByRecommendation() {
        return remainingAppSemanticMigrationPlanIndex().byRecommendation();
    }

    @JsonProperty(value = "remainingAppSemanticMigrationPlansByAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> remainingAppSemanticMigrationPlansByAction() {
        return remainingAppSemanticMigrationPlanIndex().byAction();
    }

    @JsonProperty(value = "remainingAppSemanticMigrationPlansByTargetKind",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> remainingAppSemanticMigrationPlansByTargetKind() {
        return remainingAppSemanticMigrationPlanIndex().byTargetKind();
    }

    @JsonProperty(value = "remainingAppSemanticMigrationPlansByAdapterRole",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> remainingAppSemanticMigrationPlansByAdapterRole() {
        return remainingAppSemanticMigrationPlanIndex().byAdapterRole();
    }

    @JsonProperty(value = "remainingAppSemanticMigrationPlansByParticipantCategory",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> remainingAppSemanticMigrationPlansByParticipantCategory() {
        return remainingAppSemanticMigrationPlanIndex().byParticipantCategory();
    }

    @JsonProperty(value = "remainingAppSemanticMigrationPlansByRoleOnlyReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> remainingAppSemanticMigrationPlansByRoleOnlyReadiness() {
        return remainingAppSemanticMigrationPlanIndex().byRoleOnlyReadiness();
    }

    @JsonProperty(value = "remainingAppSemanticMigrationPlansByPlatformPrimaryKeyIdentityKind",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>>
            remainingAppSemanticMigrationPlansByPlatformPrimaryKeyIdentityKind() {
        return remainingAppSemanticMigrationPlanIndex().byPlatformPrimaryKeyIdentityKind();
    }

    @JsonProperty(value = "remainingAppSemanticMigrationPlansByRemoteImplementationKind",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> remainingAppSemanticMigrationPlansByRemoteImplementationKind() {
        return remainingAppSemanticMigrationPlanIndex().byRemoteImplementationKind();
    }

    @JsonProperty(value = "remainingAppSemanticMigrationPlansByRemoteExternalReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> remainingAppSemanticMigrationPlansByRemoteExternalReadiness() {
        return remainingAppSemanticMigrationPlanIndex().byRemoteExternalReadiness();
    }

    @JsonProperty(value = "remainingAppSemanticMigrationPlansByMigrationContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> remainingAppSemanticMigrationPlansByMigrationContract() {
        return remainingAppSemanticMigrationPlanIndex().byMigrationContract();
    }

    @JsonProperty(value = "remainingAppSemanticMigrationSummaryPlans",
            access = JsonProperty.Access.READ_ONLY)
    public List<AppSemanticMigrationSummaryPlan> remainingAppSemanticMigrationSummaryPlans() {
        return appSemanticMigrationSummaryPlans(List.copyOf(remainingAppSemanticMigrationPlansByKey().values()));
    }

    @JsonProperty(value = "remainingAppSemanticMigrationSummaryPlansByType",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, AppSemanticMigrationSummaryPlan> remainingAppSemanticMigrationSummaryPlansByType() {
        return appSemanticMigrationSummaryPlansByType(remainingAppSemanticMigrationSummaryPlans());
    }

    @JsonProperty(value = "remainingAppSemanticMigrationSummaryPlansByParticipantCategory",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationSummaryPlan>>
            remainingAppSemanticMigrationSummaryPlansByParticipantCategory() {
        return appSemanticMigrationSummaryPlansByParticipantCategory(remainingAppSemanticMigrationSummaryPlans());
    }

    @JsonProperty(value = "remainingAppSemanticMigrationSummaryPlansByIdentityKind",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationSummaryPlan>>
            remainingAppSemanticMigrationSummaryPlansByIdentityKind() {
        return appSemanticMigrationSummaryPlansByIdentityKind(remainingAppSemanticMigrationSummaryPlans());
    }

    @JsonProperty(value = "contractSignerPlansByContract", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ContractSignerPlan>> contractSignerPlansByContract() {
        return contractSignerReadinessIndex().byContract();
    }

    @JsonProperty(value = "contractSignerPlansBySignerRole", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ContractSignerPlan>> contractSignerPlansBySignerRole() {
        return contractSignerReadinessIndex().bySignerRole();
    }

    @JsonProperty(value = "contractSignerPlansByScenario", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ContractSignerPlan>> contractSignerPlansByScenario() {
        return contractSignerReadinessIndex().byScenario();
    }

    @JsonProperty(value = "contractSignerPlansByContractParameter", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ContractSignerPlan>> contractSignerPlansByContractParameter() {
        return contractSignerReadinessIndex().byContractParameter();
    }

    @JsonProperty(value = "contractSignerPlansByRoleParticipantReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ContractSignerPlan>> contractSignerPlansByRoleParticipantReadiness() {
        Map<String, List<ContractSignerPlan>> byReadiness = new java.util.LinkedHashMap<>();
        for (ContractSignerPlan plan : safeList(contractSignerPlans)) {
            String key = plan.roleParticipantDefined()
                    ? "ROLE_PARTICIPANT_DEFINED"
                    : "ROLE_PARTICIPANT_MISSING";
            byReadiness.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(plan);
        }
        return immutableListMap(byReadiness);
    }

    @JsonProperty(value = "contractSignerPlansByMigrationReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ContractSignerPlan>> contractSignerPlansByMigrationReadiness() {
        Map<String, List<ContractSignerPlan>> byReadiness = new java.util.LinkedHashMap<>();
        for (ContractSignerPlan plan : safeList(contractSignerPlans)) {
            String key = plan.migrationRequired()
                    ? "SIGNER_MIGRATION_REQUIRED"
                    : "SIGNER_MIGRATION_READY";
            byReadiness.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(plan);
        }
        return immutableListMap(byReadiness);
    }

    @JsonProperty(value = "contractSignerReadinessIndex", access = JsonProperty.Access.READ_ONLY)
    public ContractSignerReadinessIndex contractSignerReadinessIndex() {
        List<ContractSignerPlan> plans = safeList(contractSignerPlans).stream()
                .filter(plan -> plan != null)
                .map(GeneratedServiceCatalog::enrichContractSignerPlan)
                .toList();
        if (plans.isEmpty()) {
            return new ContractSignerReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<ContractSignerPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<ContractSignerPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<ContractSignerPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<ContractSignerPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<ContractSignerPlan>> byContract = new LinkedHashMap<>();
        Map<String, List<ContractSignerPlan>> bySignerRole = new LinkedHashMap<>();
        Map<String, List<ContractSignerPlan>> byScenario = new LinkedHashMap<>();
        Map<String, List<ContractSignerPlan>> byContractParameter = new LinkedHashMap<>();
        Map<String, List<ContractSignerPlan>> byContractParameterPath = new LinkedHashMap<>();
        Map<String, List<ContractSignerPlan>> bySignerPath = new LinkedHashMap<>();
        Map<String, List<ContractSignerPlan>> bySigningContract = new LinkedHashMap<>();
        Map<String, List<ContractSignerPlan>> bySignerCategory = new LinkedHashMap<>();
        Map<String, List<ContractSignerPlan>> byRecommendation = new LinkedHashMap<>();
        for (ContractSignerPlan plan : plans) {
            List<String> missingReasons = safeList(plan.missingReasons());
            if ("CONTRACT_SIGNER_READY".equals(plan.readiness())) {
                addToIndex(byReadiness, plan.readiness(), plan);
                for (String reason : safeList(plan.readyReasons())) {
                    addToIndex(byReadyReason, indexKey(reason), plan);
                }
                for (String reason : missingReasons) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.actions())) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            } else {
                addToIndex(byReadiness, indexKey(plan.readiness()), plan);
                for (String reason : missingReasons) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
                for (String action : safeList(plan.actions())) {
                    addToIndex(byAction, indexKey(action), plan);
                }
            }
            addToIndex(byContract, indexKey(plan.contractType()), plan);
            addToIndex(bySignerRole, indexKey(plan.signerType()), plan);
            addToIndex(byScenario, indexKey(plan.scenarioName()), plan);
            addToIndex(byContractParameter, contractSignerContractParameterKey(plan), plan);
            addToIndex(byContractParameterPath, contractSignerContractParameterPath(plan), plan);
            addToIndex(bySignerPath, indexKey(plan.signerPath()), plan);
            addToIndex(bySigningContract, indexKey(plan.signingContract()), plan);
            addToIndex(bySignerCategory, indexKey(plan.signerCategory()), plan);
            addToIndex(byRecommendation, indexKey(plan.recommendation()), plan);
        }
        return new ContractSignerReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byContract),
                immutableListMap(bySignerRole),
                immutableListMap(byScenario),
                immutableListMap(byContractParameter),
                immutableListMap(byContractParameterPath),
                immutableListMap(bySignerPath),
                immutableListMap(bySigningContract),
                immutableListMap(bySignerCategory),
                immutableListMap(byRecommendation));
    }

    @JsonProperty(value = "contractSignerPlansByReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ContractSignerPlan>> contractSignerPlansByReadiness() {
        return contractSignerReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "contractSignerPlansByReadyReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ContractSignerPlan>> contractSignerPlansByReadyReason() {
        return contractSignerReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "contractSignerPlansByMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ContractSignerPlan>> contractSignerPlansByMissingReason() {
        return contractSignerReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "contractSignerPlansByAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ContractSignerPlan>> contractSignerPlansByAction() {
        return contractSignerReadinessIndex().byAction();
    }

    @JsonProperty(value = "contractSignerPlansByContractParameterPath",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ContractSignerPlan>> contractSignerPlansByContractParameterPath() {
        return contractSignerReadinessIndex().byContractParameterPath();
    }

    @JsonProperty(value = "contractSignerPlansBySignerPath",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ContractSignerPlan>> contractSignerPlansBySignerPath() {
        return contractSignerReadinessIndex().bySignerPath();
    }

    @JsonProperty(value = "contractSignerPlansBySigningContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ContractSignerPlan>> contractSignerPlansBySigningContract() {
        return contractSignerReadinessIndex().bySigningContract();
    }

    @JsonProperty(value = "contractSignerPlansBySignerCategory",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ContractSignerPlan>> contractSignerPlansBySignerCategory() {
        return contractSignerReadinessIndex().bySignerCategory();
    }

    @JsonProperty(value = "contractSignerPlansByRecommendation",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ContractSignerPlan>> contractSignerPlansByRecommendation() {
        return contractSignerReadinessIndex().byRecommendation();
    }

    private static String contractSignerSigningContractKey(ContractSignerPlan plan, List<String> missingReasons) {
        if (plan == null) {
            return "NO_CONTRACT_SIGNING_CONTRACT";
        }
        String readiness = safeList(missingReasons).isEmpty()
                ? "CONTRACT_SIGNER_READY"
                : "CONTRACT_SIGNER_INCOMPLETE";
        return contractSignerContractParameterPath(plan)
                + "<-" + indexKey(plan.signerType())
                + ":" + indexKey(plan.signerFieldName())
                + ":" + readiness;
    }

    private static ContractSignerPlan enrichContractSignerPlan(ContractSignerPlan plan) {
        if (plan == null) {
            return null;
        }
        List<String> missingReasons = safeList(plan.missingReasons()).isEmpty()
                ? contractSignerMissingReasons(plan)
                : safeList(plan.missingReasons());
        String readiness = !isBlank(plan.readiness())
                ? plan.readiness()
                : (missingReasons.isEmpty() ? "CONTRACT_SIGNER_READY" : "CONTRACT_SIGNER_INCOMPLETE");
        List<String> readyReasons = safeList(plan.readyReasons()).isEmpty()
                && "CONTRACT_SIGNER_READY".equals(readiness)
                ? contractSignerReadyReasons(plan)
                : safeList(plan.readyReasons());
        if (missingReasons.isEmpty() && "CONTRACT_SIGNER_READY".equals(readiness)) {
            missingReasons = List.of("NO_MISSING_CONTRACT_SIGNER_REASON");
        }
        List<String> actions = safeList(plan.actions()).isEmpty()
                ? contractSignerActions("CONTRACT_SIGNER_READY".equals(readiness)
                        ? List.of()
                        : missingReasons)
                : safeList(plan.actions());
        String signingContract = !isBlank(plan.signingContract())
                ? plan.signingContract()
                : contractSignerSigningContractKey(plan,
                        "CONTRACT_SIGNER_READY".equals(readiness) ? List.of() : missingReasons);
        return new ContractSignerPlan(plan.contractType(),
                plan.contractTableName(),
                plan.scenarioName(),
                plan.contractParameterName(),
                plan.signerPath(),
                plan.signerFieldName(),
                plan.signerType(),
                plan.signerCategory(),
                plan.roleParticipantDefined(),
                plan.signedByRole(),
                plan.migrationRequired(),
                plan.recommendation(),
                readiness,
                readyReasons,
                missingReasons,
                actions,
                signingContract);
    }

    @JsonProperty(value = "scenarioContractContextPlans", access = JsonProperty.Access.READ_ONLY)
    public List<ScenarioContractContextPlan> scenarioContractContextPlans() {
        Map<String, List<ContractSignerPlan>> plansByParameter = new LinkedHashMap<>();
        for (ContractSignerPlan plan : safeList(contractSignerReadinessIndex().plans())) {
            if (plan == null || plan.scenarioName() == null || plan.scenarioName().isBlank()
                    || plan.contractParameterName() == null || plan.contractParameterName().isBlank()) {
                continue;
            }
            plansByParameter.computeIfAbsent(contractSignerContractParameterKey(plan), ignored -> new ArrayList<>())
                    .add(plan);
        }
        List<ScenarioContractContextPlan> contextPlans = new ArrayList<>();
        for (Map.Entry<String, List<ContractSignerPlan>> entry : plansByParameter.entrySet()) {
            List<ContractSignerPlan> signerPlans = entry.getValue();
            if (signerPlans == null || signerPlans.isEmpty()) {
                continue;
            }
            ContractSignerPlan first = signerPlans.get(0);
            List<String> signerRoles = distinctContractSignerValues(signerPlans, ContractSignerPlan::signerType);
            List<String> signerParameters =
                    distinctContractSignerValues(signerPlans, ContractSignerPlan::signerFieldName);
            List<String> signerPaths = distinctContractSignerValues(signerPlans, ContractSignerPlan::signerPath);
            boolean roleParticipantDefined = signerPlans.stream().allMatch(ContractSignerPlan::roleParticipantDefined);
            boolean signedByRole = signerPlans.stream().allMatch(ContractSignerPlan::signedByRole);
            boolean contextComplete = !signerRoles.isEmpty() && roleParticipantDefined && signedByRole;
            String readiness = contextComplete ? "SCENARIO_CONTRACT_CONTEXT_READY"
                    : "SCENARIO_CONTRACT_CONTEXT_INCOMPLETE";
            List<String> readyReasons = contextComplete
                    ? scenarioContractContextReadyReasons(signerRoles, roleParticipantDefined, signedByRole)
                    : List.of();
            List<String> missingReasons = contextComplete
                    ? List.of("NO_MISSING_SCENARIO_CONTRACT_CONTEXT_REASON")
                    : scenarioContractContextMissingReasons(signerRoles, roleParticipantDefined, signedByRole);
            String action = contextComplete
                    ? "NO_ACTION_REQUIRED"
                    : "BIND_CONTRACT_CONTEXT_TO_ROLE_PARTICIPANTS";
            String signerRoleSet = signerRoleSetKey(signerRoles);
            String signingContract = scenarioContractSigningContractKey(first.contractType(), signerRoleSet, readiness);
            String executionContract = scenarioContractExecutionContractKey(
                    entry.getKey(), first.contractType(), signerRoleSet, readiness);
            contextPlans.add(new ScenarioContractContextPlan(first.scenarioName(),
                    first.contractType(),
                    first.contractTableName(),
                    first.contractParameterName(),
                    entry.getKey(),
                    signerRoles,
                    signerParameters,
                    signerPaths,
                    signerPlans.size(),
                    roleParticipantDefined,
                    signedByRole,
                    contextComplete,
                    readiness,
                    readyReasons,
                    missingReasons,
                    action,
                    contextComplete
                            ? "Contract context is signed by scenario Role participants."
                            : "Bind the Contract context to Role participants in the scenario boundary.",
                    List.of(action),
                    signerRoleSet,
                    signingContract,
                    executionContract));
        }
        return List.copyOf(contextPlans);
    }

    @JsonProperty(value = "scenarioContractContextIndex", access = JsonProperty.Access.READ_ONLY)
    public ScenarioContractContextIndex scenarioContractContextIndex() {
        List<ScenarioContractContextPlan> plans = scenarioContractContextPlans();
        Map<String, List<ScenarioContractContextPlan>> byScenario = new LinkedHashMap<>();
        Map<String, List<ScenarioContractContextPlan>> byContract = new LinkedHashMap<>();
        Map<String, List<ScenarioContractContextPlan>> byContractParameter = new LinkedHashMap<>();
        Map<String, List<ScenarioContractContextPlan>> byContractParameterPath = new LinkedHashMap<>();
        Map<String, List<ScenarioContractContextPlan>> bySignerRole = new LinkedHashMap<>();
        Map<String, List<ScenarioContractContextPlan>> bySignerRoleSet = new LinkedHashMap<>();
        Map<String, List<ScenarioContractContextPlan>> bySigningContract = new LinkedHashMap<>();
        Map<String, List<ScenarioContractContextPlan>> byExecutionContract = new LinkedHashMap<>();
        Map<String, List<ScenarioContractContextPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<ScenarioContractContextPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<ScenarioContractContextPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<ScenarioContractContextPlan>> byAction = new LinkedHashMap<>();
        for (ScenarioContractContextPlan plan : plans) {
            addToIndex(byScenario, indexKey(plan.scenarioName()), plan);
            addToIndex(byContract, indexKey(plan.contractType()), plan);
            addToIndex(byContractParameter, indexKey(plan.contractParameterKey()), plan);
            addToIndex(byContractParameterPath, scenarioContractParameterPath(plan), plan);
            for (String signerRole : plan.signerRoleTypes()) {
                addToIndex(bySignerRole, indexKey(signerRole), plan);
            }
            addToIndex(bySignerRoleSet, indexKey(plan.signerRoleSet()), plan);
            addToIndex(bySigningContract, indexKey(plan.signingContract()), plan);
            addToIndex(byExecutionContract, indexKey(plan.executionContract()), plan);
            addToIndex(byReadiness, indexKey(plan.readiness()), plan);
            for (String reason : safeList(plan.readyReasons())) {
                addToIndex(byReadyReason, reason, plan);
            }
            for (String reason : safeList(plan.missingReasons())) {
                addToIndex(byMissingReason, reason, plan);
            }
            for (String action : safeList(plan.actions())) {
                addToIndex(byAction, indexKey(action), plan);
            }
        }
        return new ScenarioContractContextIndex(plans,
                immutableListMap(byScenario),
                immutableListMap(byContract),
                immutableListMap(byContractParameter),
                immutableListMap(byContractParameterPath),
                immutableListMap(bySignerRole),
                immutableListMap(bySignerRoleSet),
                immutableListMap(bySigningContract),
                immutableListMap(byExecutionContract),
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction));
    }

    @JsonProperty(value = "scenarioContractContextPlansByScenario",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScenarioContractContextPlan>> scenarioContractContextPlansByScenario() {
        return scenarioContractContextIndex().byScenario();
    }

    @JsonProperty(value = "scenarioContractContextPlansByContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScenarioContractContextPlan>> scenarioContractContextPlansByContract() {
        return scenarioContractContextIndex().byContract();
    }

    @JsonProperty(value = "scenarioContractContextPlansByContractParameter",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScenarioContractContextPlan>> scenarioContractContextPlansByContractParameter() {
        return scenarioContractContextIndex().byContractParameter();
    }

    @JsonProperty(value = "scenarioContractContextPlansByContractParameterPath",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScenarioContractContextPlan>> scenarioContractContextPlansByContractParameterPath() {
        return scenarioContractContextIndex().byContractParameterPath();
    }

    @JsonProperty(value = "scenarioContractContextPlansBySignerRole",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScenarioContractContextPlan>> scenarioContractContextPlansBySignerRole() {
        return scenarioContractContextIndex().bySignerRole();
    }

    @JsonProperty(value = "scenarioContractContextPlansBySignerRoleSet",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScenarioContractContextPlan>> scenarioContractContextPlansBySignerRoleSet() {
        return scenarioContractContextIndex().bySignerRoleSet();
    }

    @JsonProperty(value = "scenarioContractContextPlansBySigningContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScenarioContractContextPlan>> scenarioContractContextPlansBySigningContract() {
        return scenarioContractContextIndex().bySigningContract();
    }

    @JsonProperty(value = "scenarioContractContextPlansByExecutionContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScenarioContractContextPlan>> scenarioContractContextPlansByExecutionContract() {
        return scenarioContractContextIndex().byExecutionContract();
    }

    @JsonProperty(value = "scenarioContractContextPlansByReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScenarioContractContextPlan>> scenarioContractContextPlansByReadiness() {
        return scenarioContractContextIndex().byReadiness();
    }

    @JsonProperty(value = "scenarioContractContextPlansByReadyReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScenarioContractContextPlan>> scenarioContractContextPlansByReadyReason() {
        return scenarioContractContextIndex().byReadyReason();
    }

    @JsonProperty(value = "scenarioContractContextPlansByMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScenarioContractContextPlan>> scenarioContractContextPlansByMissingReason() {
        return scenarioContractContextIndex().byMissingReason();
    }

    @JsonProperty(value = "scenarioContractContextPlansByAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ScenarioContractContextPlan>> scenarioContractContextPlansByAction() {
        return scenarioContractContextIndex().byAction();
    }

    private static List<String> scenarioContractContextReadyReasons(List<String> signerRoleTypes,
                                                                    boolean roleParticipantDefined,
                                                                    boolean signedByRole) {
        List<String> reasons = new ArrayList<>();
        if (!safeList(signerRoleTypes).isEmpty()) {
            reasons.add("roleSignedContext");
        }
        if (roleParticipantDefined) {
            reasons.add("roleParticipantDefined");
        }
        if (signedByRole) {
            reasons.add("signedByRole");
        }
        return reasons.isEmpty() ? List.of("contextComplete") : List.copyOf(reasons);
    }

    private static List<String> scenarioContractContextMissingReasons(List<String> signerRoleTypes,
                                                                      boolean roleParticipantDefined,
                                                                      boolean signedByRole) {
        List<String> reasons = new ArrayList<>();
        if (safeList(signerRoleTypes).isEmpty()) {
            reasons.add("missingSignerRole");
        }
        if (!roleParticipantDefined) {
            reasons.add("missingRoleParticipant");
        }
        if (!signedByRole) {
            reasons.add("missingRoleSignature");
        }
        return reasons.isEmpty() ? List.of("contextIncomplete") : List.copyOf(reasons);
    }

    private static String scenarioContractSigningContractKey(String contractType,
                                                             String signerRoleSet,
                                                             String readiness) {
        return indexKey(contractType)
                + "<-" + indexKey(signerRoleSet)
                + ":" + indexKey(readiness);
    }

    private static String scenarioContractExecutionContractKey(String contractParameterKey,
                                                               String contractType,
                                                               String signerRoleSet,
                                                               String readiness) {
        return indexKey(contractParameterKey)
                + ":" + indexKey(contractType)
                + "<-" + indexKey(signerRoleSet)
                + ":" + indexKey(readiness);
    }

    private static String signerRoleSetKey(List<String> signerRoleTypes) {
        List<String> roles = safeList(signerRoleTypes).stream()
                .filter(role -> role != null && !role.isBlank())
                .distinct()
                .toList();
        return roles.isEmpty() ? "NO_SIGNER_ROLE" : String.join("+", roles);
    }

    private static List<String> distinctContractSignerValues(List<ContractSignerPlan> plans,
                                                            Function<ContractSignerPlan, String> valueExtractor) {
        if (plans == null || plans.isEmpty() || valueExtractor == null) {
            return List.of();
        }
        return plans.stream()
                .map(valueExtractor)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    @JsonProperty(value = "contractSignerFieldMigrationIndex",
            access = JsonProperty.Access.READ_ONLY)
    public ContractSignerFieldMigrationIndex contractSignerFieldMigrationIndex() {
        List<AppSemanticMigrationPlan> generatedPlans = safeList(generatedAppSemanticMigrationPlans)
                .stream()
                .filter(GeneratedServiceCatalog::contractSignerFieldMigrationPlan)
                .toList();
        List<AppSemanticMigrationPlan> suppressedPlans = generatedAppSemanticMigrationPlansBySuppressedReason()
                .values()
                .stream()
                .flatMap(List::stream)
                .filter(GeneratedServiceCatalog::contractSignerFieldMigrationPlan)
                .distinct()
                .toList();
        List<AppSemanticMigrationPlan> activePlans = activeGeneratedAppSemanticMigrationPlans()
                .stream()
                .filter(GeneratedServiceCatalog::contractSignerFieldMigrationPlan)
                .toList();
        Map<String, List<AppSemanticMigrationPlan>> bySourceClass = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> bySuppressedReason = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byContract = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byTable = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byObjectType = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byPath = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byParticipantCategory = new LinkedHashMap<>();
        Map<String, List<AppSemanticMigrationPlan>> byExternalObjectKeyRoot = new LinkedHashMap<>();
        for (AppSemanticMigrationPlan plan : generatedPlans) {
            addToIndex(bySourceClass,
                    suppressedPlans.contains(plan) ? "SUPPRESSED_GENERATED" : "ACTIVE_GENERATED",
                    plan);
            addToIndex(byContract, indexKey(plan.targetName()), plan);
            addToIndex(byTable, indexKey(plan.tableName()), plan);
            addToIndex(byObjectType, indexKey(plan.objectType()), plan);
            addListValuesToIndex(byPath, plan.paths(), "NO_PATH", plan);
            addListValuesToIndex(byParticipantCategory, plan.participantCategories(),
                    "NO_PARTICIPANT_CATEGORY", plan);
            addListValuesToIndex(byExternalObjectKeyRoot, plan.externalObjectKeyRoots(),
                    "NO_EXTERNAL_OBJECT_KEY_ROOT", plan);
        }
        generatedAppSemanticMigrationPlansBySuppressedReason().forEach((reason, plans) -> {
            for (AppSemanticMigrationPlan plan : safeList(plans)) {
                if (contractSignerFieldMigrationPlan(plan)) {
                    addToIndex(bySuppressedReason, indexKey(reason), plan);
                }
            }
        });
        return new ContractSignerFieldMigrationIndex(generatedPlans,
                activePlans,
                suppressedPlans,
                immutableListMap(bySourceClass),
                immutableListMap(bySuppressedReason),
                immutableListMap(byContract),
                immutableListMap(byTable),
                immutableListMap(byObjectType),
                immutableListMap(byPath),
                immutableListMap(byParticipantCategory),
                immutableListMap(byExternalObjectKeyRoot));
    }

    @JsonProperty(value = "contractSignerFieldMigrationGeneratedPlans", access = JsonProperty.Access.READ_ONLY)
    public List<AppSemanticMigrationPlan> contractSignerFieldMigrationGeneratedPlans() {
        return contractSignerFieldMigrationIndex().generatedPlans();
    }

    @JsonProperty(value = "contractSignerFieldMigrationActiveGeneratedPlans",
            access = JsonProperty.Access.READ_ONLY)
    public List<AppSemanticMigrationPlan> contractSignerFieldMigrationActiveGeneratedPlans() {
        return contractSignerFieldMigrationIndex().activeGeneratedPlans();
    }

    @JsonProperty(value = "contractSignerFieldMigrationSuppressedGeneratedPlans",
            access = JsonProperty.Access.READ_ONLY)
    public List<AppSemanticMigrationPlan> contractSignerFieldMigrationSuppressedGeneratedPlans() {
        return contractSignerFieldMigrationIndex().suppressedGeneratedPlans();
    }

    @JsonProperty(value = "contractSignerFieldMigrationPlansBySourceClass",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> contractSignerFieldMigrationPlansBySourceClass() {
        return contractSignerFieldMigrationIndex().bySourceClass();
    }

    @JsonProperty(value = "contractSignerFieldMigrationPlansBySuppressedReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> contractSignerFieldMigrationPlansBySuppressedReason() {
        return contractSignerFieldMigrationIndex().bySuppressedReason();
    }

    @JsonProperty(value = "contractSignerFieldMigrationPlansByContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> contractSignerFieldMigrationPlansByContract() {
        return contractSignerFieldMigrationIndex().byContract();
    }

    @JsonProperty(value = "contractSignerFieldMigrationPlansByTable", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> contractSignerFieldMigrationPlansByTable() {
        return contractSignerFieldMigrationIndex().byTable();
    }

    @JsonProperty(value = "contractSignerFieldMigrationPlansByObjectType", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> contractSignerFieldMigrationPlansByObjectType() {
        return contractSignerFieldMigrationIndex().byObjectType();
    }

    @JsonProperty(value = "contractSignerFieldMigrationPlansByPath", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> contractSignerFieldMigrationPlansByPath() {
        return contractSignerFieldMigrationIndex().byPath();
    }

    @JsonProperty(value = "contractSignerFieldMigrationPlansByParticipantCategory",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> contractSignerFieldMigrationPlansByParticipantCategory() {
        return contractSignerFieldMigrationIndex().byParticipantCategory();
    }

    @JsonProperty(value = "contractSignerFieldMigrationPlansByExternalObjectKeyRoot",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<AppSemanticMigrationPlan>> contractSignerFieldMigrationPlansByExternalObjectKeyRoot() {
        return contractSignerFieldMigrationIndex().byExternalObjectKeyRoot();
    }

    @JsonProperty(value = "serviceParticipantRoleOnlyMigrationActionsByTargetRoleParameter",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>>
            serviceParticipantRoleOnlyMigrationActionsByTargetRoleParameter() {
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byParameter =
                new java.util.LinkedHashMap<>();
        for (ServiceParticipantRoleOnlyMigrationActionPlan plan
                : safeList(serviceParticipantRoleOnlyMigrationActionPlans)) {
            List<String> parameterNames = safeList(plan.targetRoleParameterNames());
            if (parameterNames.isEmpty()) {
                byParameter.computeIfAbsent("NO_TARGET_ROLE_PARAMETER",
                        ignored -> new java.util.ArrayList<>()).add(plan);
                continue;
            }
            for (String parameterName : parameterNames) {
                String key = parameterName == null || parameterName.isBlank()
                        ? "NO_TARGET_ROLE_PARAMETER"
                        : parameterName;
                byParameter.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(plan);
            }
        }
        return immutableListMap(byParameter);
    }

    @JsonProperty(value = "serviceParticipantRoleOnlyMigrationActionsByCandidateRole",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>>
            serviceParticipantRoleOnlyMigrationActionsByCandidateRole() {
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byCandidateRole =
                new java.util.LinkedHashMap<>();
        for (ServiceParticipantRoleOnlyMigrationActionPlan plan
                : safeList(serviceParticipantRoleOnlyMigrationActionPlans)) {
            List<String> candidateRoles = safeList(plan.candidateRoleTypes());
            if (candidateRoles.isEmpty()) {
                byCandidateRole.computeIfAbsent("NO_CANDIDATE_ROLE",
                        ignored -> new java.util.ArrayList<>()).add(plan);
                continue;
            }
            for (String candidateRole : candidateRoles) {
                String key = candidateRole == null || candidateRole.isBlank()
                        ? "NO_CANDIDATE_ROLE"
                        : candidateRole;
                byCandidateRole.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(plan);
            }
        }
        return immutableListMap(byCandidateRole);
    }

    @JsonProperty(value = "serviceParticipantRoleOnlyMigrationActionsByPath",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>>
            serviceParticipantRoleOnlyMigrationActionsByPath() {
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byPath =
                new java.util.LinkedHashMap<>();
        for (ServiceParticipantRoleOnlyMigrationActionPlan plan
                : safeList(serviceParticipantRoleOnlyMigrationActionPlans)) {
            String key = plan.path() == null || plan.path().isBlank()
                    ? "NO_PATH"
                    : plan.path();
            byPath.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(plan);
        }
        return immutableListMap(byPath);
    }

    @JsonProperty(value = "serviceParticipantRoleOnlyReadinessIndex",
            access = JsonProperty.Access.READ_ONLY)
    public ServiceParticipantRoleOnlyReadinessIndex serviceParticipantRoleOnlyReadinessIndex() {
        List<ServiceParticipantRoleOnlyMigrationActionPlan> plans =
                safeList(serviceParticipantRoleOnlyMigrationActionPlans).stream()
                        .filter(plan -> plan != null)
                        .toList();
        if (plans.isEmpty()) {
            return new ServiceParticipantRoleOnlyReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byService = new LinkedHashMap<>();
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byPath = new LinkedHashMap<>();
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byParticipantCategory = new LinkedHashMap<>();
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byMigrationAction = new LinkedHashMap<>();
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byTargetRole = new LinkedHashMap<>();
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byTargetRoleParameter =
                new LinkedHashMap<>();
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byCandidateRole = new LinkedHashMap<>();
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byServiceParameterContract =
                new LinkedHashMap<>();
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byExecutionContract =
                new LinkedHashMap<>();
        for (ServiceParticipantRoleOnlyMigrationActionPlan plan : plans) {
            List<String> missingReasons = serviceParticipantRoleOnlyMissingReasons(plan);
            String readiness = plan.roleOnlyReady() ? "ROLE_ONLY_READY" : "ROLE_ONLY_BLOCKED";
            addToIndex(byReadiness, readiness, plan);
            if (missingReasons.isEmpty()) {
                addToIndex(byMissingReason, "NO_MISSING_ROLE_ONLY_REASON", plan);
                for (String reason : safeList(plan.readinessReasons())) {
                    addToIndex(byReadyReason, indexKey(reason), plan);
                }
            } else {
                for (String reason : missingReasons) {
                    addToIndex(byMissingReason, indexKey(reason), plan);
                }
            }
            addToIndex(byAction, indexKey(firstNonBlank(plan.migrationAction(), "NO_ROLE_ONLY_ACTION")), plan);
            addToIndex(byService, indexKey(plan.serviceName()), plan);
            addToIndex(byPath, indexKey(firstNonBlank(plan.path(), "NO_PATH")), plan);
            addToIndex(byParticipantCategory,
                    indexKey(firstNonBlank(plan.participantCategory(), "NO_PARTICIPANT_CATEGORY")), plan);
            addToIndex(byMigrationAction,
                    indexKey(firstNonBlank(plan.migrationAction(), "NO_ROLE_ONLY_ACTION")), plan);
            addListIndexValues(byTargetRole, plan.targetRoleTypes(), plan, "NO_TARGET_ROLE");
            addListIndexValues(byTargetRoleParameter, plan.targetRoleParameterNames(), plan,
                    "NO_TARGET_ROLE_PARAMETER");
            addListIndexValues(byCandidateRole, plan.candidateRoleTypes(), plan, "NO_CANDIDATE_ROLE");
            addToIndex(byServiceParameterContract, serviceParticipantRoleOnlyServiceParameterContract(plan), plan);
            addToIndex(byExecutionContract, serviceParticipantRoleOnlyExecutionContract(plan), plan);
        }
        return new ServiceParticipantRoleOnlyReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byService),
                immutableListMap(byPath),
                immutableListMap(byParticipantCategory),
                immutableListMap(byMigrationAction),
                immutableListMap(byTargetRole),
                immutableListMap(byTargetRoleParameter),
                immutableListMap(byCandidateRole),
                immutableListMap(byServiceParameterContract),
                immutableListMap(byExecutionContract));
    }

    @JsonProperty(value = "serviceParticipantRoleOnlyMigrationActionsByServiceParameterContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>>
            serviceParticipantRoleOnlyMigrationActionsByServiceParameterContract() {
        return serviceParticipantRoleOnlyReadinessIndex().byServiceParameterContract();
    }

    @JsonProperty(value = "serviceParticipantRoleOnlyMigrationActionsByExecutionContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>>
            serviceParticipantRoleOnlyMigrationActionsByExecutionContract() {
        return serviceParticipantRoleOnlyReadinessIndex().byExecutionContract();
    }

    @JsonProperty(value = "serviceParticipantRoleOnlyMigrationActionsByReadyReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>>
            serviceParticipantRoleOnlyMigrationActionsByReadyReason() {
        return serviceParticipantRoleOnlyReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "serviceParticipantRoleOnlyMigrationActionsByMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>>
            serviceParticipantRoleOnlyMigrationActionsByMissingReason() {
        return serviceParticipantRoleOnlyReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "serviceParticipantRoleOnlyRemoteReferenceAdapterPlans",
            access = JsonProperty.Access.READ_ONLY)
    public List<ServiceParticipantRoleOnlyMigrationActionPlan> serviceParticipantRoleOnlyRemoteReferenceAdapterPlans() {
        return remoteReferenceRoleAdapterReviewPlans();
    }

    private List<ServiceParticipantRoleOnlyMigrationActionPlan>
            directServiceParticipantRoleOnlyRemoteReferenceAdapterPlans() {
        return safeList(serviceParticipantRoleOnlyMigrationActionPlans).stream()
                .filter(plan -> "references".equals(plan.participantCategory()))
                .filter(plan -> "REFERENCE_TO_REMOTE_ROLE_ADAPTER".equals(plan.migrationAction()))
                .toList();
    }

    @JsonProperty(value = "serviceParticipantRoleOnlyRemoteReferenceAdapterPlansByService",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>>
            serviceParticipantRoleOnlyRemoteReferenceAdapterPlansByService() {
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byService =
                new java.util.LinkedHashMap<>();
        for (ServiceParticipantRoleOnlyMigrationActionPlan plan
                : serviceParticipantRoleOnlyRemoteReferenceAdapterPlans()) {
            String key = plan.serviceName() == null || plan.serviceName().isBlank()
                    ? "NO_SERVICE"
                    : plan.serviceName();
            byService.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(plan);
        }
        return immutableListMap(byService);
    }

    @JsonProperty(value = "serviceParticipantRoleOnlyRemoteReferenceAdapterPlansByReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>>
            serviceParticipantRoleOnlyRemoteReferenceAdapterPlansByReadiness() {
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byReadiness =
                new java.util.LinkedHashMap<>();
        for (ServiceParticipantRoleOnlyMigrationActionPlan plan
                : serviceParticipantRoleOnlyRemoteReferenceAdapterPlans()) {
            byReadiness.computeIfAbsent(plan.roleOnlyReady() ? "ROLE_ONLY_READY" : "ROLE_ONLY_BLOCKED",
                    ignored -> new java.util.ArrayList<>()).add(plan);
        }
        return immutableListMap(byReadiness);
    }

    @JsonProperty(value = "serviceParticipantRoleOnlyRemoteReferenceAdapterPlansByPath",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>>
            serviceParticipantRoleOnlyRemoteReferenceAdapterPlansByPath() {
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byPath =
                new java.util.LinkedHashMap<>();
        for (ServiceParticipantRoleOnlyMigrationActionPlan plan
                : serviceParticipantRoleOnlyRemoteReferenceAdapterPlans()) {
            String key = plan.path() == null || plan.path().isBlank()
                    ? "NO_PATH"
                    : plan.path();
            byPath.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(plan);
        }
        return immutableListMap(byPath);
    }

    @JsonProperty(value = "remoteReferenceRoleAdapterPlans", access = JsonProperty.Access.READ_ONLY)
    public List<RemoteReferenceRoleAdapterPlan> remoteReferenceRoleAdapterPlans() {
        Map<String, RemoteServiceAssignment> assignmentsByService = safeList(remoteServiceAssignments).stream()
                .filter(assignment -> assignment.serviceName() != null && !assignment.serviceName().isBlank())
                .collect(java.util.stream.Collectors.toMap(RemoteServiceAssignment::serviceName,
                        java.util.function.Function.identity(), (first, second) -> first,
                        java.util.LinkedHashMap::new));
        return remoteReferenceRoleAdapterReviewPlans().stream()
                .map(plan -> remoteReferenceRoleAdapterPlan(plan, assignmentsByService.get(plan.serviceName())))
                .filter(plan -> !strictExternalRemoteAdapterClearsReviewPlan(plan))
                .toList();
    }

    @JsonProperty(value = "remoteReferenceRoleAdapterPlansByService", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RemoteReferenceRoleAdapterPlan>> remoteReferenceRoleAdapterPlansByService() {
        return remoteReferenceRoleAdapterPlans().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        plan -> blankAsKey(plan.serviceName(), "NO_SERVICE"),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
    }

    @JsonProperty(value = "remoteReferenceRoleAdapterPlansByReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RemoteReferenceRoleAdapterPlan>> remoteReferenceRoleAdapterPlansByReadiness() {
        return remoteReferenceRoleAdapterPlans().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        GeneratedServiceCatalog::remoteReferenceRoleAdapterReadinessKey,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
    }

    private static String remoteReferenceRoleAdapterReadinessKey(RemoteReferenceRoleAdapterPlan plan) {
        return plan != null && plan.physicalRoleAdapterReady()
                ? "REMOTE_REFERENCE_ROLE_ADAPTER_READY"
                : "REMOTE_REFERENCE_ROLE_ADAPTER_BLOCKED";
    }

    @JsonProperty(value = "remoteReferenceRoleAdapterPlansByPath", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RemoteReferenceRoleAdapterPlan>> remoteReferenceRoleAdapterPlansByPath() {
        return remoteReferenceRoleAdapterPlans().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        plan -> blankAsKey(plan.path(), "NO_PATH"),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
    }

    @JsonProperty(value = "remoteReferenceRoleAdapterPlansByReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RemoteReferenceRoleAdapterPlan>> remoteReferenceRoleAdapterPlansByReason() {
        Map<String, List<RemoteReferenceRoleAdapterPlan>> byReason = new java.util.LinkedHashMap<>();
        for (RemoteReferenceRoleAdapterPlan plan : remoteReferenceRoleAdapterPlans()) {
            List<String> reasons = plan.readinessReasons() == null || plan.readinessReasons().isEmpty()
                    ? List.of("NO_REMOTE_ROLE_ADAPTER_REASON")
                    : plan.readinessReasons();
            for (String reason : reasons) {
                byReason.computeIfAbsent(blankAsKey(reason, "NO_REMOTE_ROLE_ADAPTER_REASON"),
                        ignored -> new java.util.ArrayList<>()).add(plan);
            }
        }
        return immutableListMap(byReason);
    }

    private RemoteReferenceRoleAdapterPlan remoteReferenceRoleAdapterPlan(
            ServiceParticipantRoleOnlyMigrationActionPlan plan,
            RemoteServiceAssignment assignment) {
        java.util.Map<String, String> config = assignment == null || assignment.adapterConfiguration() == null
                ? java.util.Map.of()
                : assignment.adapterConfiguration();
        boolean roleAdapterDeclared = "true".equalsIgnoreCase(config.getOrDefault("roleAdapter",
                config.getOrDefault("remoteRoleAdapter", "")));
        String roleType = config.getOrDefault("roleType", "");
        String roleAdapterProtocol = config.getOrDefault("roleAdapterProtocol", "");
        boolean assignmentExecutable = assignment != null && assignment.adapterExecutable();
        boolean externalImplementation = assignment != null && assignment.externalImplementation();
        boolean referenceImplementation = assignment != null && assignment.referenceImplementation();
        boolean protocolSupported = remoteReferenceRoleAdapterProtocolSupported(roleAdapterProtocol);
        boolean ready = roleAdapterDeclared
                && roleType != null && !roleType.isBlank()
                && roleAdapterProtocol != null && !roleAdapterProtocol.isBlank()
                && protocolSupported
                && assignmentExecutable
                && externalImplementation;
        List<String> reasons = remoteReferenceRoleAdapterReadinessReasons(
                roleAdapterDeclared, roleType, roleAdapterProtocol, protocolSupported, assignmentExecutable,
                externalImplementation, referenceImplementation);
        String binding = blankAsKey(plan.serviceName(), "-")
                + ":" + blankAsKey(plan.participantType(), "-")
                + ":path=" + blankAsKey(plan.path(), "-")
                + ":remoteAdapter=" + blankAsKey(assignment == null ? "" : assignment.adapterImplementationType(), "-")
                + ":remoteAdapterRole=" + blankAsKey(assignment == null ? "" : assignment.adapterRole(), "-")
                + ":roleAdapterDeclared=" + roleAdapterDeclared
                + ":roleType=" + blankAsKey(roleType, "-")
                + ":roleAdapterProtocol=" + blankAsKey(roleAdapterProtocol, "-")
                + ":physicalRoleAdapterReady=" + ready
                + ":reasons=" + String.join("+", reasons);
        return new RemoteReferenceRoleAdapterPlan(plan.serviceName(), plan.path(), plan.participantType(),
                plan.participantCategory(),
                assignment == null ? "" : assignment.adapterImplementationType(),
                assignment == null ? "" : assignment.adapterRole(),
                assignmentExecutable, referenceImplementation, externalImplementation, roleAdapterDeclared,
                roleType, roleAdapterProtocol, ready, reasons, String.join("+", reasons), binding);
    }

    private List<String> remoteReferenceRoleAdapterReadinessReasons(
            boolean roleAdapterDeclared,
            String roleType,
            String roleAdapterProtocol,
            boolean protocolSupported,
            boolean assignmentExecutable,
            boolean externalImplementation,
            boolean referenceImplementation) {
        java.util.List<String> reasons = new java.util.ArrayList<>();
        if (roleAdapterDeclared && roleType != null && !roleType.isBlank()
                && roleAdapterProtocol != null && !roleAdapterProtocol.isBlank()
                && protocolSupported
                && assignmentExecutable && externalImplementation) {
            reasons.add("REMOTE_ROLE_ADAPTER_DECLARED");
            reasons.add("REMOTE_ROLE_TYPE_DECLARED");
            reasons.add("REMOTE_ROLE_ADAPTER_PROTOCOL_DECLARED");
            reasons.add("EXTERNAL_REMOTE_ADAPTER_BOUND");
            return List.copyOf(reasons);
        }
        if (!roleAdapterDeclared) {
            reasons.add("MISSING_REMOTE_ROLE_ADAPTER_DECLARATION");
        }
        if (roleType == null || roleType.isBlank()) {
            reasons.add("MISSING_REMOTE_ROLE_TYPE");
        }
        if (roleAdapterProtocol == null || roleAdapterProtocol.isBlank()) {
            reasons.add("MISSING_REMOTE_ROLE_ADAPTER_PROTOCOL");
        } else if (!protocolSupported) {
            reasons.add("UNSUPPORTED_REMOTE_ROLE_ADAPTER_PROTOCOL");
        }
        if (!assignmentExecutable) {
            reasons.add("REMOTE_ADAPTER_NOT_EXECUTABLE");
        }
        if (!externalImplementation) {
            reasons.add(referenceImplementation
                    ? "REMOTE_REFERENCE_STILL_REFERENCE_IMPLEMENTATION"
                    : "MISSING_EXTERNAL_REMOTE_ADAPTER");
        }
        return List.copyOf(reasons);
    }

    private static boolean remoteReferenceRoleAdapterProtocolSupported(String roleAdapterProtocol) {
        return "HTTP_JSON".equals(roleAdapterProtocol);
    }

    private boolean strictExternalRemoteAdapterClearsReviewPlan(RemoteReferenceRoleAdapterPlan plan) {
        return strictExternalRemoteAdapterEnabled()
                && plan != null
                && plan.physicalRoleAdapterReady()
                && plan.externalImplementation()
                && !plan.referenceImplementation();
    }

    private boolean strictExternalRemoteAdapterEnabled() {
        return physicalRuntime != null
                && physicalRuntime.configuration() != null
                && "true".equalsIgnoreCase(physicalRuntime.configuration().getOrDefault(
                "strictExternalRemoteAdapter", ""));
    }

    private List<ServiceParticipantRoleOnlyMigrationActionPlan> remoteReferenceRoleAdapterReviewPlans() {
        Map<String, ServiceParticipantRoleOnlyMigrationActionPlan> plansByPath =
                new java.util.LinkedHashMap<>();
        for (ServiceParticipantRoleOnlyMigrationActionPlan plan
                : directServiceParticipantRoleOnlyRemoteReferenceAdapterPlans()) {
            if (plan.path() != null && !plan.path().isBlank()) {
                plansByPath.putIfAbsent(plan.path(), plan);
            }
        }
        for (AppSemanticMigrationPlan migrationPlan : safeList(generatedAppSemanticMigrationPlans)) {
            if (!"SERVICE_NON_ROLE_PARTICIPANT".equals(migrationPlan.migrationType())) {
                continue;
            }
            List<String> paths = safeList(migrationPlan.paths());
            List<String> categories = safeList(migrationPlan.participantCategories());
            for (int index = 0; index < paths.size(); index++) {
                String path = paths.get(index);
                String category = index < categories.size() ? categories.get(index) : "";
                if (!"references".equals(category) || path == null || path.isBlank()) {
                    continue;
                }
                String participantType = serviceParticipantType(path);
                plansByPath.putIfAbsent(path, new ServiceParticipantRoleOnlyMigrationActionPlan(
                        migrationPlan.serviceName(), path, participantType, category,
                        "REFERENCE_TO_REMOTE_ROLE_ADAPTER", false, false, List.of(), List.of(), List.of(),
                        List.of(), false,
                        List.of("MISSING_ROLE_PARTICIPANT", "MISSING_TARGET_ROLE",
                                "REMOTE_ROLE_ADAPTER_REQUIRED"),
                        "MISSING_ROLE_PARTICIPANT+MISSING_TARGET_ROLE+REMOTE_ROLE_ADAPTER_REQUIRED",
                        "Keep the external reference behind a remote Role adapter instead of passing the reference as a service participant."));
            }
        }
        Map<String, RemoteServiceAssignment> remoteAssignmentsByService = safeList(remoteServiceAssignments).stream()
                .filter(assignment -> assignment != null && assignment.serviceName() != null)
                .collect(Collectors.toMap(RemoteServiceAssignment::serviceName,
                        Function.identity(), (left, right) -> right, LinkedHashMap::new));
        for (RoleParticipantRequestBindingPlan binding : safeList(roleParticipantRequestBindingPlans)) {
            RemoteServiceAssignment assignment = binding == null
                    ? null
                    : remoteAssignmentsByService.get(binding.serviceName());
            if (!remoteRoleAdapterRequestBindingReady(binding, assignment)) {
                continue;
            }
            String path = remoteRoleAdapterReviewPath(binding);
            plansByPath.putIfAbsent(path, new ServiceParticipantRoleOnlyMigrationActionPlan(
                    binding.serviceName(), path, binding.roleType(), "references",
                    "REFERENCE_TO_REMOTE_ROLE_ADAPTER", true, true,
                    List.of(binding.roleType()), List.of(path),
                    List.of(remoteRoleAdapterTargetRoleBinding(binding)), List.of(binding.roleType()), true,
                    List.of("REMOTE_ROLE_ADAPTER_DECLARED", "REMOTE_ROLE_TYPE_DECLARED",
                            "REMOTE_ROLE_ADAPTER_PROTOCOL_DECLARED"),
                    "REMOTE_ROLE_ADAPTER_DECLARED+REMOTE_ROLE_TYPE_DECLARED"
                            + "+REMOTE_ROLE_ADAPTER_PROTOCOL_DECLARED",
                    "Keep the external reference behind a remote Role adapter instead of passing the reference as a service participant."));
        }
        return List.copyOf(plansByPath.values());
    }

    private static boolean remoteRoleAdapterRequestBindingReady(RoleParticipantRequestBindingPlan binding,
                                                                RemoteServiceAssignment assignment) {
        Map<String, String> config = assignment == null || assignment.adapterConfiguration() == null
                ? Map.of()
                : assignment.adapterConfiguration();
        return binding != null
                && "REMOTE_ROLE_ADAPTER".equals(binding.identityBindingSource())
                && binding.complete()
                && binding.serviceName() != null && !binding.serviceName().isBlank()
                && binding.roleType() != null && !binding.roleType().isBlank()
                && binding.roleParameterName() != null && !binding.roleParameterName().isBlank()
                && assignment != null
                && assignment.externalImplementation()
                && assignment.adapterExecutable()
                && "true".equals(config.getOrDefault("roleAdapter", ""))
                && !config.getOrDefault("roleType", "").isBlank()
                && !config.getOrDefault("roleAdapterProtocol", "").isBlank();
    }

    private static String remoteRoleAdapterTargetRoleBinding(RoleParticipantRequestBindingPlan binding) {
        if (binding == null) {
            return "";
        }
        return blankAsKey(binding.serviceName(), "-")
                + ":" + blankAsKey(binding.roleType(), "-")
                + ":parameter=" + blankAsKey(binding.roleParameterName(), "-")
                + ":source=" + blankAsKey(binding.identityBindingSource(), "-");
    }

    private static String remoteRoleAdapterReviewPath(RoleParticipantRequestBindingPlan binding) {
        if (binding == null) {
            return "";
        }
        String serviceName = blankAsKey(binding.serviceName(), "-");
        String parameterName = blankAsKey(binding.roleParameterName(), "-");
        String roleType = blankAsKey(binding.roleType(), "-");
        String qualifiedParameter = parameterName.startsWith(serviceName + ".")
                ? parameterName
                : serviceName + "." + parameterName;
        return qualifiedParameter.endsWith(":" + roleType)
                ? qualifiedParameter
                : qualifiedParameter + ":" + roleType;
    }

    private String serviceParticipantType(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int colon = path.lastIndexOf(':');
        if (colon < 0 || colon == path.length() - 1) {
            return "";
        }
        String typeName = path.substring(colon + 1).trim();
        while (typeName.endsWith("[]")) {
            typeName = typeName.substring(0, typeName.length() - 2);
        }
        return typeName;
    }

    private static String blankAsKey(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @JsonProperty(value = "appSemanticMigrationStrictErrorsByKey", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<String>> appSemanticMigrationStrictErrorsByKey() {
        DeploymentValidationPlan plan = deploymentValidationPlansByCategory == null
                ? null
                : deploymentValidationPlansByCategory.get("APP_SEMANTIC_MIGRATION");
        if (plan == null || plan.metadata() == null || plan.metadata().isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> errorsByKey = new java.util.LinkedHashMap<>();
        for (String key : appSemanticMigrationStrictErrorKeys()) {
            List<String> errors = splitDeploymentErrors(plan.metadata().get(key));
            if (!errors.isEmpty()) {
                errorsByKey.put(key, errors);
            }
        }
        return immutableListMap(errorsByKey);
    }

    @JsonProperty(value = "appSemanticMigrationStrictErrorsByAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<String>> appSemanticMigrationStrictErrorsByAction() {
        Map<String, List<String>> errorsByAction = new java.util.LinkedHashMap<>();
        for (List<String> errors : appSemanticMigrationStrictErrorsByKey().values()) {
            for (String error : safeList(errors)) {
                String action = strictErrorAction(error);
                errorsByAction.computeIfAbsent(action, ignored -> new java.util.ArrayList<>()).add(error);
            }
        }
        return immutableListMap(errorsByAction);
    }

    @JsonProperty(value = "appSemanticMigrationStrictErrorsByReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<String>> appSemanticMigrationStrictErrorsByReason() {
        Map<String, List<String>> errorsByReason = new java.util.LinkedHashMap<>();
        for (List<String> errors : appSemanticMigrationStrictErrorsByKey().values()) {
            for (String error : safeList(errors)) {
                String reason = strictErrorReason(error);
                errorsByReason.computeIfAbsent(reason, ignored -> new java.util.ArrayList<>()).add(error);
            }
        }
        return immutableListMap(errorsByReason);
    }

    @JsonProperty(value = "appSemanticMigrationStrictErrorIndex",
            access = JsonProperty.Access.READ_ONLY)
    public AppSemanticMigrationStrictErrorIndex appSemanticMigrationStrictErrorIndex() {
        Map<String, List<String>> byKey = appSemanticMigrationStrictErrorsByKey();
        Map<String, List<String>> byAction = appSemanticMigrationStrictErrorsByAction();
        Map<String, List<String>> byReason = appSemanticMigrationStrictErrorsByReason();
        Map<String, List<String>> byReasonAction = appSemanticMigrationStrictErrorsByReasonAction(byKey);
        int totalErrorCount = byKey.values().stream()
                .mapToInt(List::size)
                .sum();
        return new AppSemanticMigrationStrictErrorIndex(totalErrorCount,
                byKey.size(),
                byAction.size(),
                byReason.size(),
                byReasonAction.size(),
                byKey,
                byAction,
                byReason,
                byReasonAction,
                strictErrorCountMap(byKey),
                strictErrorCountMap(byAction),
                strictErrorCountMap(byReason),
                strictErrorCountMap(byReasonAction));
    }

    @JsonProperty(value = "appSemanticMigrationStrictErrorsByReasonAction",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<String>> appSemanticMigrationStrictErrorsByReasonAction() {
        return appSemanticMigrationStrictErrorIndex().byReasonAction();
    }

    private static Map<String, List<String>> appSemanticMigrationStrictErrorsByReasonAction(
            Map<String, List<String>> errorsByKey) {
        Map<String, List<String>> errorsByReasonAction = new java.util.LinkedHashMap<>();
        if (errorsByKey == null || errorsByKey.isEmpty()) {
            return Map.of();
        }
        for (List<String> errors : errorsByKey.values()) {
            for (String error : safeList(errors)) {
                String reasonAction = strictErrorReason(error) + "->" + strictErrorAction(error);
                errorsByReasonAction.computeIfAbsent(reasonAction, ignored -> new java.util.ArrayList<>()).add(error);
            }
        }
        return immutableListMap(errorsByReasonAction);
    }

    @JsonProperty(value = "quantumServiceExternalImplementationStrictErrorsByReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<String>> quantumServiceExternalImplementationStrictErrorsByReason() {
        DeploymentValidationPlan plan = deploymentValidationPlansByCategory == null
                ? null
                : deploymentValidationPlansByCategory.get("APP_SEMANTIC_MIGRATION");
        if (plan == null || plan.metadata() == null || plan.metadata().isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> errorsByReason = new java.util.LinkedHashMap<>();
        for (String error : splitDeploymentErrors(
                plan.metadata().get("quantumServiceExternalImplementationStrictErrors"))) {
            String reason = strictErrorReason(error);
            errorsByReason.computeIfAbsent(reason, ignored -> new java.util.ArrayList<>()).add(error);
        }
        return immutableListMap(errorsByReason);
    }

    @JsonProperty(value = "quantumServiceInternalProtocolStrictErrorsByReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<String>> quantumServiceInternalProtocolStrictErrorsByReason() {
        DeploymentValidationPlan plan = deploymentValidationPlansByCategory == null
                ? null
                : deploymentValidationPlansByCategory.get("APP_SEMANTIC_MIGRATION");
        if (plan == null || plan.metadata() == null || plan.metadata().isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> errorsByReason = new java.util.LinkedHashMap<>();
        for (String error : splitDeploymentErrors(
                plan.metadata().get("quantumServiceInternalProtocolStrictErrors"))) {
            String reason = strictErrorReason(error);
            errorsByReason.computeIfAbsent(reason, ignored -> new java.util.ArrayList<>()).add(error);
        }
        return immutableListMap(errorsByReason);
    }

    @JsonProperty(value = "currentSnapshotAppDdlKeyStrictErrorsByReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<String>> currentSnapshotAppDdlKeyStrictErrorsByReason() {
        DeploymentValidationPlan plan = deploymentValidationPlansByCategory == null
                ? null
                : deploymentValidationPlansByCategory.get("APP_SEMANTIC_MIGRATION");
        if (plan == null || plan.metadata() == null || plan.metadata().isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> errorsByReason = new java.util.LinkedHashMap<>();
        for (String error : splitDeploymentErrors(plan.metadata().get("currentSnapshotAppDdlKeyStrictErrors"))) {
            String reason = strictErrorReason(error);
            errorsByReason.computeIfAbsent(reason, ignored -> new java.util.ArrayList<>()).add(error);
        }
        return immutableListMap(errorsByReason);
    }

    @JsonProperty(value = "appDdlPlatformPrimaryKeyStrictErrorsByReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<String>> appDdlPlatformPrimaryKeyStrictErrorsByReason() {
        return deploymentStrictErrorsByReason("appDdlPlatformPrimaryKeyStrictErrors");
    }

    @JsonProperty(value = "platformColumnBusinessShadowStrictErrorsByReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<String>> platformColumnBusinessShadowStrictErrorsByReason() {
        return deploymentStrictErrorsByReason("platformColumnBusinessShadowStrictErrors");
    }

    @JsonProperty(value = "repositoryReplacementStrictErrorsByReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<String>> repositoryReplacementStrictErrorsByReason() {
        return deploymentStrictErrorsByReason("repositoryReplacementStrictErrors");
    }

    @JsonProperty(value = "snapshotReplacementStrictErrorsByReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<String>> snapshotReplacementStrictErrorsByReason() {
        return deploymentStrictErrorsByReason("snapshotReplacementStrictErrors");
    }

    private Map<String, List<String>> deploymentStrictErrorsByReason(String metadataKey) {
        DeploymentValidationPlan plan = deploymentValidationPlansByCategory == null
                ? null
                : deploymentValidationPlansByCategory.get("APP_SEMANTIC_MIGRATION");
        if (plan == null || plan.metadata() == null || plan.metadata().isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> errorsByReason = new java.util.LinkedHashMap<>();
        for (String error : splitDeploymentErrors(plan.metadata().get(metadataKey))) {
            String reason = strictErrorReason(error);
            errorsByReason.computeIfAbsent(reason, ignored -> new java.util.ArrayList<>()).add(error);
        }
        return immutableListMap(errorsByReason);
    }

    @JsonProperty(value = "remoteExternalEndpointStrictErrorsByReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<String>> remoteExternalEndpointStrictErrorsByReason() {
        DeploymentValidationPlan plan = deploymentValidationPlansByCategory == null
                ? null
                : deploymentValidationPlansByCategory.get("APP_SEMANTIC_MIGRATION");
        if (plan == null || plan.metadata() == null || plan.metadata().isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> errorsByReason = new java.util.LinkedHashMap<>();
        for (String error : splitDeploymentErrors(plan.metadata().get("remoteExternalEndpointStrictErrors"))) {
            String reason = strictErrorReason(error);
            errorsByReason.computeIfAbsent(reason, ignored -> new java.util.ArrayList<>()).add(error);
        }
        return immutableListMap(errorsByReason);
    }

    @JsonProperty(value = "physicalQuantumPlansByExternalImplementationReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalQuantumPlan>> physicalQuantumPlansByExternalImplementationReadiness() {
        return physicalQuantumReadinessIndex().byExternalImplementationReadiness();
    }

    @JsonProperty(value = "physicalQuantumPlansByExternalImplementationReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalQuantumPlan>> physicalQuantumPlansByExternalImplementationReason() {
        return physicalQuantumReadinessIndex().byExternalImplementationReason();
    }

    @JsonProperty(value = "quantumAccessPlansByMissingContractReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByMissingContractReason() {
        Map<String, List<QuantumAccessPlan>> byReason = new java.util.LinkedHashMap<>();
        for (QuantumAccessPlan plan : safeList(quantumAccessPlans)) {
            if (plan == null) {
                continue;
            }
            List<String> reasons = safeList(plan.missingContractReasons());
            if (reasons.isEmpty()) {
                byReason.computeIfAbsent("NO_MISSING_CONTRACT_REASON",
                        ignored -> new java.util.ArrayList<>()).add(plan);
                continue;
            }
            for (String reason : reasons) {
                String key = reason == null || reason.isBlank()
                        ? "UNKNOWN_MISSING_CONTRACT_REASON"
                        : reason;
                byReason.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(plan);
            }
        }
        return immutableListMap(byReason);
    }

    @JsonProperty(value = "quantumAccessPlansByAccessMode", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByAccessMode() {
        Map<String, List<QuantumAccessPlan>> byAccessMode = new java.util.LinkedHashMap<>();
        for (QuantumAccessPlan plan : safeList(quantumAccessPlans)) {
            if (plan == null) {
                continue;
            }
            addListIndexValues(byAccessMode, plan.accessModes(), plan, "NO_ACCESS_MODE");
        }
        return immutableListMap(byAccessMode);
    }

    @JsonProperty(value = "quantumAccessPlansByTimeoutMillis", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByTimeoutMillis() {
        Map<String, List<QuantumAccessPlan>> byTimeoutMillis = new java.util.LinkedHashMap<>();
        for (QuantumAccessPlan plan : safeList(quantumAccessPlans)) {
            if (plan == null) {
                continue;
            }
            addListIndexValues(byTimeoutMillis, plan.timeoutMillisValues(), plan, "NO_TIMEOUT");
        }
        return immutableListMap(byTimeoutMillis);
    }

    @JsonProperty(value = "quantumAccessPlansByAdapter", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByAdapter() {
        Map<String, List<QuantumAccessPlan>> byAdapter = new java.util.LinkedHashMap<>();
        for (QuantumAccessPlan plan : safeList(quantumAccessPlans)) {
            if (plan == null) {
                continue;
            }
            addListIndexValues(byAdapter, plan.adapterNames(), plan, "NO_QUANTUM_ADAPTER");
        }
        return immutableListMap(byAdapter);
    }

    @JsonProperty(value = "quantumAccessPlansByExecutionOwner", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByExecutionOwner() {
        Map<String, List<QuantumAccessPlan>> byExecutionOwner = new java.util.LinkedHashMap<>();
        for (QuantumAccessPlan plan : safeList(quantumAccessPlans)) {
            if (plan == null) {
                continue;
            }
            addListIndexValues(byExecutionOwner, plan.executionOwners(), plan, "NO_EXECUTION_OWNER");
        }
        return immutableListMap(byExecutionOwner);
    }

    @JsonProperty(value = "quantumAccessPlansByBackendBindingRole", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByBackendBindingRole() {
        Map<String, List<QuantumAccessPlan>> byBackendBindingRole = new java.util.LinkedHashMap<>();
        for (QuantumAccessPlan plan : safeList(quantumAccessPlans)) {
            if (plan == null) {
                continue;
            }
            addListIndexValues(byBackendBindingRole, plan.backendBindingRoles(), plan, "NO_BACKEND_BINDING_ROLE");
        }
        return immutableListMap(byBackendBindingRole);
    }

    @JsonProperty(value = "quantumAccessPlansByIdempotencyBoundary", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByIdempotencyBoundary() {
        Map<String, List<QuantumAccessPlan>> byBoundary = new java.util.LinkedHashMap<>();
        for (QuantumAccessPlan plan : safeList(quantumAccessPlans)) {
            if (plan == null) {
                continue;
            }
            addToIndex(byBoundary, indexKey(plan.idempotencyBoundary()), plan);
        }
        return immutableListMap(byBoundary);
    }

    @JsonProperty(value = "quantumAccessPlansByReversalOwner", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByReversalOwner() {
        Map<String, List<QuantumAccessPlan>> byReversalOwner = new java.util.LinkedHashMap<>();
        for (QuantumAccessPlan plan : safeList(quantumAccessPlans)) {
            if (plan == null) {
                continue;
            }
            addListIndexValues(byReversalOwner, plan.reversalOwners(), plan, "NO_REVERSAL_OWNER");
        }
        return immutableListMap(byReversalOwner);
    }

    @JsonProperty(value = "quantumAccessPlansByReversalExecution", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByReversalExecution() {
        Map<String, List<QuantumAccessPlan>> byReversalExecution = new java.util.LinkedHashMap<>();
        for (QuantumAccessPlan plan : safeList(quantumAccessPlans)) {
            if (plan == null) {
                continue;
            }
            addListIndexValues(byReversalExecution, plan.reversalExecutions(), plan, "NO_REVERSAL_EXECUTION");
        }
        return immutableListMap(byReversalExecution);
    }

    @JsonProperty(value = "quantumAccessPlansByResponsibilityReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByResponsibilityReadiness() {
        return quantumAccessResponsibilityIndex().byReadiness();
    }

    @JsonProperty(value = "quantumAccessPlansByResponsibilityMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByResponsibilityMissingReason() {
        return quantumAccessResponsibilityIndex().byMissingReason();
    }

    @JsonProperty(value = "quantumAccessResponsibilityIndex", access = JsonProperty.Access.READ_ONLY)
    public QuantumAccessResponsibilityIndex quantumAccessResponsibilityIndex() {
        List<QuantumAccessPlan> plans = safeList(quantumAccessPlans).stream()
                .filter(plan -> plan != null)
                .map(GeneratedServiceCatalog::enrichQuantumAccessPlan)
                .toList();
        if (plans.isEmpty()) {
            return new QuantumAccessResponsibilityIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<QuantumAccessPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<QuantumAccessPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<QuantumAccessPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<QuantumAccessPlan>> byImplementationStrategy = new LinkedHashMap<>();
        Map<String, List<QuantumAccessPlan>> byImplementationStrategyShape = new LinkedHashMap<>();
        Map<String, List<QuantumAccessPlan>> byImplementationStrategySet = new LinkedHashMap<>();
        Map<String, List<QuantumAccessPlan>> byIdempotencyOwner = new LinkedHashMap<>();
        Map<String, List<QuantumAccessPlan>> byReversalOwner = new LinkedHashMap<>();
        Map<String, List<QuantumAccessPlan>> bySnapshotBoundary = new LinkedHashMap<>();
        Map<String, List<QuantumAccessPlan>> byResponsibilityBoundary = new LinkedHashMap<>();
        Map<String, List<QuantumAccessPlan>> byRecoveryOwner = new LinkedHashMap<>();
        Map<String, List<QuantumAccessPlan>> byAttribute = new LinkedHashMap<>();
        Map<String, List<QuantumAccessPlan>> byServiceAttributePath = new LinkedHashMap<>();
        Map<String, List<QuantumAccessPlan>> byAccessContract = new LinkedHashMap<>();
        Map<String, List<QuantumAccessPlan>> byServiceInternalProtocol = new LinkedHashMap<>();
        for (QuantumAccessPlan plan : plans) {
            addToIndex(byReadiness, indexKey(plan.responsibilityReadiness()), plan);
            addListIndexValues(byMissingReason, plan.responsibilityMissingReasons(), plan,
                    "NO_MISSING_RESPONSIBILITY_REASON");
            addListIndexValues(byAction, plan.responsibilityActions(), plan, "NO_ACTION_REQUIRED");
            addListIndexValues(byImplementationStrategy, plan.implementationStrategies(), plan,
                    "NO_IMPLEMENTATION_STRATEGY");
            addToIndex(byImplementationStrategyShape, quantumAccessImplementationStrategyShape(plan), plan);
            addToIndex(byImplementationStrategySet, indexKey(plan.implementationStrategySet()), plan);
            addToIndex(byIdempotencyOwner, indexKey(plan.idempotencyOwner()), plan);
            addListIndexValues(byReversalOwner, plan.reversalOwners(), plan, "NO_REVERSAL_OWNER");
            addToIndex(bySnapshotBoundary, indexKey(plan.snapshotBoundary()), plan);
            addToIndex(byResponsibilityBoundary, indexKey(plan.responsibilityBoundary()), plan);
            addToIndex(byRecoveryOwner, indexKey(plan.recoveryOwner()), plan);
            addListIndexValues(byAttribute, plan.attributeIds(), plan, "NO_QUANTUM_ATTRIBUTE");
            addToIndex(byServiceInternalProtocol, indexKey(plan.serviceInternalProtocolContract()), plan);
            for (String serviceAttributePath : quantumAccessServiceAttributePaths(plan)) {
                addToIndex(byServiceAttributePath, serviceAttributePath, plan);
                addToIndex(byAccessContract, quantumAccessContractKey(plan, serviceAttributePath), plan);
            }
        }
        return new QuantumAccessResponsibilityIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byImplementationStrategy),
                immutableListMap(byImplementationStrategyShape),
                immutableListMap(byImplementationStrategySet),
                immutableListMap(byIdempotencyOwner),
                immutableListMap(byReversalOwner),
                immutableListMap(bySnapshotBoundary),
                immutableListMap(byResponsibilityBoundary),
                immutableListMap(byRecoveryOwner),
                immutableListMap(byAttribute),
                immutableListMap(byServiceAttributePath),
                immutableListMap(byAccessContract),
                immutableListMap(byServiceInternalProtocol));
    }

    public static QuantumAccessPlan enrichQuantumAccessPlan(QuantumAccessPlan plan) {
        if (plan == null) {
            return null;
        }
        List<String> missingReasons = quantumAccessResponsibilityMissingReasons(plan);
        String readiness = missingReasons.isEmpty() ? "RESPONSIBILITY_COMPLETE" : "RESPONSIBILITY_INCOMPLETE";
        List<String> normalizedMissingReasons = missingReasons.isEmpty()
                ? List.of("NO_MISSING_RESPONSIBILITY_REASON")
                : missingReasons;
        List<String> actions = quantumAccessResponsibilityActions(missingReasons);
        return new QuantumAccessPlan(plan.serviceName(),
                plan.transactionMode(),
                plan.transactionResourceId(),
                plan.attributeIds(),
                plan.storeNames(),
                plan.adapterNames(),
                plan.implementationStrategies(),
                plan.singleImplementationStrategy(),
                plan.mixedImplementationStrategy(),
                plan.accessModes(),
                plan.executionOwners(),
                plan.backendBindingRoles(),
                plan.idempotencyMode(),
                plan.idempotencyOwner(),
                plan.idempotencyBoundary(),
                plan.idempotencyKeyAvailable(),
                plan.reversalModes(),
                plan.reversalOwners(),
                plan.reversalExecutions(),
                plan.snapshotBoundary(),
                plan.recoveryOwner(),
                plan.objectSnapshotManaged(),
                plan.objectCompensationManaged(),
                plan.objectRecoveryRequired(),
                plan.objectSnapshotBypassed(),
                plan.objectCompensationBypassed(),
                plan.serviceInternalSnapshotManaged(),
                plan.serviceInternalIdempotencyManaged(),
                plan.serviceInternalReversalManaged(),
                plan.negativeDeltaReversal(),
                plan.serviceReversal(),
                plan.endpoints(),
                plan.timeoutMillisValues(),
                plan.datasourceUrls(),
                plan.accessContractAvailable(),
                plan.missingContractReasons(),
                readiness,
                normalizedMissingReasons,
                actions,
                quantumAccessImplementationStrategySetKey(plan),
                quantumAccessResponsibilityBoundaryKey(plan),
                quantumAccessPlanContractKey(plan),
                quantumAccessServiceInternalProtocolKey(plan));
    }

    @JsonProperty(value = "quantumAccessPlansByResponsibilityAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByResponsibilityAction() {
        return quantumAccessResponsibilityIndex().byAction();
    }

    @JsonProperty(value = "quantumAccessPlansByResponsibilityImplementationStrategy",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByResponsibilityImplementationStrategy() {
        return quantumAccessResponsibilityIndex().byImplementationStrategy();
    }

    @JsonProperty(value = "quantumAccessPlansByResponsibilityImplementationStrategyShape",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByResponsibilityImplementationStrategyShape() {
        return quantumAccessResponsibilityIndex().byImplementationStrategyShape();
    }

    @JsonProperty(value = "quantumAccessPlansByImplementationStrategySet", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByImplementationStrategySet() {
        return quantumAccessResponsibilityIndex().byImplementationStrategySet();
    }

    @JsonProperty(value = "quantumAccessPlansByResponsibilityIdempotencyOwner",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByResponsibilityIdempotencyOwner() {
        return quantumAccessResponsibilityIndex().byIdempotencyOwner();
    }

    @JsonProperty(value = "quantumAccessPlansByResponsibilityReversalOwner",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByResponsibilityReversalOwner() {
        return quantumAccessResponsibilityIndex().byReversalOwner();
    }

    @JsonProperty(value = "quantumAccessPlansByResponsibilitySnapshotBoundary",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByResponsibilitySnapshotBoundary() {
        return quantumAccessResponsibilityIndex().bySnapshotBoundary();
    }

    @JsonProperty(value = "quantumAccessPlansByResponsibilityBoundary", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByResponsibilityBoundary() {
        return quantumAccessResponsibilityIndex().byResponsibilityBoundary();
    }

    @JsonProperty(value = "quantumAccessPlansByResponsibilityRecoveryOwner",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByResponsibilityRecoveryOwner() {
        return quantumAccessResponsibilityIndex().byRecoveryOwner();
    }

    @JsonProperty(value = "quantumAccessPlansByAttribute", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByAttribute() {
        return quantumAccessResponsibilityIndex().byAttribute();
    }

    @JsonProperty(value = "quantumAccessPlansByServiceAttributePath", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByServiceAttributePath() {
        return quantumAccessResponsibilityIndex().byServiceAttributePath();
    }

    @JsonProperty(value = "quantumAccessPlansByAccessContract", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByAccessContract() {
        return quantumAccessResponsibilityIndex().byAccessContract();
    }

    @JsonProperty(value = "quantumAccessPlansByServiceInternalProtocol", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumAccessPlansByServiceInternalProtocol() {
        return quantumAccessResponsibilityIndex().byServiceInternalProtocol();
    }

    @JsonProperty(value = "quantumConservationPlanIndex", access = JsonProperty.Access.READ_ONLY)
    public QuantumConservationPlanIndex quantumConservationPlanIndex() {
        List<QuantumConservationPlan> plans = quantumConservationPlans();
        if (plans.isEmpty()) {
            return new QuantumConservationPlanIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<QuantumConservationPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<QuantumConservationPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<QuantumConservationPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<QuantumConservationPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<QuantumConservationPlan>> byResource = new LinkedHashMap<>();
        Map<String, List<QuantumConservationPlan>> byService = new LinkedHashMap<>();
        Map<String, List<QuantumConservationPlan>> byAttribute = new LinkedHashMap<>();
        Map<String, List<QuantumConservationPlan>> byAttributeSet = new LinkedHashMap<>();
        Map<String, List<QuantumConservationPlan>> byConservationContract = new LinkedHashMap<>();
        Map<String, List<QuantumConservationPlan>> byImplementationStrategySet = new LinkedHashMap<>();
        Map<String, List<QuantumConservationPlan>> byIdempotencyOwner = new LinkedHashMap<>();
        Map<String, List<QuantumConservationPlan>> byReversalOwner = new LinkedHashMap<>();
        Map<String, List<QuantumConservationPlan>> byRecoveryOwner = new LinkedHashMap<>();
        Map<String, List<QuantumConservationPlan>> byRuntimeResponsibilityContract = new LinkedHashMap<>();
        for (QuantumConservationPlan plan : plans) {
            addToIndex(byReadiness, indexKey(plan.readiness()), plan);
            addListIndexValues(byReadyReason, plan.readyReasons(), plan,
                    "NO_READY_CONSERVATION_REASON");
            addListIndexValues(byMissingReason, plan.missingReasons(), plan,
                    "NO_MISSING_CONSERVATION_REASON");
            if (plan.missingReasons().isEmpty()) {
                addToIndex(byMissingReason, "NO_MISSING_REASON", plan);
            }
            addListIndexValues(byAction, plan.actions(), plan, "NO_ACTION_REQUIRED");
            addToIndex(byResource, indexKey(plan.resourceId()), plan);
            addToIndex(byService, indexKey(plan.serviceName()), plan);
            addListIndexValues(byAttribute, plan.attributeIds(), plan, "NO_QUANTUM_ATTRIBUTE");
            addToIndex(byAttributeSet, quantumConservationAttributeSetKey(plan.attributeIds()), plan);
            addToIndex(byConservationContract, indexKey(plan.conservationContract()), plan);
            addToIndex(byImplementationStrategySet, indexKey(plan.implementationStrategySet()), plan);
            addToIndex(byIdempotencyOwner, indexKey(plan.idempotencyOwner()), plan);
            addToIndex(byReversalOwner, indexKey(plan.reversalOwner()), plan);
            addToIndex(byRecoveryOwner, indexKey(plan.recoveryOwner()), plan);
            addToIndex(byRuntimeResponsibilityContract, indexKey(plan.runtimeResponsibilityContract()), plan);
        }
        return new QuantumConservationPlanIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byResource),
                immutableListMap(byService),
                immutableListMap(byAttribute),
                immutableListMap(byAttributeSet),
                immutableListMap(byConservationContract),
                immutableListMap(byImplementationStrategySet),
                immutableListMap(byIdempotencyOwner),
                immutableListMap(byReversalOwner),
                immutableListMap(byRecoveryOwner),
                immutableListMap(byRuntimeResponsibilityContract));
    }

    @JsonProperty(value = "quantumConservationPlansByReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumConservationPlan>> quantumConservationPlansByReadiness() {
        return quantumConservationPlanIndex().byReadiness();
    }

    @JsonProperty(value = "quantumConservationPlansByReadyReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumConservationPlan>> quantumConservationPlansByReadyReason() {
        return quantumConservationPlanIndex().byReadyReason();
    }

    @JsonProperty(value = "quantumConservationPlansByMissingReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumConservationPlan>> quantumConservationPlansByMissingReason() {
        return quantumConservationPlanIndex().byMissingReason();
    }

    @JsonProperty(value = "quantumConservationPlansByAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumConservationPlan>> quantumConservationPlansByAction() {
        return quantumConservationPlanIndex().byAction();
    }

    @JsonProperty(value = "quantumConservationPlansByResource", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumConservationPlan>> quantumConservationPlansByResource() {
        return quantumConservationPlanIndex().byResource();
    }

    @JsonProperty(value = "quantumConservationPlansByService", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumConservationPlan>> quantumConservationPlansByService() {
        return quantumConservationPlanIndex().byService();
    }

    @JsonProperty(value = "quantumConservationPlansByAttribute", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumConservationPlan>> quantumConservationPlansByAttribute() {
        return quantumConservationPlanIndex().byAttribute();
    }

    @JsonProperty(value = "quantumConservationPlansByAttributeSet", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumConservationPlan>> quantumConservationPlansByAttributeSet() {
        return quantumConservationPlanIndex().byAttributeSet();
    }

    @JsonProperty(value = "quantumConservationPlansByConservationContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumConservationPlan>> quantumConservationPlansByConservationContract() {
        return quantumConservationPlanIndex().byConservationContract();
    }

    @JsonProperty(value = "quantumConservationPlansByImplementationStrategySet",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumConservationPlan>> quantumConservationPlansByImplementationStrategySet() {
        return quantumConservationPlanIndex().byImplementationStrategySet();
    }

    @JsonProperty(value = "quantumConservationPlansByIdempotencyOwner",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumConservationPlan>> quantumConservationPlansByIdempotencyOwner() {
        return quantumConservationPlanIndex().byIdempotencyOwner();
    }

    @JsonProperty(value = "quantumConservationPlansByReversalOwner", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumConservationPlan>> quantumConservationPlansByReversalOwner() {
        return quantumConservationPlanIndex().byReversalOwner();
    }

    @JsonProperty(value = "quantumConservationPlansByRecoveryOwner", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumConservationPlan>> quantumConservationPlansByRecoveryOwner() {
        return quantumConservationPlanIndex().byRecoveryOwner();
    }

    @JsonProperty(value = "quantumConservationPlansByRuntimeResponsibilityContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumConservationPlan>> quantumConservationPlansByRuntimeResponsibilityContract() {
        return quantumConservationPlanIndex().byRuntimeResponsibilityContract();
    }

    @JsonProperty(value = "quantumConservationPlans", access = JsonProperty.Access.READ_ONLY)
    public List<QuantumConservationPlan> quantumConservationPlans() {
        List<QuantumConservationPlan> plans = new ArrayList<>();
        Map<String, List<QuantumAccessPlan>> accessPlansByService =
                quantumAccessPlansByService == null ? Map.of() : quantumAccessPlansByService;
        for (GeneratedServiceMetadata service : safeList(services)) {
            if (service == null || service.transactionMode() != TransactionMode.QUANTUM) {
                continue;
            }
            QuantumAccessPlan accessPlan =
                    firstQuantumAccessPlanForService(accessPlansByService, service.serviceName());
            List<String> attributes = safeList(service.quantumAttributeIds()).stream()
                    .filter(attribute -> attribute != null && !attribute.isBlank())
                    .distinct()
                    .toList();
            List<String> missingReasons = quantumConservationMissingReasons(service, attributes);
            String readiness = missingReasons.isEmpty()
                    ? "CONSERVATION_DECLARATION_READY"
                    : "CONSERVATION_DECLARATION_INCOMPLETE";
            List<String> readyReasons = quantumConservationReadyReasons(service, attributes,
                    accessPlan, missingReasons);
            List<String> actions = missingReasons.isEmpty()
                    ? List.of("NO_ACTION_REQUIRED")
                    : quantumConservationActions(missingReasons);
            String conservationContract = quantumConservationContract(service, attributes, readiness);
            String implementationStrategySet = quantumAccessImplementationStrategySetKey(accessPlan);
            String idempotencyOwner = accessPlan == null
                    ? "NO_IDEMPOTENCY_OWNER"
                    : indexKey(accessPlan.idempotencyOwner());
            String reversalOwner = quantumConservationReversalOwnerKey(accessPlan);
            String snapshotBoundary = accessPlan == null
                    ? "NO_SNAPSHOT_BOUNDARY"
                    : indexKey(accessPlan.snapshotBoundary());
            String recoveryOwner = accessPlan == null
                    ? "NO_RECOVERY_OWNER"
                    : indexKey(accessPlan.recoveryOwner());
            boolean negativeDeltaReversal = accessPlan != null && accessPlan.negativeDeltaReversal();
            plans.add(new QuantumConservationPlan(service.serviceName(),
                    service.serviceKind(),
                    service.transactionMode(),
                    service.transactionResourceId(),
                    attributes,
                    attributes.size(),
                    attributes.size() == 2,
                    attributes.size() >= 2,
                    readiness,
                    readyReasons,
                    missingReasons,
                    actions,
                    conservationContract,
                    accessPlan == null ? List.of() : accessPlan.implementationStrategies(),
                    implementationStrategySet,
                    idempotencyOwner,
                    reversalOwner,
                    snapshotBoundary,
                    recoveryOwner,
                    negativeDeltaReversal,
                    quantumConservationRuntimeResponsibilityContract(conservationContract,
                            implementationStrategySet,
                            idempotencyOwner,
                            reversalOwner,
                            snapshotBoundary,
                            recoveryOwner,
                            negativeDeltaReversal)));
        }
        return List.copyOf(plans);
    }

    private static QuantumAccessPlan firstQuantumAccessPlanForService(
            Map<String, List<QuantumAccessPlan>> accessPlansByService,
            String serviceName) {
        return safeList(accessPlansByService == null ? null : accessPlansByService.get(indexKey(serviceName))).stream()
                .filter(plan -> plan != null)
                .findFirst()
                .orElse(null);
    }

    private static String quantumAccessImplementationStrategyShape(QuantumAccessPlan plan) {
        if (plan == null || safeList(plan.implementationStrategies()).isEmpty()) {
            return "NO_IMPLEMENTATION_STRATEGY";
        }
        if (plan.mixedImplementationStrategy()) {
            return "MIXED_IMPLEMENTATION_STRATEGY";
        }
        if (plan.singleImplementationStrategy()) {
            return "SINGLE_IMPLEMENTATION_STRATEGY";
        }
        return "UNKNOWN_IMPLEMENTATION_STRATEGY_SHAPE";
    }

    private static String quantumAccessImplementationStrategySetKey(QuantumAccessPlan plan) {
        List<String> strategies = safeList(plan == null ? null : plan.implementationStrategies()).stream()
                .filter(strategy -> !isBlank(strategy))
                .distinct()
                .toList();
        if (strategies.isEmpty()) {
            return "NO_IMPLEMENTATION_STRATEGY";
        }
        return String.join("+", strategies);
    }

    private static List<String> quantumConservationMissingReasons(GeneratedServiceMetadata service,
                                                                  List<String> attributes) {
        List<String> missing = new ArrayList<>();
        if (service == null || isBlank(service.transactionResourceId())) {
            missing.add("resource");
        }
        int attributeCount = safeList(attributes).size();
        if (attributeCount == 0) {
            missing.add("attributePair");
        } else if (attributeCount == 1) {
            missing.add("oppositeAttribute");
        }
        return List.copyOf(missing);
    }

    private static List<String> quantumConservationReadyReasons(GeneratedServiceMetadata service,
                                                                List<String> attributes,
                                                                QuantumAccessPlan accessPlan,
                                                                List<String> missingReasons) {
        List<String> reasons = new ArrayList<>();
        if (service != null && !isBlank(service.serviceName())) {
            reasons.add("quantumService");
        }
        if (service != null && !isBlank(service.transactionResourceId())) {
            reasons.add("resourceDeclared");
        }
        List<String> safeAttributes = safeList(attributes);
        if (!safeAttributes.isEmpty()) {
            reasons.add("quantumAttributesDeclared");
        }
        if (safeAttributes.size() >= 2) {
            reasons.add("conservationAttributeSet");
        }
        if (safeAttributes.size() == 2) {
            reasons.add("pairedQuantumAttributes");
        }
        if (accessPlan != null) {
            reasons.add("quantumAccessResponsibilityBound");
        }
        if (safeList(missingReasons).isEmpty()) {
            reasons.add("conservationDeclarationReady");
        }
        return List.copyOf(reasons);
    }

    private static List<String> quantumConservationActions(List<String> missingReasons) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : safeList(missingReasons)) {
            switch (reason) {
                case "resource" -> actions.add("declareQuantumResourceName");
                case "attributePair" -> actions.add("declareQuantumConservationAttributePair");
                case "oppositeAttribute" -> actions.add("declareOppositeQuantumAttribute");
                default -> actions.add("reviewQuantumConservationDeclaration");
            }
        }
        return List.copyOf(actions);
    }

    private static String quantumConservationContract(GeneratedServiceMetadata service,
                                                      List<String> attributes,
                                                      String readiness) {
        String serviceName = service == null ? "" : service.serviceName();
        String resource = service == null ? "" : service.transactionResourceId();
        return indexKey(serviceName)
                + ":" + indexKey(resource)
                + "->" + quantumConservationAttributeSetKey(attributes)
                + ":" + indexKey(readiness);
    }

    private static String quantumConservationReversalOwnerKey(QuantumAccessPlan plan) {
        List<String> owners = safeList(plan == null ? null : plan.reversalOwners()).stream()
                .filter(owner -> !isBlank(owner))
                .distinct()
                .toList();
        return owners.isEmpty() ? "NO_REVERSAL_OWNER" : String.join("+", owners);
    }

    private static String quantumConservationRuntimeResponsibilityContract(String conservationContract,
                                                                           String implementationStrategySet,
                                                                           String idempotencyOwner,
                                                                           String reversalOwner,
                                                                           String snapshotBoundary,
                                                                           String recoveryOwner,
                                                                           boolean negativeDeltaReversal) {
        return indexKey(conservationContract)
                + "->" + indexKey(implementationStrategySet)
                + "+" + indexKey(idempotencyOwner)
                + "+" + indexKey(reversalOwner)
                + "+" + indexKey(snapshotBoundary)
                + "+" + indexKey(recoveryOwner)
                + "+" + (negativeDeltaReversal ? "NEGATIVE_DELTA_REVERSAL" : "NO_NEGATIVE_DELTA_REVERSAL");
    }

    private static String quantumConservationAttributeSetKey(List<String> attributes) {
        List<String> values = safeList(attributes).stream()
                .filter(attribute -> attribute != null && !attribute.isBlank())
                .distinct()
                .toList();
        return values.isEmpty() ? "NO_QUANTUM_ATTRIBUTE" : String.join("+", values);
    }

    private static String quantumAccessServiceInternalProtocolKey(QuantumAccessPlan plan) {
        if (plan == null) {
            return "NO_SERVICE:NO_IMPLEMENTATION_STRATEGY:NO_SERVICE_INTERNAL_PROTOCOL";
        }
        String service = isBlank(plan.serviceName()) ? "NO_SERVICE" : plan.serviceName();
        boolean quantumService = safeList(plan.implementationStrategies()).stream()
                .anyMatch("QUANTUM_SERVICE"::equals);
        if (!quantumService) {
            return indexKey(service) + ":NOT_QUANTUM_SERVICE";
        }
        String idempotency = plan.serviceInternalIdempotencyManaged()
                ? "SERVICE_IDEMPOTENCY"
                : "MISSING_SERVICE_IDEMPOTENCY";
        String snapshot = plan.serviceInternalSnapshotManaged()
                ? "SERVICE_SNAPSHOT"
                : "MISSING_SERVICE_SNAPSHOT";
        String reversal = plan.serviceInternalReversalManaged()
                ? "SERVICE_REVERSAL_INTERNAL"
                : "MISSING_SERVICE_REVERSAL_INTERNAL";
        String delta = plan.negativeDeltaReversal()
                ? "NEGATIVE_DELTA_REVERSAL"
                : "MISSING_NEGATIVE_DELTA_REVERSAL";
        String contract = plan.accessContractAvailable()
                ? "ACCESS_CONTRACT_AVAILABLE"
                : "ACCESS_CONTRACT_MISSING";
        return indexKey(service) + ":" + idempotency + "+" + snapshot + "+" + delta + "+"
                + reversal + "+" + contract;
    }

    private static String quantumAccessContractKey(QuantumAccessPlan plan, String serviceAttributePath) {
        if (plan == null) {
            return "NO_QUANTUM_ACCESS_CONTRACT";
        }
        String path = isBlank(serviceAttributePath) ? "NO_SERVICE:NO_QUANTUM_ATTRIBUTE" : serviceAttributePath;
        String availability = plan.accessContractAvailable()
                ? "ACCESS_CONTRACT_AVAILABLE"
                : "ACCESS_CONTRACT_MISSING";
        return path + "->"
                + listIndexKey(plan.implementationStrategies(), "NO_IMPLEMENTATION_STRATEGY")
                + "+"
                + indexKey(plan.idempotencyOwner())
                + "+"
                + listIndexKey(plan.reversalOwners(), "NO_REVERSAL_OWNER")
                + "+"
                + availability;
    }

    private static String quantumAccessPlanContractKey(QuantumAccessPlan plan) {
        if (plan == null) {
            return "NO_QUANTUM_ACCESS_CONTRACT";
        }
        String service = isBlank(plan.serviceName()) ? "NO_SERVICE" : plan.serviceName();
        String attributes = listIndexKey(plan.attributeIds(), "NO_QUANTUM_ATTRIBUTE");
        String availability = plan.accessContractAvailable()
                ? "ACCESS_CONTRACT_AVAILABLE"
                : "ACCESS_CONTRACT_MISSING";
        return service + ":" + attributes + "->"
                + listIndexKey(plan.implementationStrategies(), "NO_IMPLEMENTATION_STRATEGY")
                + "+"
                + indexKey(plan.idempotencyOwner())
                + "+"
                + listIndexKey(plan.reversalOwners(), "NO_REVERSAL_OWNER")
                + "+"
                + availability;
    }

    private static List<String> quantumAccessServiceAttributePaths(QuantumAccessPlan plan) {
        if (plan == null) {
            return List.of("NO_SERVICE:NO_QUANTUM_ATTRIBUTE");
        }
        String service = isBlank(plan.serviceName()) ? "NO_SERVICE" : plan.serviceName();
        List<String> attributes = safeList(plan.attributeIds()).stream()
                .filter(attribute -> attribute != null && !attribute.isBlank())
                .distinct()
                .toList();
        if (attributes.isEmpty()) {
            return List.of(service + ":NO_QUANTUM_ATTRIBUTE");
        }
        return attributes.stream()
                .map(attribute -> service + ":" + attribute)
                .toList();
    }

    private static String quantumAccessResponsibilityBoundaryKey(QuantumAccessPlan plan) {
        if (plan == null) {
            return "NO_RESPONSIBILITY_BOUNDARY";
        }
        return String.join("+",
                indexKey(plan.idempotencyOwner()),
                listIndexKey(plan.reversalOwners(), "NO_REVERSAL_OWNER"),
                indexKey(plan.snapshotBoundary()),
                indexKey(plan.recoveryOwner()));
    }

    private static String listIndexKey(List<String> values, String fallback) {
        List<String> keys = safeList(values).stream()
                .filter(value -> value != null && !value.isBlank())
                .map(GeneratedServiceCatalog::indexKey)
                .distinct()
                .toList();
        if (!keys.isEmpty()) {
            return String.join("+", keys);
        }
        return fallback;
    }

    @JsonProperty(value = "quantumServiceAccessPlansByInternalProtocolReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumServiceAccessPlansByInternalProtocolReadiness() {
        Map<String, List<QuantumAccessPlan>> byReadiness = new java.util.LinkedHashMap<>();
        for (QuantumAccessPlan plan : safeList(quantumAccessPlans)) {
            if (!quantumServiceAccessPlan(plan)) {
                continue;
            }
            String key = quantumServiceInternalProtocolMissingReasons(plan).isEmpty()
                    ? "INTERNAL_PROTOCOL_COMPLETE"
                    : "INTERNAL_PROTOCOL_INCOMPLETE";
            byReadiness.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(plan);
        }
        return immutableListMap(byReadiness);
    }

    @JsonProperty(value = "quantumServiceAccessPlansByInternalProtocolMissingReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<QuantumAccessPlan>> quantumServiceAccessPlansByInternalProtocolMissingReason() {
        Map<String, List<QuantumAccessPlan>> byReason = new java.util.LinkedHashMap<>();
        for (QuantumAccessPlan plan : safeList(quantumAccessPlans)) {
            if (!quantumServiceAccessPlan(plan)) {
                continue;
            }
            List<String> reasons = quantumServiceInternalProtocolMissingReasons(plan);
            if (reasons.isEmpty()) {
                byReason.computeIfAbsent("NO_MISSING_INTERNAL_PROTOCOL_REASON",
                        ignored -> new java.util.ArrayList<>()).add(plan);
                continue;
            }
            for (String reason : reasons) {
                byReason.computeIfAbsent(reason, ignored -> new java.util.ArrayList<>()).add(plan);
            }
        }
        return immutableListMap(byReason);
    }

    @JsonProperty(value = "remoteServiceAssignmentsByProductionReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RemoteServiceAssignment>> remoteServiceAssignmentsByProductionReadiness() {
        Map<String, List<RemoteServiceAssignment>> byReadiness = new java.util.LinkedHashMap<>();
        Map<String, PhysicalRemotePlan> plansByService = physicalRemotePlansByServiceName();
        for (RemoteServiceAssignment assignment : safeList(remoteServiceAssignments)) {
            if (assignment == null) {
                continue;
            }
            String key = remoteProductionReady(assignment, plansByService.get(assignment.serviceName()))
                    ? "REMOTE_PRODUCTION_READY"
                    : "REMOTE_PRODUCTION_NOT_READY";
            byReadiness.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(assignment);
        }
        return immutableListMap(byReadiness);
    }

    @JsonProperty(value = "remoteServiceAssignmentsByProductionReadinessReason",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RemoteServiceAssignment>> remoteServiceAssignmentsByProductionReadinessReason() {
        Map<String, List<RemoteServiceAssignment>> byReason = new java.util.LinkedHashMap<>();
        Map<String, PhysicalRemotePlan> plansByService = physicalRemotePlansByServiceName();
        for (RemoteServiceAssignment assignment : safeList(remoteServiceAssignments)) {
            if (assignment == null) {
                continue;
            }
            byReason.computeIfAbsent(remoteProductionReadinessReason(assignment,
                            plansByService.get(assignment.serviceName())),
                    ignored -> new java.util.ArrayList<>()).add(assignment);
        }
        return immutableListMap(byReason);
    }

    @JsonProperty(value = "remoteProductionReadinessIndex", access = JsonProperty.Access.READ_ONLY)
    public RemoteProductionReadinessIndex remoteProductionReadinessIndex() {
        Map<String, PhysicalRemotePlan> plansByService = physicalRemotePlansByServiceName();
        List<RemoteProductionReadinessPlan> plans = safeList(remoteServiceAssignments)
                .stream()
                .filter(assignment -> assignment != null)
                .map(assignment -> remoteProductionReadinessPlan(assignment, plansByService.get(assignment.serviceName())))
                .toList();
        if (plans.isEmpty()) {
            return new RemoteProductionReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<RemoteProductionReadinessPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<RemoteProductionReadinessPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<RemoteProductionReadinessPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<RemoteProductionReadinessPlan>> byReason = new LinkedHashMap<>();
        Map<String, List<RemoteProductionReadinessPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<RemoteProductionReadinessPlan>> byAdapterRole = new LinkedHashMap<>();
        Map<String, List<RemoteProductionReadinessPlan>> byImplementationType = new LinkedHashMap<>();
        Map<String, List<RemoteProductionReadinessPlan>> byImplementationKind = new LinkedHashMap<>();
        Map<String, List<RemoteProductionReadinessPlan>> byEndpoint = new LinkedHashMap<>();
        Map<String, List<RemoteProductionReadinessPlan>> byService = new LinkedHashMap<>();
        Map<String, List<RemoteProductionReadinessPlan>> byProductionContract = new LinkedHashMap<>();
        for (RemoteProductionReadinessPlan plan : plans) {
            addToIndex(byReadiness, indexKey(plan.readiness()), plan);
            for (String reason : safeList(plan.readyReasons())) {
                addToIndex(byReadyReason, indexKey(reason), plan);
            }
            for (String reason : safeList(plan.missingReasons())) {
                addToIndex(byMissingReason, indexKey(reason), plan);
            }
            addToIndex(byReason, indexKey(plan.reason()), plan);
            for (String action : safeList(plan.actions())) {
                addToIndex(byAction, indexKey(action), plan);
            }
            addToIndex(byAdapterRole, indexKey(plan.adapterRole()), plan);
            addToIndex(byImplementationType, indexKey(plan.adapterImplementationType()), plan);
            addToIndex(byImplementationKind, indexKey(plan.implementationKind()), plan);
            addToIndex(byEndpoint, endpointIndexKey(plan.endpoint()), plan);
            addToIndex(byService, indexKey(plan.serviceName()), plan);
            addToIndex(byProductionContract, indexKey(plan.productionContract()), plan);
        }
        return new RemoteProductionReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byReason),
                immutableListMap(byAction),
                immutableListMap(byAdapterRole),
                immutableListMap(byImplementationType),
                immutableListMap(byImplementationKind),
                immutableListMap(byEndpoint),
                immutableListMap(byService),
                immutableListMap(byProductionContract));
    }

    @JsonProperty(value = "remoteProductionPlansByReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RemoteProductionReadinessPlan>> remoteProductionPlansByReadiness() {
        return remoteProductionReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "remoteProductionPlansByReadyReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RemoteProductionReadinessPlan>> remoteProductionPlansByReadyReason() {
        return remoteProductionReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "remoteProductionPlansByMissingReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RemoteProductionReadinessPlan>> remoteProductionPlansByMissingReason() {
        return remoteProductionReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "remoteProductionPlansByReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RemoteProductionReadinessPlan>> remoteProductionPlansByReason() {
        return remoteProductionReadinessIndex().byReason();
    }

    @JsonProperty(value = "remoteProductionPlansByAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RemoteProductionReadinessPlan>> remoteProductionPlansByAction() {
        return remoteProductionReadinessIndex().byAction();
    }

    @JsonProperty(value = "remoteProductionPlansByService", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RemoteProductionReadinessPlan>> remoteProductionPlansByService() {
        return remoteProductionReadinessIndex().byService();
    }

    @JsonProperty(value = "remoteProductionPlansByAdapterRole", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RemoteProductionReadinessPlan>> remoteProductionPlansByAdapterRole() {
        return remoteProductionReadinessIndex().byAdapterRole();
    }

    @JsonProperty(value = "remoteProductionPlansByImplementationType",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RemoteProductionReadinessPlan>> remoteProductionPlansByImplementationType() {
        return remoteProductionReadinessIndex().byImplementationType();
    }

    @JsonProperty(value = "remoteProductionPlansByImplementationKind",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RemoteProductionReadinessPlan>> remoteProductionPlansByImplementationKind() {
        return remoteProductionReadinessIndex().byImplementationKind();
    }

    @JsonProperty(value = "remoteProductionPlansByEndpoint", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RemoteProductionReadinessPlan>> remoteProductionPlansByEndpoint() {
        return remoteProductionReadinessIndex().byEndpoint();
    }

    @JsonProperty(value = "remoteProductionPlansByProductionContract",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RemoteProductionReadinessPlan>> remoteProductionPlansByProductionContract() {
        return remoteProductionReadinessIndex().byProductionContract();
    }

    @JsonProperty(value = "recoveryDeploymentReadinessIndex", access = JsonProperty.Access.READ_ONLY)
    public RecoveryDeploymentReadinessIndex recoveryDeploymentReadinessIndex() {
        List<RecoveryDeploymentPlan> plans = safeList(recoveryDeploymentPlans).stream()
                .filter(plan -> plan != null)
                .map(GeneratedServiceCatalog::enrichRecoveryDeploymentPlan)
                .toList();
        if (plans.isEmpty()) {
            return new RecoveryDeploymentReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<RecoveryDeploymentPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<RecoveryDeploymentPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<RecoveryDeploymentPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<RecoveryDeploymentPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<RecoveryDeploymentPlan>> byPlan = new LinkedHashMap<>();
        Map<String, List<RecoveryDeploymentPlan>> byBackend = new LinkedHashMap<>();
        Map<String, List<RecoveryDeploymentPlan>> byDatasource = new LinkedHashMap<>();
        Map<String, List<RecoveryDeploymentPlan>> byWorkItemKind = new LinkedHashMap<>();
        Map<String, List<RecoveryDeploymentPlan>> byEvidenceSource = new LinkedHashMap<>();
        Map<String, List<RecoveryDeploymentPlan>> byUnifiedView = new LinkedHashMap<>();
        for (RecoveryDeploymentPlan plan : plans) {
            addToIndex(byReadiness, indexKey(plan.readiness()), plan);
            addListIndexValues(byReadyReason, plan.readyReasons(), plan, "NO_READY_REASON");
            addListIndexValues(byMissingReason, plan.missingReasons(), plan,
                    "NO_MISSING_RECOVERY_DEPLOYMENT_REASON");
            addListIndexValues(byAction, plan.actions(), plan, "NO_ACTION_REQUIRED");
            addToIndex(byPlan, indexKey(plan.planName()), plan);
            addToIndex(byBackend, indexKey(plan.backendName()), plan);
            addToIndex(byDatasource, indexKey(plan.backendDatasourceUrl()), plan);
            addListIndexValues(byWorkItemKind, plan.workItemKinds(), plan, "NO_WORK_ITEM_KIND");
            addListIndexValues(byEvidenceSource, plan.evidenceSources(), plan, "NO_EVIDENCE_SOURCE");
            addToIndex(byUnifiedView, recoveryDeploymentUnifiedViewKey(plan), plan);
        }
        return new RecoveryDeploymentReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byPlan),
                immutableListMap(byBackend),
                immutableListMap(byDatasource),
                immutableListMap(byWorkItemKind),
                immutableListMap(byEvidenceSource),
                immutableListMap(byUnifiedView));
    }

    public static RecoveryDeploymentPlan enrichRecoveryDeploymentPlan(RecoveryDeploymentPlan plan) {
        if (plan == null) {
            return null;
        }
        List<String> missingReasons = recoveryDeploymentMissingReasons(plan);
        String readiness = missingReasons.isEmpty()
                ? "RECOVERY_DEPLOYMENT_READY"
                : "RECOVERY_DEPLOYMENT_INCOMPLETE";
        List<String> readyReasons = missingReasons.isEmpty()
                ? recoveryDeploymentReadyReasons(plan)
                : List.of();
        List<String> normalizedMissingReasons = missingReasons.isEmpty()
                ? List.of("NO_MISSING_RECOVERY_DEPLOYMENT_REASON")
                : missingReasons;
        List<String> actions = recoveryDeploymentActions(missingReasons);
        return new RecoveryDeploymentPlan(plan.planName(),
                plan.unifiedWorkItemView(),
                plan.workItemKinds(),
                plan.evidenceSources(),
                plan.tccRecoveryAvailable(),
                plan.remoteCompensationRecoveryAvailable(),
                plan.tccTryServiceNames(),
                plan.remoteCompensationSourceServiceNames(),
                plan.backendName(),
                plan.backendAdapterName(),
                plan.backendDatasourceUrl(),
                plan.backendDriverClassName(),
                plan.sagaLogStoreName(),
                plan.sagaLogTableName(),
                plan.tccBranchStoreName(),
                plan.tccBranchTableName(),
                plan.tccReservationStoreName(),
                plan.tccReservationTableName(),
                plan.tccReservationTtlMillis(),
                readiness,
                readyReasons,
                normalizedMissingReasons,
                actions,
                recoveryDeploymentContractKey(plan, readiness),
                recoveryDeploymentRuntimeContractKey(plan, readiness));
    }

    @JsonProperty(value = "recoveryDeploymentPlansByReadyReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryDeploymentPlan>> recoveryDeploymentPlansByReadyReason() {
        return recoveryDeploymentReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "recoveryDeploymentPlansByBackend", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryDeploymentPlan>> recoveryDeploymentPlansByBackend() {
        return recoveryDeploymentReadinessIndex().byBackend();
    }

    @JsonProperty(value = "recoveryDeploymentPlansByDatasource", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryDeploymentPlan>> recoveryDeploymentPlansByDatasource() {
        return recoveryDeploymentReadinessIndex().byDatasource();
    }

    @JsonProperty(value = "recoveryDeploymentPlansByWorkItemKind", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryDeploymentPlan>> recoveryDeploymentPlansByWorkItemKind() {
        return recoveryDeploymentReadinessIndex().byWorkItemKind();
    }

    @JsonProperty(value = "recoveryDeploymentPlansByEvidenceSource", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryDeploymentPlan>> recoveryDeploymentPlansByEvidenceSource() {
        return recoveryDeploymentReadinessIndex().byEvidenceSource();
    }

    @JsonProperty(value = "recoveryDeploymentPlansByUnifiedView", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryDeploymentPlan>> recoveryDeploymentPlansByUnifiedView() {
        return recoveryDeploymentReadinessIndex().byUnifiedView();
    }

    @JsonProperty(value = "recoveryDeploymentPlansByReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryDeploymentPlan>> recoveryDeploymentPlansByReadiness() {
        return recoveryDeploymentReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "recoveryDeploymentPlansByMissingReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryDeploymentPlan>> recoveryDeploymentPlansByMissingReason() {
        return recoveryDeploymentReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "recoveryDeploymentPlansByAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryDeploymentPlan>> recoveryDeploymentPlansByAction() {
        return recoveryDeploymentReadinessIndex().byAction();
    }

    @JsonProperty(value = "recoveryWorkItemReadinessIndex", access = JsonProperty.Access.READ_ONLY)
    public RecoveryWorkItemReadinessIndex recoveryWorkItemReadinessIndex() {
        List<RecoveryWorkItemPlan> plans = recoveryWorkItemPlans();
        if (plans.isEmpty()) {
            return new RecoveryWorkItemReadinessIndex(List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of());
        }
        Map<String, List<RecoveryWorkItemPlan>> byReadiness = new LinkedHashMap<>();
        Map<String, List<RecoveryWorkItemPlan>> byReadyReason = new LinkedHashMap<>();
        Map<String, List<RecoveryWorkItemPlan>> byMissingReason = new LinkedHashMap<>();
        Map<String, List<RecoveryWorkItemPlan>> byAction = new LinkedHashMap<>();
        Map<String, List<RecoveryWorkItemPlan>> byWorkItemKind = new LinkedHashMap<>();
        Map<String, List<RecoveryWorkItemPlan>> bySourceService = new LinkedHashMap<>();
        Map<String, List<RecoveryWorkItemPlan>> byWorkItemSource = new LinkedHashMap<>();
        Map<String, List<RecoveryWorkItemPlan>> byManualEndpoint = new LinkedHashMap<>();
        Map<String, List<RecoveryWorkItemPlan>> bySchedulerPrefix = new LinkedHashMap<>();
        Map<String, List<RecoveryWorkItemPlan>> byBackend = new LinkedHashMap<>();
        Map<String, List<RecoveryWorkItemPlan>> byEvidenceStore = new LinkedHashMap<>();
        Map<String, List<RecoveryWorkItemPlan>> byExecutionContract = new LinkedHashMap<>();
        Map<String, List<RecoveryWorkItemPlan>> byPhysicalEvidencePath = new LinkedHashMap<>();
        Map<String, List<RecoveryWorkItemPlan>> bySupportedDecision = new LinkedHashMap<>();
        Map<String, List<RecoveryWorkItemPlan>> bySuggestedAction = new LinkedHashMap<>();
        for (RecoveryWorkItemPlan plan : plans) {
            addToIndex(byReadiness, indexKey(plan.readiness()), plan);
            for (String reason : safeList(plan.readyReasons())) {
                addToIndex(byReadyReason, indexKey(reason), plan);
            }
            for (String reason : safeList(plan.missingReasons())) {
                addToIndex(byMissingReason, indexKey(reason), plan);
            }
            for (String action : safeList(plan.actions())) {
                addToIndex(byAction, indexKey(action), plan);
            }
            addToIndex(byWorkItemKind, indexKey(plan.workItemKind()), plan);
            addListIndexValues(bySourceService, plan.sourceServiceNames(), plan, "NO_SOURCE_SERVICE");
            addToIndex(byWorkItemSource, indexKey(plan.workItemSource()), plan);
            addToIndex(byManualEndpoint, indexKey(plan.manualRecoveryEndpointPath()), plan);
            addToIndex(bySchedulerPrefix, indexKey(plan.schedulerPropertyPrefix()), plan);
            addToIndex(byBackend, indexKey(plan.backendName()), plan);
            for (String evidenceStore : recoveryWorkItemEvidenceStores(plan)) {
                addToIndex(byEvidenceStore, indexKey(evidenceStore), plan);
            }
            addToIndex(byExecutionContract, indexKey(plan.executionContract()), plan);
            for (String evidencePath : safeList(plan.physicalEvidencePaths())) {
                addToIndex(byPhysicalEvidencePath, evidencePath, plan);
            }
            addListIndexValues(bySupportedDecision, plan.supportedDecisions(), plan, "NO_SUPPORTED_DECISION");
            addListIndexValues(bySuggestedAction, plan.suggestedActions(), plan, "NO_SUGGESTED_ACTION");
        }
        return new RecoveryWorkItemReadinessIndex(plans,
                immutableListMap(byReadiness),
                immutableListMap(byReadyReason),
                immutableListMap(byMissingReason),
                immutableListMap(byAction),
                immutableListMap(byWorkItemKind),
                immutableListMap(bySourceService),
                immutableListMap(byWorkItemSource),
                immutableListMap(byManualEndpoint),
                immutableListMap(bySchedulerPrefix),
                immutableListMap(byBackend),
                immutableListMap(byEvidenceStore),
                immutableListMap(byExecutionContract),
                immutableListMap(byPhysicalEvidencePath),
                immutableListMap(bySupportedDecision),
                immutableListMap(bySuggestedAction));
    }

    @JsonProperty(value = "recoveryWorkItemPlans")
    public List<RecoveryWorkItemPlan> recoveryWorkItemPlans() {
        return safeList(recoveryWorkItemPlans).stream()
                .filter(plan -> plan != null)
                .map(GeneratedServiceCatalog::enrichRecoveryWorkItemPlan)
                .toList();
    }

    @JsonProperty(value = "recoveryWorkItemPlansByKind")
    public Map<String, RecoveryWorkItemPlan> recoveryWorkItemPlansByKind() {
        Map<String, RecoveryWorkItemPlan> byKind = new LinkedHashMap<>();
        for (RecoveryWorkItemPlan plan : recoveryWorkItemPlans()) {
            byKind.putIfAbsent(indexKey(plan.workItemKind()), plan);
        }
        return Map.copyOf(byKind);
    }

    @JsonProperty(value = "recoveryWorkItemPlansByReadyReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansByReadyReason() {
        return recoveryWorkItemReadinessIndex().byReadyReason();
    }

    @JsonProperty(value = "recoveryWorkItemPlansByMissingReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansByMissingReason() {
        return recoveryWorkItemReadinessIndex().byMissingReason();
    }

    @JsonProperty(value = "recoveryWorkItemPlansByAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansByAction() {
        return recoveryWorkItemReadinessIndex().byAction();
    }

    @JsonProperty(value = "recoveryWorkItemPlansByWorkItemKind", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansByWorkItemKind() {
        return recoveryWorkItemReadinessIndex().byWorkItemKind();
    }

    @JsonProperty(value = "recoveryWorkItemPlansBySourceService", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansBySourceService() {
        return recoveryWorkItemReadinessIndex().bySourceService();
    }

    @JsonProperty(value = "recoveryWorkItemPlansByManualEndpoint", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansByManualEndpoint() {
        return recoveryWorkItemReadinessIndex().byManualEndpoint();
    }

    @JsonProperty(value = "recoveryWorkItemPlansBySchedulerPrefix", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansBySchedulerPrefix() {
        return recoveryWorkItemReadinessIndex().bySchedulerPrefix();
    }

    @JsonProperty(value = "recoveryWorkItemPlansByWorkItemSource", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansByWorkItemSource() {
        return recoveryWorkItemReadinessIndex().byWorkItemSource();
    }

    @JsonProperty(value = "recoveryWorkItemPlansBySupportedDecision", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansBySupportedDecision() {
        return recoveryWorkItemReadinessIndex().bySupportedDecision();
    }

    @JsonProperty(value = "recoveryWorkItemPlansBySuggestedAction", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansBySuggestedAction() {
        return recoveryWorkItemReadinessIndex().bySuggestedAction();
    }

    @JsonProperty(value = "recoveryWorkItemPlansByReadiness", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansByReadiness() {
        return recoveryWorkItemReadinessIndex().byReadiness();
    }

    @JsonProperty(value = "recoveryWorkItemPlansByBackend", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansByBackend() {
        return recoveryWorkItemReadinessIndex().byBackend();
    }

    @JsonProperty(value = "recoveryWorkItemPlansByEvidenceStore", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansByEvidenceStore() {
        return recoveryWorkItemReadinessIndex().byEvidenceStore();
    }

    @JsonProperty(value = "recoveryWorkItemPlansByExecutionContract", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansByExecutionContract() {
        return recoveryWorkItemReadinessIndex().byExecutionContract();
    }

    @JsonProperty(value = "recoveryWorkItemPlansByPhysicalEvidencePath", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<RecoveryWorkItemPlan>> recoveryWorkItemPlansByPhysicalEvidencePath() {
        return recoveryWorkItemReadinessIndex().byPhysicalEvidencePath();
    }

    @JsonProperty(value = "physicalRemotePlansByExternalEndpointReadiness",
            access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalRemotePlan>> physicalRemotePlansByExternalEndpointReadiness() {
        Map<String, List<PhysicalRemotePlan>> byReadiness = new java.util.LinkedHashMap<>();
        for (PhysicalRemotePlan plan : safeList(physicalRemotePlans)) {
            if (!isExternalRemotePlan(plan)) {
                continue;
            }
            String key = remoteExternalEndpointReady(plan)
                    ? "PRODUCTION_READY"
                    : "CONTRACT_ONLY";
            byReadiness.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(plan);
        }
        return immutableListMap(byReadiness);
    }

    @JsonProperty(value = "physicalRemotePlansByExternalEndpointReason", access = JsonProperty.Access.READ_ONLY)
    public Map<String, List<PhysicalRemotePlan>> physicalRemotePlansByExternalEndpointReason() {
        Map<String, List<PhysicalRemotePlan>> byReason = new java.util.LinkedHashMap<>();
        for (PhysicalRemotePlan plan : safeList(physicalRemotePlans)) {
            if (!isExternalRemotePlan(plan)) {
                continue;
            }
            byReason.computeIfAbsent(remoteExternalEndpointReadinessReason(plan),
                    ignored -> new java.util.ArrayList<>()).add(plan);
        }
        return immutableListMap(byReason);
    }

    @JsonProperty(value = "deploymentValidationPassedCategories", access = JsonProperty.Access.READ_ONLY)
    public List<String> deploymentValidationPassedCategories() {
        return deploymentValidationCategories(true);
    }

    @JsonProperty(value = "deploymentValidationFailedCategories", access = JsonProperty.Access.READ_ONLY)
    public List<String> deploymentValidationFailedCategories() {
        return deploymentValidationCategories(false);
    }

    @JsonProperty(value = "deploymentValidationPassed", access = JsonProperty.Access.READ_ONLY)
    public boolean deploymentValidationPassed() {
        return validationErrorCount() == 0
                && deploymentValidationPlans.stream().allMatch(DeploymentValidationPlan::passed);
    }

    private List<String> deploymentValidationCategories(boolean passed) {
        return deploymentValidationPlans.stream()
                .filter(plan -> plan.passed() == passed)
                .map(DeploymentValidationPlan::category)
                .toList();
    }

    private static boolean isQuantumServicePlan(PhysicalQuantumPlan plan) {
        return plan != null && "QUANTUM_SERVICE".equals(plan.implementationStrategy());
    }

    private static boolean quantumServiceExternalImplementationReady(PhysicalQuantumPlan plan) {
        return isQuantumServicePlan(plan)
                && isAbsoluteHttpEndpoint(plan.endpoint())
                && !quantumServiceDevelopmentEndpoint(plan.endpoint())
                && !quantumServicePlaceholderEndpoint(plan.endpoint());
    }

    private static String quantumServiceExternalImplementationReason(PhysicalQuantumPlan plan) {
        if (plan == null || plan.endpoint() == null || plan.endpoint().isBlank()) {
            return "endpoint";
        }
        if (!isAbsoluteHttpEndpoint(plan.endpoint())) {
            return "endpointInvalid";
        }
        if (quantumServiceDevelopmentEndpoint(plan.endpoint())) {
            return "developmentEndpoint";
        }
        if (quantumServicePlaceholderEndpoint(plan.endpoint())) {
            return "placeholderEndpoint";
        }
        return "-";
    }

    private static boolean quantumServiceDevelopmentEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return "localhost".equals(normalizedHost)
                    || "127.0.0.1".equals(normalizedHost)
                    || "0.0.0.0".equals(normalizedHost)
                    || "::1".equals(normalizedHost);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isAbsoluteHttpEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            return uri.isAbsolute()
                    && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean quantumServicePlaceholderEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return "example.test".equals(normalizedHost)
                    || normalizedHost.endsWith(".example.test")
                    || "example.com".equals(normalizedHost)
                    || normalizedHost.endsWith(".example.com");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Map<String, PhysicalRemotePlan> physicalRemotePlansByServiceName() {
        Map<String, PhysicalRemotePlan> plansByService = new java.util.LinkedHashMap<>();
        for (PhysicalRemotePlan plan : safeList(physicalRemotePlans)) {
            if (plan == null || plan.serviceName() == null || plan.serviceName().isBlank()) {
                continue;
            }
            plansByService.putIfAbsent(plan.serviceName(), plan);
        }
        return plansByService;
    }

    private static boolean remoteProductionReady(RemoteServiceAssignment assignment, PhysicalRemotePlan plan) {
        return assignment != null
                && assignment.externalImplementation()
                && assignment.adapterAvailable()
                && assignment.adapterExecutable()
                && remoteExternalEndpointReady(plan);
    }

    private static String remoteProductionReadinessReason(RemoteServiceAssignment assignment,
                                                          PhysicalRemotePlan plan) {
        if (remoteProductionReady(assignment, plan)) {
            return "-";
        }
        if (assignment == null) {
            return "assignment";
        }
        if (assignment.referenceImplementation()) {
            return "externalAdapter";
        }
        if (!assignment.adapterAvailable()) {
            return "adapter";
        }
        if (!assignment.adapterExecutable()) {
            return "executableAdapter";
        }
        if (!assignment.externalImplementation()) {
            return "externalImplementation";
        }
        return remoteExternalEndpointReadinessReason(plan);
    }

    private static RemoteProductionReadinessPlan remoteProductionReadinessPlan(
            RemoteServiceAssignment assignment,
            PhysicalRemotePlan physicalPlan) {
        String reason = remoteProductionReadinessReason(assignment, physicalPlan);
        return new RemoteProductionReadinessPlan(assignment.serviceName(),
                assignment.adapterImplementationType(),
                assignment.adapterRole(),
                assignment.adapterAvailable(),
                assignment.adapterExecutable(),
                assignment.referenceImplementation(),
                assignment.externalImplementation(),
                physicalPlan == null ? "" : physicalPlan.adapterName(),
                physicalPlan == null ? "" : physicalPlan.endpoint(),
                physicalPlan == null ? "" : physicalPlan.timeoutMillis(),
                physicalPlan != null,
                remoteProductionReady(assignment, physicalPlan),
                reason,
                remoteProductionReadinessAction(reason));
    }

    private static String remoteProductionReadinessAction(String reason) {
        if (reason == null || reason.isBlank() || "-".equals(reason)) {
            return "NO_ACTION_REQUIRED";
        }
        return switch (reason) {
            case "assignment" -> "bindRemoteService";
            case "externalAdapter" -> "replaceReferenceRemoteAdapter+bindExternalDomainAdapter";
            case "adapter" -> "registerRemoteAdapter";
            case "executableAdapter" -> "enableExecutableRemoteAdapter";
            case "externalImplementation" -> "bindExternalDomainAdapter";
            case "endpoint" -> "configureRemoteEndpoint";
            case "endpointInvalid" -> "configureAbsoluteHttpRemoteEndpoint";
            case "developmentEndpoint" -> "replaceDevelopmentRemoteEndpoint";
            case "placeholderEndpoint" -> "replaceRemoteEndpointPlaceholder";
            default -> "reviewRemoteProductionReadiness";
        };
    }

    private List<PhysicalRepositoryPlan> appDdlPhysicalRepositoriesForTable(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return List.of();
        }
        return safeList(physicalRepositoryPlans).stream()
                .filter(plan -> plan != null
                        && plan.appDdlTables()
                        && safeList(plan.appDdlTableNames()).contains(tableName))
                .toList();
    }

    private List<ObjectRepositoryPlan> objectRepositoryPlansForAppDdlTable(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return List.of();
        }
        return safeList(objectRepositoryPlans).stream()
                .filter(plan -> plan != null && tableName.equals(plan.appDdlTableName()))
                .toList();
    }

    private static AppDdlTablePhysicalizationPlan appDdlTablePhysicalizationPlan(
            AppDdlCatalog.Table table,
            List<PhysicalRepositoryPlan> physicalRepositoryPlans,
            List<ObjectRepositoryPlan> objectRepositoryPlans) {
        List<String> missingReasons = appDdlTablePhysicalizationMissingReasons(table, physicalRepositoryPlans);
        List<String> actions = appDdlTablePhysicalizationActions(missingReasons);
        boolean physicalized = physicalRepositoryPlans != null && !physicalRepositoryPlans.isEmpty();
        return new AppDdlTablePhysicalizationPlan(table.name(),
                table.category(),
                table.businessType(),
                table.category() != null && !table.category().isBlank(),
                table.primaryKeys().size(),
                table.indexes().size(),
                table.indexNames().size(),
                table.columns().size(),
                table.platformPrimaryKeys().size(),
                table.objectBackedPrimaryKeys().size(),
                table.platformIndexPaths().size(),
                table.objectBackedIndexPaths().size(),
                table.platformColumns().size(),
                !table.platformPrimaryKeys().isEmpty(),
                !table.platformIndexPaths().isEmpty(),
                !table.platformColumns().isEmpty(),
                table.objectBackedPrimaryKeys().isEmpty(),
                table.objectBackedIndexPaths().isEmpty(),
                physicalized,
                physicalized
                        ? uniqueNonBlank(physicalRepositoryPlans, PhysicalRepositoryPlan::repositoryName)
                        : uniqueNonBlank(objectRepositoryPlans, ObjectRepositoryPlan::resolvedRepositoryName),
                physicalized
                        ? uniqueNonBlank(physicalRepositoryPlans, PhysicalRepositoryPlan::adapterName)
                        : uniqueNonBlank(objectRepositoryPlans, ObjectRepositoryPlan::adapterName),
                physicalized
                        ? uniqueNonBlank(physicalRepositoryPlans, PhysicalRepositoryPlan::adapterRole)
                        : List.of(),
                physicalized
                        ? uniqueNonBlank(physicalRepositoryPlans, PhysicalRepositoryPlan::storageTableName)
                        : uniqueNonBlank(objectRepositoryPlans, ObjectRepositoryPlan::storageTableName),
                physicalized
                        ? uniqueNonBlank(physicalRepositoryPlans, PhysicalRepositoryPlan::transactionHistoryTableName)
                        : uniqueNonBlank(objectRepositoryPlans, ObjectRepositoryPlan::transactionHistoryTableName),
                physicalized
                        ? uniqueNonBlank(physicalRepositoryPlans, PhysicalRepositoryPlan::transactionArchiveTableName)
                        : uniqueNonBlank(objectRepositoryPlans, ObjectRepositoryPlan::transactionArchiveTableName),
                physicalized
                        ? uniqueNonBlank(physicalRepositoryPlans, PhysicalRepositoryPlan::datasourceUrl)
                        : uniqueNonBlank(objectRepositoryPlans, ObjectRepositoryPlan::datasourceUrl),
                physicalized
                        ? uniqueNonBlank(physicalRepositoryPlans, PhysicalRepositoryPlan::driverClassName)
                        : uniqueNonBlank(objectRepositoryPlans, ObjectRepositoryPlan::driverClassName),
                missingReasons,
                actions);
    }

    private static List<String> appDdlTablePhysicalizationMissingReasons(
            AppDdlCatalog.Table table,
            List<PhysicalRepositoryPlan> physicalRepositoryPlans) {
        List<String> reasons = new ArrayList<>();
        if (table.category() == null || table.category().isBlank()) {
            reasons.add("category");
        }
        if (table.businessType() == null || table.businessType().isBlank()) {
            reasons.add("businessType");
        }
        if (table.platformPrimaryKeys().isEmpty()) {
            reasons.add("platformPrimaryKey");
        }
        if (!table.indexes().isEmpty() && table.platformIndexPaths().isEmpty()) {
            reasons.add("platformIndex");
        }
        if (table.platformColumns().isEmpty()) {
            reasons.add("platformColumn");
        }
        if (!table.objectBackedPrimaryKeys().isEmpty()) {
            reasons.add("objectBackedPrimaryKey");
        }
        if (!table.objectBackedIndexPaths().isEmpty()) {
            reasons.add("objectBackedIndex");
        }
        if (physicalRepositoryPlans == null || physicalRepositoryPlans.isEmpty()) {
            reasons.add("physicalRepository");
        }
        return List.copyOf(reasons);
    }

    private static List<String> appDdlTablePhysicalizationActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "category" -> actions.add("declareAppDdlTableCategory");
                case "businessType" -> actions.add("bindAppDdlTableBusinessType");
                case "platformPrimaryKey", "objectBackedPrimaryKey" ->
                        actions.add("promoteAppDdlPrimaryKeyToPlatformColumn");
                case "platformIndex", "objectBackedIndex" -> actions.add("promoteAppDdlIndexToPlatformColumn");
                case "platformColumn" -> actions.add("declareAppDdlPlatformColumn");
                case "physicalRepository" ->
                        actions.add("bindAppDdlTableToPhysicalRepository+enableRepositoryAppDdlTables");
                default -> actions.add("reviewAppDdlTablePhysicalization");
            }
        }
        return List.copyOf(actions);
    }

    private static boolean isExternalRemotePlan(PhysicalRemotePlan plan) {
        return plan != null && plan.externalImplementation();
    }

    private static boolean remoteExternalEndpointReady(PhysicalRemotePlan plan) {
        return isExternalRemotePlan(plan)
                && isAbsoluteHttpEndpoint(plan.endpoint())
                && !remoteExternalDevelopmentEndpoint(plan.endpoint())
                && !remoteExternalPlaceholderEndpoint(plan.endpoint());
    }

    private static String remoteExternalEndpointReadinessReason(PhysicalRemotePlan plan) {
        if (plan == null || plan.endpoint() == null || plan.endpoint().isBlank()) {
            return "endpoint";
        }
        if (!isAbsoluteHttpEndpoint(plan.endpoint())) {
            return "endpointInvalid";
        }
        if (remoteExternalDevelopmentEndpoint(plan.endpoint())) {
            return "developmentEndpoint";
        }
        if (remoteExternalPlaceholderEndpoint(plan.endpoint())) {
            return "placeholderEndpoint";
        }
        return "-";
    }

    private static boolean remoteExternalDevelopmentEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return "localhost".equals(normalizedHost)
                    || "127.0.0.1".equals(normalizedHost)
                    || "0.0.0.0".equals(normalizedHost)
                    || "::1".equals(normalizedHost);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean remoteExternalPlaceholderEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return "example.test".equals(normalizedHost)
                    || normalizedHost.endsWith(".example.test")
                    || "example.com".equals(normalizedHost)
                    || normalizedHost.endsWith(".example.com");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static List<String> sagaTransactionCallRecoveryClosureMissingReasons(SagaTransactionCallPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> missingReasons = new ArrayList<>();
        if (!plan.targetRegistered()) {
            missingReasons.add("registeredTarget");
        }
        if (!plan.localAtomicUpdateTransaction() && !plan.sourceScenarioTransaction()) {
            missingReasons.add("localAtomicUpdateTarget");
        }
        if (plan.sourceScenarioTransaction() && !plan.implementationStrategies().contains("DECLARED_BUSINESS_COMPENSATION")) {
            missingReasons.add("declaredBusinessCompensation");
        }
        if (plan.objectSnapshotRequired()
                && plan.argumentSnapshotRuleCount() <= 0) {
            missingReasons.add("argumentSnapshot");
        }
        if (plan.objectSnapshotRequired()
                && !plan.argumentRecoveryEvidence()) {
            missingReasons.add("argumentRecoveryEvidence");
        }
        if (plan.objectSnapshotRequired()
                && !plan.rollbackRecoveryEvidence()) {
            missingReasons.add("rollbackRecoveryEvidence");
        }
        if (plan.objectSnapshotRequired()
                && plan.currentSnapshotRuleCount() > 0
                && !plan.argumentRecoveryEvidence()) {
            missingReasons.add("currentSnapshotCannotReplaceArgumentRecovery");
        }
        return List.copyOf(missingReasons);
    }

    private static List<String> tccReservationMissingReasons(TccReservationPlan plan) {
        if (plan == null) {
            return List.of("plan");
        }
        List<String> missingReasons = new ArrayList<>();
        if (plan.sourceQuantumAttribute() && isBlank(plan.sourceQuantumStoreName())) {
            missingReasons.add("sourcePhysicalQuantumStore");
        }
        if (!plan.targetRepositoryAppDdlTables()) {
            missingReasons.add("targetAppDdlRepository");
        }
        if (!tccReservationTargetAppDdlPlatformColumn(plan)) {
            missingReasons.add("targetAppDdlPlatformColumn");
        }
        if (!tccReservationQuantumToAppDdlPlatformColumnClosed(plan)) {
            missingReasons.add("quantumToAppDdlPlatformColumnReservation");
        }
        return List.copyOf(missingReasons);
    }

    private static List<String> tccReservationReadyReasons(TccReservationPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> readyReasons = new ArrayList<>();
        if (plan.sourceQuantumAttribute()) {
            readyReasons.add("sourceQuantumAttribute");
        }
        if (!isBlank(plan.sourceQuantumStoreName())) {
            readyReasons.add("sourcePhysicalQuantumStore");
        }
        if (plan.targetRepositoryAppDdlTables()) {
            readyReasons.add("targetAppDdlRepository");
        }
        if (tccReservationTargetAppDdlPlatformColumn(plan)) {
            readyReasons.add("targetAppDdlPlatformColumn");
        }
        if (tccReservationQuantumToAppDdlPlatformColumnClosed(plan)) {
            readyReasons.add("quantumToAppDdlPlatformColumnReservation");
        }
        return List.copyOf(readyReasons);
    }

    private static List<String> tccReservationActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "sourcePhysicalQuantumStore" -> actions.add("bindReservationSourceQuantumStore");
                case "targetAppDdlRepository" -> actions.add("bindReservationTargetAppDdlRepository");
                case "targetAppDdlPlatformColumn" ->
                        actions.add("promoteReservationTargetToAppDdlPlatformColumn");
                case "quantumToAppDdlPlatformColumnReservation" ->
                        actions.add("closeQuantumToAppDdlPlatformColumnReservation");
                default -> actions.add("resolveTccReservationPhysicalBinding");
            }
        }
        return List.copyOf(actions);
    }

    public static TccReservationPlan enrichTccReservationPlan(TccReservationPlan plan) {
        if (plan == null) {
            return null;
        }
        List<String> computedMissingReasons = tccReservationMissingReasons(plan);
        String readiness = !isBlank(plan.readiness())
                ? plan.readiness()
                : (computedMissingReasons.isEmpty()
                        ? "TCC_RESERVATION_READY"
                        : "TCC_RESERVATION_INCOMPLETE");
        boolean ready = "TCC_RESERVATION_READY".equals(readiness);
        List<String> missingReasons = safeList(plan.missingReasons()).isEmpty()
                ? (ready ? List.of("NO_MISSING_RESERVATION_REASON") : computedMissingReasons)
                : safeList(plan.missingReasons());
        List<String> readyReasons = safeList(plan.readyReasons()).isEmpty() && ready
                ? tccReservationReadyReasons(plan)
                : safeList(plan.readyReasons());
        List<String> actions = safeList(plan.actions()).isEmpty()
                ? tccReservationActions(ready ? List.of() : missingReasons)
                : safeList(plan.actions());
        String reservationBoundary = !isBlank(plan.reservationBoundary())
                ? plan.reservationBoundary()
                : tccReservationBoundaryKey(plan);
        String physicalReservationPath = !isBlank(plan.physicalReservationPath())
                ? plan.physicalReservationPath()
                : tccReservationPhysicalPath(plan);
        String reservationContract = !isBlank(plan.reservationContract())
                ? plan.reservationContract()
                : tccReservationContractKey(plan, ready ? List.of() : missingReasons);
        return new TccReservationPlan(plan.serviceName(),
                plan.resourceId(),
                plan.actionName(),
                plan.sourceAttributeId(),
                plan.reservationAttributeId(),
                plan.sourceQuantumStoreName(),
                plan.sourceQuantumStoreAdapterName(),
                plan.sourceQuantumStoreConfiguration(),
                plan.sourceQuantumDatasourceUrl(),
                plan.sourceQuantumJournalTableName(),
                plan.sourceQuantumValueTableName(),
                plan.sourceQuantumAttribute(),
                plan.targetObjectType(),
                plan.targetObjectPath(),
                plan.targetTableName(),
                plan.targetColumnName(),
                plan.targetRepositoryName(),
                plan.targetResolvedRepositoryName(),
                plan.targetRepositoryAdapterName(),
                plan.targetRepositoryAppDdlTables(),
                plan.targetRepositoryAdapterConfiguration(),
                plan.targetRepositoryStorageTableName(),
                plan.targetRepositoryDatasourceUrl(),
                plan.targetAppDdlColumnPushdown(),
                plan.targetPlatformColumn(),
                plan.targetObjectBackedColumn(),
                plan.targetPrimaryKeyColumn(),
                readiness,
                readyReasons,
                missingReasons,
                actions,
                reservationBoundary,
                physicalReservationPath,
                reservationContract);
    }

    private static String sagaRecoveryEvidencePath(SagaRecoveryEvidenceReview plan) {
        if (plan == null) {
            return "NO_SAGA->NO_TARGET_SERVICE:NO_EVIDENCE_ROLE:NO_OBJECT_TYPE";
        }
        String saga = isBlank(plan.sagaServiceName) ? "NO_SAGA" : plan.sagaServiceName;
        String target = isBlank(plan.targetServiceName) ? "NO_TARGET_SERVICE" : plan.targetServiceName;
        String role = isBlank(plan.evidenceRole) ? "NO_EVIDENCE_ROLE" : plan.evidenceRole;
        String objectType = isBlank(plan.objectType) ? "NO_OBJECT_TYPE" : plan.objectType;
        return saga + "->" + target + ":" + role + ":" + objectType;
    }

    private static List<String> sagaRecoveryEvidenceReadyReasons(SagaRecoveryEvidenceReview plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        if (!isBlank(plan.sagaServiceName)) {
            reasons.add("saga");
        }
        if (!isBlank(plan.targetServiceName)) {
            reasons.add("targetService");
        }
        if (!isBlank(plan.evidenceRole)) {
            reasons.add("evidenceRole:" + plan.evidenceRole);
        }
        if (!isBlank(plan.objectType)) {
            reasons.add("objectType");
        }
        if (!isBlank(plan.ruleSource)) {
            reasons.add("ruleSource");
        }
        if (!safeList(plan.effectiveSavePhases).isEmpty()) {
            reasons.add("savePhase");
        }
        if (!isBlank(plan.snapshotBoundary)) {
            reasons.add("snapshotBoundary");
        }
        if (!isBlank(plan.recoveryRole)) {
            reasons.add("recoveryRole");
        }
        if (!safeList(plan.objectIdPaths).isEmpty()) {
            reasons.add("objectKey");
        }
        if (!isBlank(firstNonBlank(plan.resolvedRepositoryName, plan.repositoryName))) {
            reasons.add("repository");
        }
        if (!isBlank(plan.repositoryAdapterName)) {
            reasons.add("repositoryAdapter");
        }
        if (plan.repositoryAppDdlTables) {
            reasons.add("repositoryAppDdl");
        }
        if (!isBlank(plan.snapshotStorageTableName)) {
            reasons.add("snapshotStorageTable");
        }
        return List.copyOf(reasons);
    }

    private static List<String> sagaRecoveryEvidenceMissingReasons(SagaRecoveryEvidenceReview plan) {
        if (plan == null) {
            return List.of("plan");
        }
        List<String> missing = new ArrayList<>();
        if (isBlank(plan.sagaServiceName)) {
            missing.add("saga");
        }
        if (isBlank(plan.targetServiceName)) {
            missing.add("targetService");
        }
        if (isBlank(plan.evidenceRole)) {
            missing.add("evidenceRole");
        }
        if (isBlank(plan.objectType)) {
            missing.add("objectType");
        }
        if (isBlank(plan.ruleSource)) {
            missing.add("ruleSource");
        }
        if (safeList(plan.effectiveSavePhases).isEmpty()) {
            missing.add("savePhase");
        }
        if (isBlank(plan.snapshotBoundary)) {
            missing.add("snapshotBoundary");
        }
        if (isBlank(plan.recoveryRole)) {
            missing.add("recoveryRole");
        }
        if (safeList(plan.objectIdPaths).isEmpty()) {
            missing.add("objectKey");
        }
        if (isBlank(firstNonBlank(plan.resolvedRepositoryName, plan.repositoryName))) {
            missing.add("repository");
        }
        if (isBlank(plan.repositoryAdapterName)) {
            missing.add("repositoryAdapter");
        }
        if (!plan.repositoryAppDdlTables) {
            missing.add("repositoryAppDdl");
        }
        if (isBlank(plan.snapshotStorageTableName)) {
            missing.add("snapshotStorageTable");
        }
        return List.copyOf(missing);
    }

    private static List<String> sagaRecoveryEvidenceActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "plan" -> actions.add("registerSagaRecoveryEvidencePlan");
                case "saga", "targetService", "evidenceRole" ->
                        actions.add("bindSagaTransactionRecoveryEvidence");
                case "objectType", "ruleSource", "savePhase", "snapshotBoundary", "recoveryRole" ->
                        actions.add("declareSnapshotArgumentRecovery");
                case "objectKey" -> actions.add("pushSagaRecoveryObjectKeyToAppDdl");
                case "repository" -> actions.add("bindSagaRecoveryEvidenceRepository");
                case "repositoryAdapter" -> actions.add("configureSagaRecoveryRepositoryAdapter");
                case "repositoryAppDdl" -> actions.add("enableRepositoryAppDdlTables");
                case "snapshotStorageTable" -> actions.add("configureSnapshotStorageTable");
                default -> actions.add("resolveSagaRecoveryEvidenceReadiness");
            }
        }
        return List.copyOf(actions);
    }

    private static SagaRecoveryEvidenceReview enrichSagaRecoveryEvidenceReview(SagaRecoveryEvidenceReview plan) {
        if (plan == null) {
            return null;
        }
        List<String> missingReasons = safeList(plan.missingReasons).isEmpty()
                ? sagaRecoveryEvidenceMissingReasons(plan)
                : safeList(plan.missingReasons);
        String readiness = !isBlank(plan.readiness)
                ? plan.readiness
                : (missingReasons.isEmpty()
                        ? "SAGA_RECOVERY_EVIDENCE_READY"
                        : "SAGA_RECOVERY_EVIDENCE_INCOMPLETE");
        List<String> readyReasons = safeList(plan.readyReasons).isEmpty()
                && "SAGA_RECOVERY_EVIDENCE_READY".equals(readiness)
                ? sagaRecoveryEvidenceReadyReasons(plan)
                : safeList(plan.readyReasons);
        if (missingReasons.isEmpty() && "SAGA_RECOVERY_EVIDENCE_READY".equals(readiness)) {
            missingReasons = List.of("NO_MISSING_SAGA_RECOVERY_EVIDENCE_REASON");
        }
        List<String> actions = safeList(plan.actions).isEmpty()
                ? sagaRecoveryEvidenceActions("SAGA_RECOVERY_EVIDENCE_READY".equals(readiness)
                        ? List.of()
                        : missingReasons)
                : safeList(plan.actions);
        String evidencePath = !isBlank(plan.evidencePath)
                ? plan.evidencePath
                : sagaRecoveryEvidencePath(plan);
        String recoveryContract = !isBlank(plan.recoveryContract)
                ? plan.recoveryContract
                : sagaRecoveryEvidenceContract(plan, evidencePath, readiness);
        plan.readiness = readiness;
        plan.readyReasons = new ArrayList<>(readyReasons);
        plan.missingReasons = new ArrayList<>(missingReasons);
        plan.actions = new ArrayList<>(actions);
        plan.evidencePath = evidencePath;
        plan.recoveryContract = recoveryContract;
        return plan;
    }

    private static String sagaRecoveryEvidenceContract(SagaRecoveryEvidenceReview plan,
                                                       String evidencePath,
                                                       String readiness) {
        if (plan == null) {
            return "NO_SAGA_RECOVERY_EVIDENCE_CONTRACT";
        }
        return indexKey(evidencePath)
                + "->" + indexKey(plan.snapshotBoundary)
                + "+" + indexKey(plan.recoveryRole)
                + "+" + indexKey(firstNonBlank(plan.resolvedRepositoryName, plan.repositoryName))
                + "+" + indexKey(plan.repositoryAdapterName)
                + "+" + (plan.repositoryAppDdlTables ? "APP_DDL_TABLES" : "NO_APP_DDL_TABLES")
                + "+" + indexKey(plan.snapshotStorageTableName)
                + ":" + indexKey(readiness);
    }

    private static List<String> idempotencyReadyReasons(IdempotencyPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        if (!isBlank(plan.serviceName())) {
            reasons.add("service");
        }
        if (!isBlank(plan.inputHeaderName())) {
            reasons.add("header");
        }
        if (plan.idempotencyKeyAvailable()) {
            reasons.add("key");
        }
        if (!isBlank(plan.idempotencyMode())) {
            reasons.add("mode:" + plan.idempotencyMode());
        }
        if (!isBlank(plan.idempotencyOwner())) {
            reasons.add("owner:" + plan.idempotencyOwner());
        }
        if (!plan.physicalBindingRequired() || !isBlank(plan.backendName())) {
            reasons.add("physicalBinding");
        }
        if (plan.adapterAvailable()) {
            reasons.add("adapter");
        }
        if (plan.adapterSupportsTransaction()) {
            reasons.add("transactionSupport");
        }
        if (idempotencyFrameworkMode(plan)) {
            if (!isBlank(plan.idempotencyStoreName())) {
                reasons.add("frameworkStore");
            }
            if (!isBlank(plan.idempotencyTableName())) {
                reasons.add("frameworkTable");
            }
            if (!isBlank(plan.idempotencyDatasourceUrl())) {
                reasons.add("frameworkDatasource");
            }
            if (!isBlank(plan.idempotencyDriverClassName())) {
                reasons.add("frameworkDriver");
            }
        } else if (idempotencyObjectStoreMode(plan)) {
            reasons.add("objectStoreBoundary");
        } else if (idempotencyServiceMode(plan)) {
            reasons.add("serviceInternalIdempotency");
            reasons.add("quantumServiceOwner");
        }
        if (idempotencyBoundaryAvailable(plan)) {
            reasons.add("boundary");
        }
        return List.copyOf(reasons);
    }

    private static List<String> idempotencyMissingReasons(IdempotencyPlan plan) {
        if (plan == null) {
            return List.of("plan");
        }
        List<String> missing = new ArrayList<>();
        if (isBlank(plan.serviceName())) {
            missing.add("service");
        }
        if (plan.idempotencyKeyRequired() && !plan.idempotencyKeyAvailable()) {
            missing.add("key");
        }
        if (plan.physicalBindingRequired() && isBlank(plan.backendName())) {
            missing.add("physicalBinding");
        }
        if (!plan.adapterAvailable()) {
            missing.add("adapterUnavailable");
        }
        if (plan.adapterAvailable() && !plan.adapterSupportsTransaction()) {
            missing.add("unsupportedTransaction");
        }
        if (idempotencyFrameworkMode(plan)) {
            if (isBlank(plan.idempotencyStoreName())) {
                missing.add("storeName");
            }
            if (isBlank(plan.idempotencyTableName())) {
                missing.add("tableName");
            }
            if (isBlank(plan.idempotencyDatasourceUrl())) {
                missing.add("datasource");
            }
            if (isBlank(plan.idempotencyDriverClassName())) {
                missing.add("driver");
            }
        } else if (idempotencyServiceMode(plan)) {
            if (!idempotencyServiceInternalAvailable(plan)) {
                missing.add("serviceInternalIdempotency");
            }
        } else if (idempotencyObjectStoreMode(plan) && !idempotencyRuntimeAvailable(plan)) {
            missing.add("runtimeStore");
        }
        if (!idempotencyBoundaryAvailable(plan)) {
            missing.add("boundary");
        }
        return List.copyOf(missing);
    }

    private static List<String> idempotencyActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "plan" -> actions.add("registerIdempotencyPlan");
                case "service" -> actions.add("bindIdempotencyService");
                case "key" -> actions.add("declareIdempotencyKeyInAppHeader");
                case "physicalBinding" -> actions.add("bindIdempotentServiceToPhysicalBackend");
                case "adapterUnavailable" -> actions.add("registerIdempotencyPhysicalAdapter");
                case "unsupportedTransaction" -> actions.add("enableAdapterTransactionSupport");
                case "storeName" -> actions.add("configureIdempotencyStoreName");
                case "tableName" -> actions.add("configureIdempotencyTableName");
                case "datasource" -> actions.add("configureIdempotencyDatasource");
                case "driver" -> actions.add("configureIdempotencyDriver");
                case "serviceInternalIdempotency" ->
                        actions.add("completeServiceInternalIdempotencyContract");
                case "runtimeStore" -> actions.add("configureFrameworkIdempotencyRuntimeStore");
                case "boundary" -> actions.add("closeIdempotencyBoundary");
                default -> actions.add("resolveIdempotencyRuntimeBinding");
            }
        }
        return List.copyOf(actions);
    }

    private static IdempotencyPlan enrichIdempotencyPlan(IdempotencyPlan plan) {
        if (plan == null) {
            return null;
        }
        List<String> missingReasons = safeList(plan.missingReasons()).isEmpty()
                ? idempotencyMissingReasons(plan)
                : plan.missingReasons();
        String readiness = firstNonBlank(plan.readiness(),
                missingReasons.isEmpty() || (missingReasons.size() == 1
                        && "NO_MISSING_IDEMPOTENCY_REASON".equals(missingReasons.get(0)))
                        ? "IDEMPOTENCY_READY"
                        : "IDEMPOTENCY_INCOMPLETE");
        List<String> readyReasons = safeList(plan.readyReasons()).isEmpty()
                && "IDEMPOTENCY_READY".equals(readiness)
                ? idempotencyReadyReasons(plan)
                : plan.readyReasons();
        List<String> normalizedMissingReasons = missingReasons.isEmpty()
                ? List.of("NO_MISSING_IDEMPOTENCY_REASON")
                : missingReasons;
        List<String> actions = safeList(plan.actions()).isEmpty()
                ? idempotencyActions(missingReasons)
                : plan.actions();
        String ownershipBoundary = firstNonBlank(plan.ownershipBoundary(),
                idempotencyOwnershipBoundaryKey(plan));
        String physicalRuntimePath = firstNonBlank(plan.physicalRuntimePath(),
                idempotencyPhysicalRuntimePath(plan));
        String executionContract = firstNonBlank(plan.executionContract(),
                idempotencyExecutionContractKey(plan, readiness));
        return new IdempotencyPlan(plan.serviceName(), plan.serviceKind(), plan.transactionMode(),
                plan.transactionResourceId(), plan.inputHeaderName(), plan.idempotencyKeyRequired(),
                plan.idempotencyKeyFieldName(), plan.idempotencyKeyPath(), plan.idempotencyKeyAvailable(),
                plan.physicalBindingRequired(), plan.backendName(), plan.adapterName(),
                plan.idempotencyMode(), plan.idempotencyOwner(), plan.idempotencyOwnerStoreNames(),
                plan.idempotencyOwnerAdapterNames(), plan.idempotencyOwnerAccessModes(),
                plan.idempotencyOwnerAttributeIds(), plan.adapterAvailable(),
                plan.adapterSupportsTransaction(), plan.idempotencyStoreName(),
                plan.idempotencyTableName(), plan.idempotencyDatasourceUrl(),
                plan.idempotencyDriverClassName(), readiness, readyReasons,
                normalizedMissingReasons, actions, ownershipBoundary, physicalRuntimePath,
                executionContract);
    }

    private static boolean idempotencyFrameworkMode(IdempotencyPlan plan) {
        return plan == null
                || isBlank(plan.idempotencyMode())
                || "FRAMEWORK_IDEMPOTENCY".equals(plan.idempotencyMode());
    }

    private static boolean idempotencyObjectStoreMode(IdempotencyPlan plan) {
        return plan != null && "OBJECT_STORE_IDEMPOTENCY".equals(plan.idempotencyMode());
    }

    private static boolean idempotencyServiceMode(IdempotencyPlan plan) {
        return plan != null && "SERVICE_IDEMPOTENCY".equals(plan.idempotencyMode());
    }

    private static boolean idempotencyRuntimeAvailable(IdempotencyPlan plan) {
        if (plan == null || !plan.idempotencyKeyAvailable()) {
            return false;
        }
        if (idempotencyFrameworkMode(plan)) {
            return !isBlank(plan.idempotencyStoreName())
                    && !isBlank(plan.idempotencyTableName())
                    && !isBlank(plan.idempotencyDatasourceUrl())
                    && !isBlank(plan.idempotencyDriverClassName())
                    && plan.adapterAvailable()
                    && plan.adapterSupportsTransaction();
        }
        if (idempotencyObjectStoreMode(plan)) {
            return !plan.physicalBindingRequired()
                    || (!isBlank(plan.backendName())
                    && plan.adapterAvailable()
                    && plan.adapterSupportsTransaction());
        }
        if (idempotencyServiceMode(plan)) {
            return idempotencyServiceInternalAvailable(plan);
        }
        return false;
    }

    private static boolean idempotencyServiceInternalAvailable(IdempotencyPlan plan) {
        return idempotencyServiceMode(plan)
                && plan.idempotencyKeyAvailable()
                && "QUANTUM_SERVICE".equals(plan.idempotencyOwner())
                && !isBlank(plan.idempotencyOwnerStoreNames())
                && !isBlank(plan.idempotencyOwnerAdapterNames())
                && !isBlank(plan.idempotencyOwnerAccessModes())
                && !isBlank(plan.idempotencyOwnerAttributeIds());
    }

    private static boolean idempotencyBoundaryAvailable(IdempotencyPlan plan) {
        return idempotencyRuntimeAvailable(plan);
    }

    private static String idempotencyOwnerKey(IdempotencyPlan plan) {
        return indexKey(plan == null ? null : plan.idempotencyOwner());
    }

    private static String idempotencyModeKey(IdempotencyPlan plan) {
        return indexKey(plan == null ? null : plan.idempotencyMode());
    }

    private static String idempotencyKeyPathKey(IdempotencyPlan plan) {
        if (plan == null || isBlank(plan.idempotencyKeyPath())) {
            return "NO_IDEMPOTENCY_KEY_PATH";
        }
        return plan.idempotencyKeyPath();
    }

    private static String idempotencyBoundaryKey(IdempotencyPlan plan) {
        if (idempotencyServiceMode(plan)) {
            return "QUANTUM_SERVICE_INTERNAL";
        }
        if (idempotencyObjectStoreMode(plan)) {
            return "OBJECT_STORE_TCC_BOUNDARY";
        }
        return "FRAMEWORK_RUNTIME_STORE";
    }

    private static String idempotencyOwnershipBoundaryKey(IdempotencyPlan plan) {
        return idempotencyOwnerKey(plan) + "+"
                + idempotencyModeKey(plan) + "+"
                + idempotencyBoundaryKey(plan);
    }

    private static String idempotencyStoreKey(IdempotencyPlan plan) {
        if (plan == null) {
            return "UNKNOWN";
        }
        if (idempotencyServiceMode(plan) || idempotencyObjectStoreMode(plan)) {
            return indexKey(plan.idempotencyOwnerStoreNames());
        }
        return indexKey(firstNonBlank(plan.idempotencyStoreName(), plan.idempotencyTableName()));
    }

    private static String idempotencyPhysicalRuntimePath(IdempotencyPlan plan) {
        if (plan == null) {
            return "NO_SERVICE:NO_IDEMPOTENCY_KEY_PATH->NO_BACKEND:UNKNOWN.NO_IDEMPOTENCY_TABLE";
        }
        String service = isBlank(plan.serviceName()) ? "NO_SERVICE" : plan.serviceName();
        String keyPath = idempotencyKeyPathKey(plan);
        String backend = isBlank(plan.backendName()) ? "NO_BACKEND" : plan.backendName();
        String store = idempotencyStoreKey(plan);
        String table = isBlank(plan.idempotencyTableName()) ? "NO_IDEMPOTENCY_TABLE" : plan.idempotencyTableName();
        if (idempotencyServiceMode(plan)) {
            return service + ":" + keyPath + "->QUANTUM_SERVICE_INTERNAL:" + store;
        }
        if (idempotencyObjectStoreMode(plan)) {
            return service + ":" + keyPath + "->" + backend + ":OBJECT_STORE_TCC_BOUNDARY." + store;
        }
        return service + ":" + keyPath + "->" + backend + ":" + store + "." + table;
    }

    private static String idempotencyExecutionContractKey(IdempotencyPlan plan, String readiness) {
        if (plan == null) {
            return "NO_IDEMPOTENCY_OWNERSHIP+NO_IDEMPOTENCY_KEY_PATH+NO_RUNTIME_PATH+UNKNOWN_READINESS";
        }
        return idempotencyOwnershipBoundaryKey(plan)
                + "+"
                + idempotencyKeyPathKey(plan)
                + "+"
                + idempotencyPhysicalRuntimePath(plan)
                + "+"
                + indexKey(readiness);
    }

    private static String transactionReversalPath(TransactionReversalRule rule) {
        if (rule == null) {
            return "NO_SOURCE_SERVICE->NO_TARGET_SERVICE:NO_PHASE";
        }
        if (!isBlank(rule.reversalPath)) {
            return rule.reversalPath;
        }
        String source = isBlank(rule.sourceServiceName) ? "NO_SOURCE_SERVICE" : rule.sourceServiceName;
        String target = isBlank(rule.targetServiceName) ? "NO_TARGET_SERVICE" : rule.targetServiceName;
        String phase = isBlank(rule.phaseName) ? "NO_PHASE" : rule.phaseName;
        return source + "->" + target + ":" + phase;
    }

    private static void enrichTransactionReversalRule(TransactionReversalRule rule) {
        if (rule == null) {
            return;
        }
        if (isBlank(rule.reversalPath)) {
            rule.reversalPath = transactionReversalPath(rule);
        }
        if (isBlank(rule.executionContract)) {
            rule.executionContract = transactionReversalExecutionContractKey(rule);
        }
        List<String> calculatedMissingReasons = transactionReversalMissingReasons(rule);
        if (safeList(rule.missingReasons).isEmpty()) {
            rule.missingReasons = calculatedMissingReasons.isEmpty()
                    ? List.of("NO_MISSING_REVERSAL_REASON")
                    : calculatedMissingReasons;
        }
        if (safeList(rule.readyReasons).isEmpty() && calculatedMissingReasons.isEmpty()) {
            rule.readyReasons = transactionReversalReadyReasons(rule);
        }
        if (safeList(rule.actions).isEmpty()) {
            rule.actions = transactionReversalActions(calculatedMissingReasons);
        }
    }

    private static String transactionReversalExecutionContractKey(TransactionReversalRule rule) {
        if (rule == null) {
            return "NO_REVERSAL_KIND+NO_PHASE+UNKNOWN_READINESS";
        }
        if (!isBlank(rule.executionContract)) {
            return rule.executionContract;
        }
        String kind = isBlank(rule.ruleKind) ? "NO_REVERSAL_KIND" : rule.ruleKind;
        String phase = isBlank(rule.phaseName) ? "NO_PHASE" : rule.phaseName;
        String readiness = isBlank(rule.readiness) ? "UNKNOWN_READINESS" : rule.readiness;
        return kind + "+" + phase + "+" + readiness;
    }

    private static List<String> transactionReversalRuleReadyReasons(TransactionReversalRule rule) {
        if (rule == null) {
            return List.of();
        }
        if (!safeList(rule.readyReasons).isEmpty()) {
            return List.copyOf(rule.readyReasons);
        }
        if (!transactionReversalMissingReasons(rule).isEmpty()) {
            return List.of();
        }
        return transactionReversalReadyReasons(rule);
    }

    private static List<String> transactionReversalRuleMissingReasons(TransactionReversalRule rule) {
        if (rule == null) {
            return List.of("rule");
        }
        if (!safeList(rule.missingReasons).isEmpty()) {
            return List.copyOf(rule.missingReasons);
        }
        List<String> missingReasons = transactionReversalMissingReasons(rule);
        return missingReasons.isEmpty() ? List.of("NO_MISSING_REVERSAL_REASON") : missingReasons;
    }

    private static List<String> transactionReversalRuleActions(TransactionReversalRule rule) {
        if (rule == null) {
            return List.of("declareTransactionReversalRule");
        }
        if (!safeList(rule.actions).isEmpty()) {
            return List.copyOf(rule.actions);
        }
        return transactionReversalActions(transactionReversalMissingReasons(rule));
    }

    private static List<String> transactionReversalReadyReasons(TransactionReversalRule rule) {
        if (rule == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        if (!isBlank(rule.ruleKind)) {
            reasons.add("kind:" + rule.ruleKind);
        }
        if (!isBlank(rule.sourceServiceName)) {
            reasons.add("source");
        }
        if (rule.targetDeclared && !isBlank(rule.targetServiceName)) {
            reasons.add("targetDeclared");
        }
        if (!isBlank(rule.phaseName)) {
            reasons.add("phase");
        }
        if (!"TCC_LIFECYCLE".equals(rule.ruleKind) || rule.lifecycleComplete) {
            reasons.add("reversalLifecycleReady");
        }
        if ("OBJECT_COMPENSATION".equals(rule.ruleKind)) {
            reasons.add("objectServiceReversal");
        } else if ("REMOTE_COMPENSATION".equals(rule.ruleKind)) {
            reasons.add("remoteServiceReversal");
        } else if ("TCC_LIFECYCLE".equals(rule.ruleKind)) {
            reasons.add("tccLifecycleReversal");
        }
        return List.copyOf(reasons);
    }

    private static List<String> transactionReversalMissingReasons(TransactionReversalRule rule) {
        if (rule == null) {
            return List.of("rule");
        }
        List<String> missing = new ArrayList<>();
        if (isBlank(rule.ruleKind)) {
            missing.add("kind");
        }
        if (isBlank(rule.sourceServiceName)) {
            missing.add("source");
        }
        if (!rule.targetDeclared || isBlank(rule.targetServiceName)) {
            missing.add("target");
        }
        if (isBlank(rule.phaseName)) {
            missing.add("phase");
        }
        if ("TCC_LIFECYCLE".equals(rule.ruleKind) && !rule.lifecycleComplete) {
            missing.add("tccLifecycle");
        }
        return List.copyOf(missing);
    }

    private static List<String> transactionReversalActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "rule" -> actions.add("declareTransactionReversalRule");
                case "kind" -> actions.add("classifyTransactionReversalKind");
                case "source" -> actions.add("bindReversalSourceService");
                case "target" -> actions.add("declareObjectCompensationOrLifecycleTarget");
                case "phase" -> actions.add("declareReversalPhase");
                case "tccLifecycle" -> actions.add("declareTccConfirmAndCancelLifecycle");
                default -> actions.add("resolveTransactionReversalReadiness");
            }
        }
        return List.copyOf(actions);
    }

    private static List<String> objectSnapshotClosureMissingReasons(ObjectSnapshotPlan plan) {
        if (plan == null) {
            return List.of("plan");
        }
        List<String> missingReasons = new ArrayList<>();
        if (!plan.appDdlDerived()) {
            missingReasons.add("appDdlDerived");
        }
        if (!plan.appDdlObjectKeyPushdown()) {
            missingReasons.add("keyPushdown");
        }
        if (plan.escapeHatch()) {
            missingReasons.add("escapeHatchClear");
        }
        return List.copyOf(missingReasons);
    }

    private static List<String> objectSnapshotClosureReadyReasons(ObjectSnapshotPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> readyReasons = new ArrayList<>();
        if (plan.appDdlDerived()) {
            readyReasons.add("appDdlDerived");
        }
        if (plan.appDdlObjectKeyPushdown()) {
            readyReasons.add("keyPushdown");
        }
        if (!plan.escapeHatch()) {
            readyReasons.add("escapeHatchClear");
        }
        return List.copyOf(readyReasons);
    }

    private static List<String> objectSnapshotClosureActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "plan" -> actions.add("registerObjectSnapshotPlan");
                case "appDdlDerived" -> actions.add("deriveSnapshotFromAppDdl");
                case "keyPushdown" -> actions.add("pushSnapshotKeyToAppDdl");
                case "escapeHatchClear" -> actions.add("clearSnapshotEscapeHatch");
                default -> actions.add("resolveSnapshotBinding");
            }
        }
        return List.copyOf(actions);
    }

    private static ObjectSnapshotPlan enrichObjectSnapshotPlan(ObjectSnapshotPlan plan) {
        if (plan == null) {
            return null;
        }
        List<String> closureMissingReasons = safeList(plan.closureMissingReasons()).isEmpty()
                ? objectSnapshotClosureMissingReasons(plan)
                : safeList(plan.closureMissingReasons());
        String closureReadiness = !isBlank(plan.closureReadiness())
                ? plan.closureReadiness()
                : (closureMissingReasons.isEmpty() ? "SNAPSHOT_CLOSED" : "SNAPSHOT_OPEN");
        List<String> closureReadyReasons = safeList(plan.closureReadyReasons()).isEmpty()
                && "SNAPSHOT_CLOSED".equals(closureReadiness)
                ? objectSnapshotClosureReadyReasons(plan)
                : safeList(plan.closureReadyReasons());
        if (closureMissingReasons.isEmpty() && "SNAPSHOT_CLOSED".equals(closureReadiness)) {
            closureMissingReasons = List.of("NO_MISSING_SNAPSHOT_REASON");
        }
        List<String> closureActions = safeList(plan.closureActions()).isEmpty()
                ? objectSnapshotClosureActions("SNAPSHOT_CLOSED".equals(closureReadiness)
                        ? List.of()
                        : closureMissingReasons)
                : safeList(plan.closureActions());
        String snapshotPath = !isBlank(plan.snapshotPath())
                ? plan.snapshotPath()
                : objectSnapshotPath(plan);
        String closureContract = !isBlank(plan.closureContract())
                ? plan.closureContract()
                : objectSnapshotClosureContractKey(plan,
                        "SNAPSHOT_CLOSED".equals(closureReadiness) ? List.of() : closureMissingReasons,
                        "SNAPSHOT_CLOSED", "SNAPSHOT_OPEN");

        List<String> businessMissingReasons = safeList(plan.businessObjectClosureMissingReasons()).isEmpty()
                ? businessObjectSnapshotClosureMissingReasons(plan)
                : safeList(plan.businessObjectClosureMissingReasons());
        String businessReadiness = !isBlank(plan.businessObjectClosureReadiness())
                ? plan.businessObjectClosureReadiness()
                : (businessMissingReasons.isEmpty()
                        ? "BUSINESS_OBJECT_SNAPSHOT_CLOSED"
                        : "BUSINESS_OBJECT_SNAPSHOT_OPEN");
        List<String> businessReadyReasons = safeList(plan.businessObjectClosureReadyReasons()).isEmpty()
                && "BUSINESS_OBJECT_SNAPSHOT_CLOSED".equals(businessReadiness)
                ? businessObjectSnapshotClosureReadyReasons(plan)
                : safeList(plan.businessObjectClosureReadyReasons());
        if (businessMissingReasons.isEmpty() && "BUSINESS_OBJECT_SNAPSHOT_CLOSED".equals(businessReadiness)) {
            businessMissingReasons = List.of("NO_MISSING_BUSINESS_OBJECT_SNAPSHOT_REASON");
        }
        List<String> businessActions = safeList(plan.businessObjectClosureActions()).isEmpty()
                ? businessObjectSnapshotClosureActions("BUSINESS_OBJECT_SNAPSHOT_CLOSED".equals(businessReadiness)
                        ? List.of()
                        : businessMissingReasons)
                : safeList(plan.businessObjectClosureActions());
        String businessContract = !isBlank(plan.businessObjectClosureContract())
                ? plan.businessObjectClosureContract()
                : objectSnapshotClosureContractKey(plan,
                        "BUSINESS_OBJECT_SNAPSHOT_CLOSED".equals(businessReadiness)
                                ? List.of()
                                : businessMissingReasons,
                        "BUSINESS_OBJECT_SNAPSHOT_CLOSED", "BUSINESS_OBJECT_SNAPSHOT_OPEN");
        return new ObjectSnapshotPlan(plan.serviceName(),
                plan.objectType(),
                plan.source(),
                plan.appDdlTableName(),
                plan.appDdlIndexName(),
                plan.appDdlDerived(),
                plan.escapeHatch(),
                plan.ruleSource(),
                plan.timing(),
                plan.effectiveSavePhases(),
                plan.snapshotBoundary(),
                plan.recoveryRole(),
                plan.transactionEvidence(),
                plan.currentObject(),
                plan.argumentRecoveryEvidence(),
                plan.rollbackRecoveryEvidence(),
                plan.argumentIndex(),
                plan.objectIdPaths(),
                plan.appDdlObjectKeyPaths(),
                plan.repositoryName(),
                plan.resolvedRepositoryName(),
                plan.repositoryAdapterName(),
                plan.repositoryAppDdlTables(),
                plan.repositoryAdapterConfiguration(),
                plan.snapshotStorageTableName(),
                plan.transactionHistoryTableName(),
                plan.transactionArchiveTableName(),
                plan.objectKeyColumns(),
                plan.appDdlObjectKeyPushdown(),
                plan.externalObjectKeyRoots(),
                plan.objectKeySource(),
                plan.objectKeyAppDdlIndexName(),
                plan.objectKeyAppDdlDerived(),
                plan.objectKeyEscapeHatch(),
                closureReadiness,
                closureReadyReasons,
                closureMissingReasons,
                closureActions,
                snapshotPath,
                closureContract,
                businessReadiness,
                businessReadyReasons,
                businessMissingReasons,
                businessActions,
                businessContract,
                plan.rule());
    }

    private static String objectKeyColumnSetKey(ObjectSnapshotPlan plan) {
        if (plan == null || safeList(plan.objectKeyColumns()).isEmpty()) {
            return "NO_OBJECT_KEY_COLUMNS";
        }
        return String.join("+", plan.objectKeyColumns());
    }

    private static String objectSnapshotPath(ObjectSnapshotPlan plan) {
        if (plan == null) {
            return "NO_SERVICE:NO_OBJECT_TYPE:NO_BOUNDARY:NO_RECOVERY_ROLE";
        }
        String service = isBlank(plan.serviceName()) ? "NO_SERVICE" : plan.serviceName();
        String objectType = isBlank(plan.objectType()) ? "NO_OBJECT_TYPE" : plan.objectType();
        String boundary = isBlank(plan.snapshotBoundary()) ? "NO_BOUNDARY" : plan.snapshotBoundary();
        String recoveryRole = isBlank(plan.recoveryRole()) ? "NO_RECOVERY_ROLE" : plan.recoveryRole();
        return service + ":" + objectType + ":" + boundary + ":" + recoveryRole;
    }

    private static String objectSnapshotClosureContractKey(ObjectSnapshotPlan plan,
                                                          List<String> missingReasons,
                                                          String closedKey,
                                                          String openKey) {
        if (plan == null) {
            return "NO_SNAPSHOT_CLOSURE_CONTRACT";
        }
        String readiness = safeList(missingReasons).isEmpty() ? closedKey : openKey;
        String keyPushdown = plan.appDdlObjectKeyPushdown() ? "KEY_PUSHDOWN" : "KEY_NOT_PUSHDOWN";
        String escapeHatch = plan.objectKeyEscapeHatch() ? "ESCAPE_HATCH_PRESENT" : "ESCAPE_HATCH_CLEAR";
        return objectSnapshotPath(plan)
                + "->"
                + indexKey(plan.objectKeySource())
                + "+"
                + keyPushdown
                + "+"
                + escapeHatch
                + "+"
                + objectKeyColumnSetKey(plan)
                + "+"
                + readiness;
    }

    private static List<String> businessObjectSnapshotClosureMissingReasons(ObjectSnapshotPlan plan) {
        if (plan == null) {
            return List.of("plan");
        }
        List<String> missingReasons = new ArrayList<>();
        if (!plan.objectKeyAppDdlDerived()) {
            missingReasons.add("objectKeyAppDdlDerived");
        }
        if (!plan.appDdlObjectKeyPushdown()) {
            missingReasons.add("keyPushdown");
        }
        if (plan.objectKeyEscapeHatch()) {
            missingReasons.add("objectKeyEscapeHatchClear");
        }
        return List.copyOf(missingReasons);
    }

    private static List<String> businessObjectSnapshotClosureReadyReasons(ObjectSnapshotPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> readyReasons = new ArrayList<>();
        if (plan.objectKeyAppDdlDerived()) {
            readyReasons.add("objectKeyAppDdlDerived");
        }
        if (plan.appDdlObjectKeyPushdown()) {
            readyReasons.add("keyPushdown");
        }
        if (!plan.objectKeyEscapeHatch()) {
            readyReasons.add("objectKeyEscapeHatchClear");
        }
        return List.copyOf(readyReasons);
    }

    private static List<String> businessObjectSnapshotClosureActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "plan" -> actions.add("registerBusinessObjectSnapshotPlan");
                case "objectKeyAppDdlDerived" -> actions.add("deriveObjectKeySnapshotFromAppDdl");
                case "keyPushdown" -> actions.add("pushSnapshotKeyToAppDdl");
                case "objectKeyEscapeHatchClear" -> actions.add("clearObjectKeySnapshotEscapeHatch");
                default -> actions.add("resolveBusinessObjectSnapshotBinding");
            }
        }
        return List.copyOf(actions);
    }

    private static List<String> currentSnapshotContextMissingReasons(CurrentSnapshotContextPlan plan) {
        if (plan == null) {
            return List.of("plan");
        }
        List<String> missingReasons = new ArrayList<>();
        if (!plan.resolvedFromSagaParameter()) {
            missingReasons.add("resolvedFromSagaParameter");
        }
        if (plan.ambiguousSagaParameter()) {
            missingReasons.add("ambiguousSagaParameter");
        }
        if (!plan.objectKeyAppDdlDerived()) {
            missingReasons.add("appDdlDerived");
        }
        if (!plan.appDdlObjectKeyPushdown()) {
            missingReasons.add("appDdlObjectKeyPushdown");
        }
        return List.copyOf(missingReasons);
    }

    private static List<String> currentSnapshotContextReadyReasons(CurrentSnapshotContextPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> readyReasons = new ArrayList<>();
        if (plan.resolvedFromSagaParameter()) {
            readyReasons.add("resolvedFromSagaParameter");
        }
        if (!plan.ambiguousSagaParameter()) {
            readyReasons.add("sagaParameterUnambiguous");
        }
        if (plan.objectKeyAppDdlDerived()) {
            readyReasons.add("appDdlDerived");
        }
        if (plan.appDdlObjectKeyPushdown()) {
            readyReasons.add("appDdlObjectKeyPushdown");
        }
        return List.copyOf(readyReasons);
    }

    private static List<String> currentSnapshotContextActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "plan" -> actions.add("registerCurrentSnapshotContextPlan");
                case "resolvedFromSagaParameter" -> actions.add("bindCurrentContextToSingleSagaParameter");
                case "ambiguousSagaParameter" -> actions.add("disambiguateCurrentContextSagaParameter");
                case "appDdlDerived" -> actions.add("deriveCurrentContextKeyFromAppDdl");
                case "appDdlObjectKeyPushdown" -> actions.add("pushCurrentContextObjectKeyToAppDdl");
                default -> actions.add("resolveCurrentSnapshotContextBinding");
            }
        }
        return List.copyOf(actions);
    }

    private static String currentContextObjectKeyColumnSetKey(CurrentSnapshotContextPlan plan) {
        if (plan == null || safeList(plan.objectKeyColumns()).isEmpty()) {
            return "NO_OBJECT_KEY_COLUMNS";
        }
        return String.join("+", plan.objectKeyColumns());
    }

    private static String currentSnapshotContextPath(CurrentSnapshotContextPlan plan) {
        if (plan == null) {
            return "NO_SAGA->NO_TARGET_SERVICE:NO_OBJECT_TYPE";
        }
        String saga = isBlank(plan.sagaServiceName()) ? "NO_SAGA" : plan.sagaServiceName();
        String target = isBlank(plan.targetServiceName()) ? "NO_TARGET_SERVICE" : plan.targetServiceName();
        String objectType = isBlank(plan.objectType()) ? "NO_OBJECT_TYPE" : plan.objectType();
        return saga + "->" + target + ":" + objectType;
    }

    private static CurrentSnapshotContextPlan enrichCurrentSnapshotContextPlan(CurrentSnapshotContextPlan plan) {
        if (plan == null) {
            return null;
        }
        List<String> missingReasons = safeList(plan.missingReasons()).isEmpty()
                ? currentSnapshotContextMissingReasons(plan)
                : safeList(plan.missingReasons());
        String readiness = !isBlank(plan.readiness())
                ? plan.readiness()
                : (missingReasons.isEmpty() ? "CURRENT_CONTEXT_CLOSED" : "CURRENT_CONTEXT_OPEN");
        List<String> readyReasons = safeList(plan.readyReasons()).isEmpty()
                && "CURRENT_CONTEXT_CLOSED".equals(readiness)
                ? currentSnapshotContextReadyReasons(plan)
                : safeList(plan.readyReasons());
        if (missingReasons.isEmpty() && "CURRENT_CONTEXT_CLOSED".equals(readiness)) {
            missingReasons = List.of("NO_MISSING_CURRENT_CONTEXT_REASON");
        }
        List<String> actions = safeList(plan.actions()).isEmpty()
                ? currentSnapshotContextActions("CURRENT_CONTEXT_CLOSED".equals(readiness)
                        ? List.of()
                        : missingReasons)
                : safeList(plan.actions());
        String contextPath = !isBlank(plan.contextPath())
                ? plan.contextPath()
                : currentSnapshotContextPath(plan);
        String closureContract = !isBlank(plan.closureContract())
                ? plan.closureContract()
                : currentSnapshotContextClosureContractKey(plan,
                        "CURRENT_CONTEXT_CLOSED".equals(readiness) ? List.of() : missingReasons);
        return new CurrentSnapshotContextPlan(plan.sagaServiceName(),
                plan.targetServiceName(),
                plan.objectType(),
                plan.objectIdPaths(),
                plan.candidateParameterIndexes(),
                plan.candidateParameterTypes(),
                plan.candidateParameterNames(),
                plan.contextObjectSource(),
                plan.appDdlObjectKeyPaths(),
                plan.objectKeyColumns(),
                plan.objectKeySource(),
                plan.objectKeyAppDdlIndexName(),
                plan.objectKeyAppDdlDerived(),
                plan.appDdlObjectKeyPushdown(),
                plan.resolvedFromSagaParameter(),
                plan.ambiguousSagaParameter(),
                readiness,
                readyReasons,
                missingReasons,
                actions,
                contextPath,
                closureContract);
    }

    private static String currentSnapshotContextClosureContractKey(CurrentSnapshotContextPlan plan,
            List<String> missingReasons) {
        String readiness = missingReasons == null || missingReasons.isEmpty()
                ? "CURRENT_CONTEXT_CLOSED"
                : "CURRENT_CONTEXT_OPEN";
        String resolution = plan != null && plan.resolvedFromSagaParameter()
                ? "RESOLVED_FROM_SAGA_PARAMETER"
                : "MISSING_SAGA_PARAMETER_RESOLUTION";
        String ambiguity = plan != null && plan.ambiguousSagaParameter()
                ? "AMBIGUOUS_SAGA_PARAMETER"
                : "SAGA_PARAMETER_UNAMBIGUOUS";
        String keyDerivation = plan != null && plan.objectKeyAppDdlDerived()
                ? "APP_DDL_KEY_DERIVED"
                : "MISSING_APP_DDL_KEY_DERIVATION";
        String keyPushdown = plan != null && plan.appDdlObjectKeyPushdown()
                ? "APP_DDL_KEY_PUSHDOWN"
                : "MISSING_APP_DDL_KEY_PUSHDOWN";
        return currentSnapshotContextPath(plan)
                + "->" + resolution
                + "+" + ambiguity
                + "+" + keyDerivation
                + "+" + keyPushdown
                + ":" + readiness;
    }

    private static List<String> scopeHeaderMissingReasons(ScopeHeaderPlan plan) {
        if (plan == null) {
            return List.of("plan");
        }
        List<String> missingReasons = new ArrayList<>();
        if (!plan.physicalBindingRequiredServiceNames().isEmpty()
                && !plan.physicalBindingAvailableServiceNames()
                        .containsAll(plan.physicalBindingRequiredServiceNames())) {
            missingReasons.add("physicalBinding");
        }
        if (!plan.idempotencyRequiredServiceNames().isEmpty()
                && !plan.idempotencyAvailableServiceNames()
                        .containsAll(plan.idempotencyRequiredServiceNames())) {
            missingReasons.add("idempotencyKey");
        }
        if (!plan.idempotencyRequiredServiceNames().isEmpty()
                && !plan.idempotencyRuntimeStoreAvailableServiceNames()
                        .containsAll(plan.idempotencyRequiredServiceNames())) {
            missingReasons.add("idempotencyRuntimeStore");
        }
        return List.copyOf(missingReasons);
    }

    private static List<String> deploymentValidationMissingReasons(DeploymentValidationPlan plan) {
        if (plan == null || plan.errors().isEmpty()) {
            return List.of("validationError");
        }
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        for (String error : plan.errors()) {
            addExtractedDeploymentValidationValues(reasons, error, "missingReasons");
            addExtractedDeploymentValidationValues(reasons, error, "missingReason");
            addExtractedDeploymentValidationValues(reasons, error, "reasons");
            addExtractedDeploymentValidationValues(reasons, error, "reason");
            addExtractedDeploymentValidationValues(reasons, error, "missing");
            if (reasons.isEmpty()) {
                reasons.add("validationError");
            }
        }
        reasons.remove("-");
        return reasons.isEmpty() ? List.of("validationError") : List.copyOf(reasons);
    }

    private static List<String> deploymentValidationActions(DeploymentValidationPlan plan) {
        if (plan == null || plan.passed()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String error : plan.errors()) {
            addExtractedDeploymentValidationValues(actions, error, "actions");
            addExtractedDeploymentValidationValues(actions, error, "action");
        }
        actions.remove("-");
        if (actions.isEmpty()) {
            actions.add("resolveDeploymentValidationError");
        }
        return List.copyOf(actions);
    }

    private static void addExtractedDeploymentValidationValues(LinkedHashSet<String> values,
                                                              String source,
                                                              String fieldName) {
        for (String rawValue : extractDeploymentValidationFieldValues(source, fieldName)) {
            for (String item : rawValue.split("[+,]")) {
                String value = item.trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
    }

    private static List<String> extractDeploymentValidationFieldValues(String source, String fieldName) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        String marker = fieldName + "=";
        int searchFrom = 0;
        while (searchFrom < source.length()) {
            int markerIndex = source.indexOf(marker, searchFrom);
            if (markerIndex < 0) {
                break;
            }
            int valueStart = markerIndex + marker.length();
            int valueEnd = deploymentValidationFieldValueEnd(source, valueStart);
            String value = source.substring(valueStart, valueEnd).trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
            searchFrom = valueEnd + 1;
        }
        return List.copyOf(values);
    }

    private static int deploymentValidationFieldValueEnd(String source, int valueStart) {
        int end = source.length();
        for (char delimiter : new char[]{':', ';', ' ', '\n', '\r', '\t'}) {
            int delimiterIndex = source.indexOf(delimiter, valueStart);
            if (delimiterIndex >= 0) {
                end = Math.min(end, delimiterIndex);
            }
        }
        return end;
    }

    private static RecoveryWorkItemPlan enrichRecoveryWorkItemPlan(RecoveryWorkItemPlan plan) {
        if (plan == null) {
            return null;
        }
        List<String> missingReasons = safeList(plan.missingReasons()).isEmpty()
                ? recoveryWorkItemMissingReasons(plan)
                : plan.missingReasons();
        String readiness = firstNonBlank(plan.readiness(),
                missingReasons.isEmpty()
                        || (missingReasons.size() == 1
                        && "NO_MISSING_RECOVERY_WORK_ITEM_REASON".equals(missingReasons.get(0)))
                        ? "RECOVERY_WORK_ITEM_READY"
                        : "RECOVERY_WORK_ITEM_INCOMPLETE");
        List<String> readyReasons = safeList(plan.readyReasons()).isEmpty()
                && "RECOVERY_WORK_ITEM_READY".equals(readiness)
                ? recoveryWorkItemReadyReasons(plan)
                : plan.readyReasons();
        List<String> normalizedMissingReasons = missingReasons.isEmpty()
                ? List.of("NO_MISSING_RECOVERY_WORK_ITEM_REASON")
                : missingReasons;
        List<String> actions = safeList(plan.actions()).isEmpty()
                ? recoveryWorkItemActions(missingReasons)
                : plan.actions();
        String executionContract = firstNonBlank(plan.executionContract(),
                recoveryWorkItemExecutionContractKey(plan, missingReasons));
        List<String> physicalEvidencePaths = safeList(plan.physicalEvidencePaths()).isEmpty()
                ? recoveryWorkItemPhysicalEvidencePaths(plan)
                : plan.physicalEvidencePaths();
        return new RecoveryWorkItemPlan(plan.workItemKind(),
                plan.recoveryAvailable(),
                plan.sourceServiceNames(),
                plan.supportedDecisions(),
                plan.suggestedActions(),
                plan.workItemSource(),
                plan.schedulerPropertyPrefix(),
                plan.schedulerEnabledByDefault(),
                plan.schedulerEnabledPropertyName(),
                plan.schedulerFixedDelayMillisPropertyName(),
                plan.defaultSchedulerFixedDelayMillis(),
                plan.manualRecoveryEndpointPath(),
                plan.backendName(),
                plan.backendAdapterName(),
                plan.backendDatasourceUrl(),
                plan.backendDriverClassName(),
                plan.sagaLogStoreName(),
                plan.sagaLogTableName(),
                plan.tccBranchStoreName(),
                plan.tccBranchTableName(),
                plan.tccReservationStoreName(),
                plan.tccReservationTableName(),
                plan.tccReservationTtlMillis(),
                plan.nativeSagaStateMachineInstanceTableName(),
                plan.nativeSagaStateInstanceTableName(),
                plan.recoveryLeaseTableName(),
                readiness,
                readyReasons,
                normalizedMissingReasons,
                actions,
                executionContract,
                physicalEvidencePaths);
    }

    private static List<String> recoveryWorkItemMissingReasons(RecoveryWorkItemPlan plan) {
        if (plan == null) {
            return List.of("plan");
        }
        List<String> missingReasons = new ArrayList<>();
        addRecoveryWorkItemReadinessReason(missingReasons, plan.recoveryAvailable(),
                "recoveryAvailable");
        addRecoveryWorkItemReadinessReason(missingReasons, !plan.sourceServiceNames().isEmpty(),
                "sourceServices");
        addRecoveryWorkItemReadinessReason(missingReasons, !plan.supportedDecisions().isEmpty(),
                "supportedDecisions");
        addRecoveryWorkItemReadinessReason(missingReasons, !isBlank(plan.manualRecoveryEndpointPath()),
                "manualRecoveryEndpoint");
        addRecoveryWorkItemReadinessReason(missingReasons,
                !isBlank(plan.schedulerPropertyPrefix())
                        && !isBlank(plan.schedulerEnabledPropertyName())
                        && !isBlank(plan.schedulerFixedDelayMillisPropertyName()),
                "schedulerConfiguration");
        addRecoveryWorkItemReadinessReason(missingReasons,
                !isBlank(plan.backendName())
                        && !isBlank(plan.backendDatasourceUrl())
                        && !isBlank(plan.backendDriverClassName()),
                "backendBinding");
        addRecoveryWorkItemReadinessReason(missingReasons, recoveryWorkItemEvidenceStoreReady(plan),
                "evidenceStore");
        return List.copyOf(missingReasons);
    }

    private static List<String> recoveryWorkItemReadyReasons(RecoveryWorkItemPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> readyReasons = new ArrayList<>();
        addRecoveryWorkItemReadyReason(readyReasons, plan.recoveryAvailable(), "recoveryAvailable");
        addRecoveryWorkItemReadyReason(readyReasons, !plan.sourceServiceNames().isEmpty(), "sourceServices");
        addRecoveryWorkItemReadyReason(readyReasons, !plan.supportedDecisions().isEmpty(),
                "supportedDecisions");
        addRecoveryWorkItemReadyReason(readyReasons, !isBlank(plan.manualRecoveryEndpointPath()),
                "manualRecoveryEndpoint");
        addRecoveryWorkItemReadyReason(readyReasons,
                !isBlank(plan.schedulerPropertyPrefix())
                        && !isBlank(plan.schedulerEnabledPropertyName())
                        && !isBlank(plan.schedulerFixedDelayMillisPropertyName()),
                "schedulerConfiguration");
        addRecoveryWorkItemReadyReason(readyReasons,
                !isBlank(plan.backendName())
                        && !isBlank(plan.backendDatasourceUrl())
                        && !isBlank(plan.backendDriverClassName()),
                "backendBinding");
        addRecoveryWorkItemReadyReason(readyReasons, recoveryWorkItemEvidenceStoreReady(plan), "evidenceStore");
        return List.copyOf(readyReasons);
    }

    private static void addRecoveryWorkItemReadinessReason(List<String> missingReasons,
                                                           boolean ready,
                                                           String reason) {
        if (!ready) {
            missingReasons.add(reason);
        }
    }

    private static void addRecoveryWorkItemReadyReason(List<String> readyReasons,
                                                       boolean ready,
                                                       String reason) {
        if (ready) {
            readyReasons.add(reason);
        }
    }

    private static List<String> recoveryWorkItemActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "plan" -> actions.add("registerRecoveryWorkItemPlan");
                case "recoveryAvailable" -> actions.add("enableRecoveryWorkItemView");
                case "sourceServices" -> actions.add("bindRecoverySourceService");
                case "supportedDecisions" -> actions.add("declareSupportedRecoveryDecisions");
                case "manualRecoveryEndpoint" -> actions.add("exposeManualRecoveryEndpoint");
                case "schedulerConfiguration" -> actions.add("configureRecoveryScheduler");
                case "backendBinding" -> actions.add("bindRecoveryBackend");
                case "evidenceStore" -> actions.add("configureRecoveryEvidenceStore");
                default -> actions.add("resolveRecoveryWorkItemReadiness");
            }
        }
        return List.copyOf(actions);
    }

    private static boolean recoveryWorkItemEvidenceStoreReady(RecoveryWorkItemPlan plan) {
        if (plan == null || isBlank(plan.workItemKind())) {
            return false;
        }
        if ("TCC".equals(plan.workItemKind())) {
            return !isBlank(plan.tccBranchStoreName())
                    && !isBlank(plan.tccBranchTableName())
                    && !isBlank(plan.tccReservationStoreName())
                    && !isBlank(plan.tccReservationTableName())
                    && !isBlank(plan.tccReservationTtlMillis());
        }
        if ("REMOTE_COMPENSATION".equals(plan.workItemKind())) {
            return !isBlank(plan.sagaLogStoreName())
                    && !isBlank(plan.sagaLogTableName());
        }
        if ("NATIVE_SAGA".equals(plan.workItemKind())) {
            return !isBlank(plan.nativeSagaStateMachineInstanceTableName())
                    && !isBlank(plan.nativeSagaStateInstanceTableName())
                    && !isBlank(plan.recoveryLeaseTableName());
        }
        return (!isBlank(plan.sagaLogStoreName()) && !isBlank(plan.sagaLogTableName()))
                || (!isBlank(plan.tccBranchStoreName()) && !isBlank(plan.tccBranchTableName()))
                || (!isBlank(plan.tccReservationStoreName()) && !isBlank(plan.tccReservationTableName()));
    }

    private static String recoveryWorkItemExecutionContractKey(RecoveryWorkItemPlan plan,
                                                               List<String> missingReasons) {
        if (plan == null) {
            return "NO_WORK_ITEM_KIND+NO_WORK_ITEM_SOURCE+NO_DECISION+RECOVERY_WORK_ITEM_INCOMPLETE";
        }
        String kind = isBlank(plan.workItemKind()) ? "NO_WORK_ITEM_KIND" : plan.workItemKind();
        String source = isBlank(plan.workItemSource()) ? "NO_WORK_ITEM_SOURCE" : plan.workItemSource();
        String decisions = safeList(plan.supportedDecisions()).stream()
                .filter(decision -> decision != null && !decision.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining("|"));
        if (decisions.isBlank()) {
            decisions = "NO_DECISION";
        }
        String readiness = safeList(missingReasons).isEmpty()
                ? "RECOVERY_WORK_ITEM_READY"
                : "RECOVERY_WORK_ITEM_INCOMPLETE";
        return kind + "+" + source + "+" + decisions + "+" + readiness;
    }

    private static List<String> recoveryWorkItemPhysicalEvidencePaths(RecoveryWorkItemPlan plan) {
        if (plan == null) {
            return List.of("NO_WORK_ITEM_KIND:NO_BACKEND:NO_EVIDENCE_STORE:NO_EVIDENCE_TABLE");
        }
        List<String> paths = new ArrayList<>();
        addRecoveryWorkItemPhysicalEvidencePath(paths, plan, "SAGA_LOG",
                plan.sagaLogStoreName(), plan.sagaLogTableName());
        addRecoveryWorkItemPhysicalEvidencePath(paths, plan, "TCC_BRANCH",
                plan.tccBranchStoreName(), plan.tccBranchTableName());
        addRecoveryWorkItemPhysicalEvidencePath(paths, plan, "TCC_RESERVATION",
                plan.tccReservationStoreName(), plan.tccReservationTableName());
        addRecoveryWorkItemPhysicalEvidencePath(paths, plan, "SEATA_STATE_MACHINE_INSTANCE",
                "SEATA_SAGA_STATE_STORE", plan.nativeSagaStateMachineInstanceTableName());
        addRecoveryWorkItemPhysicalEvidencePath(paths, plan, "SEATA_STATE_INSTANCE",
                "SEATA_SAGA_STATE_STORE", plan.nativeSagaStateInstanceTableName());
        addRecoveryWorkItemPhysicalEvidencePath(paths, plan, "SAGA_RECOVERY_LEASE",
                "SPP_SAGA_RECOVERY_LEASE_STORE", plan.recoveryLeaseTableName());
        if (paths.isEmpty()) {
            String kind = isBlank(plan.workItemKind()) ? "NO_WORK_ITEM_KIND" : plan.workItemKind();
            String backend = isBlank(plan.backendName()) ? "NO_BACKEND" : plan.backendName();
            paths.add(kind + ":" + backend + ":NO_EVIDENCE_STORE:NO_EVIDENCE_TABLE");
        }
        return List.copyOf(paths);
    }

    private static void addRecoveryWorkItemPhysicalEvidencePath(List<String> paths,
                                                                RecoveryWorkItemPlan plan,
                                                                String evidenceStore,
                                                                String storeName,
                                                                String tableName) {
        if (isBlank(storeName) && isBlank(tableName)) {
            return;
        }
        String kind = isBlank(plan.workItemKind()) ? "NO_WORK_ITEM_KIND" : plan.workItemKind();
        String backend = isBlank(plan.backendName()) ? "NO_BACKEND" : plan.backendName();
        String table = isBlank(tableName) ? "NO_EVIDENCE_TABLE" : tableName;
        paths.add(kind + ":" + backend + ":" + evidenceStore + ":" + table);
    }

    private static List<String> recoveryDeploymentReadyReasons(RecoveryDeploymentPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> readyReasons = new ArrayList<>();
        if (plan.unifiedWorkItemView()) {
            readyReasons.add("unifiedWorkItemView");
        }
        if (!plan.workItemKinds().isEmpty()) {
            readyReasons.add("workItemKinds");
        }
        if (!plan.evidenceSources().isEmpty()) {
            readyReasons.add("evidenceSources");
        }
        if (!isBlank(plan.backendName())
                && !isBlank(plan.backendDatasourceUrl())
                && !isBlank(plan.backendDriverClassName())) {
            readyReasons.add("backendBinding");
        }
        if (!plan.workItemKinds().contains("TCC") || recoveryDeploymentTccEvidenceReady(plan)) {
            readyReasons.add("tccRecoveryEvidence");
        }
        if (!plan.workItemKinds().contains("REMOTE_COMPENSATION")
                || recoveryDeploymentRemoteCompensationEvidenceReady(plan)) {
            readyReasons.add("remoteCompensationEvidence");
        }
        return List.copyOf(readyReasons);
    }

    private static List<String> recoveryDeploymentMissingReasons(RecoveryDeploymentPlan plan) {
        if (plan == null) {
            return List.of("plan");
        }
        List<String> missingReasons = new ArrayList<>();
        if (!plan.unifiedWorkItemView()) {
            missingReasons.add("unifiedWorkItemView");
        }
        if (plan.workItemKinds().isEmpty()) {
            missingReasons.add("workItemKinds");
        }
        if (plan.evidenceSources().isEmpty()) {
            missingReasons.add("evidenceSources");
        }
        if (isBlank(plan.backendName())
                || isBlank(plan.backendDatasourceUrl())
                || isBlank(plan.backendDriverClassName())) {
            missingReasons.add("backendBinding");
        }
        if (plan.workItemKinds().contains("TCC") && !recoveryDeploymentTccEvidenceReady(plan)) {
            missingReasons.add("tccRecoveryEvidence");
        }
        if (plan.workItemKinds().contains("REMOTE_COMPENSATION")
                && !recoveryDeploymentRemoteCompensationEvidenceReady(plan)) {
            missingReasons.add("remoteCompensationEvidence");
        }
        return List.copyOf(missingReasons);
    }

    private static String recoveryDeploymentUnifiedViewKey(RecoveryDeploymentPlan plan) {
        return plan != null && plan.unifiedWorkItemView()
                ? "UNIFIED_WORK_ITEM_VIEW"
                : "NO_UNIFIED_WORK_ITEM_VIEW";
    }

    private static String recoveryDeploymentContractKey(RecoveryDeploymentPlan plan, String readiness) {
        if (plan == null) {
            return "NO_RECOVERY_DEPLOYMENT_CONTRACT";
        }
        return indexKey(plan.planName())
                + ":" + listIndexKey(plan.workItemKinds(), "NO_WORK_ITEM_KIND")
                + ":" + listIndexKey(plan.evidenceSources(), "NO_EVIDENCE_SOURCE")
                + ":" + recoveryDeploymentUnifiedViewKey(plan)
                + ":" + indexKey(readiness);
    }

    private static String recoveryDeploymentRuntimeContractKey(RecoveryDeploymentPlan plan, String readiness) {
        if (plan == null) {
            return "NO_RECOVERY_DEPLOYMENT_RUNTIME_CONTRACT";
        }
        return indexKey(plan.planName())
                + "->" + indexKey(plan.backendName())
                + "+datasource=" + indexKey(plan.backendDatasourceUrl())
                + "+sagaLog=" + indexKey(plan.sagaLogTableName())
                + "+tccBranch=" + indexKey(plan.tccBranchTableName())
                + "+tccReservation=" + indexKey(plan.tccReservationTableName())
                + "+ttl=" + indexKey(plan.tccReservationTtlMillis())
                + ":" + indexKey(readiness);
    }

    private static boolean recoveryDeploymentTccEvidenceReady(RecoveryDeploymentPlan plan) {
        return plan != null
                && plan.tccRecoveryAvailable()
                && !isBlank(plan.tccBranchStoreName())
                && !isBlank(plan.tccBranchTableName())
                && !isBlank(plan.tccReservationStoreName())
                && !isBlank(plan.tccReservationTableName())
                && !isBlank(plan.tccReservationTtlMillis());
    }

    private static boolean recoveryDeploymentRemoteCompensationEvidenceReady(RecoveryDeploymentPlan plan) {
        return plan != null
                && plan.remoteCompensationRecoveryAvailable()
                && !isBlank(plan.sagaLogStoreName())
                && !isBlank(plan.sagaLogTableName());
    }

    private static List<String> recoveryDeploymentActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "plan" -> actions.add("generateRecoveryDeploymentPlan");
                case "unifiedWorkItemView" -> actions.add("enableUnifiedRecoveryWorkItemView");
                case "workItemKinds" -> actions.add("bindRecoveryWorkItemKinds");
                case "evidenceSources" -> actions.add("bindRecoveryEvidenceSources");
                case "backendBinding" -> actions.add("bindRecoveryDeploymentBackend");
                case "tccRecoveryEvidence" -> actions.add("configureTccRecoveryEvidenceStores");
                case "remoteCompensationEvidence" -> actions.add("configureRemoteCompensationRecoveryEvidenceStore");
                default -> actions.add("resolveRecoveryDeploymentBinding");
            }
        }
        return List.copyOf(actions);
    }

    private static List<String> recoveryWorkItemEvidenceStores(RecoveryWorkItemPlan plan) {
        if (plan == null) {
            return List.of("NO_EVIDENCE_STORE");
        }
        List<String> stores = new ArrayList<>();
        if (!isBlank(plan.sagaLogStoreName()) || !isBlank(plan.sagaLogTableName())) {
            stores.add("SAGA_LOG");
        }
        if (!isBlank(plan.tccBranchStoreName()) || !isBlank(plan.tccBranchTableName())) {
            stores.add("TCC_BRANCH");
        }
        if (!isBlank(plan.tccReservationStoreName()) || !isBlank(plan.tccReservationTableName())) {
            stores.add("TCC_RESERVATION");
        }
        if (!isBlank(plan.nativeSagaStateMachineInstanceTableName())) {
            stores.add("SEATA_STATE_MACHINE_INSTANCE");
        }
        if (!isBlank(plan.nativeSagaStateInstanceTableName())) {
            stores.add("SEATA_STATE_INSTANCE");
        }
        if (!isBlank(plan.recoveryLeaseTableName())) {
            stores.add("SAGA_RECOVERY_LEASE");
        }
        if (stores.isEmpty()) {
            stores.add("NO_EVIDENCE_STORE");
        }
        return List.copyOf(stores);
    }

    private static List<String> transactionSnapshotEvidenceReadyReasons(TransactionSnapshotPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> readyReasons = new ArrayList<>();
        if (plan.latestSnapshotAvailable()) {
            readyReasons.add("latestSnapshot");
        }
        if (plan.historyAvailable()) {
            readyReasons.add("history");
        }
        if (plan.archiveAvailable()) {
            readyReasons.add("archive");
        }
        if (plan.traceQueryAvailable()) {
            readyReasons.add("traceQuery");
        }
        if (plan.resourcePhaseQueryAvailable()) {
            readyReasons.add("resourcePhaseQuery");
        }
        if (plan.pruneAvailable()) {
            readyReasons.add("prune");
        }
        if (plan.retainLatestAvailable()) {
            readyReasons.add("retainLatest");
        }
        if (plan.archiveOperationAvailable()) {
            readyReasons.add("archiveOperation");
        }
        return List.copyOf(readyReasons);
    }

    private static String transactionSnapshotEvidencePath(TransactionSnapshotPlan plan) {
        if (plan == null) {
            return "NO_SERVICE->NO_RESOURCE:NO_OBJECT_TYPE:NO_BOUNDARY:NO_RECOVERY_ROLE";
        }
        String service = isBlank(plan.serviceName()) ? "NO_SERVICE" : plan.serviceName();
        String resource = isBlank(plan.transactionResourceId())
                ? "NO_RESOURCE"
                : plan.transactionResourceId();
        String objectType = isBlank(plan.objectType()) ? "NO_OBJECT_TYPE" : plan.objectType();
        String boundary = isBlank(plan.snapshotBoundary())
                ? "NO_BOUNDARY"
                : plan.snapshotBoundary();
        String recoveryRole = isBlank(plan.recoveryRole())
                ? "NO_RECOVERY_ROLE"
                : plan.recoveryRole();
        return service + "->" + resource + ":" + objectType + ":" + boundary + ":" + recoveryRole;
    }

    private static List<String> transactionSnapshotEvidenceMissingReasons(TransactionSnapshotPlan plan) {
        if (plan == null) {
            return List.of("plan");
        }
        List<String> missingReasons = new ArrayList<>();
        if (!plan.latestSnapshotAvailable()) {
            missingReasons.add("latestSnapshot");
        }
        if (!plan.historyAvailable()) {
            missingReasons.add("history");
        }
        if (!plan.archiveAvailable()) {
            missingReasons.add("archive");
        }
        if (!plan.traceQueryAvailable()) {
            missingReasons.add("traceQuery");
        }
        if (!plan.resourcePhaseQueryAvailable()) {
            missingReasons.add("resourcePhaseQuery");
        }
        if (!plan.pruneAvailable()) {
            missingReasons.add("prune");
        }
        if (!plan.retainLatestAvailable()) {
            missingReasons.add("retainLatest");
        }
        if (!plan.archiveOperationAvailable()) {
            missingReasons.add("archiveOperation");
        }
        return List.copyOf(missingReasons);
    }

    private static List<String> transactionSnapshotEvidenceActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "plan" -> actions.add("generateTransactionSnapshotEvidencePlan");
                case "latestSnapshot" -> actions.add("bindLatestTransactionSnapshotTable");
                case "history" -> actions.add("bindTransactionHistoryTable");
                case "archive" -> actions.add("bindTransactionArchiveTable");
                case "traceQuery" -> actions.add("enableTransactionTraceQuery");
                case "resourcePhaseQuery" -> actions.add("enableTransactionResourcePhaseQuery");
                case "prune" -> actions.add("enableTransactionSnapshotPruneOperation");
                case "retainLatest" -> actions.add("enableTransactionSnapshotRetainLatestOperation");
                case "archiveOperation" -> actions.add("enableTransactionSnapshotArchiveOperation");
                default -> actions.add("completeTransactionSnapshotEvidence");
            }
        }
        return List.copyOf(actions);
    }

    private static List<String> scopeHeaderReadyReasons(ScopeHeaderPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> readyReasons = new ArrayList<>();
        if (plan.localScope()) {
            readyReasons.add("localScope");
        }
        if (plan.remoteScope()) {
            readyReasons.add("remoteScope");
        }
        if (plan.physicalBindingRequiredServiceNames().isEmpty()) {
            readyReasons.add("physicalBindingNotRequired");
        } else if (plan.physicalBindingAvailableServiceNames()
                .containsAll(plan.physicalBindingRequiredServiceNames())) {
            readyReasons.add("physicalBindingAvailable");
        }
        if (plan.idempotencyRequiredServiceNames().isEmpty()) {
            readyReasons.add("idempotencyNotRequired");
        } else {
            if (plan.idempotencyAvailableServiceNames()
                    .containsAll(plan.idempotencyRequiredServiceNames())) {
                readyReasons.add("idempotencyKeyAvailable");
            }
            if (plan.idempotencyRuntimeStoreAvailableServiceNames()
                    .containsAll(plan.idempotencyRequiredServiceNames())) {
                readyReasons.add("idempotencyRuntimeStoreAvailable");
            }
        }
        return List.copyOf(readyReasons);
    }

    private static List<String> scopeHeaderActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "plan" -> actions.add("registerScopeHeaderPlan");
                case "physicalBinding" -> actions.add("bindServiceToPhysicalBackend");
                case "idempotencyKey" -> actions.add("declareUniqueIdempotencyHeaderField");
                case "idempotencyRuntimeStore" -> actions.add("bindIdempotencyRuntimeStore");
                default -> actions.add("resolveScopeHeaderBinding");
            }
        }
        return List.copyOf(actions);
    }

    public static ScopeHeaderPlan enrichScopeHeaderPlan(ScopeHeaderPlan plan) {
        if (plan == null) {
            return null;
        }
        List<String> computedMissingReasons = scopeHeaderMissingReasons(plan);
        String readiness = firstNonBlank(plan.readiness(),
                computedMissingReasons.isEmpty() ? "SCOPE_HEADER_READY" : "SCOPE_HEADER_INCOMPLETE");
        boolean ready = "SCOPE_HEADER_READY".equals(readiness);
        List<String> missingReasons = safeList(plan.missingReasons()).isEmpty()
                ? (ready ? List.of("NO_MISSING_SCOPE_HEADER_REASON") : computedMissingReasons)
                : safeList(plan.missingReasons());
        List<String> readyReasons = safeList(plan.readyReasons()).isEmpty() && ready
                ? scopeHeaderReadyReasons(plan)
                : safeList(plan.readyReasons());
        List<String> actions = safeList(plan.actions()).isEmpty()
                ? scopeHeaderActions(ready ? List.of() : missingReasons)
                : safeList(plan.actions());
        String scopeKind = firstNonBlank(plan.scopeKind(), scopeHeaderKind(plan));
        String boundary = firstNonBlank(plan.boundary(), scopeHeaderBoundary(plan));
        String physicalBindingReadiness = firstNonBlank(plan.physicalBindingReadiness(),
                scopeHeaderPhysicalBindingReadiness(plan));
        String idempotencyReadiness = firstNonBlank(plan.idempotencyReadiness(),
                scopeHeaderIdempotencyReadiness(plan));
        return new ScopeHeaderPlan(plan.scopeName(),
                plan.localScope(),
                plan.remoteScope(),
                plan.inputHeaderName(),
                plan.outputHeaderName(),
                plan.serviceNames(),
                plan.transactionModes(),
                plan.transactionServiceNames(),
                plan.physicalBindingRequiredServiceNames(),
                plan.physicalBindingAvailableServiceNames(),
                plan.physicalBackendNames(),
                plan.physicalBackendAdapterNames(),
                plan.physicalBackendDatasourceUrls(),
                plan.physicalBackendDriverClassNames(),
                plan.physicalBindingBindings(),
                plan.idempotencyRequiredServiceNames(),
                plan.idempotencyAvailableServiceNames(),
                plan.idempotencyRuntimeStoreAvailableServiceNames(),
                plan.idempotencyBackendNames(),
                plan.idempotencyTableNames(),
                plan.idempotencyDatasourceUrls(),
                plan.idempotencyDriverClassNames(),
                plan.idempotencyBindings(),
                readiness,
                readyReasons,
                missingReasons,
                actions,
                scopeKind,
                boundary,
                physicalBindingReadiness,
                idempotencyReadiness);
    }

    private static String scopeHeaderPhysicalBindingReadiness(ScopeHeaderPlan plan) {
        if (plan == null || plan.physicalBindingRequiredServiceNames().isEmpty()) {
            return "SCOPE_PHYSICAL_NOT_REQUIRED";
        }
        return scopeHeaderPhysicalBindingReady(plan) ? "SCOPE_PHYSICAL_READY" : "SCOPE_PHYSICAL_BLOCKED";
    }

    private static boolean scopeHeaderPhysicalBindingReady(ScopeHeaderPlan plan) {
        return plan != null
                && plan.physicalBindingAvailableServiceNames().containsAll(plan.physicalBindingRequiredServiceNames())
                && !plan.physicalBackendNames().isEmpty()
                && !plan.physicalBackendAdapterNames().isEmpty()
                && !plan.physicalBackendDatasourceUrls().isEmpty()
                && !plan.physicalBackendDriverClassNames().isEmpty()
                && !plan.physicalBindingBindings().isEmpty();
    }

    private static String scopeHeaderIdempotencyReadiness(ScopeHeaderPlan plan) {
        if (plan == null || plan.idempotencyRequiredServiceNames().isEmpty()) {
            return "SCOPE_IDEMPOTENCY_NOT_REQUIRED";
        }
        return scopeHeaderIdempotencyReady(plan)
                ? "SCOPE_IDEMPOTENCY_READY"
                : "SCOPE_IDEMPOTENCY_BLOCKED";
    }

    private static boolean scopeHeaderIdempotencyReady(ScopeHeaderPlan plan) {
        return plan != null
                && plan.idempotencyAvailableServiceNames().containsAll(plan.idempotencyRequiredServiceNames())
                && plan.idempotencyRuntimeStoreAvailableServiceNames()
                        .containsAll(plan.idempotencyRequiredServiceNames())
                && !plan.idempotencyBackendNames().isEmpty()
                && !plan.idempotencyTableNames().isEmpty()
                && !plan.idempotencyDatasourceUrls().isEmpty()
                && !plan.idempotencyDriverClassNames().isEmpty()
                && !plan.idempotencyBindings().isEmpty();
    }

    private static String scopeHeaderKind(ScopeHeaderPlan plan) {
        if (plan == null) {
            return "UNCLASSIFIED_SCOPE";
        }
        if (plan.localScope()) {
            return "LOCAL";
        }
        if (plan.remoteScope()) {
            return "REMOTE";
        }
        return "UNCLASSIFIED_SCOPE";
    }

    private static String scopeHeaderBoundary(ScopeHeaderPlan plan) {
        if (plan == null) {
            return "SCOPE_BOUNDARY_UNCLASSIFIED";
        }
        if (plan.localScope() && !plan.transactionServiceNames().isEmpty()) {
            return "LOCAL_TRANSACTION_BOUNDARY";
        }
        if (plan.localScope()) {
            return "LOCAL_QUERY_BOUNDARY";
        }
        if (plan.remoteScope()
                && plan.physicalBindingRequiredServiceNames().isEmpty()
                && plan.physicalBackendNames().isEmpty()
                && plan.idempotencyRequiredServiceNames().isEmpty()
                && plan.idempotencyBackendNames().isEmpty()) {
            return "REMOTE_REFERENCE_BOUNDARY";
        }
        if (plan.remoteScope()) {
            return "REMOTE_BOUNDARY_WITH_LOCAL_RUNTIME";
        }
        return "SCOPE_BOUNDARY_UNCLASSIFIED";
    }

    private static boolean tccReservationTargetAppDdlPlatformColumn(TccReservationPlan plan) {
        return plan != null
                && plan.targetPlatformColumn()
                && !plan.targetObjectBackedColumn()
                && !plan.targetPrimaryKeyColumn()
                && plan.targetAppDdlColumnPushdown();
    }

    private static boolean tccReservationQuantumToAppDdlPlatformColumnClosed(TccReservationPlan plan) {
        return plan != null
                && plan.sourceQuantumAttribute()
                && !isBlank(plan.sourceQuantumStoreName())
                && tccReservationTargetAppDdlPlatformColumn(plan)
                && plan.targetRepositoryAppDdlTables();
    }

    private static String tccReservationTargetColumnKind(TccReservationPlan plan) {
        if (plan == null) {
            return "NO_TARGET_COLUMN";
        }
        if (plan.targetPrimaryKeyColumn()) {
            return "TARGET_PRIMARY_KEY_COLUMN";
        }
        if (plan.targetObjectBackedColumn()) {
            return "TARGET_OBJECT_BACKED_COLUMN";
        }
        if (plan.targetPlatformColumn()) {
            return "TARGET_PLATFORM_COLUMN";
        }
        return "NO_TARGET_PLATFORM_COLUMN";
    }

    private static String tccReservationSourceImplementationStrategy(TccReservationPlan plan) {
        if (plan == null || plan.sourceQuantumStoreConfiguration() == null) {
            return "NO_IMPLEMENTATION_STRATEGY";
        }
        return indexKey(plan.sourceQuantumStoreConfiguration().get("implementationStrategy"));
    }

    private static String tccReservationBoundaryKey(TccReservationPlan plan) {
        if (plan == null) {
            return "NO_RESERVATION_BOUNDARY";
        }
        return indexKey(plan.sourceAttributeId()) + "->" + indexKey(plan.reservationAttributeId());
    }

    private static String tccReservationPhysicalPath(TccReservationPlan plan) {
        if (plan == null) {
            return "NO_SERVICE:NO_SOURCE_QUANTUM_STORE->NO_TARGET_REPOSITORY:NO_TARGET_TABLE.NO_TARGET_COLUMN";
        }
        String service = isBlank(plan.serviceName()) ? "NO_SERVICE" : plan.serviceName();
        String sourceStore = isBlank(plan.sourceQuantumStoreName())
                ? "NO_SOURCE_QUANTUM_STORE"
                : plan.sourceQuantumStoreName();
        String targetRepository = firstNonBlank(plan.targetResolvedRepositoryName(), plan.targetRepositoryName());
        if (isBlank(targetRepository)) {
            targetRepository = "NO_TARGET_REPOSITORY";
        }
        String targetTable = firstNonBlank(plan.targetTableName(), plan.targetRepositoryStorageTableName());
        if (isBlank(targetTable)) {
            targetTable = "NO_TARGET_TABLE";
        }
        String targetColumn = isBlank(plan.targetColumnName()) ? "NO_TARGET_COLUMN" : plan.targetColumnName();
        return service + ":" + sourceStore + "->" + targetRepository + ":" + targetTable + "." + targetColumn;
    }

    private static String tccReservationContractKey(TccReservationPlan plan, List<String> missingReasons) {
        if (plan == null) {
            return "NO_SERVICE:NO_RESOURCE:NO_IMPLEMENTATION_STRATEGY:"
                    + "NO_SOURCE_QUANTUM_STORE->NO_TARGET_REPOSITORY:NO_TARGET_TABLE.NO_TARGET_COLUMN:"
                    + "TCC_RESERVATION_INCOMPLETE";
        }
        String service = isBlank(plan.serviceName()) ? "NO_SERVICE" : plan.serviceName();
        String resource = isBlank(plan.resourceId()) ? "NO_RESOURCE" : plan.resourceId();
        String readiness = missingReasons == null || missingReasons.isEmpty()
                ? "TCC_RESERVATION_READY"
                : "TCC_RESERVATION_INCOMPLETE";
        String sourceStore = isBlank(plan.sourceQuantumStoreName())
                ? "NO_SOURCE_QUANTUM_STORE"
                : plan.sourceQuantumStoreName();
        String targetRepository = firstNonBlank(plan.targetResolvedRepositoryName(), plan.targetRepositoryName());
        if (isBlank(targetRepository)) {
            targetRepository = "NO_TARGET_REPOSITORY";
        }
        String targetTable = firstNonBlank(plan.targetTableName(), plan.targetRepositoryStorageTableName());
        if (isBlank(targetTable)) {
            targetTable = "NO_TARGET_TABLE";
        }
        String targetColumn = isBlank(plan.targetColumnName()) ? "NO_TARGET_COLUMN" : plan.targetColumnName();
        return indexKey(service) + ":" + indexKey(resource) + ":"
                + tccReservationSourceImplementationStrategy(plan)
                + ":" + indexKey(sourceStore) + "->" + indexKey(targetRepository)
                + ":" + indexKey(targetTable) + "." + indexKey(targetColumn)
                + ":" + readiness;
    }

    private static List<String> sagaTransactionCallRecoveryClosureReadyReasons(SagaTransactionCallPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> readyReasons = new ArrayList<>();
        if (plan.targetRegistered()) {
            readyReasons.add("registeredTarget");
        }
        if (plan.localAtomicUpdateTransaction()) {
            readyReasons.add("localAtomicUpdateTarget");
        }
        if (plan.sourceScenarioTransaction()) {
            readyReasons.add("sourceDeclaredLocalTransactionTarget");
            if (plan.implementationStrategies().contains("DECLARED_BUSINESS_COMPENSATION")) {
                readyReasons.add("declaredBusinessCompensation");
            }
        }
        if (!plan.objectSnapshotRequired() && plan.quantumBoundaryRecoveryEvidence()) {
            readyReasons.add("committedQuantumBoundary");
            readyReasons.add("adapterQuantumReversal");
        } else if (plan.objectSnapshotRequired()) {
            if (plan.argumentSnapshotRuleCount() > 0) {
                readyReasons.add("argumentSnapshot");
            }
            if (plan.argumentRecoveryEvidence()) {
                readyReasons.add("argumentRecoveryEvidence");
            }
            if (plan.rollbackRecoveryEvidence()) {
                readyReasons.add("rollbackRecoveryEvidence");
            }
        }
        return List.copyOf(readyReasons);
    }

    private static List<String> sagaTransactionCallRecoveryClosureActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "registeredTarget" -> actions.add("registerSagaTargetService");
                case "localAtomicUpdateTarget" -> actions.add("makeTargetLocalAtomicUpdate");
                case "declaredBusinessCompensation" -> actions.add("bindLocalBusinessCompensation");
                case "argumentSnapshot" -> actions.add("declareSnapshotArgumentRecovery");
                case "argumentRecoveryEvidence" -> actions.add("persistArgumentRecoveryEvidence");
                case "rollbackRecoveryEvidence" -> actions.add("persistRollbackRecoveryEvidence");
                case "currentSnapshotCannotReplaceArgumentRecovery" ->
                        actions.add("declareSnapshotArgumentRecovery");
                default -> actions.add("reviewSagaTransactionRecoveryClosure");
            }
        }
        return List.copyOf(actions);
    }

    public static SagaTransactionCallPlan enrichSagaTransactionCallPlan(SagaTransactionCallPlan plan) {
        if (plan == null) {
            return null;
        }
        List<String> computedMissingReasons = sagaTransactionCallRecoveryClosureMissingReasons(plan);
        String readiness = !isBlank(plan.recoveryReadiness())
                ? plan.recoveryReadiness()
                : (computedMissingReasons.isEmpty() ? "RECOVERY_CLOSED" : "RECOVERY_OPEN");
        boolean closed = "RECOVERY_CLOSED".equals(readiness);
        List<String> missingReasons = safeList(plan.recoveryMissingReasons()).isEmpty()
                ? (closed ? List.of("NO_MISSING_RECOVERY_REASON") : computedMissingReasons)
                : safeList(plan.recoveryMissingReasons());
        List<String> readyReasons = safeList(plan.recoveryReadyReasons()).isEmpty() && closed
                ? sagaTransactionCallRecoveryClosureReadyReasons(plan)
                : safeList(plan.recoveryReadyReasons());
        List<String> actions = safeList(plan.recoveryActions()).isEmpty()
                ? sagaTransactionCallRecoveryClosureActions(closed ? List.of() : missingReasons)
                : safeList(plan.recoveryActions());
        String callPath = !isBlank(plan.callPath()) ? plan.callPath() : sagaTransactionCallKey(plan);
        String recoveryContract = !isBlank(plan.recoveryContract())
                ? plan.recoveryContract()
                : sagaTransactionRecoveryContractKey(plan, closed ? List.of() : missingReasons);
        return new SagaTransactionCallPlan(plan.sagaServiceName(),
                plan.targetServiceName(),
                plan.targetRegistered(),
                plan.targetLocal(),
                plan.targetRemote(),
                plan.targetServiceKind(),
                plan.targetTransactionMode(),
                plan.localAtomicUpdateTransaction(),
                plan.argumentSnapshotRuleCount(),
                plan.currentSnapshotRuleCount(),
                plan.argumentSnapshotSavePhases(),
                plan.rollbackRecoverySavePhases(),
                plan.argumentRecoveryEvidencePlans(),
                plan.rollbackRecoveryEvidencePlans(),
                plan.argumentRecoveryEvidence(),
                plan.rollbackRecoveryEvidence(),
                plan.quantumBoundaryRecoveryEvidence(),
                plan.quantumBoundaryEvidencePersistence(),
                plan.quantumReversalExecution(),
                plan.implementationStrategies(),
                plan.recoveryBoundary(),
                plan.recoveryOwner(),
                plan.objectSnapshotRequired(),
                readiness,
                readyReasons,
                missingReasons,
                actions,
                callPath,
                recoveryContract);
    }

    private static String sagaTransactionCallKey(SagaTransactionCallPlan plan) {
        if (plan == null) {
            return "UNASSIGNED->UNASSIGNED";
        }
        return indexKey(plan.sagaServiceName()) + "->" + indexKey(plan.targetServiceName());
    }

    private static String sagaTransactionRecoveryContractKey(SagaTransactionCallPlan plan,
                                                             List<String> missingReasons) {
        if (plan == null) {
            return "NO_TRANSACTION_MODE+NO_ARGUMENT_EVIDENCE+NO_ROLLBACK_EVIDENCE+RECOVERY_OPEN";
        }
        String transactionMode = plan.targetTransactionMode() == null
                ? "NO_TRANSACTION_MODE"
                : plan.targetTransactionMode().name();
        if (plan.sourceScenarioTransaction()) {
            return transactionMode + "+SPP_DECLARED_LOCAL_TRANSACTIONS+"
                    + (plan.implementationStrategies().contains("DECLARED_BUSINESS_COMPENSATION")
                            ? "DECLARED_BUSINESS_COMPENSATION" : "NO_BUSINESS_COMPENSATION")
                    + "+" + (safeList(missingReasons).isEmpty() ? "RECOVERY_CLOSED" : "RECOVERY_OPEN");
        }
        String argumentEvidence = plan.quantumBoundaryRecoveryEvidence()
                ? "QUANTUM_BOUNDARY_EVIDENCE"
                : plan.argumentRecoveryEvidence()
                        ? "ARGUMENT_EVIDENCE"
                        : "NO_ARGUMENT_EVIDENCE";
        String rollbackEvidence = plan.quantumBoundaryRecoveryEvidence()
                ? indexKey(plan.quantumReversalExecution())
                : plan.rollbackRecoveryEvidence()
                        ? "ROLLBACK_EVIDENCE"
                        : "NO_ROLLBACK_EVIDENCE";
        String readiness = safeList(missingReasons).isEmpty() ? "RECOVERY_CLOSED" : "RECOVERY_OPEN";
        return transactionMode + "+" + argumentEvidence + "+" + rollbackEvidence + "+" + readiness;
    }

    private static void addSagaRecoveryEvidenceObjectTypes(Map<String, List<SagaTransactionCallPlan>> index,
                                                           SagaTransactionCallPlan callPlan) {
        boolean added = false;
        LinkedHashSet<String> objectTypes = new LinkedHashSet<>();
        if (callPlan != null) {
            for (SagaRecoveryEvidencePlan evidencePlan : safeList(callPlan.argumentRecoveryEvidencePlans())) {
                if (evidencePlan != null && !isBlank(evidencePlan.objectType())) {
                    objectTypes.add(evidencePlan.objectType());
                }
            }
            for (SagaRecoveryEvidencePlan evidencePlan : safeList(callPlan.rollbackRecoveryEvidencePlans())) {
                if (evidencePlan != null && !isBlank(evidencePlan.objectType())) {
                    objectTypes.add(evidencePlan.objectType());
                }
            }
        }
        for (String objectType : objectTypes) {
            addToIndex(index, indexKey(objectType), callPlan);
            added = true;
        }
        if (!added) {
            addToIndex(index, "NO_EVIDENCE_OBJECT_TYPE", callPlan);
        }
    }

    private static String suppressedGeneratedAppSemanticMigrationReason(AppSemanticMigrationPlan plan,
                                                                        java.util.Set<String>
                                                                                externalRemoteServiceNames,
                                                                        java.util.Set<String>
                                                                                transactionSnapshotServiceNames,
                                                                        java.util.Set<String>
                                                                                closedCurrentContextKeys) {
        if (plan == null) {
            return "";
        }
        if ("REMOTE_REFERENCE_ADAPTER".equals(plan.migrationType())
                && externalRemoteServiceNames.contains(plan.serviceName())) {
            return "REMOTE_EXTERNAL_ADAPTER";
        }
        if ("CURRENT_SNAPSHOT_CONTEXT".equals(plan.migrationType())
                && closedCurrentContextKeys.contains(currentSnapshotContextKey(plan))) {
            return "CURRENT_CONTEXT_APP_DDL_CLOSURE";
        }
        if ("SNAPSHOT_ESCAPE_HATCH".equals(plan.migrationType())
                && "TransactionSnapshot".equals(plan.objectType())
                && transactionSnapshotServiceNames.contains(plan.serviceName())) {
            return "TRANSACTION_SNAPSHOT_PLAN";
        }
        if ("CONTRACT_SIGNER_FIELD".equals(plan.migrationType())) {
            return "LEGACY_CONTRACT_FIELD";
        }
        return "";
    }

    private List<AppSemanticMigrationPlan> activeGeneratedAppSemanticMigrationPlans() {
        Map<String, List<AppSemanticMigrationPlan>> suppressedPlansByReason =
                generatedAppSemanticMigrationPlansBySuppressedReason();
        java.util.Set<AppSemanticMigrationPlan> suppressedPlans = new java.util.LinkedHashSet<>();
        for (List<AppSemanticMigrationPlan> plans : suppressedPlansByReason.values()) {
            suppressedPlans.addAll(plans);
        }
        List<AppSemanticMigrationPlan> activePlans = new java.util.ArrayList<>();
        for (AppSemanticMigrationPlan plan : safeList(generatedAppSemanticMigrationPlans)) {
            if (!suppressedPlans.contains(plan)) {
                activePlans.add(plan);
            }
        }
        return List.copyOf(activePlans);
    }

    private static void addRemainingAppSemanticMigrationSources(
            Map<String, AppSemanticMigrationPlan> plansByKey,
            Map<String, java.util.Set<String>> sourcesByKey,
            List<AppSemanticMigrationPlan> plans,
            String source) {
        for (AppSemanticMigrationPlan plan : safeList(plans)) {
            String key = remainingAppSemanticMigrationKey(plan);
            if (key.isBlank()) {
                continue;
            }
            plansByKey.putIfAbsent(key, plan);
            sourcesByKey.computeIfAbsent(key, ignored -> new java.util.LinkedHashSet<>()).add(source);
        }
    }

    private static String remainingAppSemanticMigrationSourceClass(java.util.Set<String> sources) {
        boolean runtime = sources != null && sources.contains("runtime");
        boolean activeGenerated = sources != null && sources.contains("activeGenerated");
        if (runtime && activeGenerated) {
            return "runtimeAndActiveGenerated";
        }
        if (runtime) {
            return "runtimeOnly";
        }
        if (activeGenerated) {
            return "activeGeneratedOnly";
        }
        return "unknown";
    }

    private static boolean remainingAppSemanticMigrationAction(AppSemanticMigrationPlan plan) {
        return remainingAppSemanticMigrationAction(plan, List.of());
    }

    private static Map<String, List<AppSemanticMigrationPlan>> appSemanticMigrationPlansBySingleKey(
            List<AppSemanticMigrationPlan> plans, Function<AppSemanticMigrationPlan, String> keyMapper) {
        Map<String, List<AppSemanticMigrationPlan>> plansByKey = new java.util.LinkedHashMap<>();
        for (AppSemanticMigrationPlan plan : safeList(plans)) {
            plansByKey.computeIfAbsent(indexKey(keyMapper.apply(plan)), ignored -> new java.util.ArrayList<>())
                    .add(plan);
        }
        return immutableListMap(plansByKey);
    }

    private static Map<String, List<AppSemanticMigrationPlan>> appSemanticMigrationPlansByParticipantCategory(
            List<AppSemanticMigrationPlan> plans) {
        Map<String, List<AppSemanticMigrationPlan>> plansByKey = new java.util.LinkedHashMap<>();
        for (AppSemanticMigrationPlan plan : safeList(plans)) {
            List<String> participantCategories = plan.participantCategories().isEmpty()
                    ? List.of("NO_PARTICIPANT_CATEGORY")
                    : plan.participantCategories();
            for (String participantCategory : participantCategories) {
                plansByKey.computeIfAbsent(indexKey(participantCategory), ignored -> new java.util.ArrayList<>())
                        .add(plan);
            }
        }
        return immutableListMap(plansByKey);
    }

    private static Map<String, List<AppSemanticMigrationPlan>> appSemanticMigrationPlansByRoleOnlyReadiness(
            List<AppSemanticMigrationPlan> plans,
            List<ServiceParticipantRoleOnlyMigrationActionPlan> roleOnlyMigrationActionPlans) {
        Map<String, List<AppSemanticMigrationPlan>> plansByKey = new java.util.LinkedHashMap<>();
        for (AppSemanticMigrationPlan plan : safeList(plans)) {
            plansByKey.computeIfAbsent(
                            appSemanticMigrationRoleOnlyReadiness(plan, roleOnlyMigrationActionPlans),
                            ignored -> new java.util.ArrayList<>())
                    .add(plan);
        }
        return immutableListMap(plansByKey);
    }

    private static String appSemanticMigrationRoleOnlyReadiness(
            AppSemanticMigrationPlan plan,
            List<ServiceParticipantRoleOnlyMigrationActionPlan> roleOnlyMigrationActionPlans) {
        if (plan == null || !"SERVICE_NON_ROLE_PARTICIPANT".equals(plan.migrationType())) {
            return "NO_ROLE_ONLY_BOUNDARY";
        }
        List<ServiceParticipantRoleOnlyMigrationActionPlan> matchingActionPlans =
                safeList(roleOnlyMigrationActionPlans).stream()
                        .filter(actionPlan -> actionPlan != null
                                && java.util.Objects.equals(plan.serviceName(), actionPlan.serviceName()))
                        .filter(actionPlan -> plan.paths().isEmpty()
                                || plan.paths().contains(actionPlan.path()))
                        .toList();
        if (!matchingActionPlans.isEmpty()
                && matchingActionPlans.stream().allMatch(ServiceParticipantRoleOnlyMigrationActionPlan::roleOnlyReady)) {
            return "ROLE_ONLY_READY";
        }
        return "ROLE_ONLY_BLOCKED";
    }

    private static Map<String, List<AppSemanticMigrationPlan>>
            appSemanticMigrationPlansByPlatformPrimaryKeyIdentityKind(
            List<AppSemanticMigrationPlan> plans, List<RoleParticipantPlan> roleParticipantPlans) {
        Map<String, List<AppSemanticMigrationPlan>> plansByKey = new java.util.LinkedHashMap<>();
        for (AppSemanticMigrationPlan plan : safeList(plans)) {
            if (!"APP_DDL_PLATFORM_PRIMARY_KEY".equals(plan.migrationType())) {
                continue;
            }
            plansByKey.computeIfAbsent(
                            indexKey(appDdlPlatformPrimaryKeyIdentityKind(plan, roleParticipantPlans)),
                            ignored -> new java.util.ArrayList<>())
                    .add(plan);
        }
        return immutableListMap(plansByKey);
    }

    private static Map<String, List<AppSemanticMigrationPlan>> appSemanticMigrationPlansByRemoteImplementationKind(
            List<AppSemanticMigrationPlan> plans) {
        Map<String, List<AppSemanticMigrationPlan>> plansByKey = new java.util.LinkedHashMap<>();
        for (AppSemanticMigrationPlan plan : safeList(plans)) {
            if (!"REMOTE_SERVICE".equals(plan.targetKind())) {
                continue;
            }
            String key;
            if (plan.referenceImplementation()) {
                key = "REFERENCE_IMPLEMENTATION";
            } else if (plan.externalImplementation()) {
                key = "EXTERNAL_IMPLEMENTATION";
            } else {
                key = "UNKNOWN_REMOTE_IMPLEMENTATION";
            }
            plansByKey.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(plan);
        }
        return immutableListMap(plansByKey);
    }

    private static Map<String, List<AppSemanticMigrationPlan>> appSemanticMigrationPlansByRemoteExternalReadiness(
            List<AppSemanticMigrationPlan> plans) {
        Map<String, List<AppSemanticMigrationPlan>> plansByKey = new java.util.LinkedHashMap<>();
        for (AppSemanticMigrationPlan plan : safeList(plans)) {
            if (!"REMOTE_SERVICE".equals(plan.targetKind())) {
                continue;
            }
            String key = appSemanticMigrationRemoteExternalReadiness(plan);
            plansByKey.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(plan);
        }
        return immutableListMap(plansByKey);
    }

    private static Map<String, List<AppSemanticMigrationPlan>> appSemanticMigrationPlansByMigrationContract(
            List<AppSemanticMigrationPlan> plans,
            List<ServiceParticipantRoleOnlyMigrationActionPlan> roleOnlyMigrationActionPlans) {
        Map<String, List<AppSemanticMigrationPlan>> plansByKey = new java.util.LinkedHashMap<>();
        for (AppSemanticMigrationPlan plan : safeList(plans)) {
            plansByKey.computeIfAbsent(
                            appSemanticMigrationContractKey(plan,
                                    appSemanticMigrationRoleOnlyReadiness(plan, roleOnlyMigrationActionPlans),
                                    appSemanticMigrationRemoteExternalReadiness(plan)),
                            ignored -> new java.util.ArrayList<>())
                    .add(plan);
        }
        return immutableListMap(plansByKey);
    }

    private static String appSemanticMigrationRemoteExternalReadiness(AppSemanticMigrationPlan plan) {
        if (plan == null || !"REMOTE_SERVICE".equals(plan.targetKind())) {
            return "NO_REMOTE_EXTERNAL_BOUNDARY";
        }
        if (plan.externalImplementation()) {
            return "REMOTE_EXTERNAL_ADAPTER_READY";
        }
        if (plan.referenceImplementation()) {
            return "REMOTE_REFERENCE_ADAPTER_MISSING_EXTERNAL_ADAPTER";
        }
        return "REMOTE_EXTERNAL_ADAPTER_UNKNOWN";
    }

    private static String appSemanticMigrationContractKey(AppSemanticMigrationPlan plan,
                                                         String roleOnlyReadiness,
                                                         String remoteExternalReadiness) {
        if (plan == null) {
            return "NO_MIGRATION:NO_TARGET->NO_ACTION+NO_ROLE_ONLY_BOUNDARY+NO_REMOTE_EXTERNAL_BOUNDARY+NO_PATH";
        }
        String target = firstNonBlank(plan.serviceName(), firstNonBlank(plan.targetName(),
                firstNonBlank(plan.tableName(), firstNonBlank(plan.objectType(), "NO_TARGET"))));
        String paths = plan.paths().isEmpty() ? "NO_PATH" : String.join("+", plan.paths());
        return indexKey(plan.migrationType())
                + ":" + indexKey(plan.targetKind())
                + ":" + indexKey(target)
                + "->" + indexKey(firstNonBlank(plan.migrationAction(), "NO_ACTION"))
                + "+" + indexKey(roleOnlyReadiness)
                + "+" + indexKey(remoteExternalReadiness)
                + "+" + indexKey(paths);
    }

    private static List<AppSemanticMigrationSummaryPlan> appSemanticMigrationSummaryPlans(
            List<AppSemanticMigrationPlan> plans) {
        Map<String, AppSemanticMigrationSummaryPlanBuilder> builders = new java.util.LinkedHashMap<>();
        for (AppSemanticMigrationPlan plan : safeList(plans)) {
            builders.computeIfAbsent(plan.migrationType(), AppSemanticMigrationSummaryPlanBuilder::new)
                    .add(plan);
        }
        return builders.values().stream()
                .map(AppSemanticMigrationSummaryPlanBuilder::build)
                .toList();
    }

    private static Map<String, AppSemanticMigrationSummaryPlan> appSemanticMigrationSummaryPlansByType(
            List<AppSemanticMigrationSummaryPlan> plans) {
        Map<String, AppSemanticMigrationSummaryPlan> byType = new java.util.LinkedHashMap<>();
        for (AppSemanticMigrationSummaryPlan plan : safeList(plans)) {
            byType.put(indexKey(plan.migrationType()), plan);
        }
        return Map.copyOf(byType);
    }

    private static Map<String, List<AppSemanticMigrationSummaryPlan>>
            appSemanticMigrationSummaryPlansByParticipantCategory(
            List<AppSemanticMigrationSummaryPlan> plans) {
        Map<String, List<AppSemanticMigrationSummaryPlan>> byParticipantCategory = new java.util.LinkedHashMap<>();
        for (AppSemanticMigrationSummaryPlan plan : safeList(plans)) {
            List<String> participantCategories = plan.participantCategories().isEmpty()
                    ? List.of("NO_PARTICIPANT_CATEGORY")
                    : plan.participantCategories();
            for (String participantCategory : participantCategories) {
                byParticipantCategory
                        .computeIfAbsent(indexKey(participantCategory), ignored -> new java.util.ArrayList<>())
                        .add(plan);
            }
        }
        return immutableListMap(byParticipantCategory);
    }

    private static Map<String, List<AppSemanticMigrationSummaryPlan>>
            appSemanticMigrationSummaryPlansByIdentityKind(List<AppSemanticMigrationSummaryPlan> plans) {
        Map<String, List<AppSemanticMigrationSummaryPlan>> byIdentityKind = new java.util.LinkedHashMap<>();
        for (AppSemanticMigrationSummaryPlan plan : safeList(plans)) {
            List<String> identityKinds = plan.identityKinds().isEmpty()
                    ? List.of("NO_PLATFORM_IDENTITY")
                    : plan.identityKinds();
            for (String identityKind : identityKinds) {
                byIdentityKind.computeIfAbsent(indexKey(identityKind), ignored -> new java.util.ArrayList<>())
                        .add(plan);
            }
        }
        return immutableListMap(byIdentityKind);
    }

    private Map<String, AppSemanticMigrationPlan> remainingAppSemanticMigrationPlansByKey() {
        Map<String, AppSemanticMigrationPlan> plansByKey = new java.util.LinkedHashMap<>();
        Map<String, java.util.Set<String>> sourcesByKey = new java.util.LinkedHashMap<>();
        java.util.Set<String> roleOnlyReadyServiceParticipantPaths =
                roleOnlyReadyServiceParticipantPaths(serviceParticipantRoleOnlyMigrationActionPlans);
        addRemainingAppSemanticMigrationSources(plansByKey, sourcesByKey, safeList(appSemanticMigrationPlans)
                        .stream()
                        .filter(plan -> remainingAppSemanticMigrationAction(plan, roleParticipantPlans,
                                roleOnlyReadyServiceParticipantPaths))
                        .toList(),
                "runtime");
        addRemainingAppSemanticMigrationSources(plansByKey, sourcesByKey, activeGeneratedAppSemanticMigrationPlans()
                        .stream()
                        .filter(plan -> remainingAppSemanticMigrationAction(plan, roleParticipantPlans,
                                roleOnlyReadyServiceParticipantPaths))
                        .toList(),
                "activeGenerated");
        return plansByKey;
    }

    private Map<String, java.util.Set<String>> remainingAppSemanticMigrationSourcesByKey() {
        Map<String, AppSemanticMigrationPlan> plansByKey = new java.util.LinkedHashMap<>();
        Map<String, java.util.Set<String>> sourcesByKey = new java.util.LinkedHashMap<>();
        java.util.Set<String> roleOnlyReadyServiceParticipantPaths =
                roleOnlyReadyServiceParticipantPaths(serviceParticipantRoleOnlyMigrationActionPlans);
        addRemainingAppSemanticMigrationSources(plansByKey, sourcesByKey, safeList(appSemanticMigrationPlans)
                        .stream()
                        .filter(plan -> remainingAppSemanticMigrationAction(plan, roleParticipantPlans,
                                roleOnlyReadyServiceParticipantPaths))
                        .toList(),
                "runtime");
        addRemainingAppSemanticMigrationSources(plansByKey, sourcesByKey, activeGeneratedAppSemanticMigrationPlans()
                        .stream()
                        .filter(plan -> remainingAppSemanticMigrationAction(plan, roleParticipantPlans,
                                roleOnlyReadyServiceParticipantPaths))
                        .toList(),
                "activeGenerated");
        return sourcesByKey;
    }

    private static boolean remainingAppSemanticMigrationAction(AppSemanticMigrationPlan plan,
                                                              List<RoleParticipantPlan> roleParticipantPlans) {
        return remainingAppSemanticMigrationAction(plan, roleParticipantPlans, java.util.Set.of());
    }

    private static boolean remainingAppSemanticMigrationAction(AppSemanticMigrationPlan plan,
                                                              List<RoleParticipantPlan> roleParticipantPlans,
                                                              java.util.Set<String>
                                                                      roleOnlyReadyServiceParticipantPaths) {
        if (plan == null) {
            return false;
        }
        if ("APP_DDL_PLATFORM_PRIMARY_KEY".equals(plan.migrationType())
                && appDdlPlatformPrimaryKeyClosedIdentity(plan.identityKind())) {
            return false;
        }
        if ("APP_DDL_PLATFORM_PRIMARY_KEY".equals(plan.migrationType())
                && appDdlPlatformPrimaryKeyResolvedByRoleIdentity(plan, roleParticipantPlans)) {
            return false;
        }
        if ("SERVICE_NON_ROLE_PARTICIPANT".equals(plan.migrationType())
                && serviceParticipantPathsRoleOnlyReady(plan, roleOnlyReadyServiceParticipantPaths)) {
            return false;
        }
        return "OBJECT_BACKED_PRIMARY_KEY".equals(plan.migrationType())
                || "APP_DDL_PLATFORM_PRIMARY_KEY".equals(plan.migrationType())
                || "OBJECT_BACKED_INDEX".equals(plan.migrationType())
                || "SPP_TECHNICAL_FIELD_PLATFORM_COLUMN".equals(plan.migrationType())
                || "REPOSITORY_ESCAPE_HATCH".equals(plan.migrationType())
                || "SNAPSHOT_ESCAPE_HATCH".equals(plan.migrationType())
                || "REMOTE_REFERENCE_ADAPTER".equals(plan.migrationType())
                || "SERVICE_NON_ROLE_PARTICIPANT".equals(plan.migrationType())
                || "QUANTUM_SERVICE_INTERNAL_PROTOCOL".equals(plan.migrationType());
    }

    private static java.util.Set<String> roleOnlyReadyServiceParticipantPaths(
            List<ServiceParticipantRoleOnlyMigrationActionPlan> plans) {
        java.util.Set<String> paths = new java.util.LinkedHashSet<>();
        for (ServiceParticipantRoleOnlyMigrationActionPlan plan : safeList(plans)) {
            if (plan.roleOnlyReady() && plan.path() != null && !plan.path().isBlank()) {
                paths.add(plan.path());
            }
        }
        return java.util.Set.copyOf(paths);
    }

    private static boolean serviceParticipantPathsRoleOnlyReady(
            AppSemanticMigrationPlan plan, java.util.Set<String> roleOnlyReadyServiceParticipantPaths) {
        if (plan == null || plan.paths() == null || plan.paths().isEmpty()
                || roleOnlyReadyServiceParticipantPaths == null || roleOnlyReadyServiceParticipantPaths.isEmpty()) {
            return false;
        }
        return roleOnlyReadyServiceParticipantPaths.containsAll(plan.paths());
    }

    private static boolean appDdlPlatformPrimaryKeyResolvedByRoleIdentity(
            AppSemanticMigrationPlan plan, List<RoleParticipantPlan> roleParticipantPlans) {
        String identityKind = appDdlPlatformPrimaryKeyIdentityKind(plan, roleParticipantPlans);
        return appDdlPlatformPrimaryKeyClosedIdentity(identityKind);
    }

    private static boolean appDdlPlatformPrimaryKeyClosedIdentity(String identityKind) {
        return "ROLE_PERFORMER_IDENTITY".equals(identityKind)
                || "COMPOSITION_IDENTITY".equals(identityKind)
                || "BUSINESS_FIELD_IDENTITY".equals(identityKind);
    }

    private static String appDdlPlatformPrimaryKeyIdentityKind(AppSemanticMigrationPlan plan,
                                                              List<RoleParticipantPlan> roleParticipantPlans) {
        if (plan != null && plan.identityKind() != null && !plan.identityKind().isBlank()) {
            return plan.identityKind();
        }
        RoleParticipantPlan exactRoleParticipant = appDdlPlatformPrimaryKeyRoleParticipant(plan,
                roleParticipantPlans);
        if (exactRoleParticipant != null) {
            return "ROLE_PERFORMER_IDENTITY";
        }
        RoleParticipantPlan compositionRoleParticipant = appDdlPlatformPrimaryKeyCompositionRoleParticipant(plan,
                roleParticipantPlans);
        return compositionRoleParticipant == null ? "SIDECAR_PLATFORM_IDENTITY" : "COMPOSITION_IDENTITY";
    }

    private static RoleParticipantPlan appDdlPlatformPrimaryKeyRoleParticipant(
            AppSemanticMigrationPlan plan, List<RoleParticipantPlan> roleParticipantPlans) {
        if (plan == null || roleParticipantPlans == null || roleParticipantPlans.isEmpty()) {
            return null;
        }
        List<String> pathColumns = appDdlPlatformPrimaryKeyPathColumns(plan);
        if (pathColumns.isEmpty()) {
            return null;
        }
        return roleParticipantPlans.stream()
                .filter(rolePlan -> rolePlan != null
                        && matchesAppDdlPlatformPrimaryKeyRoleParticipant(plan, rolePlan, pathColumns))
                .findFirst()
                .orElse(null);
    }

    private static RoleParticipantPlan appDdlPlatformPrimaryKeyCompositionRoleParticipant(
            AppSemanticMigrationPlan plan, List<RoleParticipantPlan> roleParticipantPlans) {
        if (plan == null || roleParticipantPlans == null || roleParticipantPlans.isEmpty()) {
            return null;
        }
        List<String> pathColumns = appDdlPlatformPrimaryKeyPathColumns(plan);
        if (pathColumns.isEmpty()) {
            return null;
        }
        return roleParticipantPlans.stream()
                .filter(rolePlan -> rolePlan != null
                        && matchesAppDdlPlatformPrimaryKeyComposition(plan, rolePlan, pathColumns))
                .findFirst()
                .orElse(null);
    }

    private static boolean matchesAppDdlPlatformPrimaryKeyRoleParticipant(AppSemanticMigrationPlan plan,
                                                                         RoleParticipantPlan rolePlan,
                                                                         List<String> pathColumns) {
        boolean tableMatches = plan.tableName() != null
                && !plan.tableName().isBlank()
                && plan.tableName().equals(rolePlan.roleTableName());
        boolean typeMatches = plan.objectType() != null
                && !plan.objectType().isBlank()
                && plan.objectType().equals(rolePlan.roleType());
        return (tableMatches || typeMatches)
                && !rolePlan.platformColumns().isEmpty()
                && rolePlan.platformColumns().containsAll(pathColumns);
    }

    private static boolean matchesAppDdlPlatformPrimaryKeyComposition(AppSemanticMigrationPlan plan,
                                                                      RoleParticipantPlan rolePlan,
                                                                      List<String> pathColumns) {
        boolean tableMatches = plan.tableName() != null
                && !plan.tableName().isBlank()
                && plan.tableName().equals(rolePlan.roleTableName());
        boolean typeMatches = plan.objectType() != null
                && !plan.objectType().isBlank()
                && plan.objectType().equals(rolePlan.roleType());
        return (tableMatches || typeMatches)
                && !rolePlan.platformColumns().isEmpty()
                && pathColumns.containsAll(rolePlan.platformColumns())
                && pathColumns.size() > rolePlan.platformColumns().size();
    }

    private static List<String> appDdlPlatformPrimaryKeyPathColumns(AppSemanticMigrationPlan plan) {
        if (plan == null || plan.paths() == null || plan.paths().isEmpty()) {
            return List.of();
        }
        return plan.paths().stream()
                .map(GeneratedServiceCatalog::appDdlPlatformPrimaryKeyPathColumn)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private static String appDdlPlatformPrimaryKeyPathColumn(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int dot = path.lastIndexOf('.');
        return dot >= 0 && dot + 1 < path.length() ? path.substring(dot + 1) : path;
    }

    private static String remainingAppSemanticMigrationKey(AppSemanticMigrationPlan plan) {
        if (plan == null) {
            return "";
        }
        return String.join(":",
                metadataValue(plan.migrationType()),
                metadataValue(plan.targetKind()),
                metadataValue(plan.targetName()),
                metadataValue(plan.serviceName()),
                metadataValue(plan.tableName()),
                metadataValue(plan.objectType()),
                metadataValue(plan.adapterName()),
                metadataValue(plan.adapterRole()),
                metadataValue(plan.objectKeySource()),
                metadataValue(plan.objectKeyAppDdlIndexName()),
                String.join("+", plan.paths()),
                String.join("+", plan.appDdlObjectKeyPaths()),
                String.join("+", plan.objectKeyColumns()),
                String.join("+", plan.externalObjectKeyRoots()),
                String.join("+", plan.participantCategories()),
                metadataValue(plan.identityKind()),
                metadataValue(plan.recommendation()));
    }

    private static void addTechnicalFieldPlatformColumnPlans(Map<String, AppSemanticMigrationPlan> plansByKey,
                                                             List<AppSemanticMigrationPlan> plans) {
        for (AppSemanticMigrationPlan plan : safeList(plans)) {
            if (!"SPP_TECHNICAL_FIELD_PLATFORM_COLUMN".equals(plan.migrationType())) {
                continue;
            }
            String key = remainingAppSemanticMigrationKey(plan);
            if (!key.isBlank()) {
                plansByKey.putIfAbsent(key, plan);
            }
        }
    }

    private static void addAppDdlPlatformPrimaryKeyPlans(Map<String, AppSemanticMigrationPlan> plansByKey,
                                                         List<AppSemanticMigrationPlan> plans) {
        for (AppSemanticMigrationPlan plan : safeList(plans)) {
            if (!"APP_DDL_PLATFORM_PRIMARY_KEY".equals(plan.migrationType())) {
                continue;
            }
            String key = remainingAppSemanticMigrationKey(plan);
            if (!key.isBlank()) {
                plansByKey.putIfAbsent(key, plan);
            }
        }
    }

    private static void addQuantumServiceInternalProtocolPlans(
            Map<String, AppSemanticMigrationPlan> plansByKey, List<AppSemanticMigrationPlan> plans) {
        for (AppSemanticMigrationPlan plan : safeList(plans)) {
            if (!"QUANTUM_SERVICE_INTERNAL_PROTOCOL".equals(plan.migrationType())) {
                continue;
            }
            String key = remainingAppSemanticMigrationKey(plan);
            if (!key.isBlank()) {
                plansByKey.putIfAbsent(key, plan);
            }
        }
    }

    private static List<String> technicalFieldPlatformColumns(AppSemanticMigrationPlan plan) {
        return migrationPlanPlatformColumns(plan);
    }

    private static List<String> migrationPlanPlatformColumns(AppSemanticMigrationPlan plan) {
        if (plan == null) {
            return List.of();
        }
        if (!plan.objectKeyColumns().isEmpty()) {
            return plan.objectKeyColumns();
        }
        List<String> columns = new ArrayList<>();
        for (String path : plan.paths()) {
            if (path == null || path.isBlank()) {
                continue;
            }
            String trimmed = path.trim();
            int dot = trimmed.lastIndexOf('.');
            columns.add(dot >= 0 && dot + 1 < trimmed.length()
                    ? trimmed.substring(dot + 1)
                    : trimmed);
        }
        return List.copyOf(columns);
    }

    private static boolean repositoryReplacementReady(RepositoryQueryPlan plan) {
        return plan != null
                && plan.appDdlDerived()
                && plan.appDdlColumnPushdown()
                && !plan.escapeHatch();
    }

    private static String repositoryReplacementAccessContractKey(RepositoryQueryPlan plan, String readiness) {
        if (plan == null) {
            return "NO_REPOSITORY_ACCESS_CONTRACT";
        }
        String repository = firstNonBlank(plan.resolvedRepositoryName(), plan.repositoryName());
        String columnSet = plan.repositoryColumns().isEmpty()
                ? "NO_COLUMNS"
                : String.join("+", plan.repositoryColumns());
        String pushdown = plan.appDdlColumnPushdown()
                ? "COLUMN_PUSHDOWN"
                : "COLUMN_NOT_PUSHDOWN";
        return indexKey(plan.serviceName()) + ":" + indexKey(plan.objectType())
                + "->" + indexKey(repository)
                + ":" + indexKey(plan.repositoryLookupKind())
                + ":" + indexKey(plan.source())
                + ":" + columnSet
                + ":" + indexKey(plan.aggregateRelationKind())
                + ":" + pushdown
                + ":" + indexKey(readiness);
    }

    private static List<String> repositoryReplacementReadyReasons(RepositoryQueryPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        if (plan.appDdlDerived()) {
            reasons.add("appDdlDerived");
        }
        if (plan.appDdlColumnPushdown()) {
            reasons.add("columnPushdown");
        }
        if (!plan.escapeHatch()) {
            reasons.add("escapeHatchClear");
        }
        return List.copyOf(reasons);
    }

    private static List<String> repositoryReplacementMissingReasons(RepositoryQueryPlan plan) {
        if (plan == null) {
            return List.of("queryPlan");
        }
        List<String> reasons = new ArrayList<>();
        if (!plan.appDdlDerived()) {
            reasons.add("appDdlDerived");
        }
        if (!plan.appDdlColumnPushdown()) {
            reasons.add("columnPushdown");
        }
        if (plan.escapeHatch()) {
            reasons.add("escapeHatchClear");
        }
        return List.copyOf(reasons);
    }

    private static List<String> repositoryReplacementActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of();
        }
        List<String> actions = new ArrayList<>();
        for (String reason : missingReasons) {
            if ("appDdlDerived".equals(reason)) {
                actions.add("deriveRepositoryFromAppDdl");
            } else if ("columnPushdown".equals(reason)) {
                actions.add("pushRepositoryColumnsToAppDdl");
            } else if ("escapeHatchClear".equals(reason)) {
                actions.add("clearRepositoryEscapeHatch");
            } else {
                actions.add("reviewRepositoryReplacementReadiness");
            }
        }
        return List.copyOf(actions);
    }

    private static boolean repositoryMigrationColumnPushdownComplete(RepositoryMigrationPlan plan) {
        return plan != null
                && plan.totalQueryCount() > 0
                && plan.appDdlColumnPushdownCount() == plan.totalQueryCount();
    }

    private static boolean repositoryMigrationReady(RepositoryMigrationPlan plan) {
        return repositoryMigrationMissingReasons(plan).isEmpty();
    }

    private static String repositoryMigrationContractKey(RepositoryMigrationPlan plan, String readiness) {
        if (plan == null) {
            return "NO_REPOSITORY_MIGRATION_CONTRACT";
        }
        String repository = firstNonBlank(plan.resolvedRepositoryName(), plan.repositoryName());
        return indexKey(plan.planName()) + ":" + indexKey(repository)
                + "->" + indexKey(plan.repositoryAdapterName())
                + ":" + indexKey(plan.storageTableName())
                + ":" + listContractKey(plan.replacementSources(), "NO_REPLACEMENT_SOURCE")
                + ":" + listContractKey(plan.repositoryLookupKinds(), "NO_LOOKUP_KIND")
                + ":" + listContractKey(plan.aggregateRelationKinds(), "NO_AGGREGATE_RELATION")
                + ":" + indexKey(readiness);
    }

    private static String listContractKey(List<String> values, String emptyKey) {
        List<String> safeValues = safeList(values);
        return safeValues.isEmpty() ? emptyKey : String.join("+", safeValues);
    }

    private static List<String> repositoryMigrationReadyReasons(RepositoryMigrationPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        if (plan.totalQueryCount() > 0) {
            reasons.add("queryPlan");
        }
        if (plan.appDdlDerivedQueryCount() == plan.totalQueryCount() && plan.totalQueryCount() > 0) {
            reasons.add("appDdlDerived");
        }
        if (repositoryMigrationColumnPushdownComplete(plan)) {
            reasons.add("columnPushdown");
        }
        if (plan.escapeHatchQueryCount() == 0) {
            reasons.add("escapeHatchClear");
        }
        if (!isBlank(firstNonBlank(plan.resolvedRepositoryName(), plan.repositoryName()))) {
            reasons.add("repository");
        }
        if (!isBlank(plan.repositoryAdapterName())) {
            reasons.add("adapter");
        }
        if (!isBlank(plan.storageTableName())) {
            reasons.add("storageTable");
        }
        if (!isBlank(plan.datasourceUrl())) {
            reasons.add("datasource");
        }
        if (!isBlank(plan.driverClassName())) {
            reasons.add("driver");
        }
        return List.copyOf(reasons);
    }

    private static List<String> repositoryMigrationMissingReasons(RepositoryMigrationPlan plan) {
        if (plan == null) {
            return List.of("migrationPlan");
        }
        List<String> reasons = new ArrayList<>();
        if (plan.totalQueryCount() <= 0) {
            reasons.add("queryPlan");
        }
        if (plan.totalQueryCount() <= 0 || plan.appDdlDerivedQueryCount() != plan.totalQueryCount()) {
            reasons.add("appDdlDerived");
        }
        if (!repositoryMigrationColumnPushdownComplete(plan)) {
            reasons.add("columnPushdown");
        }
        if (plan.escapeHatchQueryCount() > 0) {
            reasons.add("escapeHatchClear");
        }
        if (isBlank(firstNonBlank(plan.resolvedRepositoryName(), plan.repositoryName()))) {
            reasons.add("repository");
        }
        if (isBlank(plan.repositoryAdapterName())) {
            reasons.add("adapter");
        }
        if (isBlank(plan.storageTableName())) {
            reasons.add("storageTable");
        }
        if (isBlank(plan.datasourceUrl())) {
            reasons.add("datasource");
        }
        if (isBlank(plan.driverClassName())) {
            reasons.add("driver");
        }
        return List.copyOf(reasons);
    }

    private static List<String> repositoryMigrationActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of();
        }
        List<String> actions = new ArrayList<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "queryPlan" -> actions.add("declareRepositoryQuery");
                case "appDdlDerived" -> actions.add("deriveRepositoryFromAppDdl");
                case "columnPushdown" -> actions.add("pushRepositoryColumnsToAppDdl");
                case "escapeHatchClear" -> actions.add("clearRepositoryEscapeHatch");
                case "repository" -> actions.add("bindRepositoryAdapter");
                case "adapter" -> actions.add("registerRepositoryAdapter");
                case "storageTable" -> actions.add("configureRepositoryStorageTable");
                case "datasource" -> actions.add("configureRepositoryDatasource");
                case "driver" -> actions.add("configureRepositoryDriver");
                default -> actions.add("reviewRepositoryMigrationReadiness");
            }
        }
        return List.copyOf(actions);
    }

    private static boolean objectRepositoryReady(ObjectRepositoryPlan plan) {
        return objectRepositoryMissingReasons(plan).isEmpty();
    }

    private static List<String> objectRepositoryReadyReasons(ObjectRepositoryPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        if (!isBlank(plan.resolvedRepositoryName())) {
            reasons.add("repository");
        }
        if (!isBlank(plan.adapterName())) {
            reasons.add("adapter");
        }
        if (!isBlank(plan.storageTableName())) {
            reasons.add("storageTable");
        }
        if (!isBlank(plan.datasourceUrl())) {
            reasons.add("datasource");
        }
        if (!isBlank(plan.driverClassName())) {
            reasons.add("driver");
        }
        if (plan.appDdlTables()) {
            reasons.add("appDdlTable");
        }
        if (plan.appDdlColumnPushdown()) {
            reasons.add("columnPushdown");
        }
        if (!safeList(plan.keySources()).isEmpty()) {
            reasons.add("keySource");
        }
        if (!safeList(plan.objectKeyColumns()).isEmpty()) {
            reasons.add("objectKeyColumns");
        }
        return List.copyOf(reasons);
    }

    private static List<String> objectRepositoryMissingReasons(ObjectRepositoryPlan plan) {
        if (plan == null) {
            return List.of("objectRepositoryPlan");
        }
        List<String> reasons = new ArrayList<>();
        if (isBlank(plan.resolvedRepositoryName())) {
            reasons.add("repository");
        }
        if (isBlank(plan.adapterName())) {
            reasons.add("adapter");
        }
        if (isBlank(plan.storageTableName())) {
            reasons.add("storageTable");
        }
        if (isBlank(plan.datasourceUrl())) {
            reasons.add("datasource");
        }
        if (isBlank(plan.driverClassName())) {
            reasons.add("driver");
        }
        if (plan.repositoryQuery()) {
            if (!plan.appDdlTables()) {
                reasons.add("appDdlTable");
            }
            if (!plan.appDdlColumnPushdown()) {
                reasons.add("columnPushdown");
            }
            if (safeList(plan.keySources()).isEmpty()) {
                reasons.add("keySource");
            }
            if (safeList(plan.objectKeyColumns()).isEmpty()) {
                reasons.add("objectKeyColumns");
            }
        }
        return List.copyOf(reasons);
    }

    private static List<String> objectRepositoryActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "objectRepositoryPlan" -> actions.add("generateObjectRepositoryPlan");
                case "repository" -> actions.add("bindObjectRepository");
                case "adapter" -> actions.add("configureObjectRepositoryAdapter");
                case "storageTable" -> actions.add("configureObjectRepositoryStorageTable");
                case "datasource" -> actions.add("configureObjectRepositoryDatasource");
                case "driver" -> actions.add("configureObjectRepositoryDriver");
                case "appDdlTable" -> actions.add("enableObjectRepositoryAppDdlTable");
                case "columnPushdown" -> actions.add("pushObjectRepositoryColumnsToAppDdl");
                case "keySource" -> actions.add("deriveObjectRepositoryKeySource");
                case "objectKeyColumns" -> actions.add("pushObjectRepositoryKeyColumnsToAppDdl");
                default -> actions.add("reviewObjectRepositoryReadiness");
            }
        }
        return List.copyOf(actions);
    }

    private static String objectRepositoryKeyColumnSetKey(ObjectRepositoryPlan plan) {
        if (plan == null || safeList(plan.objectKeyColumns()).isEmpty()) {
            return "NO_OBJECT_KEY_COLUMNS";
        }
        return String.join("+", plan.objectKeyColumns());
    }

    private static List<String> physicalRepositoryReadyReasons(PhysicalRepositoryPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        if (!isBlank(plan.repositoryName())) {
            reasons.add("repository");
        }
        if (!isBlank(plan.adapterName())) {
            reasons.add("adapter");
        }
        if (!isBlank(plan.adapterRole())) {
            reasons.add("adapterRole");
        }
        if (plan.localJdbcObjectStore()) {
            reasons.add("jdbcObjectRepository");
        }
        if (plan.localJsonSnapshotStore()) {
            reasons.add("jsonSnapshotStore");
        }
        if (plan.referenceStore()) {
            reasons.add("referenceStore");
        }
        if (plan.appDdlTables()) {
            reasons.add("appDdlRepository");
        }
        if (!safeList(plan.appDdlTableNames()).isEmpty()) {
            reasons.add("appDdlTables");
        }
        if (!isBlank(plan.storageTableName())) {
            reasons.add("storageTable");
        }
        if (!isBlank(plan.transactionHistoryTableName())) {
            reasons.add("transactionHistoryTable");
        }
        if (!isBlank(plan.transactionArchiveTableName())) {
            reasons.add("transactionArchiveTable");
        }
        if (!isBlank(plan.datasourceUrl())) {
            reasons.add("datasource");
        }
        if (!isBlank(plan.driverClassName())) {
            reasons.add("driver");
        }
        if (!isBlank(plan.implementationClass())) {
            reasons.add("implementationClass");
        }
        return List.copyOf(reasons);
    }

    private static List<String> physicalRepositoryMissingReasons(PhysicalRepositoryPlan plan) {
        if (plan == null) {
            return List.of("physicalRepositoryPlan");
        }
        List<String> reasons = new ArrayList<>();
        if (isBlank(plan.repositoryName())) {
            reasons.add("repository");
        }
        if (isBlank(plan.adapterName())) {
            reasons.add("adapter");
        }
        if (isBlank(plan.adapterRole())) {
            reasons.add("adapterRole");
        }
        if (plan.localJdbcObjectStore()) {
            if (!plan.appDdlTables()) {
                reasons.add("appDdlRepository");
            }
            if (safeList(plan.appDdlTableNames()).isEmpty()) {
                reasons.add("appDdlTables");
            }
            if (isBlank(plan.storageTableName())) {
                reasons.add("storageTable");
            }
            if (isBlank(plan.transactionHistoryTableName())) {
                reasons.add("transactionHistoryTable");
            }
            if (isBlank(plan.transactionArchiveTableName())) {
                reasons.add("transactionArchiveTable");
            }
            if (isBlank(plan.datasourceUrl())) {
                reasons.add("datasource");
            }
            if (isBlank(plan.driverClassName())) {
                reasons.add("driver");
            }
        } else if (plan.localJsonSnapshotStore()) {
            if (isBlank(plan.storageTableName())) {
                reasons.add("storageTable");
            }
            if (isBlank(plan.datasourceUrl())) {
                reasons.add("datasource");
            }
            if (isBlank(plan.driverClassName())) {
                reasons.add("driver");
            }
        } else if (!plan.referenceStore()) {
            reasons.add("repositoryAdapterCapability");
        }
        return List.copyOf(reasons);
    }

    private static List<String> physicalRepositoryActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "physicalRepositoryPlan" -> actions.add("generatePhysicalRepositoryPlan");
                case "repository" -> actions.add("declarePhysicalRepository");
                case "adapter" -> actions.add("configureRepositoryAdapter");
                case "adapterRole" -> actions.add("resolveRepositoryAdapterPhysicalBinding");
                case "repositoryAdapterCapability" -> actions.add("configureJdbcObjectRepositoryAdapter");
                case "appDdlRepository" -> actions.add("enableRepositoryAppDdlTables");
                case "appDdlTables" -> actions.add("publishRepositoryAppDdlTableNames");
                case "storageTable" -> actions.add("configureRepositoryStorageTable");
                case "transactionHistoryTable" -> actions.add("configureRepositoryTransactionHistoryTable");
                case "transactionArchiveTable" -> actions.add("configureRepositoryTransactionArchiveTable");
                case "datasource" -> actions.add("configureRepositoryDatasource");
                case "driver" -> actions.add("configureRepositoryDriver");
                default -> actions.add("resolveRepositoryAdapterPhysicalBinding");
            }
        }
        return List.copyOf(actions);
    }

    public static PhysicalBackendPlan enrichPhysicalBackendPlan(PhysicalBackendPlan plan) {
        if (plan == null) {
            return null;
        }
        List<String> missingReasons = physicalBackendMissingReasons(plan);
        String readiness = missingReasons.isEmpty() ? "PHYSICAL_BACKEND_READY" : "PHYSICAL_BACKEND_BLOCKED";
        List<String> normalizedMissingReasons = missingReasons.isEmpty()
                ? List.of("NO_MISSING_REASON")
                : missingReasons;
        List<String> actions = missingReasons.isEmpty()
                ? List.of("NO_ACTION_REQUIRED")
                : physicalBackendActions(missingReasons);
        String backendContract = physicalBackendContractKey(plan, readiness);
        String runtimeContract = physicalBackendRuntimeContractKey(plan, readiness);
        return new PhysicalBackendPlan(plan.backendName(),
                plan.adapterName(),
                plan.adapterAvailable(),
                plan.adapterImplementationType(),
                plan.supportedTransactions(),
                plan.applicationId(),
                plan.transactionServiceGroup(),
                plan.transactionBridgeName(),
                plan.transactionBridgeClassName(),
                plan.seataTransactionTimeoutMillis(),
                plan.seataQuantumSupported(),
                plan.datasourceUrl(),
                plan.driverClassName(),
                plan.quantumStoreName(),
                plan.quantumDatasourceUrl(),
                plan.quantumDatasourceSeparate(),
                plan.quantumDatasourceCoordinated(),
                plan.quantumDatasourceCoordinationReadiness(),
                plan.quantumDatasourceCoordinationMissingReason(),
                plan.quantumDatasourceCoordinationAction(),
                plan.quantumDatasourceCoordinationContract(),
                plan.quantumJournalStoreName(),
                plan.quantumJournalTableName(),
                plan.quantumValueTableName(),
                plan.transactionCoordinatorName(),
                plan.idempotencyStoreName(),
                plan.idempotencyTableName(),
                plan.sagaLogStoreName(),
                plan.sagaLogTableName(),
                plan.tccBranchStoreName(),
                plan.tccBranchTableName(),
                plan.tccReservationStoreName(),
                plan.tccReservationTableName(),
                plan.tccReservationTtlMillis(),
                readiness,
                physicalBackendReadyReasons(plan),
                normalizedMissingReasons,
                actions,
                backendContract,
                runtimeContract);
    }

    private static String physicalBackendContractKey(PhysicalBackendPlan plan, String readiness) {
        return indexKey(plan.backendName())
                + ":" + indexKey(plan.adapterName())
                + ":" + indexKey(plan.adapterImplementationType())
                + ":" + physicalBackendTransactionSetKey(plan.supportedTransactions())
                + ":" + indexKey(readiness);
    }

    private static String physicalBackendTransactionSetKey(List<String> supportedTransactions) {
        List<String> values = safeList(supportedTransactions).stream()
                .map(GeneratedServiceCatalog::indexKey)
                .toList();
        return values.isEmpty() ? "NO_TRANSACTION_SUPPORT" : String.join("+", values);
    }

    private static String physicalBackendRuntimeContractKey(PhysicalBackendPlan plan, String readiness) {
        return indexKey(plan.backendName())
                + "->" + indexKey(plan.adapterImplementationType())
                + "+datasource=" + indexKey(plan.datasourceUrl())
                + "+quantumStore=" + indexKey(plan.quantumStoreName())
                + "+quantumDatasource=" + indexKey(plan.quantumDatasourceUrl())
                + ":" + indexKey(readiness);
    }

    private static List<String> physicalBackendReadyReasons(PhysicalBackendPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        if (!isBlank(plan.backendName())) {
            reasons.add("backend");
        }
        if (!isBlank(plan.adapterName())) {
            reasons.add("adapter");
        }
        if (plan.adapterAvailable()) {
            reasons.add("adapterAvailable");
        }
        if (!isBlank(plan.adapterImplementationType())) {
            reasons.add("implementationType");
        }
        if (!safeList(plan.supportedTransactions()).isEmpty()) {
            reasons.add("transactionSupport");
        }
        if (physicalBackendSupports(plan, "at")) {
            reasons.add("atSupport");
        }
        if (physicalBackendSupports(plan, "tcc")) {
            reasons.add("tccSupport");
        }
        if (physicalBackendSupports(plan, "saga")) {
            reasons.add("sagaSupport");
        }
        if (physicalBackendSupports(plan, "quantum")) {
            reasons.add("quantumSupport");
        }
        if (!isBlank(plan.datasourceUrl())) {
            reasons.add("datasource");
        }
        if (!isBlank(plan.driverClassName())) {
            reasons.add("driver");
        }
        if (!isBlank(plan.quantumStoreName())) {
            reasons.add("quantumStore");
        }
        if (!isBlank(plan.transactionCoordinatorName())) {
            reasons.add("transactionCoordinator");
        }
        if (!isBlank(plan.idempotencyStoreName())) {
            reasons.add("idempotencyStore");
        }
        if (!isBlank(plan.idempotencyTableName())) {
            reasons.add("idempotencyTable");
        }
        if (!isBlank(plan.sagaLogStoreName())) {
            reasons.add("sagaLogStore");
        }
        if (!isBlank(plan.sagaLogTableName())) {
            reasons.add("sagaLogTable");
        }
        if (!isBlank(plan.tccBranchStoreName())) {
            reasons.add("tccBranchStore");
        }
        if (!isBlank(plan.tccBranchTableName())) {
            reasons.add("tccBranchTable");
        }
        if (!isBlank(plan.tccReservationStoreName())) {
            reasons.add("tccReservationStore");
        }
        if (!isBlank(plan.tccReservationTableName())) {
            reasons.add("tccReservationTable");
        }
        if (!isBlank(plan.tccReservationTtlMillis())) {
            reasons.add("tccReservationTtl");
        }
        if (!isBlank(plan.applicationId())) {
            reasons.add("seataApplicationId");
        }
        if (!isBlank(plan.transactionServiceGroup())) {
            reasons.add("seataTransactionServiceGroup");
        }
        if (!isBlank(plan.transactionBridgeName())) {
            reasons.add("seataTransactionBridge");
        }
        if (!isBlank(plan.transactionBridgeClassName())) {
            reasons.add("seataTransactionBridgeClass");
        }
        if (!isBlank(plan.seataTransactionTimeoutMillis())) {
            reasons.add("seataTransactionTimeout");
        }
        if (plan.seataQuantumSupported()) {
            reasons.add("seataQuantumSupport");
        }
        return List.copyOf(reasons);
    }

    private static List<String> physicalBackendMissingReasons(PhysicalBackendPlan plan) {
        if (plan == null) {
            return List.of("physicalBackendPlan");
        }
        List<String> reasons = new ArrayList<>();
        if (isBlank(plan.backendName())) {
            reasons.add("backend");
        }
        if (isBlank(plan.adapterName())) {
            reasons.add("adapter");
        }
        if (!plan.adapterAvailable()) {
            reasons.add("adapterAvailability");
        }
        if (isBlank(plan.adapterImplementationType())) {
            reasons.add("implementationType");
        }
        if (safeList(plan.supportedTransactions()).isEmpty()) {
            reasons.add("transactionSupport");
        }
        if (physicalLocalJdbcBackend(plan)) {
            addLocalJdbcBackendMissingReasons(plan, reasons);
        } else if (physicalSeataBackend(plan)) {
            addSeataBackendMissingReasons(plan, reasons);
        }
        return List.copyOf(reasons);
    }

    private static void addLocalJdbcBackendMissingReasons(PhysicalBackendPlan plan, List<String> reasons) {
        if (isBlank(plan.datasourceUrl())) {
            reasons.add("localJdbcDatasource");
        }
        if (isBlank(plan.driverClassName())) {
            reasons.add("localJdbcDriver");
        }
        if (isBlank(plan.transactionCoordinatorName())) {
            reasons.add("localJdbcTransactionCoordinator");
        }
        if (isBlank(plan.idempotencyStoreName())) {
            reasons.add("localJdbcIdempotencyStore");
        }
        if (isBlank(plan.idempotencyTableName())) {
            reasons.add("localJdbcIdempotencyTable");
        }
        if (physicalBackendSupports(plan, "saga")) {
            if (isBlank(plan.sagaLogStoreName())) {
                reasons.add("localJdbcSagaLogStore");
            }
            if (isBlank(plan.sagaLogTableName())) {
                reasons.add("localJdbcSagaLogTable");
            }
        }
        if (physicalBackendSupports(plan, "tcc")) {
            if (isBlank(plan.tccBranchStoreName())) {
                reasons.add("localJdbcTccBranchStore");
            }
            if (isBlank(plan.tccBranchTableName())) {
                reasons.add("localJdbcTccBranchTable");
            }
            if (isBlank(plan.tccReservationStoreName())) {
                reasons.add("localJdbcTccReservationStore");
            }
            if (isBlank(plan.tccReservationTableName())) {
                reasons.add("localJdbcTccReservationTable");
            }
            if (isBlank(plan.tccReservationTtlMillis())) {
                reasons.add("localJdbcTccReservationTtl");
            }
        }
        if (physicalBackendSupports(plan, "quantum") && isBlank(plan.quantumStoreName())) {
            reasons.add("quantumStore");
        }
    }

    private static void addSeataBackendMissingReasons(PhysicalBackendPlan plan, List<String> reasons) {
        if (isBlank(plan.applicationId())) {
            reasons.add("seataApplicationId");
        }
        if (isBlank(plan.transactionServiceGroup())) {
            reasons.add("seataTransactionServiceGroup");
        }
        if (isBlank(plan.transactionBridgeName())) {
            reasons.add("seataTransactionBridge");
        }
        if (isBlank(plan.transactionBridgeClassName())) {
            reasons.add("seataTransactionBridgeClass");
        }
        if (isBlank(plan.seataTransactionTimeoutMillis())) {
            reasons.add("seataTransactionTimeout");
        }
        if (physicalBackendSupports(plan, "quantum")) {
            if (!plan.seataQuantumSupported()) {
                reasons.add("seataQuantumSupport");
            }
            if (isBlank(plan.quantumStoreName())) {
                reasons.add("quantumStore");
            }
        }
    }

    private static List<String> physicalBackendActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            actions.add(physicalBackendAction(reason));
        }
        return List.copyOf(actions);
    }

    private static String physicalBackendAction(String missingReason) {
        if (isBlank(missingReason)) {
            return "resolvePhysicalBackendBinding";
        }
        return switch (missingReason) {
            case "physicalBackendPlan" -> "generatePhysicalBackendPlan";
            case "backend" -> "declarePhysicalBackend";
            case "adapter" -> "configurePhysicalBackendAdapter";
            case "adapterAvailability" -> "registerPhysicalBackendAdapter";
            case "implementationType" -> "resolvePhysicalBackendImplementation";
            case "transactionSupport" -> "enableBackendTransactionSupport";
            case "quantumStore" -> "bindBackendQuantumStore";
            default -> physicalBackendMissingActionName(missingReason);
        };
    }

    private static String physicalBackendMissingActionName(String missingReason) {
        return "configure" + Character.toUpperCase(missingReason.charAt(0)) + missingReason.substring(1);
    }

    private static boolean physicalLocalJdbcBackend(PhysicalBackendPlan plan) {
        return plan != null
                && ("localJdbc".equals(plan.adapterName())
                || "appframe-local-jdbc".equals(plan.adapterImplementationType()));
    }

    private static boolean physicalSeataBackend(PhysicalBackendPlan plan) {
        return plan != null
                && ("seata".equals(plan.adapterName())
                || plan.adapterImplementationType().startsWith("seata"));
    }

    private static boolean physicalBackendSupports(PhysicalBackendPlan plan, String transactionKind) {
        return plan != null && safeList(plan.supportedTransactions()).contains(transactionKind);
    }

    private static List<String> physicalRemoteReadyReasons(PhysicalRemotePlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        if (!isBlank(plan.serviceName())) {
            reasons.add("service");
        }
        if (!isBlank(plan.adapterName())) {
            reasons.add("adapter");
        }
        if (!isBlank(plan.adapterRole())) {
            reasons.add("adapterRole");
        }
        if (plan.adapterExecutable()) {
            reasons.add("adapterExecutable");
        }
        if (plan.referenceImplementation()) {
            reasons.add("referenceImplementation");
            addPhysicalRemoteReadyReason(reasons, !isBlank(plan.objectType()), "objectType");
            addPhysicalRemoteReadyReason(reasons, !safeList(plan.objectIdPaths()).isEmpty(), "objectIdPath");
            addPhysicalRemoteReadyReason(reasons, !isBlank(plan.repositoryName()), "repository");
            addPhysicalRemoteReadyReason(reasons, !isBlank(plan.repositoryAdapterName()), "repositoryAdapter");
            addPhysicalRemoteReadyReason(reasons, !isBlank(plan.repositoryAdapterRole()), "repositoryAdapterRole");
            addPhysicalRemoteReadyReason(reasons, plan.repositoryAppDdlTables(), "repositoryAppDdl");
            addPhysicalRemoteReadyReason(reasons, !isBlank(plan.repositoryStorageTableName()), "repositoryStorageTable");
            addPhysicalRemoteReadyReason(reasons, !isBlank(plan.repositoryDatasourceUrl()), "repositoryDatasource");
            addPhysicalRemoteReadyReason(reasons, !isBlank(plan.repositoryDriverClassName()), "repositoryDriver");
        }
        if (plan.externalImplementation()) {
            reasons.add("externalImplementation");
            addPhysicalRemoteReadyReason(reasons, !isBlank(plan.endpoint()), "endpoint");
            addPhysicalRemoteReadyReason(reasons,
                    isBlank(plan.endpoint()) || isAbsoluteHttpEndpoint(plan.endpoint()),
                    "absoluteHttpEndpoint");
            addPhysicalRemoteReadyReason(reasons,
                    isBlank(plan.timeoutMillis()) || isPositiveLong(plan.timeoutMillis()),
                    "positiveTimeout");
            if (remoteExternalEndpointReady(plan)) {
                reasons.add("productionEndpoint");
            }
        }
        return List.copyOf(reasons);
    }

    private static void addPhysicalRemoteReadyReason(List<String> reasons, boolean ready, String reason) {
        if (ready) {
            reasons.add(reason);
        }
    }

    private static List<String> physicalRemoteMissingReasons(PhysicalRemotePlan plan) {
        if (plan == null) {
            return List.of("physicalRemotePlan");
        }
        List<String> reasons = new ArrayList<>();
        if (isBlank(plan.serviceName())) {
            reasons.add("service");
        }
        if (isBlank(plan.adapterName())) {
            reasons.add("adapter");
        }
        if (isBlank(plan.adapterRole())) {
            reasons.add("adapterRole");
        }
        if (!plan.adapterExecutable()) {
            reasons.add("adapterExecutable");
        }
        if (plan.referenceImplementation()) {
            addReferenceRemoteMissingReasons(plan, reasons);
        } else if (plan.externalImplementation()) {
            addExternalRemoteMissingReasons(plan, reasons);
        } else {
            reasons.add("implementationKind");
        }
        return List.copyOf(reasons);
    }

    private static void addReferenceRemoteMissingReasons(PhysicalRemotePlan plan, List<String> reasons) {
        if (isBlank(plan.objectType())) {
            reasons.add("referenceObjectType");
        }
        if (safeList(plan.objectIdPaths()).isEmpty()) {
            reasons.add("referenceObjectIdPath");
        }
        if (isBlank(plan.repositoryName())) {
            reasons.add("referenceRepository");
        }
        if (isBlank(plan.repositoryAdapterName())) {
            reasons.add("referenceRepositoryAdapter");
        }
        if (isBlank(plan.repositoryAdapterRole())) {
            reasons.add("referenceRepositoryAdapterRole");
        }
        if (!plan.repositoryAppDdlTables()) {
            reasons.add("referenceRepositoryAppDdl");
        }
        if (isBlank(plan.repositoryStorageTableName())) {
            reasons.add("referenceRepositoryStorageTable");
        }
        if (isBlank(plan.repositoryDatasourceUrl())) {
            reasons.add("referenceRepositoryDatasource");
        }
        if (isBlank(plan.repositoryDriverClassName())) {
            reasons.add("referenceRepositoryDriver");
        }
    }

    private static void addExternalRemoteMissingReasons(PhysicalRemotePlan plan, List<String> reasons) {
        String endpointReason = remoteExternalEndpointReadinessReason(plan);
        if (!"-".equals(endpointReason)) {
            reasons.add(endpointReason);
        }
        if (!isBlank(plan.timeoutMillis()) && !isPositiveLong(plan.timeoutMillis())) {
            reasons.add("timeoutInvalid");
        }
    }

    private static List<String> physicalRemoteActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            actions.add(physicalRemoteAction(reason));
        }
        return List.copyOf(actions);
    }

    private static String physicalRemoteAction(String missingReason) {
        if (isBlank(missingReason)) {
            return "resolvePhysicalRemoteBinding";
        }
        return switch (missingReason) {
            case "physicalRemotePlan" -> "generatePhysicalRemotePlan";
            case "service" -> "bindRemoteService";
            case "adapter" -> "configureRemoteAdapter";
            case "adapterRole" -> "resolveRemoteAdapterRole";
            case "adapterExecutable" -> "enableExecutableRemoteAdapter";
            case "implementationKind" -> "chooseRemoteImplementationKind";
            case "referenceObjectType" -> "configureReferenceRemoteObjectType";
            case "referenceObjectIdPath" -> "configureReferenceRemoteObjectIdPath";
            case "referenceRepository" -> "bindReferenceRemoteRepository";
            case "referenceRepositoryAdapter" -> "configureReferenceRemoteRepositoryAdapter";
            case "referenceRepositoryAdapterRole" -> "resolveReferenceRemoteRepositoryAdapterRole";
            case "referenceRepositoryAppDdl" -> "enableReferenceRemoteRepositoryAppDdl";
            case "referenceRepositoryStorageTable" -> "configureReferenceRemoteRepositoryStorageTable";
            case "referenceRepositoryDatasource" -> "configureReferenceRemoteRepositoryDatasource";
            case "referenceRepositoryDriver" -> "configureReferenceRemoteRepositoryDriver";
            case "endpoint" -> "configureRemoteEndpoint";
            case "endpointInvalid" -> "configureAbsoluteHttpRemoteEndpoint";
            case "developmentEndpoint" -> "replaceDevelopmentRemoteEndpoint";
            case "placeholderEndpoint" -> "replaceRemoteEndpointPlaceholder";
            case "timeoutInvalid" -> "configurePositiveRemoteTimeout";
            default -> "resolvePhysicalRemoteReadiness";
        };
    }

    private static String physicalRemoteImplementationKind(PhysicalRemotePlan plan) {
        if (plan == null) {
            return "UNKNOWN_IMPLEMENTATION";
        }
        if (plan.externalImplementation()) {
            return "EXTERNAL_IMPLEMENTATION";
        }
        if (plan.referenceImplementation()) {
            return "REFERENCE_IMPLEMENTATION";
        }
        return "CUSTOM_IMPLEMENTATION";
    }

    private static List<String> physicalQuantumReadyReasons(PhysicalQuantumPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        if (!isBlank(plan.storeName())) {
            reasons.add("store");
        }
        if (!isBlank(plan.adapterName())) {
            reasons.add("adapter");
        }
        if (!isBlank(plan.adapterRole())) {
            reasons.add("adapterRole");
        }
        if (!isBlank(plan.implementationStrategy())) {
            reasons.add("implementationStrategy");
        }
        if (!isBlank(plan.accessMode())) {
            reasons.add("accessMode");
        }
        if (!isBlank(plan.idempotencyMode())) {
            reasons.add("idempotencyMode");
            reasons.add("idempotencyOwner:" + physicalQuantumIdempotencyOwner(plan));
        }
        if (!isBlank(plan.reversalMode())) {
            reasons.add("reversalMode");
            reasons.add("reversalOwner:" + physicalQuantumReversalOwner(plan));
        }
        if (!safeList(plan.attributeIds()).isEmpty()) {
            reasons.add("attributes");
        }
        if ("GLOBAL_QUANTUM_STORE".equals(plan.implementationStrategy())) {
            addPhysicalQuantumReadyReason(reasons, !isBlank(plan.datasourceUrl()), "globalDatasource");
            addPhysicalQuantumReadyReason(reasons, !isBlank(plan.driverClassName()), "globalDriver");
            addPhysicalQuantumReadyReason(reasons, !isBlank(plan.journalStoreName()), "journalStore");
            addPhysicalQuantumReadyReason(reasons, !isBlank(plan.journalTableName()), "journalTable");
            addPhysicalQuantumReadyReason(reasons, !isBlank(plan.valueTableName()), "valueTable");
        } else if ("TCC_OBJECT_STORE".equals(plan.implementationStrategy())) {
            addPhysicalQuantumReadyReason(reasons, !safeList(plan.backendNames()).isEmpty(),
                    "objectBackendBinding");
            addPhysicalQuantumReadyReason(reasons, safeList(plan.backendSupportedTransactions()).contains("tcc"),
                    "tccBackend");
            addPhysicalQuantumReadyReason(reasons, safeList(plan.backendSupportedTransactions()).contains("quantum"),
                    "quantumBackend");
            reasons.add("noGlobalQuantumDatasourceRequired");
        } else if ("QUANTUM_SERVICE".equals(plan.implementationStrategy())) {
            addPhysicalQuantumReadyReason(reasons, !isBlank(plan.endpoint()), "serviceEndpoint");
            addPhysicalQuantumReadyReason(reasons, isBlank(plan.endpoint()) || isAbsoluteHttpEndpoint(plan.endpoint()),
                    "absoluteHttpEndpoint");
            addPhysicalQuantumReadyReason(reasons, isBlank(plan.timeoutMillis()) || isPositiveLong(plan.timeoutMillis()),
                    "positiveTimeout");
            reasons.add("serviceInternalIdempotency");
            reasons.add("LOGICAL_QSERVICE".equals(plan.serviceProtocol())
                    ? "serviceReversal"
                    : "negativeDeltaReversal");
            reasons.add("noGlobalQuantumDatasourceRequired");
        }
        if (physicalQuantumExpectedContractMatches(plan)) {
            reasons.add("adapterContractMatches");
        }
        return List.copyOf(reasons);
    }

    private static void addPhysicalQuantumReadyReason(List<String> reasons, boolean ready, String reason) {
        if (ready) {
            reasons.add(reason);
        }
    }

    private static List<String> physicalQuantumMissingReasons(PhysicalQuantumPlan plan) {
        if (plan == null) {
            return List.of("physicalQuantumPlan");
        }
        List<String> reasons = new ArrayList<>(physicalQuantumContractMissingReasons(plan));
        if ("QUANTUM_SERVICE".equals(plan.implementationStrategy())) {
            if (!"SERVICE_IDEMPOTENCY".equals(plan.idempotencyMode())) {
                reasons.add("serviceIdempotency");
            }
            String expectedReversal = expectedPhysicalQuantumReversalMode(
                    plan.adapterName(), plan.serviceProtocol());
            if (!expectedReversal.isBlank() && !expectedReversal.equals(plan.reversalMode())) {
                reasons.add("SERVICE_REVERSAL".equals(expectedReversal)
                        ? "serviceReversal"
                        : "negativeDeltaReversal");
            }
        } else if ("TCC_OBJECT_STORE".equals(plan.implementationStrategy())) {
            if (!"OBJECT_STORE_IDEMPOTENCY".equals(plan.idempotencyMode())) {
                reasons.add("objectStoreIdempotency");
            }
            if (!"NEGATIVE_DELTA".equals(plan.reversalMode())) {
                reasons.add("negativeDeltaReversal");
            }
        } else if ("GLOBAL_QUANTUM_STORE".equals(plan.implementationStrategy())) {
            if (!"FRAMEWORK_IDEMPOTENCY".equals(plan.idempotencyMode())) {
                reasons.add("frameworkIdempotency");
            }
            if (!"NEGATIVE_DELTA".equals(plan.reversalMode())) {
                reasons.add("negativeDeltaReversal");
            }
        }
        return List.copyOf(new LinkedHashSet<>(reasons));
    }

    public static PhysicalQuantumPlan enrichPhysicalQuantumPlan(PhysicalQuantumPlan plan) {
        if (plan == null) {
            return null;
        }
        List<String> missingReasons = physicalQuantumMissingReasons(plan);
        String readiness = missingReasons.isEmpty() ? "PHYSICAL_QUANTUM_READY" : "PHYSICAL_QUANTUM_BLOCKED";
        List<String> normalizedMissingReasons = missingReasons.isEmpty()
                ? List.of("NO_MISSING_REASON")
                : missingReasons;
        List<String> actions = missingReasons.isEmpty()
                ? List.of("NO_ACTION_REQUIRED")
                : physicalQuantumActions(missingReasons);
        String idempotencyOwner = physicalQuantumIdempotencyOwner(plan);
        String reversalOwner = physicalQuantumReversalOwner(plan);
        String implementationContract = physicalQuantumImplementationContractKey(plan);
        String runtimeContract = physicalQuantumRuntimeContractKey(plan, missingReasons.isEmpty());
        return new PhysicalQuantumPlan(plan.storeName(),
                plan.adapterName(),
                plan.adapterRole(),
                plan.implementationStrategy(),
                plan.accessMode(),
                plan.idempotencyMode(),
                plan.reversalMode(),
                plan.serviceProtocol(),
                plan.endpoint(),
                plan.timeoutMillis(),
                plan.attributeIds(),
                plan.datasourceUrl(),
                plan.driverClassName(),
                plan.journalStoreName(),
                plan.journalTableName(),
                plan.valueTableName(),
                plan.localJdbcQuantumStore(),
                plan.backendNames(),
                plan.backendAdapterNames(),
                plan.backendSupportedTransactions(),
                readiness,
                physicalQuantumReadyReasons(plan),
                normalizedMissingReasons,
                actions,
                idempotencyOwner,
                reversalOwner,
                implementationContract,
                runtimeContract);
    }

    private static List<String> physicalQuantumContractMissingReasons(PhysicalQuantumPlan plan) {
        List<String> missing = new ArrayList<>();
        if (plan == null) {
            missing.add("store");
            return missing;
        }
        if (isBlank(plan.storeName())) {
            missing.add("store");
        }
        if (isBlank(plan.implementationStrategy())) {
            missing.add("strategy");
        }
        if (isBlank(plan.accessMode())) {
            missing.add("access");
        }
        if (isBlank(plan.idempotencyMode())) {
            missing.add("idempotency");
        }
        if (isBlank(plan.reversalMode())) {
            missing.add("reversal");
        }
        if (safeList(plan.attributeIds()).isEmpty()) {
            missing.add("attributes");
        }
        addPhysicalQuantumContractMismatch(missing, "implementationStrategy",
                plan.implementationStrategy(), expectedPhysicalQuantumImplementationStrategy(plan.adapterName()));
        addPhysicalQuantumContractMismatch(missing, "accessMode",
                plan.accessMode(), expectedPhysicalQuantumAccessMode(plan.adapterName()));
        addPhysicalQuantumContractMismatch(missing, "idempotencyMode",
                plan.idempotencyMode(), expectedPhysicalQuantumIdempotencyMode(plan.adapterName()));
        addPhysicalQuantumContractMismatch(missing, "reversalMode",
                plan.reversalMode(),
                expectedPhysicalQuantumReversalMode(plan.adapterName(), plan.serviceProtocol()));
        if ("quantumService".equals(plan.adapterName())
                && !Set.of("HTTP_JSON", "LOGICAL_QSERVICE").contains(plan.serviceProtocol())) {
            missing.add("serviceProtocol");
        }
        String strategy = plan.implementationStrategy();
        if ("QUANTUM_SERVICE".equals(strategy)) {
            if (isBlank(plan.endpoint())) {
                missing.add("endpoint");
            } else if (!isAbsoluteHttpEndpoint(plan.endpoint())) {
                missing.add("endpointInvalid");
            }
            if (!isBlank(plan.timeoutMillis()) && !isPositiveLong(plan.timeoutMillis())) {
                missing.add("timeoutInvalid");
            }
        } else if ("GLOBAL_QUANTUM_STORE".equals(strategy)) {
            if (isBlank(plan.datasourceUrl())) {
                missing.add("datasource");
            }
            if (isBlank(plan.driverClassName())) {
                missing.add("driver");
            }
            if (isBlank(plan.journalStoreName())) {
                missing.add("journalStore");
            } else if (!"com.servpp.service.transaction.JdbcQuantumJournalStore"
                    .equals(plan.journalStoreName())) {
                missing.add("journalStoreMismatch");
            }
            if (isBlank(plan.journalTableName())) {
                missing.add("journalTable");
            }
            if (isBlank(plan.valueTableName())) {
                missing.add("valueTable");
            }
        } else if ("TCC_OBJECT_STORE".equals(strategy)) {
            if (safeList(plan.backendNames()).isEmpty()) {
                missing.add("backendBinding");
            } else {
                if (!safeList(plan.backendSupportedTransactions()).contains("tcc")) {
                    missing.add("tccBackend");
                }
                if (!safeList(plan.backendSupportedTransactions()).contains("quantum")) {
                    missing.add("quantumBackend");
                }
            }
        } else if (!isBlank(strategy)) {
            missing.add("knownStrategy");
        }
        return List.copyOf(missing);
    }

    private static void addPhysicalQuantumContractMismatch(List<String> missing, String key,
                                                           String actualValue, String expectedValue) {
        if (isBlank(expectedValue) || isBlank(actualValue) || expectedValue.equals(actualValue)) {
            return;
        }
        missing.add(key + "Mismatch");
    }

    private static boolean physicalQuantumExpectedContractMatches(PhysicalQuantumPlan plan) {
        if (plan == null) {
            return false;
        }
        return quantumContractValueMatches(plan.implementationStrategy(),
                expectedPhysicalQuantumImplementationStrategy(plan.adapterName()))
                && quantumContractValueMatches(plan.accessMode(), expectedPhysicalQuantumAccessMode(plan.adapterName()))
                && quantumContractValueMatches(plan.idempotencyMode(),
                        expectedPhysicalQuantumIdempotencyMode(plan.adapterName()))
                && quantumContractValueMatches(plan.reversalMode(),
                        expectedPhysicalQuantumReversalMode(plan.adapterName(), plan.serviceProtocol()));
    }

    private static boolean quantumContractValueMatches(String actual, String expected) {
        return !isBlank(expected) && expected.equals(actual);
    }

    private static String expectedPhysicalQuantumImplementationStrategy(String adapterName) {
        if ("jdbcQuantum".equals(adapterName) || "jdbc-quantum".equals(adapterName)) {
            return "GLOBAL_QUANTUM_STORE";
        }
        if ("tccObjectStore".equals(adapterName)) {
            return "TCC_OBJECT_STORE";
        }
        if ("quantumService".equals(adapterName)) {
            return "QUANTUM_SERVICE";
        }
        return "";
    }

    private static String expectedPhysicalQuantumAccessMode(String adapterName) {
        if ("jdbcQuantum".equals(adapterName) || "jdbc-quantum".equals(adapterName)) {
            return "GLOBAL_STORE_ADAPTER";
        }
        if ("tccObjectStore".equals(adapterName)) {
            return "TCC_OBJECT_STORE_ADAPTER";
        }
        if ("quantumService".equals(adapterName)) {
            return "QUANTUM_SERVICE_ADAPTER";
        }
        return "";
    }

    private static String expectedPhysicalQuantumIdempotencyMode(String adapterName) {
        if ("jdbcQuantum".equals(adapterName) || "jdbc-quantum".equals(adapterName)) {
            return "FRAMEWORK_IDEMPOTENCY";
        }
        if ("tccObjectStore".equals(adapterName)) {
            return "OBJECT_STORE_IDEMPOTENCY";
        }
        if ("quantumService".equals(adapterName)) {
            return "SERVICE_IDEMPOTENCY";
        }
        return "";
    }

    private static String expectedPhysicalQuantumReversalMode(String adapterName, String serviceProtocol) {
        if ("jdbcQuantum".equals(adapterName)
                || "jdbc-quantum".equals(adapterName)
                || "tccObjectStore".equals(adapterName)) {
            return "NEGATIVE_DELTA";
        }
        if ("quantumService".equals(adapterName)) {
            return "LOGICAL_QSERVICE".equals(serviceProtocol)
                    ? "SERVICE_REVERSAL"
                    : "NEGATIVE_DELTA";
        }
        return "";
    }

    private static String physicalQuantumIdempotencyOwner(PhysicalQuantumPlan plan) {
        if (plan == null) {
            return "-";
        }
        if ("SERVICE_IDEMPOTENCY".equals(plan.idempotencyMode())) {
            return "QUANTUM_SERVICE";
        }
        if ("OBJECT_STORE_IDEMPOTENCY".equals(plan.idempotencyMode())) {
            return "OBJECT_STORE";
        }
        if ("FRAMEWORK_IDEMPOTENCY".equals(plan.idempotencyMode())) {
            return "FRAMEWORK";
        }
        return metadataValue(plan.idempotencyMode());
    }

    private static String physicalQuantumReversalOwner(PhysicalQuantumPlan plan) {
        if (plan == null) {
            return "-";
        }
        if ("QUANTUM_SERVICE".equals(plan.implementationStrategy())) {
            return "QUANTUM_SERVICE";
        }
        if ("TCC_OBJECT_STORE".equals(plan.implementationStrategy())) {
            return "OBJECT_STORE";
        }
        if ("GLOBAL_QUANTUM_STORE".equals(plan.implementationStrategy())) {
            return "GLOBAL_QUANTUM_STORE";
        }
        return metadataValue(plan.implementationStrategy());
    }

    private static String physicalQuantumImplementationContractKey(PhysicalQuantumPlan plan) {
        if (plan == null) {
            return "NO_PHYSICAL_QUANTUM_CONTRACT";
        }
        return indexKey(plan.implementationStrategy())
                + "+" + indexKey(plan.accessMode())
                + "+" + indexKey(physicalQuantumIdempotencyOwner(plan))
                + "+" + indexKey(physicalQuantumReversalOwner(plan));
    }

    private static String physicalQuantumRuntimeContractKey(PhysicalQuantumPlan plan, boolean ready) {
        if (plan == null) {
            return "NO_PHYSICAL_QUANTUM_RUNTIME_CONTRACT";
        }
        return indexKey(plan.storeName())
                + "->" + physicalQuantumImplementationContractKey(plan)
                + "+" + physicalQuantumRuntimeBindingKey(plan)
                + ":" + (ready ? "PHYSICAL_QUANTUM_READY" : "PHYSICAL_QUANTUM_BLOCKED");
    }

    private static String physicalQuantumRuntimeBindingKey(PhysicalQuantumPlan plan) {
        if (plan == null) {
            return "runtime=-";
        }
        if ("QUANTUM_SERVICE".equals(plan.implementationStrategy())) {
            return "protocol=" + indexKey(plan.serviceProtocol())
                    + "+endpoint=" + endpointIndexKey(plan.endpoint());
        }
        if ("GLOBAL_QUANTUM_STORE".equals(plan.implementationStrategy())) {
            return "datasource=" + indexKey(plan.datasourceUrl());
        }
        if ("TCC_OBJECT_STORE".equals(plan.implementationStrategy())) {
            return "backend=" + indexedListKey(plan.backendNames(), "NO_BACKEND_BINDING");
        }
        if (!isBlank(plan.endpoint())) {
            return "endpoint=" + endpointIndexKey(plan.endpoint());
        }
        if (!isBlank(plan.datasourceUrl())) {
            return "datasource=" + indexKey(plan.datasourceUrl());
        }
        return "backend=" + indexedListKey(plan.backendNames(), "NO_BACKEND_BINDING");
    }

    private static String indexedListKey(List<String> values, String emptyKey) {
        List<String> normalized = safeList(values).stream()
                .map(GeneratedServiceCatalog::indexKey)
                .toList();
        if (normalized.isEmpty()) {
            return emptyKey;
        }
        return String.join("+", normalized);
    }

    private static boolean isPositiveLong(String value) {
        if (isBlank(value)) {
            return false;
        }
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static List<String> physicalQuantumActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            actions.add(physicalQuantumAction(reason));
        }
        return List.copyOf(actions);
    }

    private static String physicalQuantumAction(String missingReason) {
        if (isBlank(missingReason)) {
            return "resolvePhysicalQuantumBinding";
        }
        return switch (missingReason) {
            case "physicalQuantumPlan", "store" -> "declarePhysicalQuantumStore";
            case "strategy" -> "declareQuantumImplementationStrategy";
            case "access" -> "declareQuantumAccessMode";
            case "idempotency", "serviceIdempotency" -> "declareQuantumServiceIdempotency";
            case "objectStoreIdempotency" -> "declareObjectStoreIdempotency";
            case "frameworkIdempotency" -> "declareFrameworkIdempotency";
            case "reversal", "negativeDeltaReversal" -> "declareNegativeDeltaReversal";
            case "attributes" -> "bindQuantumAttributesToPhysicalStore";
            case "implementationStrategyMismatch" -> "alignQuantumImplementationStrategyWithAdapter";
            case "accessModeMismatch" -> "alignQuantumAccessModeWithAdapter";
            case "idempotencyModeMismatch" -> "alignQuantumIdempotencyModeWithAdapter";
            case "reversalModeMismatch" -> "alignQuantumReversalModeWithAdapter";
            case "serviceProtocol" -> "selectSupportedQuantumServiceProtocol";
            case "endpoint" -> "configureQuantumServiceEndpoint";
            case "endpointInvalid" -> "configureAbsoluteHttpQuantumServiceEndpoint";
            case "timeoutInvalid" -> "configurePositiveQuantumServiceTimeout";
            case "datasource" -> "configureQuantumStoreDatasource";
            case "driver" -> "configureQuantumStoreDriver";
            case "journalStore" -> "configureQuantumJournalStore";
            case "journalStoreMismatch" -> "useJdbcQuantumJournalStore";
            case "journalTable" -> "configureQuantumJournalTable";
            case "valueTable" -> "configureQuantumValueTable";
            case "backendBinding" -> "bindQuantumStoreToObjectBackend";
            case "tccBackend" -> "enableTccBackendForQuantumStore";
            case "quantumBackend" -> "enableQuantumBackendForObjectStore";
            case "knownStrategy" -> "chooseSupportedQuantumImplementationStrategy";
            default -> "resolvePhysicalQuantumReadiness";
        };
    }

    private static List<String> objectRepositoryRoles(ObjectRepositoryPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> roles = new ArrayList<>();
        if (plan.repositoryQuery()) {
            roles.add("REPOSITORY_QUERY");
        }
        if (plan.objectSnapshot()) {
            roles.add("OBJECT_SNAPSHOT");
        }
        if (plan.transactionEvidence()) {
            roles.add("TRANSACTION_EVIDENCE");
        }
        if (plan.collectionQuery()) {
            roles.add("COLLECTION_QUERY");
        }
        return List.copyOf(roles);
    }

    private static boolean contractSignerFieldMigrationPlan(AppSemanticMigrationPlan plan) {
        return plan != null && "CONTRACT_SIGNER_FIELD".equals(plan.migrationType());
    }

    private static TransactionSnapshotRetentionExecutionPlan enrichTransactionSnapshotRetentionExecutionPlan(
            TransactionSnapshotRetentionExecutionPlan plan) {
        if (plan == null) {
            return null;
        }
        List<String> missingReasons = safeList(plan.missingReasons()).isEmpty()
                ? retentionExecutionMissingReasons(plan)
                : plan.missingReasons();
        String readiness = firstNonBlank(plan.readiness(),
                missingReasons.isEmpty()
                        || (missingReasons.size() == 1
                        && "NO_MISSING_REASON".equals(missingReasons.get(0)))
                        ? "RETENTION_EXECUTION_READY"
                        : "RETENTION_EXECUTION_BLOCKED");
        List<String> readyReasons = safeList(plan.readyReasons()).isEmpty()
                && "RETENTION_EXECUTION_READY".equals(readiness)
                ? retentionExecutionReadyReasons(plan)
                : plan.readyReasons();
        List<String> normalizedMissingReasons = missingReasons.isEmpty()
                ? List.of("NO_MISSING_REASON")
                : missingReasons;
        List<String> actions = safeList(plan.actions()).isEmpty()
                ? (missingReasons.isEmpty() ? List.of("NO_ACTION_REQUIRED") : retentionExecutionActions(missingReasons))
                : plan.actions();
        String executionContract = firstNonBlank(plan.executionContract(),
                retentionExecutionContractKey(plan, missingReasons));
        return new TransactionSnapshotRetentionExecutionPlan(plan.planName(),
                plan.retentionPlanName(),
                plan.automationPlanName(),
                plan.repositoryName(),
                plan.resolvedRepositoryName(),
                plan.serviceName(),
                plan.resourceName(),
                plan.retentionMillis(),
                plan.serviceSpecificRetention(),
                plan.resourceSpecificRetention(),
                plan.cutoffSource(),
                plan.archiveRetentionMillis(),
                plan.resourceSpecificArchiveRetention(),
                plan.archiveCutoffSource(),
                plan.archiveRuntimeOperation(),
                plan.pruneArchiveRuntimeOperation(),
                plan.hotHistoryTableName(),
                plan.coldArchiveTableName(),
                plan.schedulerPropertyPrefix(),
                plan.retentionMillisPropertyName(),
                plan.archiveRetentionMillisPropertyName(),
                readiness,
                readyReasons,
                normalizedMissingReasons,
                actions,
                executionContract);
    }

    private static List<String> retentionExecutionReadyReasons(
            TransactionSnapshotRetentionExecutionPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        if (!isBlank(plan.retentionPlanName())) {
            reasons.add("retentionPlan");
        }
        if (!isBlank(plan.automationPlanName())) {
            reasons.add("automationPlan");
        }
        if (!isBlank(firstNonBlank(plan.resolvedRepositoryName(), plan.repositoryName()))) {
            reasons.add("repository");
        }
        if (!isBlank(plan.serviceName())) {
            reasons.add("service");
        }
        if (!isBlank(plan.resourceName())) {
            reasons.add("resource");
        }
        if (plan.retentionMillis() > 0) {
            reasons.add("retentionWindow");
        }
        if (plan.archiveRetentionMillis() > 0) {
            reasons.add("archiveRetentionWindow");
        }
        if (!isBlank(plan.cutoffSource())) {
            reasons.add("retentionCutoffSource");
        }
        if (!isBlank(plan.archiveCutoffSource())) {
            reasons.add("archiveCutoffSource");
        }
        if (!isBlank(plan.archiveRuntimeOperation())) {
            reasons.add("archiveRuntimeOperation");
        }
        if (!isBlank(plan.pruneArchiveRuntimeOperation())) {
            reasons.add("pruneArchiveRuntimeOperation");
        }
        if (!isBlank(plan.hotHistoryTableName())) {
            reasons.add("hotHistoryTable");
        }
        if (!isBlank(plan.coldArchiveTableName())) {
            reasons.add("coldArchiveTable");
        }
        if (!isBlank(plan.schedulerPropertyPrefix())) {
            reasons.add("schedulerPropertyPrefix");
        }
        if (!isBlank(plan.retentionMillisPropertyName())) {
            reasons.add("retentionProperty");
        }
        if (!isBlank(plan.archiveRetentionMillisPropertyName())) {
            reasons.add("archiveRetentionProperty");
        }
        return List.copyOf(reasons);
    }

    private static String retentionExecutionContractKey(
            TransactionSnapshotRetentionExecutionPlan plan,
            List<String> missingReasons) {
        if (plan == null) {
            return "NO_RESOURCE:repository=NO_REPOSITORY:retention=NO_RETENTION"
                    + ":source=NO_RETENTION_SOURCE:archiveRetention=NO_ARCHIVE_RETENTION"
                    + ":archiveSource=NO_ARCHIVE_SOURCE:hot=NO_HOT_HISTORY:cold=NO_COLD_ARCHIVE"
                    + ":archive=NO_ARCHIVE_OPERATION:prune=NO_PRUNE_ARCHIVE_OPERATION"
                    + ":scheduler=NO_SCHEDULER:RETENTION_EXECUTION_BLOCKED";
        }
        String resource = isBlank(plan.resourceName()) ? "NO_RESOURCE" : plan.resourceName();
        String retention = plan.retentionMillis() > 0
                ? plan.retentionMillis() + "ms"
                : "NO_RETENTION";
        String archiveRetention = plan.archiveRetentionMillis() > 0
                ? plan.archiveRetentionMillis() + "ms"
                : "NO_ARCHIVE_RETENTION";
        String scheduler = isBlank(plan.schedulerPropertyPrefix())
                ? "NO_SCHEDULER"
                : "SCHEDULED";
        String readiness = safeList(missingReasons).isEmpty()
                ? "RETENTION_EXECUTION_READY"
                : "RETENTION_EXECUTION_BLOCKED";
        return resource
                + ":repository=" + indexKey(firstNonBlank(
                        firstNonBlank(plan.resolvedRepositoryName(), plan.repositoryName()),
                        "NO_REPOSITORY"))
                + ":retention=" + retention
                + ":source=" + indexKey(firstNonBlank(plan.cutoffSource(), "NO_RETENTION_SOURCE"))
                + ":archiveRetention=" + archiveRetention
                + ":archiveSource=" + indexKey(firstNonBlank(plan.archiveCutoffSource(), "NO_ARCHIVE_SOURCE"))
                + ":hot=" + indexKey(firstNonBlank(plan.hotHistoryTableName(), "NO_HOT_HISTORY"))
                + ":cold=" + indexKey(firstNonBlank(plan.coldArchiveTableName(), "NO_COLD_ARCHIVE"))
                + ":archive=" + indexKey(firstNonBlank(plan.archiveRuntimeOperation(), "NO_ARCHIVE_OPERATION"))
                + ":prune=" + indexKey(firstNonBlank(plan.pruneArchiveRuntimeOperation(),
                        "NO_PRUNE_ARCHIVE_OPERATION"))
                + ":scheduler=" + scheduler
                + ":" + readiness;
    }

    private static String retentionExecutionStorageTierKey(
            TransactionSnapshotRetentionExecutionPlan plan) {
        if (plan == null) {
            return "NO_STORAGE_TIER";
        }
        List<String> tiers = new ArrayList<>();
        if (!isBlank(plan.hotHistoryTableName())) {
            tiers.add("HOT_HISTORY");
        }
        if (!isBlank(plan.coldArchiveTableName())
                || !isBlank(plan.archiveRuntimeOperation())
                || !isBlank(plan.pruneArchiveRuntimeOperation())) {
            tiers.add("COLD_ARCHIVE");
        }
        if (tiers.isEmpty()) {
            return "NO_STORAGE_TIER";
        }
        return String.join("+", tiers);
    }

    private static List<String> retentionExecutionMissingReasons(
            TransactionSnapshotRetentionExecutionPlan plan) {
        if (plan == null) {
            return List.of("executionPlan");
        }
        List<String> reasons = new ArrayList<>();
        if (isBlank(plan.retentionPlanName())) {
            reasons.add("retentionPlan");
        }
        if (isBlank(plan.automationPlanName())) {
            reasons.add("automationPlan");
        }
        if (isBlank(firstNonBlank(plan.resolvedRepositoryName(), plan.repositoryName()))) {
            reasons.add("repository");
        }
        if (isBlank(plan.serviceName())) {
            reasons.add("service");
        }
        if (isBlank(plan.resourceName())) {
            reasons.add("resource");
        }
        if (plan.retentionMillis() <= 0) {
            reasons.add("retentionWindow");
        }
        if (plan.archiveRetentionMillis() <= 0) {
            reasons.add("archiveRetentionWindow");
        }
        if (isBlank(plan.cutoffSource())) {
            reasons.add("retentionCutoffSource");
        }
        if (isBlank(plan.archiveCutoffSource())) {
            reasons.add("archiveCutoffSource");
        }
        if (isBlank(plan.archiveRuntimeOperation())) {
            reasons.add("archiveRuntimeOperation");
        }
        if (isBlank(plan.pruneArchiveRuntimeOperation())) {
            reasons.add("pruneArchiveRuntimeOperation");
        }
        if (isBlank(plan.hotHistoryTableName())) {
            reasons.add("hotHistoryTable");
        }
        if (isBlank(plan.coldArchiveTableName())) {
            reasons.add("coldArchiveTable");
        }
        if (isBlank(plan.schedulerPropertyPrefix())) {
            reasons.add("schedulerPropertyPrefix");
        }
        if (isBlank(plan.retentionMillisPropertyName())) {
            reasons.add("retentionProperty");
        }
        if (isBlank(plan.archiveRetentionMillisPropertyName())) {
            reasons.add("archiveRetentionProperty");
        }
        return List.copyOf(reasons);
    }

    private static List<String> retentionExecutionActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of();
        }
        List<String> actions = new ArrayList<>();
        for (String reason : missingReasons) {
            if ("retentionPlan".equals(reason)) {
                actions.add("createTransactionSnapshotRetentionPlan");
            } else if ("automationPlan".equals(reason)
                    || "schedulerPropertyPrefix".equals(reason)
                    || "retentionProperty".equals(reason)
                    || "archiveRetentionProperty".equals(reason)) {
                actions.add("bindTransactionRetentionAutomationPlan");
            } else if ("repository".equals(reason)
                    || "hotHistoryTable".equals(reason)
                    || "coldArchiveTable".equals(reason)) {
                actions.add("configureTransactionSnapshotRetentionRepository");
            } else if ("retentionWindow".equals(reason)
                    || "retentionCutoffSource".equals(reason)) {
                actions.add("configureTransactionRetentionMillisByResource");
            } else if ("archiveRetentionWindow".equals(reason)
                    || "archiveCutoffSource".equals(reason)) {
                actions.add("configureTransactionArchiveRetentionMillisByResource");
            } else if ("archiveRuntimeOperation".equals(reason)
                    || "pruneArchiveRuntimeOperation".equals(reason)) {
                actions.add("registerTransactionRetentionRuntimeOperations");
            } else {
                actions.add("reviewTransactionRetentionExecutionPlan");
            }
        }
        return List.copyOf(actions);
    }

    private static List<String> roleParticipantReadyReasons(RoleParticipantPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        if (!isBlank(plan.roleType())) {
            reasons.add("role");
        }
        if (!isBlank(plan.roleTableName())) {
            reasons.add("roleTable");
        }
        if (!isBlank(plan.mapperName())) {
            reasons.add("mapper");
        }
        if (!safeList(plan.performerTypes()).isEmpty()) {
            reasons.add("performers");
        }
        reasons.add(plan.jointPerformer() ? "jointPerformer" : "singlePerformer");
        if (plan.performerKeyComplete()) {
            reasons.add("performerKey");
        }
        if (plan.roleKeyComplete()) {
            reasons.add("rolePlatformKey");
        }
        if (plan.complete()) {
            reasons.add("roleIdentity");
        }
        if (!safeList(plan.platformColumns()).isEmpty()) {
            reasons.add("platformColumns");
        }
        if (!safeList(plan.compatRoleKeyPaths()).isEmpty()) {
            reasons.add("compatRoleKey");
        }
        return List.copyOf(reasons);
    }

    private static List<String> roleParticipantMissingReasons(RoleParticipantPlan plan) {
        if (plan == null) {
            return List.of("plan");
        }
        List<String> missing = new ArrayList<>();
        if (isBlank(plan.roleType())) {
            missing.add("role");
        }
        if (isBlank(plan.roleTableName())) {
            missing.add("roleTable");
        }
        if (isBlank(plan.mapperName())) {
            missing.add("mapper");
        }
        if (safeList(plan.performerTypes()).isEmpty()) {
            missing.add("performers");
        }
        if (!plan.performerKeyComplete()) {
            missing.add("performerKey");
        }
        if (!plan.roleKeyComplete()) {
            missing.add("rolePlatformKey");
        }
        if (safeList(plan.platformColumns()).isEmpty()) {
            missing.add("platformColumns");
        }
        if (!plan.complete()) {
            missing.add("roleIdentity");
        }
        return List.copyOf(missing);
    }

    private static List<String> roleParticipantActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "plan" -> actions.add("registerRoleParticipantPlan");
                case "role" -> actions.add("declareRoleParticipant");
                case "roleTable" -> actions.add("bindRoleToAppDdlTable");
                case "mapper" -> actions.add("generateRoleParticipantMapper");
                case "performers" -> actions.add("declareRolePerformer");
                case "performerKey" -> actions.add("derivePerformerKeyFromAppDdl");
                case "rolePlatformKey", "platformColumns" ->
                        actions.add("pushRoleIdentityToAppDdlPlatformColumns");
                case "roleIdentity" -> actions.add("bindRuntimeRoleParticipantIdentity");
                default -> actions.add("resolveRoleParticipantReadiness");
            }
        }
        return List.copyOf(actions);
    }

    public static RoleParticipantPlan enrichRoleParticipantPlan(RoleParticipantPlan plan) {
        if (plan == null) {
            return null;
        }
        List<String> computedMissingReasons = roleParticipantMissingReasons(plan);
        String readiness = firstNonBlank(plan.readiness(),
                computedMissingReasons.isEmpty() ? "ROLE_PARTICIPANT_READY" : "ROLE_PARTICIPANT_INCOMPLETE");
        boolean ready = "ROLE_PARTICIPANT_READY".equals(readiness);
        List<String> missingReasons = safeList(plan.missingReasons()).isEmpty()
                ? (ready ? List.of("NO_MISSING_REASON") : computedMissingReasons)
                : safeList(plan.missingReasons());
        List<String> readyReasons = safeList(plan.readyReasons()).isEmpty() && ready
                ? roleParticipantReadyReasons(plan)
                : safeList(plan.readyReasons());
        List<String> actions = safeList(plan.actions()).isEmpty()
                ? roleParticipantActions(ready ? List.of() : missingReasons)
                : safeList(plan.actions());
        String identityReadiness = firstNonBlank(plan.identityReadiness(), roleParticipantIdentityReadiness(plan));
        String performerTypeSet = firstNonBlank(plan.performerTypeSet(), performerTypeSetKey(plan.performerTypes()));
        String platformColumnSet = firstNonBlank(plan.platformColumnSet(), platformColumnSetKey(plan.platformColumns()));
        return new RoleParticipantPlan(plan.roleType(),
                plan.roleTableName(),
                plan.mapperName(),
                plan.performerTypes(),
                plan.performerKeyPaths(),
                plan.roleKeyPaths(),
                plan.roleSourceKeyPaths(),
                plan.compatRoleKeyPaths(),
                plan.platformColumns(),
                plan.performerKeyComplete(),
                plan.roleKeyComplete(),
                plan.complete(),
                plan.performerCount(),
                plan.jointPerformer(),
                plan.participationKind(),
                plan.binding(),
                readiness,
                readyReasons,
                missingReasons,
                actions,
                identityReadiness,
                performerTypeSet,
                platformColumnSet);
    }

    private static String roleParticipantIdentityReadiness(RoleParticipantPlan plan) {
        if (plan == null) {
            return "ROLE_IDENTITY_INCOMPLETE";
        }
        if (plan.complete()) {
            return "ROLE_IDENTITY_READY";
        }
        if (!plan.performerKeyComplete()) {
            return "PERFORMER_KEY_INCOMPLETE";
        }
        if (!plan.roleKeyComplete()) {
            return "ROLE_KEY_INCOMPLETE";
        }
        return "ROLE_IDENTITY_INCOMPLETE";
    }

    private static List<String> roleParticipantRequestBindingReadyReasons(
            RoleParticipantRequestBindingPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        if (!isBlank(plan.serviceName())) {
            reasons.add("service");
        }
        if (!isBlank(plan.roleType())) {
            reasons.add("role");
        }
        if (!isBlank(plan.roleParameterName())) {
            reasons.add("roleParameter");
        }
        if (!safeList(plan.performerTypes()).isEmpty()) {
            reasons.add("performers");
        }
        if (plan.requestTypeAvailable() && !isBlank(plan.requestTypeName())) {
            reasons.add("requestEnvelope");
        }
        if (!isBlank(plan.identityBindingSource())) {
            reasons.add(plan.identityBindingSource().toLowerCase(Locale.ROOT));
        }
        if (!safeList(plan.sidecarFieldNames()).isEmpty()) {
            reasons.add("performerSidecar");
        }
        if (safeList(plan.missingSidecarFieldNames()).isEmpty()) {
            reasons.add("sidecarFieldsComplete");
        }
        if (plan.complete()) {
            reasons.add("identityBinding");
        }
        return List.copyOf(reasons);
    }

    private static List<String> roleParticipantRequestBindingMissingReasons(
            RoleParticipantRequestBindingPlan plan) {
        if (plan == null) {
            return List.of("plan");
        }
        List<String> missing = new ArrayList<>();
        if (isBlank(plan.serviceName())) {
            missing.add("service");
        }
        if (isBlank(plan.roleType())) {
            missing.add("role");
        }
        if (isBlank(plan.roleParameterName())) {
            missing.add("roleParameter");
        }
        if (safeList(plan.performerTypes()).isEmpty()) {
            missing.add("performers");
        }
        if (!plan.requestTypeAvailable() || isBlank(plan.requestTypeName())) {
            missing.add("requestEnvelope");
        }
        if (safeList(plan.sidecarFieldNames()).isEmpty()
                || !safeList(plan.missingSidecarFieldNames()).isEmpty()) {
            missing.add("performerSidecar");
        }
        if (!plan.complete()) {
            missing.add("identityBinding");
        }
        return List.copyOf(missing);
    }

    private static List<String> roleParticipantRequestBindingActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "plan" -> actions.add("registerRoleParticipantRequestBindingPlan");
                case "service", "role", "roleParameter", "performers" ->
                        actions.add("alignRoleParticipantRequestBinding");
                case "requestEnvelope" -> actions.add("generateRoleParticipantRequestEnvelope");
                case "performerSidecar" -> actions.add("addRoleParticipantPerformerSidecarFields");
                case "identityBinding" -> actions.add("bindRuntimeRoleParticipantIdentity");
                default -> actions.add("resolveRoleParticipantRequestBindingReadiness");
            }
        }
        return List.copyOf(actions);
    }

    private static List<String> contractSignerReadyReasons(ContractSignerPlan plan) {
        if (plan == null) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        if (!isBlank(plan.contractType())) {
            reasons.add("contract");
        }
        if (!isBlank(plan.contractTableName())) {
            reasons.add("contractTable");
        }
        if (!isBlank(plan.scenarioName())) {
            reasons.add("scenario");
        }
        if (!isBlank(plan.contractParameterName())) {
            reasons.add("contractParameter");
        }
        if (!isBlank(plan.signerPath())) {
            reasons.add("signerPath");
        }
        if (plan.signedByRole()) {
            reasons.add("roleSigner");
        }
        if (plan.roleParticipantDefined()) {
            reasons.add("roleParticipant");
        }
        if (!plan.migrationRequired()) {
            reasons.add("migrationReady");
        }
        return List.copyOf(reasons);
    }

    private static List<String> contractSignerMissingReasons(ContractSignerPlan plan) {
        if (plan == null) {
            return List.of("plan");
        }
        List<String> missing = new ArrayList<>();
        if (isBlank(plan.contractType())) {
            missing.add("contract");
        }
        if (isBlank(plan.scenarioName())) {
            missing.add("scenario");
        }
        if (isBlank(plan.contractParameterName())) {
            missing.add("contractParameter");
        }
        if (isBlank(plan.signerPath())) {
            missing.add("signerPath");
        }
        if (isBlank(plan.signerType())) {
            missing.add("signerRole");
        }
        if (!plan.signedByRole()) {
            missing.add("roleSigner");
        }
        if (!plan.roleParticipantDefined()) {
            missing.add("roleParticipant");
        }
        if (plan.migrationRequired()) {
            missing.add("migration");
        }
        return List.copyOf(missing);
    }

    private static List<String> contractSignerActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "plan" -> actions.add("registerContractSignerPlan");
                case "contract", "scenario", "contractParameter", "signerPath", "signerRole" ->
                        actions.add("deriveContractSignerFromScenarioRoles");
                case "roleSigner" -> actions.add("moveContractSignerToScenarioRoleParticipant");
                case "roleParticipant" -> actions.add("declareRoleParticipantIdentity");
                case "migration" -> actions.add("suppressLegacyContractFieldSignerOrMigrateToRoleSigner");
                default -> actions.add("resolveContractSignerReadiness");
            }
        }
        return List.copyOf(actions);
    }

    private static String contractSignerContractParameterKey(ContractSignerPlan plan) {
        if (plan == null) {
            return "NO_SCENARIO.NO_CONTRACT_PARAMETER";
        }
        String scenario = isBlank(plan.scenarioName()) ? "NO_SCENARIO" : plan.scenarioName();
        String parameter = isBlank(plan.contractParameterName())
                ? "NO_CONTRACT_PARAMETER"
                : plan.contractParameterName();
        return scenario + "." + parameter;
    }

    private static String contractSignerContractParameterPath(ContractSignerPlan plan) {
        if (plan == null) {
            return "NO_SCENARIO.NO_CONTRACT_PARAMETER:NO_CONTRACT";
        }
        String contract = isBlank(plan.contractType()) ? "NO_CONTRACT" : plan.contractType();
        return contractSignerContractParameterKey(plan) + ":" + contract;
    }

    private static String scenarioContractParameterPath(ScenarioContractContextPlan plan) {
        if (plan == null) {
            return "NO_SCENARIO.NO_CONTRACT_PARAMETER:NO_CONTRACT";
        }
        String parameterKey = isBlank(plan.contractParameterKey())
                ? "NO_SCENARIO.NO_CONTRACT_PARAMETER"
                : plan.contractParameterKey();
        String contract = isBlank(plan.contractType()) ? "NO_CONTRACT" : plan.contractType();
        return parameterKey + ":" + contract;
    }

    private static List<String> serviceParticipantRoleOnlyMissingReasons(
            ServiceParticipantRoleOnlyMigrationActionPlan plan) {
        if (plan == null) {
            return List.of("plan");
        }
        if (plan.roleOnlyReady()) {
            return List.of();
        }
        List<String> reasons = safeList(plan.readinessReasons());
        return reasons.isEmpty() ? List.of("roleOnlyBoundary") : reasons;
    }

    private static String serviceParticipantRoleOnlyExecutionContract(
            ServiceParticipantRoleOnlyMigrationActionPlan plan) {
        if (plan == null) {
            return "NO_SERVICE:NO_PATH->NO_PARTICIPANT+NO_ROLE_ONLY_ACTION+NO_TARGET_ROLE+ROLE_ONLY_BLOCKED";
        }
        String service = isBlank(plan.serviceName()) ? "NO_SERVICE" : plan.serviceName();
        String path = isBlank(plan.path()) ? "NO_PATH" : plan.path();
        String participantType = isBlank(plan.participantType()) ? "NO_PARTICIPANT" : plan.participantType();
        String category = isBlank(plan.participantCategory())
                ? "NO_PARTICIPANT_CATEGORY"
                : plan.participantCategory();
        String action = isBlank(plan.migrationAction()) ? "NO_ROLE_ONLY_ACTION" : plan.migrationAction();
        String targetRoles = safeList(plan.targetRoleTypes()).isEmpty()
                ? "NO_TARGET_ROLE"
                : String.join("+", plan.targetRoleTypes());
        String readiness = plan.roleOnlyReady() ? "ROLE_ONLY_READY" : "ROLE_ONLY_BLOCKED";
        return service + ":" + path
                + "->" + participantType
                + "+" + category
                + "+" + action
                + "+" + targetRoles
                + "+" + readiness;
    }

    private static String serviceParticipantRoleOnlyServiceParameterContract(
            ServiceParticipantRoleOnlyMigrationActionPlan plan) {
        if (plan == null) {
            return "NO_SERVICE:NO_PATH->NO_PARTICIPANT+NO_PARTICIPANT_CATEGORY";
        }
        String service = isBlank(plan.serviceName()) ? "NO_SERVICE" : plan.serviceName();
        String path = isBlank(plan.path()) ? "NO_PATH" : plan.path();
        String participantType = isBlank(plan.participantType()) ? "NO_PARTICIPANT" : plan.participantType();
        String category = isBlank(plan.participantCategory())
                ? "NO_PARTICIPANT_CATEGORY"
                : plan.participantCategory();
        return service + ":" + path + "->" + participantType + "+" + category;
    }

    private static <T> void addListValuesToIndex(Map<String, List<T>> index,
                                                 List<String> values,
                                                 String emptyKey,
                                                 T value) {
        List<String> safeValues = safeList(values);
        if (safeValues.isEmpty()) {
            addToIndex(index, emptyKey, value);
            return;
        }
        for (String item : safeValues) {
            addToIndex(index, indexKey(item), value);
        }
    }

    private static String metadataValue(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String indexKey(String value) {
        return value == null || value.isBlank() ? "UNASSIGNED" : value;
    }

    private static String endpointIndexKey(String value) {
        return value == null || value.isBlank() ? "NO_ENDPOINT" : value;
    }

    private static <T> List<String> uniqueNonBlank(List<T> values, Function<T, String> mapper) {
        if (values == null || values.isEmpty() || mapper == null) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (T value : values) {
            if (value == null) {
                continue;
            }
            String mapped = mapper.apply(value);
            if (mapped != null && !mapped.isBlank()) {
                result.add(mapped);
            }
        }
        return List.copyOf(result);
    }

    private static <T> void addToIndex(Map<String, List<T>> index, String key, T value) {
        index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
    }

    private static void addToStringIndex(Map<String, List<String>> index, String key, String value) {
        List<String> values = index.computeIfAbsent(key, ignored -> new ArrayList<>());
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private static <T> void addListIndexValues(Map<String, List<T>> index, List<String> keys, T value,
                                               String emptyKey) {
        List<String> safeKeys = safeList(keys);
        if (safeKeys.isEmpty()) {
            addToIndex(index, emptyKey, value);
            return;
        }
        for (String key : safeKeys) {
            addToIndex(index, indexKey(key), value);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private static List<String> appSemanticMigrationStrictErrorKeys() {
        return List.of(
                "serviceParticipantRoleOnlyStrictErrors",
                "roleParticipantRequestBindingErrors",
                "contractSignerRoleOnlyErrors",
                "roleBusinessModelStrictErrors",
                "repositoryReplacementStrictErrors",
                "snapshotReplacementStrictErrors",
                "externalRemoteAdapterStrictErrors",
                "remoteExternalEndpointStrictErrors",
                "quantumServiceExternalImplementationStrictErrors",
                "quantumServiceInternalProtocolStrictErrors",
                "platformColumnBusinessShadowStrictErrors",
                "appDdlPlatformPrimaryKeyStrictErrors",
                "currentSnapshotAppDdlKeyStrictErrors");
    }

    private static List<String> splitDeploymentErrors(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> errors = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String item : value.split(";")) {
            if (item == null || item.isBlank()) {
                continue;
            }
            if (!current.isEmpty()) {
                current.append(';');
            }
            current.append(item);
            if (containsStrictActionField(item)) {
                errors.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            errors.add(current.toString());
        }
        return List.copyOf(errors);
    }

    private static boolean containsStrictActionField(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("action=")
                || trimmed.contains(" action=")
                || trimmed.contains(", action=");
    }

    private static String strictErrorReason(String error) {
        if (error == null || error.isBlank()) {
            return "UNKNOWN_STRICT_REASON";
        }
        int start = error.indexOf("reason=");
        if (start < 0) {
            return "UNKNOWN_STRICT_REASON";
        }
        int valueStart = start + "reason=".length();
        int valueEnd = error.indexOf(',', valueStart);
        if (valueEnd < 0) {
            valueEnd = error.indexOf(')', valueStart);
        }
        if (valueEnd < 0) {
            valueEnd = error.length();
        }
        String reason = error.substring(valueStart, valueEnd).trim();
        return reason.isEmpty() ? "UNKNOWN_STRICT_REASON" : reason;
    }

    private static String strictErrorAction(String error) {
        if (error == null || error.isBlank()) {
            return "UNKNOWN_STRICT_ACTION";
        }
        int start = error.indexOf("action=");
        if (start < 0) {
            return "UNKNOWN_STRICT_ACTION";
        }
        int valueStart = start + "action=".length();
        int valueEnd = error.indexOf(';', valueStart);
        if (valueEnd < 0) {
            valueEnd = error.indexOf(',', valueStart);
        }
        if (valueEnd < 0) {
            valueEnd = error.indexOf(')', valueStart);
        }
        if (valueEnd < 0) {
            valueEnd = error.indexOf('.', valueStart);
        }
        if (valueEnd < 0) {
            valueEnd = error.length();
        }
        String action = error.substring(valueStart, valueEnd).trim();
        return action.isEmpty() ? "UNKNOWN_STRICT_ACTION" : action;
    }

    private static List<String> quantumAccessResponsibilityMissingReasons(QuantumAccessPlan plan) {
        if (plan == null) {
            return List.of("plan");
        }
        List<String> missing = new java.util.ArrayList<>();
        if (!plan.accessContractAvailable()) {
            missing.add("accessContract");
        }
        if (isBlank(plan.idempotencyOwner()) || isBlank(plan.idempotencyBoundary())) {
            missing.add("idempotencyOwner");
        }
        if ("QUANTUM_SERVICE".equals(plan.idempotencyOwner())
                && !plan.serviceInternalIdempotencyManaged()) {
            missing.add("serviceInternalIdempotency");
        }
        if (safeList(plan.reversalOwners()).isEmpty() || safeList(plan.reversalExecutions()).isEmpty()) {
            missing.add("reversalOwner");
        }
        if (!plan.negativeDeltaReversal() && !plan.serviceReversal()) {
            missing.add("reversalContract");
        }
        if (plan.objectCompensationBypassed() && !plan.serviceInternalReversalManaged()) {
            missing.add("serviceInternalReversal");
        }
        if (isBlank(plan.snapshotBoundary())) {
            missing.add("snapshotBoundary");
        }
        if (plan.objectSnapshotBypassed() && !plan.serviceInternalSnapshotManaged()) {
            missing.add("serviceInternalSnapshot");
        }
        if (isBlank(plan.recoveryOwner())) {
            missing.add("recoveryOwner");
        }
        return List.copyOf(missing);
    }

    private static List<String> quantumAccessResponsibilityActions(List<String> missingReasons) {
        if (missingReasons == null || missingReasons.isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (String reason : missingReasons) {
            switch (reason) {
                case "plan" -> actions.add("registerQuantumAccessPlan");
                case "accessContract" -> actions.add("fixQuantumAccessContract");
                case "idempotencyOwner" -> actions.add("declareQuantumIdempotencyOwner");
                case "serviceInternalIdempotency" ->
                        actions.add("idempotencyBoundary=QUANTUM_SERVICE_INTERNAL");
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

    private static final class AppSemanticMigrationSummaryPlanBuilder {
        private final String migrationType;
        private int totalCount;
        private int appDdlDerivedCount;
        private int escapeHatchCount;
        private int appDdlObjectKeyPushdownCount;
        private int referenceImplementationCount;
        private int externalImplementationCount;
        private final LinkedHashSet<String> targetKinds = new LinkedHashSet<>();
        private final LinkedHashSet<String> tableNames = new LinkedHashSet<>();
        private final LinkedHashSet<String> serviceNames = new LinkedHashSet<>();
        private final LinkedHashSet<String> objectTypes = new LinkedHashSet<>();
        private final LinkedHashSet<String> adapterNames = new LinkedHashSet<>();
        private final LinkedHashSet<String> objectKeySources = new LinkedHashSet<>();
        private final LinkedHashSet<String> objectKeyColumns = new LinkedHashSet<>();
        private final LinkedHashSet<String> externalObjectKeyRoots = new LinkedHashSet<>();
        private final LinkedHashSet<String> identityKinds = new LinkedHashSet<>();
        private final LinkedHashSet<String> participantCategories = new LinkedHashSet<>();
        private final LinkedHashSet<String> recommendations = new LinkedHashSet<>();

        private AppSemanticMigrationSummaryPlanBuilder(String migrationType) {
            this.migrationType = migrationType;
        }

        private AppSemanticMigrationSummaryPlanBuilder add(AppSemanticMigrationPlan plan) {
            totalCount++;
            if (plan.objectKeyAppDdlDerived()) {
                appDdlDerivedCount++;
            }
            if (plan.objectKeyEscapeHatch() || "REPOSITORY_ESCAPE_HATCH".equals(plan.migrationType())
                    || "SNAPSHOT_ESCAPE_HATCH".equals(plan.migrationType())) {
                escapeHatchCount++;
            }
            if (plan.appDdlObjectKeyPushdown()) {
                appDdlObjectKeyPushdownCount++;
            }
            if (plan.referenceImplementation()) {
                referenceImplementationCount++;
            }
            if (plan.externalImplementation()) {
                externalImplementationCount++;
            }
            add(targetKinds, plan.targetKind());
            add(tableNames, plan.tableName());
            add(serviceNames, plan.serviceName());
            add(objectTypes, plan.objectType());
            add(adapterNames, plan.adapterName());
            add(objectKeySources, plan.objectKeySource());
            objectKeyColumns.addAll(plan.objectKeyColumns());
            externalObjectKeyRoots.addAll(plan.externalObjectKeyRoots());
            add(identityKinds, plan.identityKind());
            participantCategories.addAll(plan.participantCategories());
            add(recommendations, plan.recommendation());
            return this;
        }

        private void add(LinkedHashSet<String> values, String value) {
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }

        private AppSemanticMigrationSummaryPlan build() {
            return new AppSemanticMigrationSummaryPlan(migrationType, totalCount, appDdlDerivedCount,
                    escapeHatchCount, appDdlObjectKeyPushdownCount, referenceImplementationCount,
                    externalImplementationCount, List.copyOf(targetKinds), List.copyOf(tableNames),
                    List.copyOf(serviceNames), List.copyOf(objectTypes), List.copyOf(adapterNames),
                    List.copyOf(objectKeySources), List.copyOf(objectKeyColumns),
                    List.copyOf(externalObjectKeyRoots), List.copyOf(identityKinds),
                    List.copyOf(participantCategories), List.copyOf(recommendations));
        }
    }

    private static boolean quantumServiceAccessPlan(QuantumAccessPlan plan) {
        return plan != null
                && safeList(plan.implementationStrategies()).contains("QUANTUM_SERVICE");
    }

    private static boolean derivedCalculationReady(GeneratedServiceMetadata item,
                                                   CalculationAssignment assignment) {
        return item != null
                && item.serviceKind() == GeneratedServiceKind.ATOMIC_CALCULATE
                && assignment != null
                && assignment.targetPath() != null
                && !assignment.targetPath().isBlank()
                && assignment.expression() != null
                && !assignment.expression().isBlank();
    }

    private static List<String> quantumServiceInternalProtocolMissingReasons(QuantumAccessPlan plan) {
        if (!quantumServiceAccessPlan(plan)) {
            return List.of();
        }
        List<String> missing = new java.util.ArrayList<>();
        if (!plan.serviceInternalIdempotencyManaged()) {
            missing.add("idempotency");
        }
        if (plan.objectSnapshotBypassed() && !plan.serviceInternalSnapshotManaged()) {
            missing.add("snapshot");
        }
        if (plan.objectCompensationBypassed() && !plan.serviceInternalReversalManaged()) {
            missing.add("reversal");
        }
        return List.copyOf(missing);
    }

    private static boolean closedCurrentSnapshotContext(AppSemanticMigrationPlan plan) {
        return plan != null
                && "CURRENT_SNAPSHOT_CONTEXT".equals(plan.migrationType())
                && plan.objectKeyAppDdlDerived()
                && plan.appDdlObjectKeyPushdown()
                && !plan.objectKeyEscapeHatch();
    }

    private static String currentSnapshotContextKey(AppSemanticMigrationPlan plan) {
        if (plan == null) {
            return "";
        }
        return valueOrBlank(plan.serviceName()) + ":" + valueOrBlank(plan.objectType());
    }

    private static String valueOrBlank(String value) {
        return value == null ? "" : value;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static <T> Map<String, List<T>> immutableListMap(Map<String, List<T>> values) {
        Map<String, List<T>> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, List<T>> entry : values.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    private static Map<String, Integer> strictErrorCountMap(Map<String, List<String>> values) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : values.entrySet()) {
            counts.put(entry.getKey(), safeList(entry.getValue()).size());
        }
        return Map.copyOf(counts);
    }

    private static Map<String, List<String>> immutableStringListMap(Map<String, List<String>> values) {
        Map<String, List<String>> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : values.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    private String physicalSourceSelectionContract(List<String> all,
                                                   List<String> shared,
                                                   List<String> profile) {
        return "profile=" + blankAsDash(physicalRuntime.physicalProfile())
                + "+explicit=" + physicalRuntime.physicalProfileExplicit()
                + "+shared=" + listContractPart(shared)
                + "+profileFiles=" + listContractPart(profile)
                + "+all=" + listContractPart(all)
                + ":PHYSICAL_SOURCE_SELECTION_READY";
    }

    private static List<String> physicalSourceSelectionEvidence(List<String> all,
                                                                List<String> shared,
                                                                List<String> profile) {
        List<String> evidence = new ArrayList<>();
        for (int index = 0; index < all.size(); index++) {
            String sourceFile = all.get(index);
            String role = profile.contains(sourceFile) ? "PROFILE" : "SHARED";
            evidence.add(index + ":" + sourceFile + ":" + role);
        }
        for (String sourceFile : shared) {
            evidence.add("shared=" + sourceFile);
        }
        for (String sourceFile : profile) {
            evidence.add("profile=" + sourceFile);
        }
        return List.copyOf(evidence);
    }

    private static String physicalProfileStrictContract(Map<String, String> metadata,
                                                        List<String> configuredKeys,
                                                        List<String> enabledKeys,
                                                        List<String> disabledKeys) {
        return "profile=" + metadataValue(metadata, "physicalProfile")
                + "+explicit=" + metadataValue(metadata, "physicalProfileExplicit")
                + "+shared=" + listContractPart(csvValues(metadata.get("sharedPhysicalSourceFiles")))
                + "+profileFiles=" + listContractPart(csvValues(metadata.get("profilePhysicalSourceFiles")))
                + "+configured=" + listContractPart(configuredKeys)
                + "+enabled=" + listContractPart(enabledKeys)
                + "+disabled=" + listContractPart(disabledKeys)
                + ":PHYSICAL_PROFILE_STRICT_READY";
    }

    private static List<String> physicalProfileStrictContractEvidence(Map<String, String> metadata,
                                                                      List<String> configuredBindings,
                                                                      List<String> effectiveBindings) {
        List<String> evidence = new ArrayList<>();
        evidence.add("profile=" + metadataValue(metadata, "physicalProfile"));
        evidence.add("explicit=" + metadataValue(metadata, "physicalProfileExplicit"));
        for (String sourceFile : csvValues(metadata.get("physicalSourceFiles"))) {
            evidence.add("sourceFile=" + sourceFile);
        }
        for (String binding : configuredBindings) {
            evidence.add("configured:" + binding);
        }
        for (String binding : effectiveBindings) {
            evidence.add("effective:" + binding);
        }
        return List.copyOf(evidence);
    }

    private static String metadataValue(Map<String, String> metadata, String key) {
        String value = metadata.get(key);
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private static String blankAsDash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private static String listContractPart(List<String> values) {
        return values == null || values.isEmpty()
                ? "-"
                : String.join("+", values);
    }

    private static List<String> csvValues(String values) {
        if (values == null || values.isBlank()) {
            return List.of();
        }
        List<String> parsed = new ArrayList<>();
        for (String value : values.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                parsed.add(trimmed);
            }
        }
        return List.copyOf(parsed);
    }

    private static boolean physicalSourceFileMatchesProfile(String sourceFile, String physicalProfile) {
        if (sourceFile == null || sourceFile.isBlank()
                || physicalProfile == null || physicalProfile.isBlank()) {
            return false;
        }
        String normalizedSourceFile = sourceFile.trim();
        String normalizedProfile = physicalProfile.trim();
        return normalizedSourceFile.equals(normalizedProfile + ".phy")
                || normalizedSourceFile.endsWith("." + normalizedProfile + ".phy");
    }
}
