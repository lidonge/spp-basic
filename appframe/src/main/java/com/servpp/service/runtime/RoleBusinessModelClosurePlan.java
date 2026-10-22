package com.servpp.service.runtime;

import java.util.ArrayList;
import java.util.List;

public record RoleBusinessModelClosurePlan(String roleType,
                                           String roleTableName,
                                           String participationKind,
                                           List<String> performerTypes,
                                           List<String> platformColumns,
                                           boolean performerDeclared,
                                           boolean roleParticipantIdentityComplete,
                                           boolean businessFieldsOnly,
                                           boolean closed,
                                           String readiness,
                                           List<String> openReasons,
                                           List<String> actions,
                                           String roleModelContract) {
    public RoleBusinessModelClosurePlan {
        roleType = normalize(roleType);
        roleTableName = normalize(roleTableName);
        participationKind = normalize(participationKind);
        performerTypes = normalizeList(performerTypes);
        platformColumns = normalizeList(platformColumns);
        readiness = normalize(readiness);
        openReasons = normalizeList(openReasons);
        actions = normalizeList(actions);
        roleModelContract = normalize(roleModelContract);
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
