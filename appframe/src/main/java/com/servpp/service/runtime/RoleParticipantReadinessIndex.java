package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record RoleParticipantReadinessIndex(
        List<RoleParticipantPlan> plans,
        Map<String, List<RoleParticipantPlan>> byReadiness,
        Map<String, List<RoleParticipantPlan>> byReadyReason,
        Map<String, List<RoleParticipantPlan>> byMissingReason,
        Map<String, List<RoleParticipantPlan>> byAction,
        Map<String, List<RoleParticipantPlan>> byRole,
        Map<String, List<RoleParticipantPlan>> byParticipationKind,
        Map<String, List<RoleParticipantPlan>> byJointPerformer,
        Map<String, List<RoleParticipantPlan>> byPerformerCount,
        Map<String, List<RoleParticipantPlan>> byPerformerType,
        Map<String, List<RoleParticipantPlan>> byPerformerTypeSet,
        Map<String, List<RoleParticipantPlan>> byPlatformColumn,
        Map<String, List<RoleParticipantPlan>> byPlatformColumnSet,
        Map<String, List<RoleParticipantPlan>> byCompatRoleKey) {
}
