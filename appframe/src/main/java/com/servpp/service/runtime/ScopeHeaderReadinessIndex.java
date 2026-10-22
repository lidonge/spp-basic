package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record ScopeHeaderReadinessIndex(
        List<ScopeHeaderPlan> plans,
        Map<String, List<ScopeHeaderPlan>> byReadiness,
        Map<String, List<ScopeHeaderPlan>> byReadyReason,
        Map<String, List<ScopeHeaderPlan>> byMissingReason,
        Map<String, List<ScopeHeaderPlan>> byAction,
        Map<String, List<ScopeHeaderPlan>> byScope,
        Map<String, List<ScopeHeaderPlan>> byService,
        Map<String, List<ScopeHeaderPlan>> byScopeKind,
        Map<String, List<ScopeHeaderPlan>> byBoundary,
        Map<String, List<ScopeHeaderPlan>> byPhysicalBackend,
        Map<String, List<ScopeHeaderPlan>> byIdempotencyBackend,
        Map<String, List<ScopeHeaderPlan>> byPhysicalBinding,
        Map<String, List<ScopeHeaderPlan>> byIdempotencyBinding) {
}
