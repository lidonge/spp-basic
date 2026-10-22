package com.servpp.service.transaction;

public interface TransactionStrategy {
    TransactionMode getMode();

    void begin(TransactionContext context, TransactionParticipant participant);

    void beforeExecute(TransactionContext context, TransactionParticipant participant);

    void afterExecute(TransactionContext context, TransactionParticipant participant);

    void commit(TransactionContext context, TransactionParticipant participant);

    void rollback(TransactionContext context, TransactionParticipant participant, Throwable cause);
}
