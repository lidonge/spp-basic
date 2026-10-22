package com.servpp.service.runtime;

import java.util.List;

public record ContractSignerPlan(String contractType,
                                 String contractTableName,
                                 String scenarioName,
                                 String contractParameterName,
                                 String signerPath,
                                 String signerFieldName,
                                 String signerType,
                                 String signerCategory,
                                 boolean roleParticipantDefined,
                                 boolean signedByRole,
                                 boolean migrationRequired,
                                 String recommendation,
                                 String readiness,
                                 List<String> readyReasons,
                                 List<String> missingReasons,
                                 List<String> actions,
                                 String signingContract) {
    public ContractSignerPlan {
        readyReasons = readyReasons == null ? List.of() : List.copyOf(readyReasons);
        missingReasons = missingReasons == null ? List.of() : List.copyOf(missingReasons);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
