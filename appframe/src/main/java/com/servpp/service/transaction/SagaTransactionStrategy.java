package com.servpp.service.transaction;

/**
 * Reference Saga strategy. Participant rollback is the compensation action.
 */
public class SagaTransactionStrategy extends AbstractTransactionStrategy {
    @Override
    public TransactionMode getMode() {
        return TransactionMode.SAGA;
    }
}
