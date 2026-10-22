package com.servpp.service.runtime;

import java.util.List;

public record GeneratedAppSemanticMigrationSummary(int repositoryQueryPlanCount,
                                                   int repositoryReplacementReadyCount,
                                                   int missingRepositoryReplacementReadyCount,
                                                   List<String> repositoryReplacementSources,
                                                   List<String> repositoryReplacementReadyServiceNames,
                                                   List<String> missingRepositoryReplacementReadyServiceNames) {
    public GeneratedAppSemanticMigrationSummary {
        repositoryReplacementSources = repositoryReplacementSources == null
                ? List.of()
                : List.copyOf(repositoryReplacementSources);
        repositoryReplacementReadyServiceNames = repositoryReplacementReadyServiceNames == null
                ? List.of()
                : List.copyOf(repositoryReplacementReadyServiceNames);
        missingRepositoryReplacementReadyServiceNames = missingRepositoryReplacementReadyServiceNames == null
                ? List.of()
                : List.copyOf(missingRepositoryReplacementReadyServiceNames);
    }
}
