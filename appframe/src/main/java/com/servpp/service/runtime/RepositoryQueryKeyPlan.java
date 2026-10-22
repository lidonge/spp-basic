package com.servpp.service.runtime;

public record RepositoryQueryKeyPlan(int argumentIndex,
                                     String argumentTypeName,
                                     String argumentPath,
                                     String repositoryPath,
                                     String repositoryPathRoot,
                                     String identitySource,
                                     String runtimeIdentityPath,
                                     String repositoryColumn,
                                     boolean crossAggregateKey,
                                     boolean appDdlColumnPushdown) {
}
