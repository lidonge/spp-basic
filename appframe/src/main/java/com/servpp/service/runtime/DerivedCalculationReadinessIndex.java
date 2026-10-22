package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record DerivedCalculationReadinessIndex(
        List<DerivedCalculationPlan> plans,
        Map<String, List<DerivedCalculationPlan>> byReadiness,
        Map<String, List<DerivedCalculationPlan>> byReadyReason,
        Map<String, List<DerivedCalculationPlan>> byService,
        Map<String, List<DerivedCalculationPlan>> byTargetPath,
        Map<String, List<DerivedCalculationPlan>> bySourcePath,
        Map<String, List<DerivedCalculationPlan>> byExpression,
        Map<String, List<DerivedCalculationPlan>> byTransactionResource,
        Map<String, List<DerivedCalculationPlan>> byExecutionContract) {
}
