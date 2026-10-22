package com.servpp.service.transaction;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Measured evidence for a failed boundary; earlier independent transactions are outside it. */
public final class TransactionOutcomes {
    public enum Outcome { UNREPORTED, ROLLED_BACK, COMMITTED, UNKNOWN }

    private static final ReferenceQueue<Throwable> QUEUE = new ReferenceQueue<>();
    private static final Map<Key, Outcome> FAILURES = new HashMap<>();
    private static final ThreadLocal<Observation> CURRENT = new ThreadLocal<>();

    private TransactionOutcomes() { }

    /** Exceptions may override equals; evidence belongs to the exact original failure. */
    private static final class Key extends WeakReference<Throwable> {
        private final int hash;

        Key(Throwable value, ReferenceQueue<Throwable> queue) {
            super(value, queue);
            hash = System.identityHashCode(value);
        }

        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object value) {
            return this == value || value instanceof Key other && get() != null && get() == other.get();
        }
    }

    private static final class Observation {
        private final Observation parent;
        private final Map<Object, Outcome> resources = new IdentityHashMap<>();
        Observation(Observation parent) { this.parent = parent; }
    }

    private static Outcome merge(Outcome left, Outcome right) {
        if (left == Outcome.UNREPORTED) return right;
        if (right == Outcome.UNREPORTED || left == right) return left;
        return Outcome.UNKNOWN;
    }

    private static void drainStaleFailures() {
        Key stale;
        while ((stale = (Key) QUEUE.poll()) != null) FAILURES.remove(stale);
    }

    public static synchronized void record(Throwable failure, Outcome outcome) {
        drainStaleFailures();
        Key key = new Key(failure, QUEUE);
        FAILURES.put(key, merge(FAILURES.getOrDefault(key, Outcome.UNREPORTED), outcome));
    }

    public static void recordResource(Object resource, Outcome outcome) {
        for (Observation observation = CURRENT.get(); observation != null; observation = observation.parent) {
            observation.resources.merge(resource, outcome, TransactionOutcomes::merge);
        }
    }

    public static Outcome outcome(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> pending = new ArrayDeque<>();
        pending.add(failure);
        Outcome result = Outcome.UNREPORTED;
        synchronized (TransactionOutcomes.class) {
            drainStaleFailures();
            while (!pending.isEmpty()) {
                Throwable item = pending.removeFirst();
                if (!visited.add(item)) continue;
                result = merge(result, FAILURES.getOrDefault(new Key(item, null), Outcome.UNREPORTED));
                if (item.getCause() != null) pending.add(item.getCause());
                Collections.addAll(pending, item.getSuppressed());
            }
        }
        return result;
    }

    /** Independently committed Saga stages must not merge into the failed stage's outcome. */
    static void observeIndependent(Runnable action) {
        Observation previous = CURRENT.get(); CURRENT.remove();
        try { observe(action); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }

    public static void observe(Runnable action) {
        Observation previous = CURRENT.get();
        Observation observation = new Observation(previous);
        CURRENT.set(observation);
        try {
            action.run();
        } catch (RuntimeException | Error failure) {
            Outcome result = Outcome.UNREPORTED;
            for (Outcome outcome : observation.resources.values()) result = merge(result, outcome);
            // An earlier successful inner commit does not prove a later unmeasured operation committed.
            if (result == Outcome.COMMITTED && outcome(failure) == Outcome.UNREPORTED) result = Outcome.UNKNOWN;
            record(failure, result);
            throw failure;
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }
}
