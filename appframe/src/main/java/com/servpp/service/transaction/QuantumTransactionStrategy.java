package com.servpp.service.transaction;

/**
 * Placeholder strategy for indivisible quantum-resource mutations.
 */
public class QuantumTransactionStrategy extends AbstractTransactionStrategy {
    @Override
    public TransactionMode getMode() {
        return TransactionMode.QUANTUM;
    }
}
