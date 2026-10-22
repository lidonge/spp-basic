package com.servpp.service.runtime;

import java.util.List;

public record AppSemanticMigrationSummaryPlan(String migrationType,
                                              int totalCount,
                                              int appDdlDerivedCount,
                                              int escapeHatchCount,
                                              int appDdlObjectKeyPushdownCount,
                                              int referenceImplementationCount,
                                              int externalImplementationCount,
                                              List<String> targetKinds,
                                              List<String> tableNames,
                                              List<String> serviceNames,
                                              List<String> objectTypes,
                                              List<String> adapterNames,
                                              List<String> objectKeySources,
                                              List<String> objectKeyColumns,
                                              List<String> externalObjectKeyRoots,
                                              List<String> identityKinds,
                                              List<String> participantCategories,
                                              List<String> recommendations) {
    public AppSemanticMigrationSummaryPlan {
        targetKinds = targetKinds == null ? List.of() : List.copyOf(targetKinds);
        tableNames = tableNames == null ? List.of() : List.copyOf(tableNames);
        serviceNames = serviceNames == null ? List.of() : List.copyOf(serviceNames);
        objectTypes = objectTypes == null ? List.of() : List.copyOf(objectTypes);
        adapterNames = adapterNames == null ? List.of() : List.copyOf(adapterNames);
        objectKeySources = objectKeySources == null ? List.of() : List.copyOf(objectKeySources);
        objectKeyColumns = objectKeyColumns == null ? List.of() : List.copyOf(objectKeyColumns);
        externalObjectKeyRoots = externalObjectKeyRoots == null ? List.of() : List.copyOf(externalObjectKeyRoots);
        identityKinds = identityKinds == null ? List.of() : List.copyOf(identityKinds);
        participantCategories = participantCategories == null ? List.of() : List.copyOf(participantCategories);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
    }
}
