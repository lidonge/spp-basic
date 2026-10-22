package com.servpp.service.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class RoleBusinessModelClosureSupport {
    private RoleBusinessModelClosureSupport() {
    }

    static List<RoleBusinessModelClosurePlan> plans(List<RoleParticipantPlan> roleParticipantPlans,
                                                    List<AppSemanticMigrationPlan> migrationPlans) {
        Map<String, List<RoleParticipantPlan>> participantPlansByRole = new LinkedHashMap<>();
        for (RoleParticipantPlan plan : safeList(roleParticipantPlans)) {
            if (plan == null || isBlank(plan.roleType())) {
                continue;
            }
            participantPlansByRole.computeIfAbsent(plan.roleType(), ignored -> new ArrayList<>()).add(plan);
        }
        Map<String, List<AppSemanticMigrationPlan>> migrationPlansByRole = new LinkedHashMap<>();
        for (AppSemanticMigrationPlan plan : safeList(migrationPlans)) {
            if (plan == null || !roleBusinessModelMigrationType(plan.migrationType())) {
                continue;
            }
            migrationPlansByRole.computeIfAbsent(roleBusinessModelRole(plan), ignored -> new ArrayList<>())
                    .add(plan);
        }
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        roles.addAll(participantPlansByRole.keySet());
        roles.addAll(migrationPlansByRole.keySet());
        List<RoleBusinessModelClosurePlan> plans = new ArrayList<>();
        for (String role : roles) {
            List<RoleParticipantPlan> participantPlans = participantPlansByRole.get(role);
            if (safeList(participantPlans).isEmpty()) {
                plans.add(plan(role, null, migrationPlansByRole.get(role)));
                continue;
            }
            for (RoleParticipantPlan participantPlan : participantPlans) {
                plans.add(plan(role, participantPlan, migrationPlansByRole.get(role)));
            }
        }
        return List.copyOf(plans);
    }

    static String performerSetKey(List<String> performerTypes) {
        List<String> performers = safeList(performerTypes).stream()
                .filter(performer -> !isBlank(performer))
                .distinct()
                .toList();
        return performers.isEmpty() ? "NO_PERFORMER" : String.join("+", performers);
    }

    static String roleBusinessModelRole(AppSemanticMigrationPlan plan) {
        if (plan == null) {
            return "NO_ROLE";
        }
        if (!isBlank(plan.objectType())) {
            return plan.objectType();
        }
        if (!isBlank(plan.targetName())) {
            return plan.targetName();
        }
        return "NO_ROLE";
    }

    static boolean roleBusinessModelMigrationType(String migrationType) {
        return "ROLE_PERFORMER_FIELD".equals(migrationType)
                || "ROLE_OBJECT_FIELD".equals(migrationType)
                || "ROLE_WITHOUT_PERFORMER".equals(migrationType);
    }

    private static RoleBusinessModelClosurePlan plan(String role,
                                                     RoleParticipantPlan participantPlan,
                                                     List<AppSemanticMigrationPlan> migrationPlans) {
        List<String> openReasons = openReasons(participantPlan, migrationPlans);
        List<String> actions = actions(openReasons, migrationPlans);
        boolean performerDeclared = participantPlan != null
                && !safeList(participantPlan.performerTypes()).isEmpty();
        boolean roleParticipantIdentityComplete = participantPlan != null && participantPlan.complete();
        boolean businessFieldsOnly = safeList(migrationPlans).stream()
                .noneMatch(plan -> "ROLE_OBJECT_FIELD".equals(plan.migrationType())
                        || "ROLE_PERFORMER_FIELD".equals(plan.migrationType()));
        boolean closed = openReasons.isEmpty();
        String readiness = closed ? "ROLE_MODEL_CLOSED" : "ROLE_MODEL_OPEN";
        return new RoleBusinessModelClosurePlan(role,
                participantPlan == null ? "" : participantPlan.roleTableName(),
                participantPlan == null ? "NO_PARTICIPATION" : participantPlan.participationKind(),
                participantPlan == null ? List.of() : participantPlan.performerTypes(),
                participantPlan == null ? List.of() : participantPlan.platformColumns(),
                performerDeclared,
                roleParticipantIdentityComplete,
                businessFieldsOnly,
                closed,
                readiness,
                openReasons,
                actions,
                contract(role, participantPlan, readiness));
    }

    private static List<String> openReasons(RoleParticipantPlan participantPlan,
                                            List<AppSemanticMigrationPlan> migrationPlans) {
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        for (AppSemanticMigrationPlan migrationPlan : safeList(migrationPlans)) {
            if (migrationPlan == null) {
                continue;
            }
            switch (indexKey(migrationPlan.migrationType())) {
                case "ROLE_OBJECT_FIELD" -> reasons.add("roleObjectField");
                case "ROLE_PERFORMER_FIELD" -> reasons.add("rolePerformerField");
                case "ROLE_WITHOUT_PERFORMER" -> reasons.add("roleWithoutPerformer");
                default -> {
                }
            }
        }
        if (participantPlan == null || safeList(participantPlan.performerTypes()).isEmpty()) {
            reasons.add("missingPerformerDeclaration");
        }
        if (participantPlan == null || !participantPlan.complete()) {
            reasons.add("incompleteParticipantIdentity");
        }
        return List.copyOf(reasons);
    }

    private static List<String> actions(List<String> openReasons,
                                        List<AppSemanticMigrationPlan> migrationPlans) {
        if (safeList(openReasons).isEmpty()) {
            return List.of("NO_ACTION_REQUIRED");
        }
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (AppSemanticMigrationPlan migrationPlan : safeList(migrationPlans)) {
            if (migrationPlan != null && !isBlank(migrationPlan.migrationAction())) {
                actions.add(migrationPlan.migrationAction());
            }
        }
        for (String reason : safeList(openReasons)) {
            switch (reason) {
                case "missingPerformerDeclaration", "roleWithoutPerformer" ->
                        actions.add("declareRoleTakePerformer");
                case "incompleteParticipantIdentity" ->
                        actions.add("pushRoleParticipantIdentityToAppDdl");
                default -> {
                }
            }
        }
        return List.copyOf(actions);
    }

    private static String contract(String role,
                                   RoleParticipantPlan participantPlan,
                                   String readiness) {
        String participation = participantPlan == null ? "NO_PARTICIPATION" : participantPlan.participationKind();
        List<String> performerTypes = participantPlan == null ? List.of() : participantPlan.performerTypes();
        return indexKey(role)
                + ":" + indexKey(participation)
                + ":" + performerSetKey(performerTypes)
                + ":" + indexKey(readiness);
    }

    private static String indexKey(String value) {
        return value == null || value.isBlank() ? "UNASSIGNED" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
