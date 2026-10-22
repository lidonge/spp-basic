package com.servpp.service;

import com.servpp.service.transaction.TransactionMode;

/**
 * Marker for the smallest indivisible service that updates quantum resources.
 */
public interface QuantumService extends AtomicService {
    @Override
    default TransactionMode getTransactionMode() {
        return TransactionMode.QUANTUM;
    }
}
