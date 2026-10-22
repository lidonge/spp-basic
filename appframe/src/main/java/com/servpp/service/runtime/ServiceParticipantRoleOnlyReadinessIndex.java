package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record ServiceParticipantRoleOnlyReadinessIndex(
        List<ServiceParticipantRoleOnlyMigrationActionPlan> plans,
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byReadiness,
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byReadyReason,
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byMissingReason,
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byAction,
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byService,
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byPath,
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byParticipantCategory,
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byMigrationAction,
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byTargetRole,
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byTargetRoleParameter,
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byCandidateRole,
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byServiceParameterContract,
        Map<String, List<ServiceParticipantRoleOnlyMigrationActionPlan>> byExecutionContract) {
}
