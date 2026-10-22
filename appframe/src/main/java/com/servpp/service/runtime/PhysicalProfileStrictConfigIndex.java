package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record PhysicalProfileStrictConfigIndex(List<String> configuredKeys,
                                               List<String> enabledKeys,
                                               List<String> disabledKeys,
                                               List<String> configuredBindings,
                                               List<String> effectiveBindings,
                                               Map<String, List<String>> keysByEffect,
                                               Map<String, List<String>> bindingsByEffect,
                                               List<String> strictContracts,
                                               Map<String, List<String>> byStrictContract) {
}
