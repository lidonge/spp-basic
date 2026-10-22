package com.servpp.service.runtime;

import java.util.List;

public record CurrentSnapshotContextPlan(String sagaServiceName,
                                         String targetServiceName,
                                         String objectType,
                                         List<String> objectIdPaths,
                                         List<Integer> candidateParameterIndexes,
                                         List<String> candidateParameterTypes,
                                         List<String> candidateParameterNames,
                                         String contextObjectSource,
                                         List<String> appDdlObjectKeyPaths,
                                         List<String> objectKeyColumns,
                                         String objectKeySource,
                                         String objectKeyAppDdlIndexName,
                                         boolean objectKeyAppDdlDerived,
                                         boolean appDdlObjectKeyPushdown,
                                         boolean resolvedFromSagaParameter,
                                         boolean ambiguousSagaParameter,
                                         String readiness,
                                         List<String> readyReasons,
                                         List<String> missingReasons,
                                         List<String> actions,
                                         String contextPath,
                                         String closureContract) {
    public CurrentSnapshotContextPlan(String sagaServiceName,
                                      String targetServiceName,
                                      String objectType,
                                      List<String> objectIdPaths,
                                      List<Integer> candidateParameterIndexes,
                                      List<String> candidateParameterTypes,
                                      List<String> candidateParameterNames,
                                      String contextObjectSource,
                                      List<String> appDdlObjectKeyPaths,
                                      List<String> objectKeyColumns,
                                      String objectKeySource,
                                      String objectKeyAppDdlIndexName,
                                      boolean objectKeyAppDdlDerived,
                                      boolean appDdlObjectKeyPushdown,
                                      boolean resolvedFromSagaParameter,
                                      boolean ambiguousSagaParameter) {
        this(sagaServiceName, targetServiceName, objectType, objectIdPaths, candidateParameterIndexes,
                candidateParameterTypes, candidateParameterNames, contextObjectSource, appDdlObjectKeyPaths,
                objectKeyColumns, objectKeySource, objectKeyAppDdlIndexName, objectKeyAppDdlDerived,
                appDdlObjectKeyPushdown, resolvedFromSagaParameter, ambiguousSagaParameter,
                "", List.of(), List.of(), List.of(), "", "");
    }

    public CurrentSnapshotContextPlan {
        objectIdPaths = objectIdPaths == null ? List.of() : List.copyOf(objectIdPaths);
        candidateParameterIndexes = candidateParameterIndexes == null
                ? List.of()
                : List.copyOf(candidateParameterIndexes);
        candidateParameterTypes = candidateParameterTypes == null ? List.of() : List.copyOf(candidateParameterTypes);
        candidateParameterNames = candidateParameterNames == null ? List.of() : List.copyOf(candidateParameterNames);
        appDdlObjectKeyPaths = appDdlObjectKeyPaths == null ? List.of() : List.copyOf(appDdlObjectKeyPaths);
        objectKeyColumns = objectKeyColumns == null ? List.of() : List.copyOf(objectKeyColumns);
        readyReasons = readyReasons == null ? List.of() : List.copyOf(readyReasons);
        missingReasons = missingReasons == null ? List.of() : List.copyOf(missingReasons);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
