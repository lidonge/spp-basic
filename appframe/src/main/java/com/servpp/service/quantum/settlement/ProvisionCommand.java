package com.servpp.service.quantum.settlement;

/** Explicit creation or import; settlement never allocates a quantum implicitly. */
public record ProvisionCommand(LogicalQuantumKey key, QuantumDefinition definition, String initialValue) {
    public ProvisionCommand {
        key = Values.required(key, "key");
        definition = Values.required(definition, "definition");
        initialValue = Values.text(initialValue, "initialValue");
        definition.physicalValue(initialValue);
    }

    public String expectedPhysicalInitialValue() {
        return definition.physicalValue(initialValue);
    }
}
