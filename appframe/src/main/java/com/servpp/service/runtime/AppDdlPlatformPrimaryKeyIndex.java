package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record AppDdlPlatformPrimaryKeyIndex(List<AppSemanticMigrationPlan> plans,
                                            Map<String, List<AppSemanticMigrationPlan>> byIdentityKind,
                                            Map<String, List<AppSemanticMigrationPlan>> byObjectType,
                                            Map<String, List<AppSemanticMigrationPlan>> byTableName,
                                            Map<String, List<AppSemanticMigrationPlan>> byPath,
                                            Map<String, List<AppSemanticMigrationPlan>> byPlatformColumn) {
}
