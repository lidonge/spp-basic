package com.servpp.service.quantum.settlement;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Netted, conserved mutations under one idempotent key. */
public record SettlementCommand(SettlementKey key, Optional<String> originalRequestKey,
                                String resourceName, AtomicGroupShape shape,
                                List<QuantumMutation> mutations) {
    public enum AtomicGroupShape { NONE, ONE_TO_MANY, MANY_TO_ONE, NETTED }

    public SettlementCommand {
        key = Values.required(key, "key");
        originalRequestKey = Values.required(originalRequestKey, "originalRequestKey");
        if (originalRequestKey.isPresent()) {
            String original = Values.text(originalRequestKey.get(), "originalRequestKey");
            if (original.equals(key.requestKey())) {
                throw new IllegalArgumentException("reversal requestKey must differ from originalRequestKey");
            }
            originalRequestKey = Optional.of(original);
        }
        resourceName = Values.text(resourceName, "resourceName");
        shape = Values.required(shape, "shape");
        if (mutations == null || mutations.size() < 2) {
            throw new IllegalArgumentException("settlement command requires at least two mutations");
        }
        mutations = List.copyOf(mutations);
        Set<LogicalQuantumKey> seen = new HashSet<>();
        BigDecimal net = BigDecimal.ZERO;
        for (QuantumMutation mutation : mutations) {
            if (!seen.add(mutation.key())) {
                throw new IllegalArgumentException("mutations must be netted per logical key: " + mutation.key());
            }
            net = net.add(mutation.deltaValue());
        }
        if (net.signum() != 0) {
            throw new IllegalArgumentException("mutations are not conserved: " + net.stripTrailingZeros().toPlainString());
        }
        AtomicGroupShape derived = deriveShape(mutations);
        if (shape != derived) {
            throw new IllegalArgumentException("shape " + shape + " does not match netted shape " + derived);
        }
    }

    public boolean isReversal() {
        return originalRequestKey.isPresent();
    }

    /** The shape implied by the netted mutations. */
    public static AtomicGroupShape deriveShape(List<QuantumMutation> mutations) {
        if (mutations.size() == 2) {
            return AtomicGroupShape.NONE;
        }
        long contributors = mutations.stream().filter(mutation -> mutation.deltaValue().signum() < 0).count();
        long recipients = mutations.size() - contributors;
        if (contributors == 1 && recipients > 1) {
            return AtomicGroupShape.ONE_TO_MANY;
        }
        if (contributors > 1 && recipients == 1) {
            return AtomicGroupShape.MANY_TO_ONE;
        }
        return AtomicGroupShape.NETTED;
    }

    /** Guard for {@code settle}: a forward command declares no original. */
    public SettlementCommand requireForward() {
        if (isReversal()) {
            throw new IllegalArgumentException("settle must not declare originalRequestKey");
        }
        return this;
    }

    /** Guard for {@code reverse}: a reversal requires the original key. */
    public SettlementCommand requireReversal() {
        if (!isReversal()) {
            throw new IllegalArgumentException("reversal requires originalRequestKey");
        }
        return this;
    }
}
