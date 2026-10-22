package com.servpp.service.runtime;

import java.util.List;

import com.servpp.service.repository.ObjectSnapshotRule;
import com.servpp.service.transaction.TccBranchRecord;
import com.servpp.service.transaction.TccReservationRecord;

public record TccRecoveryCandidate(TccBranchRecord branch,
                                   String tryServiceName,
                                   String confirmServiceName,
                                   String cancelServiceName,
                                   List<ObjectSnapshotRule> objectSnapshotRules,
                                   int argumentSnapshotRuleCount,
                                   List<String> argumentSnapshotSavePhases,
                                   List<String> rollbackRecoverySavePhases,
                                   boolean lifecycleAvailable,
                                   boolean recoveryPayloadAvailable,
                                   List<TccReservationRecord> reservations,
                                   boolean expiredReservationAvailable) {
    public TccRecoveryCandidate(TccBranchRecord branch,
                                String tryServiceName,
                                String confirmServiceName,
                                String cancelServiceName,
                                List<ObjectSnapshotRule> objectSnapshotRules,
                                boolean lifecycleAvailable,
                                boolean recoveryPayloadAvailable) {
        this(branch, tryServiceName, confirmServiceName, cancelServiceName, objectSnapshotRules,
                lifecycleAvailable, recoveryPayloadAvailable, List.of(), false);
    }

    public TccRecoveryCandidate(TccBranchRecord branch,
                                String tryServiceName,
                                String confirmServiceName,
                                String cancelServiceName,
                                List<ObjectSnapshotRule> objectSnapshotRules,
                                boolean lifecycleAvailable,
                                boolean recoveryPayloadAvailable,
                                List<TccReservationRecord> reservations,
                                boolean expiredReservationAvailable) {
        this(branch, tryServiceName, confirmServiceName, cancelServiceName, objectSnapshotRules,
                countArgumentSnapshotRules(objectSnapshotRules),
                argumentSnapshotSavePhases(objectSnapshotRules),
                rollbackRecoverySavePhases(objectSnapshotRules),
                lifecycleAvailable, recoveryPayloadAvailable, reservations, expiredReservationAvailable);
    }

    public TccRecoveryCandidate {
        objectSnapshotRules = objectSnapshotRules == null ? List.of() : List.copyOf(objectSnapshotRules);
        argumentSnapshotSavePhases = argumentSnapshotSavePhases == null
                ? List.of()
                : List.copyOf(argumentSnapshotSavePhases);
        rollbackRecoverySavePhases = rollbackRecoverySavePhases == null
                ? List.of()
                : List.copyOf(rollbackRecoverySavePhases);
        reservations = reservations == null ? List.of() : List.copyOf(reservations);
    }

    private static int countArgumentSnapshotRules(List<ObjectSnapshotRule> rules) {
        if (rules == null) {
            return 0;
        }
        return (int) rules.stream()
                .filter(rule -> rule.source() == ObjectSnapshotRule.Source.ARGUMENT)
                .count();
    }

    private static List<String> argumentSnapshotSavePhases(List<ObjectSnapshotRule> rules) {
        if (rules == null) {
            return List.of();
        }
        return rules.stream()
                .filter(rule -> rule.source() == ObjectSnapshotRule.Source.ARGUMENT)
                .flatMap(rule -> effectiveSnapshotSavePhases(rule.timing()).stream())
                .distinct()
                .toList();
    }

    private static List<String> rollbackRecoverySavePhases(List<ObjectSnapshotRule> rules) {
        if (rules == null) {
            return List.of();
        }
        return rules.stream()
                .filter(rule -> rule.source() == ObjectSnapshotRule.Source.ARGUMENT)
                .filter(rule -> snapshotSavesAfterExecution(rule.timing()))
                .flatMap(rule -> effectiveSnapshotSavePhases(rule.timing()).stream())
                .distinct()
                .toList();
    }

    private static List<String> effectiveSnapshotSavePhases(ObjectSnapshotRule.Timing timing) {
        if (timing == ObjectSnapshotRule.Timing.AFTER_COMMIT) {
            return List.of(ObjectSnapshotRule.Timing.AFTER.name(), ObjectSnapshotRule.Timing.COMMIT.name());
        }
        return List.of(timing.name());
    }

    private static boolean snapshotSavesAfterExecution(ObjectSnapshotRule.Timing timing) {
        return timing == ObjectSnapshotRule.Timing.AFTER
                || timing == ObjectSnapshotRule.Timing.AFTER_COMMIT;
    }
}
