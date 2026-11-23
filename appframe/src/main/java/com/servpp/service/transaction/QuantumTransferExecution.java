package com.servpp.service.transaction;

import com.servpp.service.Service;
import com.servpp.service.runtime.QuantumServiceMetadataProvider;

/** Confirms that a generated transfer body ran inside a quantum settlement boundary. */
public final class QuantumTransferExecution {
    private static final ThreadLocal<Invocation> CURRENT = new ThreadLocal<>();
    private static final class Invocation {
        boolean applied;
    }

    private QuantumTransferExecution() { }

    public static void applied() {
        Invocation invocation = CURRENT.get();
        if (invocation == null) throw rejection("QUANTUM_TRANSFER_SETTLEMENT_REQUIRED");
        invocation.applied = true;
    }

    static void execute(Service service, TransactionContext context, Runnable action) {
        if (!(service instanceof QuantumServiceMetadataProvider metadata) || !metadata.hasQuantumTransfers()) {
            action.run();
            return;
        }
        var boundary = context.getQuantumEntanglementBoundary();
        if (boundary == null) throw rejection("QUANTUM_TRANSFER_SETTLEMENT_REQUIRED");
        int previousRecords = boundary.recordCount();
        Invocation previous = CURRENT.get();
        var invocation = new Invocation();
        CURRENT.set(invocation);
        try {
            action.run();
            if (!invocation.applied || boundary.recordCount() == previousRecords)
                throw rejection("QUANTUM_TRANSFER_NOT_APPLIED");
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    private static IllegalStateException rejection(String code) {
        var failure = new IllegalStateException(code);
        SourceTransactionBoundary.markRollbackOnly(failure);
        return failure;
    }
}
