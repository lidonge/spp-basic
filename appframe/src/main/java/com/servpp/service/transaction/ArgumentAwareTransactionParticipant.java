package com.servpp.service.transaction;

public interface ArgumentAwareTransactionParticipant extends TransactionParticipant {
    void setArguments(Object[] arguments);
}
