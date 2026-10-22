package com.servpp.service.runtime;

import java.util.List;

public record ServiceParticipantRoleOnlyMigrationActionPlan(String serviceName,
                                                            String path,
                                                            String participantType,
                                                            String participantCategory,
                                                            String migrationAction,
                                                            boolean roleParticipantPresent,
                                                            boolean targetRolePresent,
                                                            List<String> targetRoleTypes,
                                                            List<String> targetRoleParameterNames,
                                                            List<String> targetRoleBindings,
                                                            List<String> candidateRoleTypes,
                                                            boolean roleOnlyReady,
                                                            List<String> readinessReasons,
                                                            String readinessReasonSummary,
                                                            String recommendation) {
    public ServiceParticipantRoleOnlyMigrationActionPlan {
        targetRoleTypes = targetRoleTypes == null ? List.of() : List.copyOf(targetRoleTypes);
        targetRoleParameterNames = targetRoleParameterNames == null
                ? List.of()
                : List.copyOf(targetRoleParameterNames);
        targetRoleBindings = targetRoleBindings == null ? List.of() : List.copyOf(targetRoleBindings);
        candidateRoleTypes = candidateRoleTypes == null ? List.of() : List.copyOf(candidateRoleTypes);
        readinessReasons = readinessReasons == null ? List.of() : List.copyOf(readinessReasons);
        readinessReasonSummary = readinessReasonSummary == null || readinessReasonSummary.isBlank()
                ? String.join("+", readinessReasons)
                : readinessReasonSummary;
    }
}
