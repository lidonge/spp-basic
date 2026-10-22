package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record ContractSignerFieldMigrationIndex(
        List<AppSemanticMigrationPlan> generatedPlans,
        List<AppSemanticMigrationPlan> activeGeneratedPlans,
        List<AppSemanticMigrationPlan> suppressedGeneratedPlans,
        Map<String, List<AppSemanticMigrationPlan>> bySourceClass,
        Map<String, List<AppSemanticMigrationPlan>> bySuppressedReason,
        Map<String, List<AppSemanticMigrationPlan>> byContract,
        Map<String, List<AppSemanticMigrationPlan>> byTable,
        Map<String, List<AppSemanticMigrationPlan>> byObjectType,
        Map<String, List<AppSemanticMigrationPlan>> byPath,
        Map<String, List<AppSemanticMigrationPlan>> byParticipantCategory,
        Map<String, List<AppSemanticMigrationPlan>> byExternalObjectKeyRoot) {
}
