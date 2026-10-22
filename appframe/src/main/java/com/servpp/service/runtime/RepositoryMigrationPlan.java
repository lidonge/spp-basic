package com.servpp.service.runtime;

import java.util.List;

public record RepositoryMigrationPlan(String planName,
                                      String repositoryName,
                                      String resolvedRepositoryName,
                                      String repositoryAdapterName,
                                      boolean repositoryAppDdlTables,
                                      String storageTableName,
                                      String datasourceUrl,
                                      String driverClassName,
                                      int totalQueryCount,
                                      int collectionQueryCount,
                                      int appDdlDerivedQueryCount,
                                      int escapeHatchQueryCount,
                                      int appDdlColumnPushdownCount,
                                      int crossAggregateQueryCount,
                                      int compositeKeyQueryCount,
                                      List<String> serviceNames,
                                      List<String> objectTypes,
                                      List<String> appDdlTableNames,
                                      List<String> appDdlIndexNames,
                                      List<String> repositoryLookupKinds,
                                      List<String> aggregateRelationKinds,
                                      List<String> externalAggregateRoots,
                                      List<String> appDdlDerivedServiceNames,
                                      List<String> appDdlColumnPushdownServiceNames,
                                      List<String> escapeHatchServiceNames,
                                      List<String> escapeHatchRepositoryPaths,
                                      List<String> replacementSources,
                                      String recommendation) {
    public RepositoryMigrationPlan {
        serviceNames = serviceNames == null ? List.of() : List.copyOf(serviceNames);
        objectTypes = objectTypes == null ? List.of() : List.copyOf(objectTypes);
        appDdlTableNames = appDdlTableNames == null ? List.of() : List.copyOf(appDdlTableNames);
        appDdlIndexNames = appDdlIndexNames == null ? List.of() : List.copyOf(appDdlIndexNames);
        repositoryLookupKinds = repositoryLookupKinds == null ? List.of() : List.copyOf(repositoryLookupKinds);
        aggregateRelationKinds = aggregateRelationKinds == null ? List.of() : List.copyOf(aggregateRelationKinds);
        externalAggregateRoots = externalAggregateRoots == null ? List.of() : List.copyOf(externalAggregateRoots);
        appDdlDerivedServiceNames = appDdlDerivedServiceNames == null
                ? List.of()
                : List.copyOf(appDdlDerivedServiceNames);
        appDdlColumnPushdownServiceNames = appDdlColumnPushdownServiceNames == null
                ? List.of()
                : List.copyOf(appDdlColumnPushdownServiceNames);
        escapeHatchServiceNames = escapeHatchServiceNames == null ? List.of() : List.copyOf(escapeHatchServiceNames);
        escapeHatchRepositoryPaths = escapeHatchRepositoryPaths == null
                ? List.of()
                : List.copyOf(escapeHatchRepositoryPaths);
        replacementSources = replacementSources == null ? List.of() : List.copyOf(replacementSources);
    }
}
