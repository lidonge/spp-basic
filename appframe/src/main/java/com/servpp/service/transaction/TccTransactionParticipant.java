package com.servpp.service.transaction;

public interface TccTransactionParticipant extends TransactionParticipant {
    default void beforeTry(TransactionContext context) {
    }

    default void afterTry(TransactionContext context) {
    }

    default void confirm(TransactionContext context) {
    }

    default void cancel(TransactionContext context, Throwable cause) {
    }
}
