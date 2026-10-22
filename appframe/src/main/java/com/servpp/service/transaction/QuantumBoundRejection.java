package com.servpp.service.transaction;

import java.math.BigDecimal;

/** Carries the rejected settlement attempt while preserving the stable rollback code. */
public final class QuantumBoundRejection extends BusinessTransactionRejection implements FailureReasonProvider {
    private final QuantumConservationRecord record;
    public QuantumBoundRejection(QuantumConservationRecord record) {
        super("QUANTUM_BOUND_REJECTED"); this.record = record;
    }
    private static String value(BigDecimal value) { return value == null ? null : value.stripTrailingZeros().toPlainString(); }
    public String getFailureCode() { return getMessage(); }
    public String getFailureReason() {
        return "Proposed value " + getFailureActualValue() + " is outside ["
                + (getFailureMinimum() == null ? "unbounded" : getFailureMinimum()) + ", "
                + (getFailureMaximum() == null ? "unbounded" : getFailureMaximum()) + "] for " + getFailureAttribute();
    }
    public String getFailureAttribute() { return record.getAttributeId(); }
    public String getFailureActualValue() { return value(record.getAfterValue()); }
    public String getFailureMinimum() { return value(record.getMinimum()); }
    public String getFailureMaximum() { return value(record.getMaximum()); }
}
