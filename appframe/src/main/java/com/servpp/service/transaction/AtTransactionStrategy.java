package com.servpp.service.transaction;

/**
 * Reference AT strategy. Participants provide before-image and undo behavior.
 */
public class AtTransactionStrategy extends AbstractTransactionStrategy {
    @Override
    public TransactionMode getMode() {
        return TransactionMode.AT;
    }
}
