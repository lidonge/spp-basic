package com.servpp.service.transaction;

public interface TransactionalService {
    String getTransactionResourceId();

    TransactionMode getTransactionMode();

    default TransactionParticipant getTransactionParticipant() {
        return TransactionParticipant.noop(getTransactionResourceId());
    }
}
