package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record DeploymentValidationReadinessIndex(
        List<DeploymentValidationPlan> plans,
        Map<String, List<DeploymentValidationPlan>> byReadiness,
        Map<String, List<DeploymentValidationPlan>> byCategory,
        Map<String, List<DeploymentValidationPlan>> byReadyReason,
        Map<String, List<DeploymentValidationPlan>> byMissingReason,
        Map<String, List<DeploymentValidationPlan>> byAction,
        Map<String, List<String>> categoriesByMetadataKey) {
}
