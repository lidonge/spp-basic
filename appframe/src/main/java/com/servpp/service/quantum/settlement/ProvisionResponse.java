package com.servpp.service.quantum.settlement;

public record ProvisionResponse(LogicalQuantumKey key, QuantumDefinition definition,
                                String physicalInitialValue, SettlementResponse.Outcome outcome) {
    public ProvisionResponse {
        key = Values.required(key, "key");
        definition = Values.required(definition, "definition");
        physicalInitialValue = Values.text(physicalInitialValue, "physicalInitialValue");
        outcome = Values.required(outcome, "outcome");
    }

    /** Verifies the echo of key, definition and the scaled initial value. */
    public void requireIdentity(ProvisionCommand command) {
        if (!key.equals(command.key()) || !definition.equals(command.definition())
                || !physicalInitialValue.equals(command.expectedPhysicalInitialValue())) {
            throw new IllegalStateException("Provisioning response identity mismatch");
        }
    }
}
