package com.servpp.service.runtime;

import java.util.List;

import com.servpp.service.transaction.TransactionMode;

public record DerivedCalculationPlan(String serviceName,
                                     GeneratedServiceKind serviceKind,
                                     TransactionMode transactionMode,
                                     String transactionResourceId,
                                     String targetPath,
                                     List<String> sourcePaths,
                                     String expression,
                                     List<String> serviceParameterTypes,
                                     List<String> serviceParameterNames,
                                     String readiness,
                                     List<String> readyReasons,
                                     List<String> missingReasons,
                                     List<String> actions,
                                     String executionContract) {
    public DerivedCalculationPlan {
        sourcePaths = sourcePaths == null ? List.of() : List.copyOf(sourcePaths);
        serviceParameterTypes = serviceParameterTypes == null ? List.of() : List.copyOf(serviceParameterTypes);
        serviceParameterNames = serviceParameterNames == null ? List.of() : List.copyOf(serviceParameterNames);
        readyReasons = readyReasons == null ? List.of() : List.copyOf(readyReasons);
        missingReasons = missingReasons == null ? List.of() : List.copyOf(missingReasons);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
