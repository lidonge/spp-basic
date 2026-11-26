package com.servpp.service.transaction;

import com.servpp.service.quantum.settlement.LogicalQuantumKey;
import com.servpp.service.quantum.settlement.ProvisionCommand;
import com.servpp.service.quantum.settlement.ProvisionResponse;
import com.servpp.service.quantum.settlement.QuantumDefinition;
import com.servpp.service.quantum.settlement.QuantumMutation;
import com.servpp.service.quantum.settlement.QuantumSettlementService;
import com.servpp.service.quantum.settlement.SettlementCommand;
import com.servpp.service.quantum.settlement.SettlementKey;
import com.servpp.service.quantum.settlement.SettlementResponse;
import com.servpp.service.quantum.settlement.SettlementResponse.Operation;
import com.servpp.service.quantum.settlement.SettlementResponse.Outcome;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns a {@link QuantumEntanglementBoundary} into a {@link SettlementCommand} for any
 * {@link QuantumSettlementService}: it nets the records per logical key, derives the shape and
 * attaches the PHY definitions.
 *
 * <p>Outcomes: SUCCESS marks the boundary. BUSINESS_FAILED, NOT_RECEIVED and UNKNOWN are recorded
 * on the boundary and raised as {@link QuantumServiceOutcomeException}; a service failure or an
 * identity mismatch is UNKNOWN. NOT_RECEIVED is first retried under the same key, up to
 * {@code notReceivedRetries} times (none by default). A service that settles on the caller's own
 * ledger ({@link LedgerQuantumSettlement}) hands over the boundary itself, and its bound
 * rejection ({@link QuantumBoundRejection}) propagates unchanged, keeping its precedence over
 * ordinary writes.
 */
public class SettlementQuantumAccessAdapter implements QuantumAccessAdapter {
    private final QuantumSettlementService service;
    private final String namespace;
    private final Map<String, QuantumDefinition> definitions;
    private final int notReceivedRetries;

    public SettlementQuantumAccessAdapter(QuantumSettlementService service, String namespace,
                                          Map<String, QuantumDefinition> definitionsByAttribute) {
        this(service, namespace, definitionsByAttribute, 0);
    }

    public SettlementQuantumAccessAdapter(QuantumSettlementService service, String namespace,
                                          Map<String, QuantumDefinition> definitionsByAttribute,
                                          int notReceivedRetries) {
        this.service = Objects.requireNonNull(service, "service");
        this.namespace = namespace == null ? "" : namespace.trim();
        this.definitions = definitionsByAttribute == null ? Map.of() : Map.copyOf(definitionsByAttribute);
        if (notReceivedRetries < 0) {
            throw new IllegalArgumentException("notReceivedRetries must not be negative");
        }
        this.notReceivedRetries = notReceivedRetries;
    }

    /** LOCAL: today's journal. */
    public static SettlementQuantumAccessAdapter journal(QuantumJournalStore journalStore) {
        return new SettlementQuantumAccessAdapter(new JournalQuantumSettlement(journalStore), "", Map.of());
    }

    public QuantumSettlementService getService() {
        return service;
    }

    @Override
    public boolean supportsInvocationBounds() {
        return service.capabilities().contains(QuantumSettlementService.Capability.INVOCATION_BOUNDS);
    }

    @Override
    public void executeInAccessScope(Runnable action) {
        service.executeInAccessScope(action);
    }

    @Override
    public void commit(TransactionContext context, QuantumEntanglementBoundary boundary) {
        if (service instanceof LedgerQuantumSettlement ledger) {
            ledger.settleLedger(context, boundary);
            return;
        }
        submit(Operation.SETTLE, context, boundary);
    }

    @Override
    public void reverse(TransactionContext context, QuantumEntanglementBoundary boundary) {
        if (service instanceof LedgerQuantumSettlement ledger) {
            ledger.reverseLedger(context, boundary);
            return;
        }
        submit(Operation.REVERSE, context, boundary);
    }

    @Override
    public void rollback(TransactionContext context, QuantumEntanglementBoundary boundary) {
        if (boundary != null && boundary.getQuantumServiceOutcome() != null) {
            return;
        }
        QuantumAccessAdapter.super.rollback(context, boundary);
    }

    /** Explicitly provisions the given attributes of one object; the whole request is validated first. */
    public List<ProvisionResponse> provisionObject(String objectId, Map<String, ?> initialValues) {
        requireNamespace();
        if (!service.capabilities().contains(QuantumSettlementService.Capability.PROVISIONING)) {
            throw new IllegalStateException("Quantum settlement service " + service.name()
                    + " does not support provisioning.");
        }
        String stableObjectId = requireText(objectId, "objectId");
        if (initialValues == null) {
            throw new IllegalArgumentException("initialValues must not be null");
        }
        List<ProvisionCommand> commands = new ArrayList<>();
        for (Map.Entry<String, ?> entry : initialValues.entrySet()) {
            String attributeId = requireText(entry.getKey(), "attributeId");
            QuantumDefinition definition = definitions.get(attributeId);
            if (definition == null) {
                throw new IllegalArgumentException("Quantum settlement service " + service.name()
                        + " has no definition for " + attributeId + ".");
            }
            commands.add(new ProvisionCommand(new LogicalQuantumKey(namespace, attributeId, stableObjectId),
                    definition, initialValue(entry.getValue(), attributeId)));
        }
        List<ProvisionResponse> responses = new ArrayList<>();
        for (ProvisionCommand command : commands) {
            responses.add(service.provision(command));
        }
        return List.copyOf(responses);
    }

