package com.servpp.service.runtime;

import java.util.List;

public record RemoteReferenceRoleAdapterPlan(String serviceName,
                                             String path,
                                             String participantType,
                                             String participantCategory,
                                             String remoteAdapterName,
                                             String remoteAdapterRole,
                                             boolean adapterExecutable,
                                             boolean referenceImplementation,
                                             boolean externalImplementation,
                                             boolean roleAdapterDeclared,
                                             String roleType,
                                             String roleAdapterProtocol,
                                             boolean physicalRoleAdapterReady,
                                             List<String> readinessReasons,
                                             String readinessReasonSummary,
                                             String binding) {
    public RemoteReferenceRoleAdapterPlan {
        readinessReasons = readinessReasons == null ? List.of() : List.copyOf(readinessReasons);
        readinessReasonSummary = readinessReasonSummary == null || readinessReasonSummary.isBlank()
                ? String.join("+", readinessReasons)
                : readinessReasonSummary;
    }
}
