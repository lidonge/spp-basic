package com.servpp.service.transaction;

/** Legacy HTTP_JSON quantum service adapter (NEGATIVE_DELTA reversal). The logical protocol runs through the settlement bridge. */
public class QuantumServiceAccessAdapter implements QuantumAccessAdapter {
    private final String storeName;
    private final String endpoint;
    private final String reversalMode;
    private final QuantumServiceClient client;

    public QuantumServiceAccessAdapter(String storeName, String endpoint, String reversalMode) {
        this(storeName, endpoint, reversalMode, null);
    }

    public QuantumServiceAccessAdapter(String storeName, String endpoint, String reversalMode,
                                       QuantumServiceClient client) {
        this.storeName = storeName == null ? "" : storeName.trim();
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.reversalMode = reversalMode == null || reversalMode.isBlank() ? "NEGATIVE_DELTA" : reversalMode.trim();
        this.client = client;
    }

    @Override
    public boolean supportsInvocationBounds() {
        return client != null && client.supportsInvocationBounds();
    }

    @Override
    public void commit(TransactionContext context, QuantumEntanglementBoundary boundary) {
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
        return client != null;
    }
}
