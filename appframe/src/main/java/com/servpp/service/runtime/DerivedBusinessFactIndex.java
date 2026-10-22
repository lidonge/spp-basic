package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record DerivedBusinessFactIndex(
        List<DerivedBusinessFactPlan> plans,
        Map<String, List<DerivedBusinessFactPlan>> byFactKind,
        Map<String, List<DerivedBusinessFactPlan>> byTargetOwnerType,
        Map<String, List<DerivedBusinessFactPlan>> byTargetOwnerCategory,
        Map<String, List<DerivedBusinessFactPlan>> byTargetPath,
        Map<String, List<DerivedBusinessFactPlan>> bySourcePath,
        Map<String, List<DerivedBusinessFactPlan>> bySourceOwnerType,
        Map<String, List<DerivedBusinessFactPlan>> byService,
        Map<String, List<DerivedBusinessFactPlan>> byReadiness,
        Map<String, List<DerivedBusinessFactPlan>> byReadyReason,
        Map<String, List<DerivedBusinessFactPlan>> byMissingReason,
        Map<String, List<DerivedBusinessFactPlan>> byAction,
        Map<String, List<DerivedBusinessFactPlan>> byExpression,
        Map<String, List<DerivedBusinessFactPlan>> byTransactionResource,
        Map<String, List<DerivedBusinessFactPlan>> byBusinessFactKindSet,
        Map<String, List<DerivedBusinessFactPlan>> byBusinessFactContract) {
}
