package com.servpp.service.quantum.settlement;

/** Logical identity only: the caller never names a physical quantum ID. */
public record LogicalQuantumKey(String namespace, String attributeId, String objectId) {
    public LogicalQuantumKey {
        namespace = Values.text(namespace, "namespace");
        attributeId = Values.text(attributeId, "attributeId");
        objectId = Values.text(objectId, "objectId");
    }
}
