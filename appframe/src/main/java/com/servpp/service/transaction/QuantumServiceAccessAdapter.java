package com.servpp.service.transaction;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class QuantumServiceAccessAdapter implements QuantumAccessAdapter {
    private final String storeName;
    private final String endpoint;
    private final String reversalMode;
    private final QuantumServiceClient client;
    private final String namespace;
    private final List<QuantumServiceDefinition> definitions;
    private final LogicalQuantumServiceClient logicalClient;
    private final LogicalQuantumProvisioningClient provisioningClient;

    public QuantumServiceAccessAdapter(String storeName, String endpoint, String reversalMode) {
        this(storeName, endpoint, reversalMode, null);
    }

    public QuantumServiceAccessAdapter(String storeName, String endpoint, String reversalMode,
                                       QuantumServiceClient client) {
        this.storeName = storeName == null ? "" : storeName.trim();
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.reversalMode = reversalMode == null || reversalMode.isBlank() ? "NEGATIVE_DELTA" : reversalMode.trim();
        this.client = client;
        this.namespace = "";
        this.definitions = List.of();
        this.logicalClient = null;
        this.provisioningClient = null;
    }

    public QuantumServiceAccessAdapter(String storeName, String endpoint, String reversalMode,
                                       String namespace, List<QuantumServiceDefinition> definitions,
                                       LogicalQuantumServiceClient logicalClient) {
        this(storeName, endpoint, reversalMode, namespace, definitions,
                logicalClient, null);
    }

    public QuantumServiceAccessAdapter(
            String storeName, String endpoint, String reversalMode,
            String namespace, List<QuantumServiceDefinition> definitions,
            LogicalQuantumServiceClient logicalClient,
            LogicalQuantumProvisioningClient provisioningClient) {
        this.storeName = storeName == null ? "" : storeName.trim();
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.reversalMode = reversalMode == null || reversalMode.isBlank()
                ? "SERVICE_REVERSAL"
                : reversalMode.trim();
        this.client = null;
        this.namespace = namespace == null ? "" : namespace.trim();
        this.definitions = definitions == null ? List.of() : List.copyOf(definitions);
        this.logicalClient = logicalClient;
        this.provisioningClient = provisioningClient;
    }

    public List<LogicalQuantumProvisioningResponse> provisionObject(
            String objectId, Map<String, ?> initialValues) {
        requireLogicalConfiguration();
        if (provisioningClient == null) {
            throw new IllegalStateException(
                    "Logical quantum service access adapter for store " + storeName
                            + " requires a provisioning client.");
        }
        String stableObjectId = requireText(objectId, "objectId");
        if (initialValues == null) {
            throw new IllegalArgumentException("initialValues must not be null");
        }
        Map<String, QuantumServiceDefinition> byAttribute = definitionsByAttribute();
        List<LogicalQuantumProvisioningRequest> requests = new ArrayList<>();
        for (Map.Entry<String, ?> entry : initialValues.entrySet()) {
            String attributeId = requireText(entry.getKey(), "attributeId");
            QuantumServiceDefinition definition = byAttribute.get(attributeId);
            if (definition == null) {
                throw new IllegalArgumentException(
                        "Quantum service store " + storeName
                                + " has no definition for " + attributeId + ".");
            }
            requests.add(new LogicalQuantumProvisioningRequest(
                    new LogicalQuantumServiceCommand.LogicalKey(
                            namespace, attributeId, stableObjectId),
                    new LogicalQuantumServiceCommand.Definition(
                            definition.numberType(), definition.minValue(),
                            definition.maxValue(), definition.scale()),
                    initialValue(entry.getValue(), attributeId)));
        }
        List<LogicalQuantumProvisioningResponse> responses = new ArrayList<>();
        for (LogicalQuantumProvisioningRequest request : requests) {
            responses.add(provisioningClient.provision(request));
        }
        return List.copyOf(responses);
    }

    @Override
    public boolean supportsInvocationBounds() {
        return logicalClient == null && client != null && client.supportsInvocationBounds();
    }

    @Override
    public void commit(TransactionContext context, QuantumEntanglementBoundary boundary) {
        if (logicalClient != null) {
            executeLogical(LogicalQuantumServiceCommand.Operation.ENTANGLE, context, boundary);
            return;
        }
        requireSupportedReversalMode();
        if (client == null) {
            throw new IllegalStateException("Quantum service access adapter for store " + storeName
                    + " requires a concrete service client before committing quantum boundary to " + endpoint + ".");
        }
        if (boundary == null) {
            return;
        }
        client.commit(QuantumServiceCommitRequest.from(storeName, endpoint, reversalMode, context, boundary));
        boundary.markCommitted();
    }

    @Override
    public void reverse(TransactionContext context, QuantumEntanglementBoundary boundary) {
        if (logicalClient != null) {
            executeLogical(LogicalQuantumServiceCommand.Operation.REVERSE_ENTANGLE, context, boundary);
            return;
        }
        requireSupportedReversalMode();
        if (client == null) {
            throw new IllegalStateException("Quantum service access adapter for store " + storeName
                    + " requires a concrete service client before reversing quantum boundary at " + endpoint + ".");
        }
        if (boundary == null) {
            return;
        }
        client.reverse(QuantumServiceReversalRequest.from(storeName, endpoint, reversalMode, context, boundary));
        boundary.markRolledBack();
    }

    @Override
    public void rollback(TransactionContext context, QuantumEntanglementBoundary boundary) {
        if (logicalClient != null && boundary != null && boundary.getQuantumServiceOutcome() != null) {
            return;
        }
        if (boundary != null) {
            boundary.markRolledBack();
        }
    }

    private void executeLogical(LogicalQuantumServiceCommand.Operation operation,
                                TransactionContext context, QuantumEntanglementBoundary boundary) {
        requireLogicalConfiguration();
        Optional<LogicalQuantumServiceCommand> command =
                LogicalQuantumServiceCommand.from(operation, namespace, context, boundary, definitions);
        if (command.isEmpty()) {
            boundary.recordQuantumServiceOutcome(LogicalQuantumServiceResponse.Outcome.SUCCESS);
            markBoundary(operation, boundary);
            return;
        }
        LogicalQuantumServiceResponse response;
        try {
            response = logicalClient.execute(command.get());
        } catch (RuntimeException e) {
            boundary.recordQuantumServiceOutcome(LogicalQuantumServiceResponse.Outcome.UNKNOWN);
            throw new QuantumServiceOutcomeException(LogicalQuantumServiceResponse.Outcome.UNKNOWN,
                    "QService logical " + operation + " result is UNKNOWN for request "
                            + command.get().requestKey() + ".", e);
        }
        LogicalQuantumServiceResponse.Outcome outcome = response.boundaryResponse().status();
        boundary.recordQuantumServiceOutcome(outcome);
        if (outcome == LogicalQuantumServiceResponse.Outcome.SUCCESS) {
            markBoundary(operation, boundary);
            return;
        }
        throw new QuantumServiceOutcomeException(outcome,
                "QService logical " + operation + " returned " + outcome
                        + " for request " + command.get().requestKey() + ".");
    }

    private void markBoundary(LogicalQuantumServiceCommand.Operation operation,
                              QuantumEntanglementBoundary boundary) {
        if (boundary == null) {
            return;
        }
        if (operation == LogicalQuantumServiceCommand.Operation.ENTANGLE) {
            boundary.markCommitted();
        } else {
            boundary.markRolledBack();
        }
    }

    private void requireLogicalConfiguration() {
        if (!"SERVICE_REVERSAL".equals(reversalMode)) {
            throw new IllegalStateException("Logical quantum service access adapter for store " + storeName
                    + " requires SERVICE_REVERSAL mode, but was " + reversalMode + ".");
        }
        if (namespace.isBlank()) {
            throw new IllegalStateException("Logical quantum service access adapter for store " + storeName
                    + " requires a namespace.");
        }
    }

    private void requireSupportedReversalMode() {
        if (!"NEGATIVE_DELTA".equals(reversalMode)) {
            throw new IllegalStateException("Quantum service access adapter for store " + storeName
                    + " supports only NEGATIVE_DELTA reversal mode, but was " + reversalMode + ".");
        }
    }

    public String getStoreName() {
        return storeName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getReversalMode() {
        return reversalMode;
    }

    public boolean hasClient() {
        return client != null || logicalClient != null;
    }

    public boolean hasProvisioningClient() {
        return provisioningClient != null;
    }

    private Map<String, QuantumServiceDefinition> definitionsByAttribute() {
        Map<String, QuantumServiceDefinition> byAttribute = new LinkedHashMap<>();
        for (QuantumServiceDefinition definition : definitions) {
            QuantumServiceDefinition previous =
                    byAttribute.putIfAbsent(definition.attributeId(), definition);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate quantum service definition "
                                + definition.attributeId() + ".");
            }
        }
        return byAttribute;
    }

    private static String initialValue(Object value, String attributeId) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Initial quantum value for " + attributeId + " must not be null.");
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof BigInteger integer) {
            return integer.toString();
        }
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double
                || value instanceof CharSequence) {
            return value.toString();
        }
        throw new IllegalArgumentException(
                "Initial quantum value for " + attributeId
                        + " must be a number or decimal string.");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
