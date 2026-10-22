package com.servpp.service.runtime;

import java.util.List;

import com.servpp.service.repository.ObjectSnapshotRule;
import com.servpp.service.repository.RepositoryHydratingServiceProxy.HydrationRule;
import com.servpp.service.transaction.TccReservationRule;
import com.servpp.service.transaction.TransactionMode;

public record GeneratedServiceMetadata(String serviceName,
                                       String serviceClassName,
                                       GeneratedServiceKind serviceKind,
                                       TransactionMode transactionMode,
                                       String transactionResourceId,
                                       String tccConfirmServiceName,
                                       String tccCancelServiceName,
                                       List<TccReservationRule> tccReservationRules,
                                       List<String> compensationServiceNames,
                                       List<String> remoteCompensationServiceNames,
                                       List<String> quantumAttributeIds,
                                       List<String> sagaTransactionCallServiceNames,
                                       List<String> serviceParameterTypes,
                                       List<String> serviceParameterNames,
                                       List<String> serviceContextParameterNames,
                                       List<ObjectSnapshotRule> objectSnapshotRules,
                                       List<HydrationRule> repositoryHydrationRules,
                                       boolean scopeLocal,
                                       boolean async,
                                       String inputHeaderName,
                                       String outputHeaderName,
                                       boolean idempotencyKeyRequired,
                                       String idempotencyKeyFieldName,
                                       String idempotencyKeyPath,
                                       boolean idempotencyKeyAvailable,
                                       boolean physicalBindingRequired,
                                       boolean remote,
                                       List<CalculationAssignment> calculationAssignments) {
}
