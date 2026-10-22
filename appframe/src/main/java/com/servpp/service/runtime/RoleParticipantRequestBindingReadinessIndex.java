package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record RoleParticipantRequestBindingReadinessIndex(
        List<RoleParticipantRequestBindingPlan> plans,
        Map<String, List<RoleParticipantRequestBindingPlan>> byReadiness,
        Map<String, List<RoleParticipantRequestBindingPlan>> byReadyReason,
        Map<String, List<RoleParticipantRequestBindingPlan>> byMissingReason,
        Map<String, List<RoleParticipantRequestBindingPlan>> byAction,
        Map<String, List<RoleParticipantRequestBindingPlan>> byService,
        Map<String, List<RoleParticipantRequestBindingPlan>> byRole,
        Map<String, List<RoleParticipantRequestBindingPlan>> byRequest,
        Map<String, List<RoleParticipantRequestBindingPlan>> byParameterPath,
        Map<String, List<RoleParticipantRequestBindingPlan>> byBindingContract,
        Map<String, List<RoleParticipantRequestBindingPlan>> byIdentityBindingSource,
        Map<String, List<RoleParticipantRequestBindingPlan>> bySidecarField,
        Map<String, List<RoleParticipantRequestBindingPlan>> byMissingSidecarField,
        Map<String, List<RoleParticipantRequestBindingPlan>> byPerformerType) {
}
