package com.servpp.service.runtime;

import java.util.List;

public record ScenarioContractContextPlan(String scenarioName,
                                          String contractType,
                                          String contractTableName,
                                          String contractParameterName,
                                          String contractParameterKey,
                                          List<String> signerRoleTypes,
                                          List<String> signerParameterNames,
                                          List<String> signerPaths,
                                          int signerCount,
                                          boolean roleParticipantDefined,
                                          boolean signedByRole,
                                          boolean contextComplete,
                                          String readiness,
                                          List<String> readyReasons,
                                          List<String> missingReasons,
                                          String action,
                                          String recommendation,
                                          List<String> actions,
                                          String signerRoleSet,
                                          String signingContract,
                                          String executionContract) {
    public ScenarioContractContextPlan(String scenarioName,
                                       String contractType,
                                       String contractTableName,
                                       String contractParameterName,
                                       String contractParameterKey,
                                       List<String> signerRoleTypes,
                                       List<String> signerParameterNames,
                                       List<String> signerPaths,
                                       int signerCount,
                                       boolean roleParticipantDefined,
                                       boolean signedByRole,
                                       boolean contextComplete,
                                       String readiness,
                                       List<String> readyReasons,
                                       List<String> missingReasons,
                                       String action,
                                       String recommendation) {
        this(scenarioName,
                contractType,
                contractTableName,
                contractParameterName,
                contractParameterKey,
                signerRoleTypes,
                signerParameterNames,
                signerPaths,
                signerCount,
                roleParticipantDefined,
                signedByRole,
                contextComplete,
                readiness,
                readyReasons,
                missingReasons,
                action,
                recommendation,
                action == null || action.isBlank() ? List.of() : List.of(action),
                "",
                "",
                "");
    }

    public ScenarioContractContextPlan {
        signerRoleTypes = signerRoleTypes == null ? List.of() : List.copyOf(signerRoleTypes);
        signerParameterNames = signerParameterNames == null ? List.of() : List.copyOf(signerParameterNames);
        signerPaths = signerPaths == null ? List.of() : List.copyOf(signerPaths);
        readyReasons = readyReasons == null ? List.of() : List.copyOf(readyReasons);
        missingReasons = missingReasons == null ? List.of() : List.copyOf(missingReasons);
        actions = actions == null ? List.of() : List.copyOf(actions);
        if (actions.isEmpty() && action != null && !action.isBlank()) {
            actions = List.of(action);
        }
        if (signerRoleSet == null || signerRoleSet.isBlank()) {
            signerRoleSet = signerRoleSet(signerRoleTypes);
        }
        if (signingContract == null || signingContract.isBlank()) {
            signingContract = contractKey(contractType, signerRoleSet, readiness);
        }
        if (executionContract == null || executionContract.isBlank()) {
            executionContract = contractKey(key(contractParameterKey) + ":" + key(contractType),
                    signerRoleSet,
                    readiness);
        }
    }

    private static String contractKey(String leftSide, String signerRoleSet, String readiness) {
        return key(leftSide) + "<-" + key(signerRoleSet) + ":" + key(readiness);
    }

    private static String signerRoleSet(List<String> signerRoleTypes) {
        List<String> roles = signerRoleTypes == null
                ? List.of()
                : signerRoleTypes.stream()
                        .filter(role -> role != null && !role.isBlank())
                        .distinct()
                        .toList();
        return roles.isEmpty() ? "NO_SIGNER_ROLE" : String.join("+", roles);
    }

    private static String key(String value) {
        return value == null || value.isBlank() ? "UNASSIGNED" : value;
    }
}
