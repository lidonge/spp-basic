package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;

public record TransactionReversalReadinessIndex(
        List<TransactionReversalRule> rules,
        Map<String, List<TransactionReversalRule>> byReadiness,
        Map<String, List<TransactionReversalRule>> byReadyReason,
        Map<String, List<TransactionReversalRule>> byMissingReason,
        Map<String, List<TransactionReversalRule>> byAction,
        Map<String, List<TransactionReversalRule>> byKind,
        Map<String, List<TransactionReversalRule>> bySource,
        Map<String, List<TransactionReversalRule>> byTarget,
        Map<String, List<TransactionReversalRule>> byReversalPath,
        Map<String, List<TransactionReversalRule>> byPhase,
        Map<String, List<TransactionReversalRule>> byExecutionContract) {
}
