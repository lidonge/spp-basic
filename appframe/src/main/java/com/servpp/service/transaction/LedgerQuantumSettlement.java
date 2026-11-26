package com.servpp.service.transaction;

/**
 * Settlement that runs on the caller's own ledger and resource transaction (capability
 * SHARED_LOCAL_TRANSACTION). The runtime already holds before and after values and the
 * invocation bounds of every record, so the bridge hands over the boundary itself.
 */
public interface LedgerQuantumSettlement {
    void settleLedger(TransactionContext context, QuantumEntanglementBoundary boundary);

    void reverseLedger(TransactionContext context, QuantumEntanglementBoundary boundary);
}
