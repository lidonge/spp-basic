package com.servpp.service.quantum.settlement;

import java.util.Set;

/**
 * Unified quantum settlement interface, modelled on QService's external contract.
 * Design: sdk-quantum-settlement-interface-v1.
 */
public interface QuantumSettlementService {
    /** The PHY quantumStore name. */
    String name();

    Set<Capability> capabilities();

    /** One phase: apply now. */
    SettlementResponse settle(SettlementCommand command);

    /** New key plus originalRequestKey, carrying the forward deltas (never pre-negated). */
    SettlementResponse reverse(SettlementCommand reversal);

    /** Durable result of any operation under {@code key}. */
    SettlementResponse query(SettlementKey key);

    /** Explicit creation or import of a quantum. */
    ProvisionResponse provision(ProvisionCommand command);

    /** Two phase (TCC). Present only when {@link #capabilities()} contains TWO_PHASE. */
    default SettlementResponse reserve(SettlementCommand command) { throw unsupported("reserve"); }

    default SettlementResponse confirm(SettlementKey reservation) { throw unsupported("confirm"); }

    default SettlementResponse cancel(SettlementKey reservation) { throw unsupported("cancel"); }

    /** Same-resource atomicity for LOCAL and the central store's own side. */
    default void executeInAccessScope(Runnable action) { action.run(); }

    private UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException(
                "Quantum settlement service " + name() + " does not support " + operation + ".");
    }

    enum Capability { ONE_PHASE, TWO_PHASE, INVOCATION_BOUNDS, PROVISIONING, SHARED_LOCAL_TRANSACTION }
}
