package com.servpp.service.transaction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class TransactionContext {
    private final String id;
    private final Map<String, TransactionMode> resourceModes;
    private final QuantumEntanglementBoundary quantumEntanglementBoundary;
    private final QuantumJournalStore quantumJournalStore;
    private final String globalSerialNumber;
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    public TransactionContext(Map<String, TransactionMode> resourceModes) {
        this(resourceModes, new InMemoryQuantumJournalStore());
    }

    public TransactionContext(Map<String, TransactionMode> resourceModes, QuantumJournalStore quantumJournalStore) {
        this(UUID.randomUUID().toString(),
                BusinessTraceContext.currentGlobalSerialNumber().orElse(null),
                resourceModes,
                quantumJournalStore);
    }

    private TransactionContext(String id,
                               String globalSerialNumber,
                               Map<String, TransactionMode> resourceModes,
                               QuantumJournalStore quantumJournalStore) {
        this.id = id;
        this.resourceModes = Collections.unmodifiableMap(new LinkedHashMap<>(resourceModes));
        this.quantumEntanglementBoundary = createQuantumEntanglementBoundary(resourceModes);
        this.quantumJournalStore = quantumJournalStore;
        this.globalSerialNumber = globalSerialNumber;
    }

    static TransactionContext reversal(String id,
                                       String globalSerialNumber,
                                       List<String> quantumResourceIds,
                                       QuantumJournalStore quantumJournalStore) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Quantum reversal transaction id must not be blank");
        }
        Map<String, TransactionMode> resourceModes = new LinkedHashMap<>();
        for (String resourceId : quantumResourceIds == null ? List.<String>of() : quantumResourceIds) {
            if (resourceId == null || resourceId.isBlank()) {
                throw new IllegalArgumentException("Quantum reversal resource id must not be blank");
            }
            resourceModes.put(resourceId, TransactionMode.QUANTUM);
        }
        if (resourceModes.isEmpty()) {
            throw new IllegalArgumentException("Quantum reversal requires at least one resource id");
        }
        return new TransactionContext(
                id.trim(),
                globalSerialNumber == null || globalSerialNumber.isBlank()
                        ? null
                        : globalSerialNumber.trim(),
                resourceModes,
                quantumJournalStore == null ? new InMemoryQuantumJournalStore() : quantumJournalStore);
    }

    public String getId() {
        return id;
    }

    public Map<String, TransactionMode> getResourceModes() {
        return resourceModes;
    }

    public QuantumEntanglementBoundary getQuantumEntanglementBoundary() {
        return quantumEntanglementBoundary;
    }

    public QuantumJournalStore getQuantumJournalStore() {
        return quantumJournalStore;
    }

    public String getGlobalSerialNumber() {
        return globalSerialNumber;
    }

    public String getTraceId() {
        return globalSerialNumber == null || globalSerialNumber.isBlank() ? id : globalSerialNumber;
    }

    @SuppressWarnings("unchecked")
    public synchronized <T> T getOrCreateAttribute(String name, Supplier<T> factory) {
        return (T) attributes.computeIfAbsent(name, ignored -> factory.get());
    }

    private QuantumEntanglementBoundary createQuantumEntanglementBoundary(Map<String, TransactionMode> resourceModes) {
        List<String> quantumResources = resourceModes.entrySet().stream()
                .filter(entry -> entry.getValue() == TransactionMode.QUANTUM || entry.getValue() == TransactionMode.LOCAL)
                .map(Map.Entry::getKey)
                .toList();
        if (quantumResources.isEmpty()) {
            return null;
        }
        return new QuantumEntanglementBoundary(String.join("+", quantumResources), quantumResources);
    }
}
