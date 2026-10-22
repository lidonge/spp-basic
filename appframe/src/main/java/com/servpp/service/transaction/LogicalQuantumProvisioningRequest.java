package com.servpp.service.transaction;

public record LogicalQuantumProvisioningRequest(
        LogicalQuantumServiceCommand.LogicalKey logicalKey,
        LogicalQuantumServiceCommand.Definition definition,
        String initialValue) {

    public LogicalQuantumProvisioningRequest {
        logicalKey = LogicalQuantumProvisioningValues.requireLogicalKey(logicalKey);
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
        initialValue = LogicalQuantumProvisioningValues.requireLogicalValue(
                initialValue, definition);
    }

    public String expectedPhysicalInitialValue() {
        return LogicalQuantumProvisioningValues.physicalValue(initialValue, definition);
    }
}
