package com.servpp.service;

import com.servpp.service.transaction.TransactionMode;
import com.servpp.service.transaction.TransactionalService;

/**
 * @author lidong@date 2023-10-31@version 1.0
 */
public interface Scenario extends Service, TransactionalService{
    String startTransaction();
    void endTransaction(String id);

    @Override
    default String getTransactionResourceId() {
        return getName();
    }

    @Override
    default TransactionMode getTransactionMode() {
        return TransactionMode.SAGA;
    }
}
