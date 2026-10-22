package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record RepositoryReplacementReadinessIndex(
        List<RepositoryQueryPlan> plans,
        Map<String, List<RepositoryQueryPlan>> byReadiness,
        Map<String, List<RepositoryQueryPlan>> byReadyReason,
        Map<String, List<RepositoryQueryPlan>> byMissingReason,
        Map<String, List<RepositoryQueryPlan>> byAction,
        Map<String, List<RepositoryQueryPlan>> bySource,
        Map<String, List<RepositoryQueryPlan>> byRepository,
        Map<String, List<RepositoryQueryPlan>> byAccessContract) {
}
