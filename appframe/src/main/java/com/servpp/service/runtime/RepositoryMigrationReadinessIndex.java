package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record RepositoryMigrationReadinessIndex(
        List<RepositoryMigrationPlan> plans,
        Map<String, List<RepositoryMigrationPlan>> byReadiness,
        Map<String, List<RepositoryMigrationPlan>> byReadyReason,
        Map<String, List<RepositoryMigrationPlan>> byMissingReason,
        Map<String, List<RepositoryMigrationPlan>> byAction,
        Map<String, List<RepositoryMigrationPlan>> byRepository,
        Map<String, List<RepositoryMigrationPlan>> byReplacementSource,
        Map<String, List<RepositoryMigrationPlan>> byRecommendation,
        Map<String, List<RepositoryMigrationPlan>> byLookupKind,
        Map<String, List<RepositoryMigrationPlan>> byAggregateRelation,
        Map<String, List<RepositoryMigrationPlan>> byMigrationContract) {
}
