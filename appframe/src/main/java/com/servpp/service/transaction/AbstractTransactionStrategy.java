package com.servpp.service.transaction;

public abstract class AbstractTransactionStrategy implements TransactionStrategy {
    @Override
    public void begin(TransactionContext context, TransactionParticipant participant) {
        participant.begin(context);
    }

    @Override
    public void beforeExecute(TransactionContext context, TransactionParticipant participant) {
        participant.beforeExecute(context);
    }

    @Override
    public void afterExecute(TransactionContext context, TransactionParticipant participant) {
        participant.afterExecute(context);
    }

    @Override
    public void commit(TransactionContext context, TransactionParticipant participant) {
        participant.commit(context);
    }

    @Override
    public void rollback(TransactionContext context, TransactionParticipant participant, Throwable cause) {
        participant.rollback(context, cause);
    }
}
