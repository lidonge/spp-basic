package com.servpp.service.transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QuantumEntanglementBoundary {
    private final String serviceId;
    private final List<String> resourceIds;
    private final List<QuantumConservationRecord> records = new ArrayList<>();
    private Status status = Status.OPEN;
    private LogicalQuantumServiceResponse.Outcome quantumServiceOutcome;

    public QuantumEntanglementBoundary(String serviceId, List<String> resourceIds) {
        this.serviceId = serviceId;
        this.resourceIds = List.copyOf(resourceIds);
    }

    public String getServiceId() {
        return serviceId;
    }

    public List<String> getResourceIds() {
        return resourceIds;
    }

    public void addRecord(QuantumConservationRecord record) {
        records.add(record);
    }

    public List<QuantumConservationRecord> getRecords() {
        return List.copyOf(records);
    }

    public QuantumEntanglementBoundary negativeDeltaReversal() {
        QuantumEntanglementBoundary reversal = new QuantumEntanglementBoundary(serviceId, resourceIds);
        for (QuantumConservationRecord record : records) {
            reversal.addRecord(new QuantumConservationRecord(
                    record.getGroupId(),
                    record.getAttributeId(),
                    record.getObjectId(),
                    record.getAfterValue(),
                    record.getDeltaValue().negate(),
                    record.getBeforeValue()));
        }
        reversal.markRolledBack();
        return reversal;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isCommitted() {
        return status == Status.COMMITTED;
    }

    public LogicalQuantumServiceResponse.Outcome getQuantumServiceOutcome() {
        return quantumServiceOutcome;
    }

    public void recordQuantumServiceOutcome(LogicalQuantumServiceResponse.Outcome outcome) {
        quantumServiceOutcome = outcome;
    }

    public void validateConservation() {
        Map<String, BigDecimal> deltas = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (QuantumConservationRecord record : records) {
            if (!QuantumConservationRecord.isConservedGroup(record.getGroupId())) continue;
            deltas.merge(record.getGroupId(), record.getDeltaValue(), BigDecimal::add);
            counts.merge(record.getGroupId(), 1, Integer::sum);
        }
        for (Map.Entry<String, BigDecimal> entry : deltas.entrySet()) {
            String groupId = entry.getKey();
            if (counts.getOrDefault(groupId, 0) < 2) {
                throw new IllegalStateException("quantum group " + groupId
                        + " must contain at least two quantum attributes");
            }
            if (entry.getValue().compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalStateException("quantum group " + groupId
                        + " is not conserved: " + entry.getValue());
            }
        }
    }

    public void markCommitted() {
        status = Status.COMMITTED;
    }

    public void markRolledBack() {
        status = Status.ROLLED_BACK;
    }

    public enum Status {
        OPEN,
        COMMITTED,
        ROLLED_BACK
    }
}