    private void submit(Operation operation, TransactionContext context, QuantumEntanglementBoundary boundary) {
        if (boundary == null) {
            return;
        }
        Optional<SettlementCommand> built = command(operation, context, boundary);
        if (built.isEmpty()) {
            boundary.recordQuantumServiceOutcome(LogicalQuantumServiceResponse.Outcome.SUCCESS);
            mark(operation, boundary);
            return;
        }
        SettlementCommand command = built.get();
        SettlementResponse response = null;
        for (int attempt = 0; attempt <= notReceivedRetries; attempt++) {
            try {
                response = operation == Operation.SETTLE ? service.settle(command) : service.reverse(command);
                response.requireIdentity(command, operation);
            } catch (RuntimeException failure) {
                boundary.recordQuantumServiceOutcome(LogicalQuantumServiceResponse.Outcome.UNKNOWN);
                throw new QuantumServiceOutcomeException(LogicalQuantumServiceResponse.Outcome.UNKNOWN,
                        "Quantum settlement " + operation + " result is UNKNOWN for request "
                                + command.key().requestKey() + ".", failure);
            }
            if (response.outcome() != Outcome.NOT_RECEIVED) {
                break;
            }
        }
        LogicalQuantumServiceResponse.Outcome outcome =
                LogicalQuantumServiceResponse.Outcome.valueOf(response.outcome().name());
        boundary.recordQuantumServiceOutcome(outcome);
        if (response.outcome() == Outcome.SUCCESS) {
            mark(operation, boundary);
            return;
        }
        throw new QuantumServiceOutcomeException(outcome, "Quantum settlement " + operation + " returned "
                + outcome + " for request " + command.key().requestKey()
                + response.failureReason().map(reason -> ": " + reason).orElse("") + ".");
    }

    private void mark(Operation operation, QuantumEntanglementBoundary boundary) {
        if (operation == Operation.SETTLE) {
            boundary.markCommitted();
        } else {
            boundary.markRolledBack();
        }
    }

    /** Nets per logical key, drops zero-net quantities and derives the shape. Empty when nothing nets. */
    Optional<SettlementCommand> command(Operation operation, TransactionContext context,
                                        QuantumEntanglementBoundary boundary) {
        if (context == null) {
            throw new IllegalArgumentException("transaction context must not be null");
        }
        requireNamespace();
        Map<LogicalQuantumKey, BigDecimal> netDeltas = new LinkedHashMap<>();
        for (QuantumConservationRecord record : boundary.getRecords()) {
            netDeltas.merge(new LogicalQuantumKey(namespace, record.getAttributeId(), record.getObjectId()),
                    record.getDeltaValue(), BigDecimal::add);
        }
        List<QuantumMutation> mutations = new ArrayList<>();
        for (Map.Entry<LogicalQuantumKey, BigDecimal> entry : netDeltas.entrySet()) {
            if (entry.getValue().signum() == 0) {
                continue;
            }
            QuantumDefinition definition = definitions.get(entry.getKey().attributeId());
            if (definition == null) {
                throw new IllegalStateException("Quantum service has no binding definition for "
                        + entry.getKey().attributeId() + ".");
            }
            mutations.add(new QuantumMutation(entry.getKey(), definition,
                    entry.getValue().stripTrailingZeros().toPlainString()));
        }
        if (mutations.isEmpty()) {
            return Optional.empty();
        }
        if (mutations.size() < 2) {
            throw new IllegalStateException("Quantum service boundary has only one non-zero logical mutation.");
        }
        BigDecimal net = mutations.stream().map(QuantumMutation::deltaValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (net.signum() != 0) {
            throw new IllegalStateException("Quantum service logical mutations are not conserved: " + net + ".");
        }
        String original = context.getTraceId();
        return Optional.of(new SettlementCommand(
                new SettlementKey(namespace, operation == Operation.SETTLE ? original : original + ":reversal"),
                operation == Operation.SETTLE ? Optional.empty() : Optional.of(original),
                boundary.getServiceId(), SettlementCommand.deriveShape(mutations), mutations));
    }

    private void requireNamespace() {
        if (namespace.isBlank()) {
            throw new IllegalStateException("Quantum settlement service " + service.name()
                    + " requires a namespace.");
        }
    }

    private static String initialValue(Object value, String attributeId) {
        if (value == null) {
            throw new IllegalArgumentException("Initial quantum value for " + attributeId + " must not be null.");
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof BigInteger integer) {
            return integer.toString();
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double || value instanceof CharSequence) {
            return value.toString();
        }
        throw new IllegalArgumentException(
                "Initial quantum value for " + attributeId + " must be a number or decimal string.");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
