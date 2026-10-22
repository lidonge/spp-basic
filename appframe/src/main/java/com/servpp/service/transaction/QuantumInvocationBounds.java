package com.servpp.service.transaction;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Invocation metadata. Enforcement belongs to the selected quantum commit implementation. */
public final class QuantumInvocationBounds {
    private static final ThreadLocal<List<Bound>> CURRENT = new ThreadLocal<>();
    private QuantumInvocationBounds() { }
    public record Bound(Object participant, String property, Object minimum, Object maximum,
                        com.servpp.service.numeric.NumericAdapter<?> adapter) { }
    public static List<Bound> current() {
        List<Bound> bounds = CURRENT.get();
        return bounds == null ? List.of() : bounds;
    }
    public static void execute(List<Bound> bounds, Runnable update) {
        List<Bound> previous = CURRENT.get();
        List<Bound> captured = expand(bounds);
        if (!captured.isEmpty()) SourceTransactionBoundary.requireQuantumExecution();
        CURRENT.set(captured);
        try { update.run(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
    public static boolean hasLimits(List<Bound> bounds) {
        return bounds.stream().anyMatch(bound -> bound.minimum() != null || bound.maximum() != null);
    }
    public static Bound find(Object participant, String property) {
        return current().stream().filter(bound -> bound.participant() == participant
                && bound.property().equals(property)).findFirst().orElse(null);
    }
    /** Local APP projection retains the bounds when the provider has a different Java model. */
    public static List<Bound> project(Object[] source, Object[] target) {
        List<Bound> result = new ArrayList<>();
        for (Bound bound : current()) {
            boolean mapped = false;
            for (int i = 0; i < source.length; i++) {
                List<Object> originals = members(source[i]), projected = members(target[i]);
                for (int j = 0; j < originals.size(); j++) {
                    if (originals.get(j) != bound.participant()) continue;
                    if (originals.size() != projected.size())
                        throw new IllegalStateException("QUANTUM_BOUND_PROJECTION_MISMATCH");
                    result.add(new Bound(projected.get(j), bound.property(), bound.minimum(), bound.maximum(), bound.adapter()));
                    mapped = true;
                }
            }
            if (!mapped) throw new IllegalStateException("QUANTUM_BOUND_PARTICIPANT_NOT_PROJECTED");
        }
        return List.copyOf(result);
    }
    private static List<Bound> expand(List<Bound> bounds) {
        List<Bound> captured = new ArrayList<>();
        for (Bound bound : bounds) {
            if (bound.minimum() != null && bound.maximum() != null
                    && compare(bound.minimum(), bound.maximum(), bound.adapter()) > 0)
                SourceTransactionBoundary.rejectCurrent("QUANTUM_INTERVAL_INVALID");
            for (Object member : members(bound.participant())) {
                if (member == null) throw new IllegalStateException("QUANTUM_PARTICIPANT_REQUIRED");
                captured.add(new Bound(member, bound.property(), bound.minimum(), bound.maximum(), bound.adapter()));
            }
        }
        return List.copyOf(captured);
    }
    private static List<Object> members(Object participant) {
        if (participant == null) throw new IllegalStateException("QUANTUM_PARTICIPANT_REQUIRED");
        List<Object> members = new ArrayList<>();
        if (participant instanceof Iterable<?> values) values.forEach(members::add);
        else if (participant.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(participant); i++) members.add(Array.get(participant, i));
        } else members.add(participant);
        return members;
    }
    @SuppressWarnings("unchecked")
    private static int compare(Object left, Object right, com.servpp.service.numeric.NumericAdapter<?> adapter) {
        if (adapter != null) return ((com.servpp.service.numeric.NumericAdapter<Object>) adapter).compare(left, right);
        return decimal(left).compareTo(decimal(right));
    }
    /** Codec used by the existing decimal conservation ledger, not a universal SPP number type. */
    static BigDecimal decimal(Object number) {
        if (number == null) return null;
        if (number instanceof BigDecimal value) return value;
        if (number instanceof BigInteger value) return new BigDecimal(value);
        if (number instanceof Number value) return new BigDecimal(value.toString());
        throw new IllegalStateException("QUANTUM_LEDGER_NUMBER_CODEC_UNSUPPORTED");
    }
}
