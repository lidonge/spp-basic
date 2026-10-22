package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record QuantumConservationPlanIndex(
        List<QuantumConservationPlan> plans,
        Map<String, List<QuantumConservationPlan>> byReadiness,
        Map<String, List<QuantumConservationPlan>> byReadyReason,
        Map<String, List<QuantumConservationPlan>> byMissingReason,
        Map<String, List<QuantumConservationPlan>> byAction,
        Map<String, List<QuantumConservationPlan>> byResource,
        Map<String, List<QuantumConservationPlan>> byService,
        Map<String, List<QuantumConservationPlan>> byAttribute,
        Map<String, List<QuantumConservationPlan>> byAttributeSet,
        Map<String, List<QuantumConservationPlan>> byConservationContract,
        Map<String, List<QuantumConservationPlan>> byImplementationStrategySet,
        Map<String, List<QuantumConservationPlan>> byIdempotencyOwner,
        Map<String, List<QuantumConservationPlan>> byReversalOwner,
        Map<String, List<QuantumConservationPlan>> byRecoveryOwner,
        Map<String, List<QuantumConservationPlan>> byRuntimeResponsibilityContract) {
}
