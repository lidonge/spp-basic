package com.servpp.service;

import com.servpp.service.transaction.TransactionMode;
import com.servpp.service.transaction.TransactionParticipant;
import com.servpp.service.transaction.TransactionalService;
import com.servpp.service.transaction.ObjectSnapshotTransactionParticipant;

/**
 * @author lidong@date 2023-10-31@version 1.0
 * Semantic service client
 */
public interface AtomicService extends Service, TransactionalService {
    @Override
    default String getTransactionResourceId() {
        return getName();
    }

    @Override
    default TransactionMode getTransactionMode() {
        return TransactionMode.AT;
    }

    @Override
    default TransactionParticipant getTransactionParticipant() {
        return new ObjectSnapshotTransactionParticipant(getTransactionResourceId());
    }
}
