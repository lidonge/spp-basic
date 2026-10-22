package com.servpp.lang.models.app;

import java.util.ArrayList;
import java.util.List;

/** APP's deployable environment; resource endpoints remain in PHY. */
public final class DeploymentEnvironment {
    private final String name;
    private final List<String> domains = new ArrayList<>();
    private String ordinaryUpdates;
    public String getOrdinaryUpdates() { return ordinaryUpdates; }
    public void ordinaryUpdates(String value) {
        if (ordinaryUpdates != null) throw new IllegalArgumentException("Duplicate environment ordinaryUpdates: " + name);
        ordinaryUpdates = value;
    }
    private String transactionMode;
    private String transactionResource;
    private String quantumSystem;
    private String quantumResource;
    public DeploymentEnvironment(String name) { this.name = name; }
    public String getName() { return name; }
    public List<String> getDomains() { return domains; }
    public String getTransactionMode() { return transactionMode; }
    public String getTransactionResource() { return transactionResource; }
    public void transactions(String mode, String resource) {
        if (transactionMode != null) throw new IllegalArgumentException("Duplicate environment transaction policy: " + name);
        transactionMode = mode; transactionResource = resource;
    }
    public String getQuantumSystem() { return quantumSystem; }
    public String getQuantumResource() { return quantumResource; }
    public void quantum(String system, String resource) {
        if (quantumSystem != null) throw new IllegalArgumentException("Duplicate environment quantum policy: " + name);
        quantumSystem = system; quantumResource = resource;
    }
}
