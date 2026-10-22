package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record IdempotencyReadinessIndex(
        List<IdempotencyPlan> plans,
        Map<String, List<IdempotencyPlan>> byReadiness,
        Map<String, List<IdempotencyPlan>> byReadyReason,
        Map<String, List<IdempotencyPlan>> byMissingReason,
        Map<String, List<IdempotencyPlan>> byAction,
        Map<String, List<IdempotencyPlan>> byService,
        Map<String, List<IdempotencyPlan>> byIdempotencyKeyPath,
        Map<String, List<IdempotencyPlan>> byOwner,
        Map<String, List<IdempotencyPlan>> byMode,
        Map<String, List<IdempotencyPlan>> byBoundary,
        Map<String, List<IdempotencyPlan>> byOwnershipBoundary,
        Map<String, List<IdempotencyPlan>> byBackend,
        Map<String, List<IdempotencyPlan>> byStore,
        Map<String, List<IdempotencyPlan>> byPhysicalRuntimePath,
        Map<String, List<IdempotencyPlan>> byExecutionContract) {
}
