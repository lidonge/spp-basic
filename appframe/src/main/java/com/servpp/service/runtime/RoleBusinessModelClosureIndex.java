package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record RoleBusinessModelClosureIndex(
        List<RoleBusinessModelClosurePlan> plans,
        Map<String, List<RoleBusinessModelClosurePlan>> byReadiness,
        Map<String, List<RoleBusinessModelClosurePlan>> byOpenReason,
        Map<String, List<RoleBusinessModelClosurePlan>> byAction,
        Map<String, List<RoleBusinessModelClosurePlan>> byRole,
        Map<String, List<RoleBusinessModelClosurePlan>> byParticipationKind,
        Map<String, List<RoleBusinessModelClosurePlan>> byPerformerTypeSet,
        Map<String, List<RoleBusinessModelClosurePlan>> byRoleModelContract) {
}
