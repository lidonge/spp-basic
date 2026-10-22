package com.servpp.service.transaction;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.sql.DataSource;

public class JdbcTccObjectStoreQuantumCoordinator implements TccObjectStoreQuantumCoordinator {
    public static final String DEFAULT_JOURNAL_TABLE = "spp_tcc_quantum_journal";
    public static final String DEFAULT_VALUE_TABLE = "spp_tcc_quantum_value";

    private final JdbcQuantumJournalStore store;

    public JdbcTccObjectStoreQuantumCoordinator(DataSource dataSource) {
        this(dataSource, DEFAULT_JOURNAL_TABLE, DEFAULT_VALUE_TABLE);
    }

    public JdbcTccObjectStoreQuantumCoordinator(DataSource dataSource,
                                                String journalTableName,
                                                String valueTableName) {
        store = new JdbcQuantumJournalStore(Objects.requireNonNull(dataSource, "dataSource"),
                journalTableName, valueTableName);
    }

    @Override
    public void commit(String storeName, TransactionContext context, QuantumEntanglementBoundary boundary) {
        requireBoundary(storeName, context, boundary);
        boundary.validateConservation();
        String operationId = operationId(storeName, context.getId(), "COMMIT");
        List<QuantumJournalEntry> existing = store.findByTransactionId(operationId);
        if (!existing.isEmpty()) {
            verifySameRecords(existing, boundary.getRecords(), "commit");
            return;
        }

        QuantumEntanglementBoundary committed = copyBoundary(boundary);
        committed.markCommitted();
        store.append(operationContext(operationId, context, committed), committed);
    }

    @Override
    public void reverse(String storeName, TransactionContext context,
                        QuantumEntanglementBoundary reversalBoundary) {
        requireBoundary(storeName, context, reversalBoundary);
        String reversalOperationId = operationId(storeName, context.getId(), "REVERSAL");
        if (!store.findByTransactionId(reversalOperationId).isEmpty()) {
            return;
        }

        String commitOperationId = operationId(storeName, context.getId(), "COMMIT");
        List<QuantumJournalEntry> committed = store.findByTransactionId(commitOperationId);
        if (committed.isEmpty()) {
            throw new IllegalStateException("Cannot reverse TCC object-store quantum transaction "
                    + context.getId() + " before its commit evidence exists.");
        }

        QuantumEntanglementBoundary persistedReversal = new QuantumEntanglementBoundary(
                committed.get(0).getBoundaryServiceId(), reversalBoundary.getResourceIds());
        for (QuantumJournalEntry entry : committed) {
            persistedReversal.addRecord(new QuantumConservationRecord(
                    entry.getGroupId(),
                    entry.getAttributeId(),
                    entry.getObjectId(),
                    entry.getAfterValue(),
                    entry.getDeltaValue().negate(),
                    entry.getBeforeValue()));
        }
        persistedReversal.validateConservation();
        persistedReversal.markRolledBack();
        store.append(operationContext(reversalOperationId, context, persistedReversal), persistedReversal);
    }

    public JdbcQuantumJournalStore getStore() {
        return store;
    }

    @Override
    public String journalTableName() {
        return store.getJournalTable();
    }

    @Override
    public String valueTableName() {
        return store.getValueTable();
    }

    @Override
    public void clear() {
        store.clear();
    }

    private TransactionContext operationContext(String operationId,
                                                TransactionContext source,
                                                QuantumEntanglementBoundary boundary) {
        return TransactionContext.reversal(operationId, source.getGlobalSerialNumber(),
                boundary.getResourceIds(), store);
    }

    private QuantumEntanglementBoundary copyBoundary(QuantumEntanglementBoundary source) {
        QuantumEntanglementBoundary copy =
                new QuantumEntanglementBoundary(source.getServiceId(), source.getResourceIds());
        source.getRecords().forEach(copy::addRecord);
        return copy;
    }

    private void requireBoundary(String storeName,
                                 TransactionContext context,
                                 QuantumEntanglementBoundary boundary) {
        if (storeName == null || storeName.isBlank()) {
            throw new IllegalArgumentException("TCC object-store quantum store name must not be blank.");
        }
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(boundary, "boundary");
        if (boundary.getRecords().isEmpty()) {
            throw new IllegalStateException("TCC object-store quantum boundary must contain conservation records.");
        }
    }

    private void verifySameRecords(List<QuantumJournalEntry> existing,
                                   List<QuantumConservationRecord> requested,
                                   String operation) {
        if (existing.size() != requested.size()) {
            throw idempotencyConflict(operation);
        }
        for (int index = 0; index < existing.size(); index++) {
            QuantumJournalEntry saved = existing.get(index);
            QuantumConservationRecord record = requested.get(index);
            if (!saved.getGroupId().equals(record.getGroupId())
                    || !saved.getAttributeId().equals(record.getAttributeId())
                    || !saved.getObjectId().equals(record.getObjectId())
                    || saved.getBeforeValue().compareTo(record.getBeforeValue()) != 0
                    || saved.getDeltaValue().compareTo(record.getDeltaValue()) != 0
                    || saved.getAfterValue().compareTo(record.getAfterValue()) != 0) {
                throw idempotencyConflict(operation);
            }
        }
    }

    private IllegalStateException idempotencyConflict(String operation) {
        return new IllegalStateException("TCC object-store quantum " + operation
                + " reused an idempotency key with different conservation records.");
    }

    private String operationId(String storeName, String transactionId, String operation) {
        String source = storeName.trim() + ":" + transactionId + ":" + operation;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
