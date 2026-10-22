package com.servpp.service.transaction;

import java.util.*;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/** Runs business disposition after the failed boundary finishes its technical recovery. */
public final class TransactionFailureHandling {
    private record Origin(String service, String resource, String phase, boolean reversed) { }
    private static final ReferenceQueue<Throwable> STALE = new ReferenceQueue<>();
    private static final Map<Key, Origin> ORIGINS = new HashMap<>();
    private static final class Key extends WeakReference<Throwable> {
        private final int hash;
        Key(Throwable failure, ReferenceQueue<Throwable> queue) { super(failure, queue); hash = System.identityHashCode(failure); }
        public int hashCode() { return hash; }
        public boolean equals(Object value) { return this == value || value instanceof Key other && get() != null && get() == other.get(); }
    }
    private static void drain() { Key key; while ((key = (Key)STALE.poll()) != null) ORIGINS.remove(key); }
    private TransactionFailureHandling() { }

    static void recordOrigin(Throwable failure, String service, String resource, String phase) {
        synchronized (ORIGINS) { drain(); ORIGINS.putIfAbsent(new Key(failure, STALE), new Origin(service, resource, phase, false)); }
    }
    public static void recordReversed(Throwable failure) {
        synchronized (ORIGINS) {
            var origin = origin(failure);
            drain(); ORIGINS.put(new Key(failure, STALE), new Origin(origin.service(), origin.resource(), origin.phase(), true));
        }
    }
    private static Origin origin(Throwable failure) {
        synchronized (ORIGINS) {
        drain();
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (var current = failure; current != null && seen.add(current); current = current.getCause()) {
            var origin = ORIGINS.get(new Key(current, null)); if (origin != null) return origin;
        }
        return new Origin("", "", "TRANSACTION", false);
        }
    }
    private static String message(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank() ? "TRANSACTION_FAILED" : failure.getMessage();
    }
    private static FailureReasonProvider detail(Throwable failure) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (var current = failure; current != null && seen.add(current); current = current.getCause())
            if (current instanceof FailureReasonProvider provider) return provider;
        return null;
    }
    public static void execute(Object result, Object contract, Runnable transaction, Runnable disposition) {
        ScenarioResults.attach(contract, result);
        try {
            transaction.run();
        } catch (RuntimeException failure) {
            var outcome = TransactionOutcomes.outcome(failure);
            var origin = origin(failure);
            boolean recovered = outcome == TransactionOutcomes.Outcome.ROLLED_BACK
                    || origin.reversed() && outcome != TransactionOutcomes.Outcome.UNKNOWN && outcome != TransactionOutcomes.Outcome.COMMITTED;
            Object facts = null;
            try {
                // Participant restoration may have restored the contract's old metadata too.
                ScenarioResults.attach(contract, result);
                ScenarioResults.write(result, "Status", "FAILED"); ScenarioResults.write(result, "Handled", false);
                facts = ScenarioResults.newFailure(result);
                var detail = detail(failure);
                ScenarioResults.write(facts, "Code", detail == null ? message(failure) : detail.getFailureCode());
                ScenarioResults.write(facts, "Reason", detail == null ? message(failure) : detail.getFailureReason());
                ScenarioResults.write(facts, "Attribute", detail == null ? null : detail.getFailureAttribute());
                ScenarioResults.write(facts, "ActualValue", detail == null ? null : detail.getFailureActualValue());
                ScenarioResults.write(facts, "Minimum", detail == null ? null : detail.getFailureMinimum());
                ScenarioResults.write(facts, "Maximum", detail == null ? null : detail.getFailureMaximum());
                ScenarioResults.write(facts, "ServiceName", origin.service());
                ScenarioResults.write(facts, "ResourceName", origin.resource());
                ScenarioResults.write(facts, "Phase", origin.phase());
                ScenarioResults.write(facts, "TransactionOutcome", outcome.name());
                ScenarioResults.write(facts, "RecoveryStatus", recovered
                        ? origin.reversed() ? "REVERSED" : "ROLLED_BACK" : "RECOVERY_REQUIRED");
                var causes = new ArrayList<String>();
                var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
                seen.add(failure);
                for (var cause = failure.getCause(); cause != null && seen.add(cause); cause = cause.getCause()) causes.add(message(cause));
                ScenarioResults.write(facts, "Causes", causes);
                ScenarioResults.write(facts, "SuppressedCodes", Arrays.stream(failure.getSuppressed()).map(TransactionFailureHandling::message).toList());
                disposition.run();
                if (recovered) ScenarioResults.write(result, "Handled", true);
            } catch (RuntimeException | Error secondary) {
                if (secondary != failure) failure.addSuppressed(secondary);
                if (facts != null) {
                    try {
                        ScenarioResults.write(facts, "SuppressedCodes", Arrays.stream(failure.getSuppressed())
                                .map(TransactionFailureHandling::message).toList());
                    } catch (RuntimeException | Error reportingFailure) {
                        if (reportingFailure != failure) failure.addSuppressed(reportingFailure);
                    }
                }
                throw failure;
            }
            // Business disposition does not certify an uncertain commit or failed reversal.
            if (!recovered) throw failure;
        }
    }
}
