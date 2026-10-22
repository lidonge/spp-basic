package com.servpp.service.runtime;

import java.util.List;

public record AppDdlCompositionIndexPlan(String tableName,
                                         String roleType,
                                         String namedIndexName,
                                         List<String> indexColumns,
                                         List<String> primaryKeyColumns,
                                         List<String> compositionColumns,
                                         List<String> rolePerformerColumns,
                                         List<String> performerTypes,
                                         List<String> signerContextContracts,
                                         String readiness,
                                         List<String> readyReasons,
                                         List<String> missingReasons,
                                         List<String> actions,
                                         String compositionContract) {
    public AppDdlCompositionIndexPlan {
        indexColumns = indexColumns == null ? List.of() : List.copyOf(indexColumns);
        primaryKeyColumns = primaryKeyColumns == null ? List.of() : List.copyOf(primaryKeyColumns);
        compositionColumns = compositionColumns == null ? List.of() : List.copyOf(compositionColumns);
        rolePerformerColumns = rolePerformerColumns == null ? List.of() : List.copyOf(rolePerformerColumns);
        performerTypes = performerTypes == null ? List.of() : List.copyOf(performerTypes);
        signerContextContracts = signerContextContracts == null ? List.of() : List.copyOf(signerContextContracts);
        readyReasons = readyReasons == null ? List.of() : List.copyOf(readyReasons);
        missingReasons = missingReasons == null ? List.of() : List.copyOf(missingReasons);
        actions = actions == null ? List.of() : List.copyOf(actions);
        if (readiness == null || readiness.isBlank()) {
            readiness = missingReasons.isEmpty()
                    ? "COMPOSITION_INDEX_READY"
                    : "COMPOSITION_INDEX_INCOMPLETE";
        }
        if (compositionContract == null || compositionContract.isBlank()) {
            compositionContract = key(roleType) + ":" + key(namedIndexName)
                    + ":composition=" + key(String.join("+", compositionColumns))
                    + ":performer=" + key(String.join("+", rolePerformerColumns))
                    + ":" + key(readiness);
        }
    }

    private static String key(String value) {
        return value == null || value.isBlank() ? "UNASSIGNED" : value;
    }
}
