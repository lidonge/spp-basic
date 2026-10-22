package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record RepositoryCompositionQueryIndex(
        List<RepositoryCompositionQueryPlan> plans,
        Map<String, List<RepositoryCompositionQueryPlan>> byRoleType,
        Map<String, List<RepositoryCompositionQueryPlan>> byNamedIndex,
        Map<String, List<RepositoryCompositionQueryPlan>> byContract,
        Map<String, List<RepositoryCompositionQueryPlan>> byScenario,
        Map<String, List<RepositoryCompositionQueryPlan>> bySignerRoleSet,
        Map<String, List<RepositoryCompositionQueryPlan>> byCompositionColumn,
        Map<String, List<RepositoryCompositionQueryPlan>> byRolePerformerColumn,
        Map<String, List<RepositoryCompositionQueryPlan>> byReadiness,
        Map<String, List<RepositoryCompositionQueryPlan>> byReadyReason,
        Map<String, List<RepositoryCompositionQueryPlan>> byMissingReason,
        Map<String, List<RepositoryCompositionQueryPlan>> byAction,
        Map<String, List<RepositoryCompositionQueryPlan>> byQueryContract,
        Map<String, List<RepositoryCompositionQueryPlan>> byExecutableReadiness,
        Map<String, List<RepositoryCompositionQueryPlan>> byExecutionContract) {
}
