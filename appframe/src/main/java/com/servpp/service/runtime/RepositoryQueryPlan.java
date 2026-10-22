package com.servpp.service.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.servpp.service.repository.RepositoryHydratingServiceProxy.HydrationRule;

public record RepositoryQueryPlan(String serviceName,
                                  String methodName,
                                  String objectType,
                                  String responseMethodName,
                                  boolean collectionQuery,
                                  String responseCollectionType,
                                  String responseElementType,
                                  String repositoryName,
                                  String resolvedRepositoryName,
                                  String repositoryAdapterName,
                                  boolean repositoryAppDdlTables,
                                  Map<String, String> repositoryAdapterConfiguration,
                                  String source,
                                  String appDdlTableName,
                                  String appDdlIndexName,
                                  List<String> appDdlIndexPaths,
                                  List<String> appDdlIndexColumns,
                                  boolean appDdlIndexCoverage,
                                  boolean appDdlDerived,
                                  boolean escapeHatch,
                                  List<String> argumentPaths,
                                  List<String> repositoryPaths,
                                  List<String> repositoryColumns,
                                  boolean appDdlColumnPushdown,
                                  String repositoryLookupKind,
                                  int repositoryKeyCount,
                                  boolean compositeRepositoryKey,
                                  boolean crossAggregateQuery,
                                  int crossAggregateKeyCount,
                                  List<String> externalAggregateRoots,
                                  String aggregateRelationKind,
                                  List<RepositoryQueryKeyPlan> keyPlans,
                                  List<HydrationRule> rules) {
    public RepositoryQueryPlan {
        repositoryAdapterConfiguration = repositoryAdapterConfiguration == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(repositoryAdapterConfiguration));
        appDdlIndexPaths = appDdlIndexPaths == null ? List.of() : List.copyOf(appDdlIndexPaths);
        appDdlIndexColumns = appDdlIndexColumns == null ? List.of() : List.copyOf(appDdlIndexColumns);
        argumentPaths = argumentPaths == null ? List.of() : List.copyOf(argumentPaths);
        repositoryPaths = repositoryPaths == null ? List.of() : List.copyOf(repositoryPaths);
        repositoryColumns = repositoryColumns == null ? List.of() : List.copyOf(repositoryColumns);
        externalAggregateRoots = externalAggregateRoots == null ? List.of() : List.copyOf(externalAggregateRoots);
        keyPlans = keyPlans == null ? List.of() : List.copyOf(keyPlans);
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    @JsonProperty(value = "executable", access = JsonProperty.Access.READ_ONLY)
    public boolean executable() {
        if (isBlank(serviceName) || isBlank(methodName) || isBlank(objectType)
                || isBlank(resolvedRepositoryName) || isBlank(repositoryAdapterName)
                || isBlank(repositoryLookupKind) || rules.isEmpty()) {
            return false;
        }
        if (repositoryKeyCount < 0
                || argumentPaths.size() != repositoryKeyCount
                || repositoryPaths.size() != repositoryKeyCount
                || keyPlans.size() != repositoryKeyCount) {
            return false;
        }
        return repositoryKeyCount == 0
                || !repositoryAppDdlTables
                || appDdlColumnPushdown && repositoryColumns.size() == repositoryKeyCount;
    }

    @JsonProperty(value = "executionReadiness", access = JsonProperty.Access.READ_ONLY)
    public String executionReadiness() {
        return executable() ? "REPOSITORY_QUERY_EXECUTABLE" : "REPOSITORY_QUERY_NOT_EXECUTABLE";
    }

    @JsonProperty(value = "executionContract", access = JsonProperty.Access.READ_ONLY)
    public String executionContract() {
        return "service=" + key(serviceName)
                + ":method=" + key(methodName)
                + ":object=" + key(objectType)
                + ":repository=" + key(resolvedRepositoryName)
                + ":adapter=" + key(repositoryAdapterName)
                + ":lookup=" + key(repositoryLookupKind)
                + ":arguments=" + key(join(argumentPaths))
                + ":paths=" + key(join(repositoryPaths))
                + ":columns=" + key(join(repositoryColumns))
                + ":identitySources=" + key(identitySourceSet())
                + ":relation=" + key(aggregateRelationKind)
                + ":source=" + key(source)
                + ":" + executionReadiness();
    }

    private String identitySourceSet() {
        return keyPlans.stream()
                .map(RepositoryQueryKeyPlan::identitySource)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .collect(Collectors.joining("+"));
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join("+", values);
    }

    private static String key(String value) {
        return isBlank(value) ? "UNASSIGNED" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
