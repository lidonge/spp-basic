package com.servpp.service.transaction;

import java.util.ArrayList;
import java.util.List;

public class InMemoryQuantumJournalStore implements QuantumJournalStore {
    private final List<QuantumJournalEntry> entries = new ArrayList<>();

    @Override
    public synchronized void append(TransactionContext context, QuantumEntanglementBoundary boundary) {
        List<QuantumJournalEntry> existingEntries = findByTransactionId(context.getId());
        if (!existingEntries.isEmpty()) {
            QuantumJournalIdempotency.requireMatchingRetry(context, boundary, existingEntries);
            return;
        }
        List<QuantumConservationRecord> records = boundary.getRecords();
        for (int i = 0; i < records.size(); i++) {
            entries.add(new QuantumJournalEntry(context.getId(), boundary.getServiceId(),
                    boundary.getStatus(), i, records.get(i)));
        }
    }

    @Override
    public synchronized List<QuantumJournalEntry> findByTransactionId(String transactionId) {
        return entries.stream()
                .filter(entry -> entry.getTransactionId().equals(transactionId))
                .toList();
    }

    public synchronized List<QuantumJournalEntry> entries() {
        return List.copyOf(entries);
    }

    @Override
    public synchronized void clear() {
        entries.clear();
    }
}
