package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record AppSemanticMigrationStrictErrorIndex(
        int totalErrorCount,
        int keyCount,
        int actionCount,
        int reasonCount,
        int reasonActionCount,
        Map<String, List<String>> byKey,
        Map<String, List<String>> byAction,
        Map<String, List<String>> byReason,
        Map<String, List<String>> byReasonAction,
        Map<String, Integer> countByKey,
        Map<String, Integer> countByAction,
        Map<String, Integer> countByReason,
        Map<String, Integer> countByReasonAction) {
}
