package com.servpp.service.transaction;

public record LogicalQuantumProvisioningResponse(
        LogicalQuantumServiceCommand.LogicalKey logicalKey,
        long quantumID,
        LogicalQuantumServiceCommand.Definition definition,
        String physicalInitialValue) {

    public LogicalQuantumProvisioningResponse {
        logicalKey = LogicalQuantumProvisioningValues.requireLogicalKey(logicalKey);
        if (quantumID < 0) {
            throw new IllegalArgumentException("quantumID must not be negative");
        }
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
        physicalInitialValue = LogicalQuantumProvisioningValues.requirePhysicalValue(
                physicalInitialValue, definition);
    }

    public void requireIdentity(LogicalQuantumProvisioningRequest request) {
        if (request == null
                || !logicalKey.equals(request.logicalKey())
                || !definition.equals(request.definition())
                || !physicalInitialValue.equals(request.expectedPhysicalInitialValue())) {
            throw new IllegalStateException("QService provisioning response identity mismatch");
        }
    }
}
