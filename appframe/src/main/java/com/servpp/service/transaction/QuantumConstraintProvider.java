package com.servpp.service.transaction;

public interface QuantumConstraintProvider {
    void validateQuantumConstraints(Object[] participants);
    static void check(boolean valid, String code) {
        if (!valid) throw new QuantumConstraintRejection(code);
    }
}
