package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record AppDdlCompositionIndex(
        List<AppDdlCompositionIndexPlan> plans,
        Map<String, List<AppDdlCompositionIndexPlan>> byTableName,
        Map<String, List<AppDdlCompositionIndexPlan>> byRoleType,
        Map<String, List<AppDdlCompositionIndexPlan>> byNamedIndex,
        Map<String, List<AppDdlCompositionIndexPlan>> byCompositionColumn,
        Map<String, List<AppDdlCompositionIndexPlan>> byRolePerformerColumn,
        Map<String, List<AppDdlCompositionIndexPlan>> byPerformerType,
        Map<String, List<AppDdlCompositionIndexPlan>> bySignerContextContract,
        Map<String, List<AppDdlCompositionIndexPlan>> byReadiness,
        Map<String, List<AppDdlCompositionIndexPlan>> byReadyReason,
        Map<String, List<AppDdlCompositionIndexPlan>> byMissingReason,
        Map<String, List<AppDdlCompositionIndexPlan>> byAction,
        Map<String, List<AppDdlCompositionIndexPlan>> byCompositionContract) {
}
