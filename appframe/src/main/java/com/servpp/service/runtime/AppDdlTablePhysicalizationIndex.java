package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record AppDdlTablePhysicalizationIndex(
        List<AppDdlTablePhysicalizationPlan> plans,
        Map<String, List<AppDdlTablePhysicalizationPlan>> byReadiness,
        Map<String, List<AppDdlTablePhysicalizationPlan>> byCategory,
        Map<String, List<AppDdlTablePhysicalizationPlan>> byBusinessType,
        Map<String, List<AppDdlTablePhysicalizationPlan>> byRepository,
        Map<String, List<AppDdlTablePhysicalizationPlan>> byMissingReason,
        Map<String, List<AppDdlTablePhysicalizationPlan>> byAction) {
}
