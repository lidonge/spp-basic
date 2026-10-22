package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record ContractSignerReadinessIndex(
        List<ContractSignerPlan> plans,
        Map<String, List<ContractSignerPlan>> byReadiness,
        Map<String, List<ContractSignerPlan>> byReadyReason,
        Map<String, List<ContractSignerPlan>> byMissingReason,
        Map<String, List<ContractSignerPlan>> byAction,
        Map<String, List<ContractSignerPlan>> byContract,
        Map<String, List<ContractSignerPlan>> bySignerRole,
        Map<String, List<ContractSignerPlan>> byScenario,
        Map<String, List<ContractSignerPlan>> byContractParameter,
        Map<String, List<ContractSignerPlan>> byContractParameterPath,
        Map<String, List<ContractSignerPlan>> bySignerPath,
        Map<String, List<ContractSignerPlan>> bySigningContract,
        Map<String, List<ContractSignerPlan>> bySignerCategory,
        Map<String, List<ContractSignerPlan>> byRecommendation) {
}
