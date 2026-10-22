package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record AppSemanticMigrationPlanIndex(Map<String, List<AppSemanticMigrationPlan>> byType,
                                            Map<String, List<AppSemanticMigrationPlan>> byService,
                                            Map<String, List<AppSemanticMigrationPlan>> byRecommendation,
                                            Map<String, List<AppSemanticMigrationPlan>> byAction,
                                            Map<String, List<AppSemanticMigrationPlan>> byTargetKind,
                                            Map<String, List<AppSemanticMigrationPlan>> byAdapterRole,
                                            Map<String, List<AppSemanticMigrationPlan>> byParticipantCategory,
                                            Map<String, List<AppSemanticMigrationPlan>> byRoleOnlyReadiness,
                                            Map<String, List<AppSemanticMigrationPlan>>
                                                    byPlatformPrimaryKeyIdentityKind,
                                            Map<String, List<AppSemanticMigrationPlan>> byRemoteImplementationKind,
                                            Map<String, List<AppSemanticMigrationPlan>> byRemoteExternalReadiness,
                                            Map<String, List<AppSemanticMigrationPlan>> byMigrationContract) {
}
