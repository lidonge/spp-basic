package com.servpp.service.runtime;

import java.util.List;

public record RepositoryCompositionQueryPlan(String roleType,
                                             String tableName,
                                             String namedIndexName,
                                             String scenarioName,
                                             String contractType,
                                             String contractParameterKey,
                                             List<String> signerRoleTypes,
                                             String signerRoleSet,
                                             List<String> compositionColumns,
                                             List<String> rolePerformerColumns,
                                             List<String> performerTypes,
                                             String repositoryLookupKind,
                                             String compositionIdentitySource,
                                             String performerIdentitySource,
                                             boolean collectionQuery,
                                             boolean appDdlIndexCoverage,
                                             boolean roleOnlyBoundary,
                                             String readiness,
                                             List<String> readyReasons,
                                             List<String> missingReasons,
                                             List<String> actions,
                                             String queryContract,
                                             boolean executable,
                                             String executionContract) {
    public RepositoryCompositionQueryPlan {
        signerRoleTypes = signerRoleTypes == null ? List.of() : List.copyOf(signerRoleTypes);
        compositionColumns = compositionColumns == null ? List.of() : List.copyOf(compositionColumns);
        rolePerformerColumns = rolePerformerColumns == null ? List.of() : List.copyOf(rolePerformerColumns);
        performerTypes = performerTypes == null ? List.of() : List.copyOf(performerTypes);
        readyReasons = readyReasons == null ? List.of() : List.copyOf(readyReasons);
        missingReasons = missingReasons == null ? List.of() : List.copyOf(missingReasons);
        actions = actions == null ? List.of() : List.copyOf(actions);
        if (readiness == null || readiness.isBlank()) {
            readiness = missingReasons.isEmpty()
                    ? "REPOSITORY_COMPOSITION_QUERY_READY"
                    : "REPOSITORY_COMPOSITION_QUERY_INCOMPLETE";
        }
        if (queryContract == null || queryContract.isBlank()) {
            queryContract = key(roleType) + " by " + key(namedIndexName)
                    + "<-" + key(contractType) + ":" + key(signerRoleSet)
                    + ":" + key(readiness);
        }
        if (executionContract == null || executionContract.isBlank()) {
            executionContract = key(roleType)
                    + ":index=" + key(namedIndexName)
                    + ":contract=" + key(contractType)
                    + ":scenario=" + key(scenarioName)
                    + ":contractParameter=" + key(contractParameterKey)
                    + ":signers=" + key(signerRoleSet)
                    + ":lookup=" + key(repositoryLookupKind)
                    + ":composition=" + key(join(compositionColumns))
                    + ":performer=" + key(join(rolePerformerColumns))
                    + ":compositionIdentity=" + key(compositionIdentitySource)
                    + ":performerIdentity=" + key(performerIdentitySource)
                    + ":" + (executable
                    ? "REPOSITORY_COMPOSITION_QUERY_EXECUTABLE"
                    : "REPOSITORY_COMPOSITION_QUERY_NOT_EXECUTABLE");
        }
    }

    private static String key(String value) {
        return value == null || value.isBlank() ? "UNASSIGNED" : value;
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join("+", values);
    }
}
