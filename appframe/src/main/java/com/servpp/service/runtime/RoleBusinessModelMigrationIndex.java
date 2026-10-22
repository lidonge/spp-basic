package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record RoleBusinessModelMigrationIndex(
        List<AppSemanticMigrationPlan> plans,
        Map<String, List<AppSemanticMigrationPlan>> byRole,
        Map<String, List<AppSemanticMigrationPlan>> byPath,
        Map<String, List<AppSemanticMigrationPlan>> byMigrationType,
        Map<String, List<AppSemanticMigrationPlan>> byAction,
        Map<String, List<AppSemanticMigrationPlan>> byTargetKind,
        Map<String, List<AppSemanticMigrationPlan>> byParticipantCategory,
        Map<String, List<AppSemanticMigrationPlan>> byRoleMigrationType,
        Map<String, List<AppSemanticMigrationPlan>> byRoleModelContract) {
}
