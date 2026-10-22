package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record ScenarioContractContextIndex(
        List<ScenarioContractContextPlan> plans,
        Map<String, List<ScenarioContractContextPlan>> byScenario,
        Map<String, List<ScenarioContractContextPlan>> byContract,
        Map<String, List<ScenarioContractContextPlan>> byContractParameter,
        Map<String, List<ScenarioContractContextPlan>> byContractParameterPath,
        Map<String, List<ScenarioContractContextPlan>> bySignerRole,
        Map<String, List<ScenarioContractContextPlan>> bySignerRoleSet,
        Map<String, List<ScenarioContractContextPlan>> bySigningContract,
        Map<String, List<ScenarioContractContextPlan>> byExecutionContract,
        Map<String, List<ScenarioContractContextPlan>> byReadiness,
        Map<String, List<ScenarioContractContextPlan>> byReadyReason,
        Map<String, List<ScenarioContractContextPlan>> byMissingReason,
        Map<String, List<ScenarioContractContextPlan>> byAction) {
}
