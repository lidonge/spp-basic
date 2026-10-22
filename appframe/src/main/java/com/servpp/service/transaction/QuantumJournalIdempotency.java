package com.servpp.service.transaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

final class QuantumJournalIdempotency {
    private QuantumJournalIdempotency() {
    }

    static void requireMatchingRetry(TransactionContext context,
                                     QuantumEntanglementBoundary boundary,
                                     List<QuantumJournalEntry> existingEntries) {
        List<QuantumConservationRecord> records = boundary.getRecords();
        boolean matches = existingEntries.size() == records.size();
        for (int i = 0; matches && i < records.size(); i++) {
            QuantumJournalEntry entry = existingEntries.get(i);
            QuantumConservationRecord record = records.get(i);
            matches = entry.getSequence() == i
                    && Objects.equals(entry.getBoundaryServiceId(), boundary.getServiceId())
                    && entry.getStatus() == boundary.getStatus()
                    && Objects.equals(entry.getGroupId(), record.getGroupId())
                    && Objects.equals(entry.getAttributeId(), record.getAttributeId())
                    && Objects.equals(entry.getObjectId(), record.getObjectId())
                    && decimalEquals(entry.getBeforeValue(), record.getBeforeValue())
                    && decimalEquals(entry.getDeltaValue(), record.getDeltaValue())
                    && decimalEquals(entry.getAfterValue(), record.getAfterValue());
        }
        if (!matches) {
            throw new IllegalStateException("Quantum transaction " + context.getId()
                    + " reused an idempotency key with different boundary or conservation records");
        }
    }

    private static boolean decimalEquals(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }
}
