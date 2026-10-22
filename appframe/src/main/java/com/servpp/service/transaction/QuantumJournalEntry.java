package com.servpp.service.transaction;

import java.math.BigDecimal;

public class QuantumJournalEntry {
    private final String transactionId;
    private final String boundaryServiceId;
    private final QuantumEntanglementBoundary.Status status;
    private final int sequence;
    private final String groupId;
    private final String attributeId;
    private final String objectId;
    private final BigDecimal beforeValue;
    private final BigDecimal deltaValue;
    private final BigDecimal afterValue;

    public QuantumJournalEntry(String transactionId, String boundaryServiceId,
                               QuantumEntanglementBoundary.Status status, int sequence,
                               QuantumConservationRecord record) {
        this.transactionId = transactionId;
        this.boundaryServiceId = boundaryServiceId;
        this.status = status;
        this.sequence = sequence;
        this.groupId = record.getGroupId();
        this.attributeId = record.getAttributeId();
        this.objectId = record.getObjectId();
        this.beforeValue = record.getBeforeValue();
        this.deltaValue = record.getDeltaValue();
        this.afterValue = record.getAfterValue();
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getBoundaryServiceId() {
        return boundaryServiceId;
    }

    public QuantumEntanglementBoundary.Status getStatus() {
        return status;
    }

    public int getSequence() {
        return sequence;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getAttributeId() {
        return attributeId;
    }

    public String getObjectId() {
        return objectId;
    }

    public BigDecimal getBeforeValue() {
        return beforeValue;
    }

    public BigDecimal getDeltaValue() {
        return deltaValue;
    }

    public BigDecimal getAfterValue() {
        return afterValue;
    }
}
