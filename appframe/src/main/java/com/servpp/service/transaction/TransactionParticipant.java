package com.servpp.service.transaction;

public interface TransactionParticipant {
    String getResourceId();

    default void begin(TransactionContext context) {
    }

    default void beforeExecute(TransactionContext context) {
    }

    default void afterExecute(TransactionContext context) {
    }

    default void commit(TransactionContext context) {
    }

    default void rollback(TransactionContext context, Throwable cause) {
    }

    static TransactionParticipant noop(String resourceId) {
        return new TransactionParticipant() {
            @Override
            public String getResourceId() {
                return resourceId;
            }
        };
    }
}
