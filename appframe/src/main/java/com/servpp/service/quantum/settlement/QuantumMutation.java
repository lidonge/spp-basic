package com.servpp.service.quantum.settlement;

import java.math.BigDecimal;
import java.util.Optional;

/** A signed business delta; the optional caller interval is checked atomically (INVOCATION_BOUNDS). */
public record QuantumMutation(LogicalQuantumKey key, QuantumDefinition definition, String delta,
                              Optional<Interval> invocationBounds) {
    /** Either bound may be absent, not both. */
    public record Interval(String min, String max) {
        public Interval {
            if (min == null && max == null) {
                throw new IllegalArgumentException("interval requires a minimum or a maximum");
            }
            BigDecimal low = min == null ? null : Values.number(min, "interval min");
            BigDecimal high = max == null ? null : Values.number(max, "interval max");
            if (low != null && high != null && low.compareTo(high) > 0) {
                throw new IllegalArgumentException("interval min must not be greater than max");
            }
        }
    }

    public QuantumMutation {
        key = Values.required(key, "key");
        definition = Values.required(definition, "definition");
        if (Values.number(delta, "delta").signum() == 0) {
            throw new IllegalArgumentException("delta must not be zero; zero-net quantities drop out");
        }
        delta = delta.trim();
        invocationBounds = Values.required(invocationBounds, "invocationBounds");
    }

    public QuantumMutation(LogicalQuantumKey key, QuantumDefinition definition, String delta) {
        this(key, definition, delta, Optional.empty());
    }

    public BigDecimal deltaValue() {
        return new BigDecimal(delta);
    }
}
