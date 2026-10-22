package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record DeploymentValidationPlan(String category,
                                       boolean passed,
                                       int errorCount,
                                       List<String> errors,
                                       Map<String, String> metadata) {
    public DeploymentValidationPlan {
        errors = errors == null ? List.of() : List.copyOf(errors);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public DeploymentValidationPlan(String category, boolean passed, int errorCount, List<String> errors) {
        this(category, passed, errorCount, errors, Map.of());
    }
}
