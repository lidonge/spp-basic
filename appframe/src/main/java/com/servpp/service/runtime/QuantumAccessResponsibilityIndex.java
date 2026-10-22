package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record QuantumAccessResponsibilityIndex(
        List<QuantumAccessPlan> plans,
        Map<String, List<QuantumAccessPlan>> byReadiness,
        Map<String, List<QuantumAccessPlan>> byMissingReason,
        Map<String, List<QuantumAccessPlan>> byAction,
        Map<String, List<QuantumAccessPlan>> byImplementationStrategy,
        Map<String, List<QuantumAccessPlan>> byImplementationStrategyShape,
        Map<String, List<QuantumAccessPlan>> byImplementationStrategySet,
        Map<String, List<QuantumAccessPlan>> byIdempotencyOwner,
        Map<String, List<QuantumAccessPlan>> byReversalOwner,
        Map<String, List<QuantumAccessPlan>> bySnapshotBoundary,
        Map<String, List<QuantumAccessPlan>> byResponsibilityBoundary,
        Map<String, List<QuantumAccessPlan>> byRecoveryOwner,
        Map<String, List<QuantumAccessPlan>> byAttribute,
        Map<String, List<QuantumAccessPlan>> byServiceAttributePath,
        Map<String, List<QuantumAccessPlan>> byAccessContract,
        Map<String, List<QuantumAccessPlan>> byServiceInternalProtocol) {
}
