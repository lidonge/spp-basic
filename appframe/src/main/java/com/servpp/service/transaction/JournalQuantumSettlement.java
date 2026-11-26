package com.servpp.service.transaction;

import com.servpp.service.quantum.settlement.LogicalQuantumKey;
import com.servpp.service.quantum.settlement.ProvisionCommand;
import com.servpp.service.quantum.settlement.ProvisionResponse;
import com.servpp.service.quantum.settlement.QuantumMutation;
import com.servpp.service.quantum.settlement.QuantumSettlementService;
import com.servpp.service.quantum.settlement.SettlementCommand;
import com.servpp.service.quantum.settlement.SettlementKey;
import com.servpp.service.quantum.settlement.SettlementResponse;
import com.servpp.service.quantum.settlement.SettlementResponse.Operation;
import com.servpp.service.quantum.settlement.SettlementResponse.Outcome;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The LOCAL quantum journal behind the unified settlement interface.
 *
 * <p>The runtime's own settlement ({@link LedgerQuantumSettlement}) is today's journal
 * behaviour, unchanged: it settles on the owning JDBC connection inside
 * {@link #executeInAccessScope}. The command methods settle a netted command against the
 * locked current values of a JDBC journal and journal it under {@code namespace/requestKey}.
 */
public class JournalQuantumSettlement implements QuantumSettlementService, LedgerQuantumSettlement {
    static final String DEFAULT_NAME = "journal";
    private static final String GROUP = "settlement";

    private final String name;
    private final QuantumJournalStore journalStore;

    public JournalQuantumSettlement(QuantumJournalStore journalStore) {
        this(DEFAULT_NAME, journalStore);
    }

    public JournalQuantumSettlement(String name, QuantumJournalStore journalStore) {
        this.name = Objects.requireNonNull(name, "name");
        this.journalStore = Objects.requireNonNull(journalStore, "journalStore");
    }

    public QuantumJournalStore getJournalStore() {
        return journalStore;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Set<Capability> capabilities() {
        Set<Capability> capabilities = EnumSet.of(Capability.ONE_PHASE, Capability.SHARED_LOCAL_TRANSACTION);
        if (journalStore.supportsInvocationBounds()) capabilities.add(Capability.INVOCATION_BOUNDS);
        if (journalStore instanceof JdbcQuantumJournalStore) capabilities.add(Capability.PROVISIONING);
        return capabilities;
    }

    @Override
    public void executeInAccessScope(Runnable action) {
        if (journalStore instanceof JdbcQuantumJournalStore jdbcStore) {
            jdbcStore.executeInTransaction(action);
            return;
        }
        action.run();
    }

    @Override
    public void settleLedger(TransactionContext context, QuantumEntanglementBoundary boundary) {
        if (boundary == null) {
            return;
        }
        boundary.markCommitted();
        journalStore.append(context, boundary);
    }

    @Override
    public void reverseLedger(TransactionContext context, QuantumEntanglementBoundary boundary) {
        if (boundary == null) {
            return;
        }
        if (journalStore instanceof JdbcQuantumJournalStore jdbcStore) jdbcStore.reverseDeltas(context, boundary);
        else journalStore.append(context, boundary.negativeDeltaReversal());
        boundary.markRolledBack();
    }

    @Override
    public SettlementResponse settle(SettlementCommand command) {
        command.requireForward();
        JdbcQuantumJournalStore jdbc = jdbc();
        String transactionId = transactionId(command.key());
        List<QuantumJournalEntry> prior = jdbc.findByTransactionId(transactionId);
        if (!prior.isEmpty()) {
            return response(command, Operation.SETTLE, sameDeltas(prior, command.mutations())
                    && prior.get(0).getStatus() == QuantumEntanglementBoundary.Status.COMMITTED
                    ? Outcome.SUCCESS : Outcome.BUSINESS_FAILED, transactionId,
                    "IDEMPOTENCY_PAYLOAD_MISMATCH");
        }
        QuantumEntanglementBoundary boundary = new QuantumEntanglementBoundary(
                command.resourceName(), List.of(command.resourceName()));
        TransactionContext context = context(transactionId, command);
        String[] failure = new String[1];
        try {
            jdbc.executeInTransaction(() -> {
                for (QuantumMutation mutation : command.mutations()) {
                    LogicalQuantumKey key = mutation.key();
                    BigDecimal before = jdbc.findCurrentValueForUpdate(key.attributeId(), key.objectId())
                            .orElseThrow(() -> new NotProvisioned(key));
                    BigDecimal delta = mutation.deltaValue();
                    boundary.addRecord(new QuantumConservationRecord(GROUP, key.attributeId(), key.objectId(),
                            before, delta, before.add(delta),
                            mutation.invocationBounds().map(i -> i.min() == null ? null : new BigDecimal(i.min()))
                                    .orElse(null),
                            mutation.invocationBounds().map(i -> i.max() == null ? null : new BigDecimal(i.max()))
                                    .orElse(null)));
                }
                boundary.validateConservation();
                boundary.markCommitted();
                jdbc.append(context, boundary);
            });
        } catch (QuantumBoundRejection rejected) {
            failure[0] = rejected.getFailureReason();
        } catch (NotProvisioned missing) {
            failure[0] = "QUANTUM_NOT_PROVISIONED: " + missing.key;
        }
        return failure[0] == null
                ? response(command, Operation.SETTLE, Outcome.SUCCESS, transactionId, null)
                : response(command, Operation.SETTLE, Outcome.BUSINESS_FAILED, null, failure[0]);
    }

    @Override
    public SettlementResponse reverse(SettlementCommand reversal) {
        reversal.requireReversal();
        JdbcQuantumJournalStore jdbc = jdbc();
        String namespace = reversal.key().namespace();
        String originalId = transactionId(new SettlementKey(namespace, reversal.originalRequestKey().orElseThrow()));
        String reversalId = transactionId(reversal.key());
        List<QuantumJournalEntry> original = jdbc.findByTransactionId(originalId);
        if (original.isEmpty()) {
            return response(reversal, Operation.REVERSE, Outcome.BUSINESS_FAILED, null, "ORIGINAL_NOT_FOUND");
        }
        if (original.get(0).getStatus() != QuantumEntanglementBoundary.Status.COMMITTED) {
            return response(reversal, Operation.REVERSE, Outcome.BUSINESS_FAILED, null, "ORIGINAL_NOT_SUCCESS");
        }
        if (!sameDeltas(original, reversal.mutations())) {
            return response(reversal, Operation.REVERSE, Outcome.BUSINESS_FAILED, null,
                    "ORIGINAL_PAYLOAD_MISMATCH");
        }
        QuantumEntanglementBoundary boundary = new QuantumEntanglementBoundary(
                original.get(0).getBoundaryServiceId(), List.of(reversal.resourceName()));
        for (QuantumJournalEntry entry : original) {
            boundary.addRecord(new QuantumConservationRecord(entry.getGroupId(), entry.getAttributeId(),
                    entry.getObjectId(), entry.getBeforeValue(), entry.getDeltaValue(), entry.getAfterValue()));
        }
        boundary.markCommitted();
        reverseLedger(context(reversalId, reversal), boundary);
        return response(reversal, Operation.REVERSE, Outcome.SUCCESS, reversalId, null);
    }

    @Override
    public SettlementResponse query(SettlementKey key) {
        List<QuantumJournalEntry> entries = journalStore.findByTransactionId(transactionId(key));
        return new SettlementResponse(key, Optional.empty(), Operation.QUERY,
                entries.isEmpty() ? Outcome.NOT_RECEIVED : Outcome.SUCCESS,
                entries.isEmpty() ? Optional.empty() : Optional.of(transactionId(key)), Optional.empty());
    }

    @Override
    public ProvisionResponse provision(ProvisionCommand command) {
        JdbcQuantumJournalStore jdbc = jdbc();
        LogicalQuantumKey key = command.key();
        BigDecimal initial = new BigDecimal(command.initialValue());
        Optional<BigDecimal> existing = jdbc.findCurrentValue(key.attributeId(), key.objectId());
        Outcome outcome = Outcome.SUCCESS;
        if (existing.isEmpty()) {
            jdbc.executeInTransaction(() -> jdbc.insertInitialValue(key.attributeId(), key.objectId(), initial));
        } else if (existing.get().compareTo(initial) != 0) {
            outcome = Outcome.BUSINESS_FAILED;
        }
        return new ProvisionResponse(key, command.definition(), command.expectedPhysicalInitialValue(), outcome);
    }

    private JdbcQuantumJournalStore jdbc() {
        if (journalStore instanceof JdbcQuantumJournalStore jdbc) {
            return jdbc;
        }
        throw new UnsupportedOperationException(
                "Journal settlement commands require a JDBC journal store: " + name + ".");
    }

    private static String transactionId(SettlementKey key) {
        return key.namespace() + "/" + key.requestKey();
    }

    private TransactionContext context(String transactionId, SettlementCommand command) {
        return TransactionContext.reversal(transactionId, null, List.of(command.resourceName()), journalStore);
    }

    private static boolean sameDeltas(List<QuantumJournalEntry> entries, List<QuantumMutation> mutations) {
        Map<String, BigDecimal> journaled = new LinkedHashMap<>();
        for (QuantumJournalEntry entry : entries) {
            journaled.merge(entry.getAttributeId() + "\u0000" + entry.getObjectId(), entry.getDeltaValue(),
                    BigDecimal::add);
        }
        journaled.values().removeIf(value -> value.signum() == 0);
        Map<String, BigDecimal> requested = new HashMap<>();
        for (QuantumMutation mutation : mutations) {
            requested.put(mutation.key().attributeId() + "\u0000" + mutation.key().objectId(),
                    mutation.deltaValue());
        }
        if (journaled.size() != requested.size()) return false;
        for (Map.Entry<String, BigDecimal> entry : journaled.entrySet()) {
            BigDecimal other = requested.get(entry.getKey());
            if (other == null || other.compareTo(entry.getValue()) != 0) return false;
        }
        return true;
    }

    private static SettlementResponse response(SettlementCommand command, Operation operation, Outcome outcome,
                                               String receipt, String reason) {
        return new SettlementResponse(command.key(), command.originalRequestKey(), operation,
                outcome, Optional.ofNullable(receipt), outcome == Outcome.SUCCESS ? Optional.empty()
                : Optional.ofNullable(reason));
    }

    private static final class NotProvisioned extends RuntimeException {
        private final LogicalQuantumKey key;

        NotProvisioned(LogicalQuantumKey key) {
            super("QUANTUM_NOT_PROVISIONED", null, false, false);
            this.key = key;
        }
    }
}
