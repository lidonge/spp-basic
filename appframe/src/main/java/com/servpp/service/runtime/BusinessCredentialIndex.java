package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record BusinessCredentialIndex(List<BusinessCredentialPlan> plans,
                                      Map<String, List<BusinessCredentialPlan>> byTableName,
                                      Map<String, List<BusinessCredentialPlan>> byBusinessType,
                                      Map<String, List<BusinessCredentialPlan>> byCategory,
                                      Map<String, List<BusinessCredentialPlan>> byColumn,
                                      Map<String, List<BusinessCredentialPlan>> byPath) {
}
