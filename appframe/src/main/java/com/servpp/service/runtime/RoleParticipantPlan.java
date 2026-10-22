package com.servpp.service.runtime;

import java.util.ArrayList;
import java.util.List;

public record RoleParticipantPlan(String roleType,
                                  String roleTableName,
                                  String mapperName,
                                  List<String> performerTypes,
                                  List<String> performerKeyPaths,
                                  List<String> roleKeyPaths,
                                  List<String> roleSourceKeyPaths,
                                  List<String> compatRoleKeyPaths,
                                  List<String> platformColumns,
                                  boolean performerKeyComplete,
                                  boolean roleKeyComplete,
                                  boolean complete,
                                  int performerCount,
                                  boolean jointPerformer,
                                  String participationKind,
                                  String binding,
                                  String readiness,
                                  List<String> readyReasons,
                                  List<String> missingReasons,
                                  List<String> actions,
                                  String identityReadiness,
                                  String performerTypeSet,
                                  String platformColumnSet) {
    public RoleParticipantPlan {
        roleType = normalize(roleType);
        roleTableName = normalize(roleTableName);
        mapperName = normalize(mapperName);
        performerTypes = normalizeList(performerTypes);
        performerKeyPaths = normalizeList(performerKeyPaths);
        roleKeyPaths = normalizeList(roleKeyPaths);
        roleSourceKeyPaths = normalizeList(roleSourceKeyPaths);
        compatRoleKeyPaths = normalizeList(compatRoleKeyPaths);
        platformColumns = normalizeList(platformColumns);
        performerCount = performerCount <= 0 ? performerTypes.size() : performerCount;
        jointPerformer = jointPerformer || performerCount > 1;
        participationKind = participationKind == null || participationKind.isBlank()
                ? (jointPerformer ? "JOINT_PERFORMER" : "SINGLE_PERFORMER")
                : participationKind.trim();
        binding = normalize(binding);
        readiness = normalize(readiness);
        readyReasons = normalizeList(readyReasons);
        missingReasons = normalizeList(missingReasons);
        actions = normalizeList(actions);
        identityReadiness = normalize(identityReadiness);
        performerTypeSet = normalize(performerTypeSet);
        platformColumnSet = normalize(platformColumnSet);
    }

    public RoleParticipantPlan(String roleType,
                               String roleTableName,
                               String mapperName,
                               List<String> performerTypes,
                               List<String> performerKeyPaths,
                               List<String> roleKeyPaths,
                               List<String> roleSourceKeyPaths,
                               List<String> compatRoleKeyPaths,
                               List<String> platformColumns,
                               boolean performerKeyComplete,
                               boolean roleKeyComplete,
                               boolean complete,
                               int performerCount,
                               boolean jointPerformer,
                               String participationKind,
                               String binding) {
        this(roleType, roleTableName, mapperName, performerTypes, performerKeyPaths,
                roleKeyPaths, roleSourceKeyPaths, compatRoleKeyPaths, platformColumns,
                performerKeyComplete, roleKeyComplete, complete, performerCount, jointPerformer,
                participationKind, binding, "", List.of(), List.of(), List.of(), "", "", "");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            normalized.add(normalize(value));
        }
        return List.copyOf(normalized);
    }
}
