package com.servpp.service.transaction;

/** Internal rollback signal consumed by application execution, never an SPP business exception. */
public final class QuantumConstraintRejection extends BusinessTransactionRejection {
    public QuantumConstraintRejection(String code) { super(code); }
}
