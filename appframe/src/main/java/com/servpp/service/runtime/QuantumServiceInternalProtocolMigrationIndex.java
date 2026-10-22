package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record QuantumServiceInternalProtocolMigrationIndex(
        List<AppSemanticMigrationPlan> plans,
        Map<String, List<AppSemanticMigrationPlan>> byService,
        Map<String, List<AppSemanticMigrationPlan>> bySource,
        Map<String, List<AppSemanticMigrationPlan>> byAttributePath,
        Map<String, List<AppSemanticMigrationPlan>> bySnapshotBoundary,
        Map<String, List<AppSemanticMigrationPlan>> byRecoveryRole,
        Map<String, List<AppSemanticMigrationPlan>> byAdapter) {
}
