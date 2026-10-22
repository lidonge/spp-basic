package com.servpp.lang.models.app;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class AppQuantumStoreAdapter implements INamedObject {
    private String name;
    private String adapterName;
    private final Map<String, String> configuration = new LinkedHashMap<>();
    private final Set<String> duplicateConfigurationKeys = new LinkedHashSet<>();
    private final List<AppQuantumDefinition> quantumDefinitions = new ArrayList<>();
    private final Set<String> duplicateQuantumDefinitionAttributeIds = new LinkedHashSet<>();

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAdapterName() {
        return adapterName;
    }

    public void setAdapterName(String adapterName) {
        this.adapterName = adapterName;
    }

    public Map<String, String> getConfiguration() {
        return configuration;
    }

    public void putConfiguration(String key, String value) {
        if (configuration.containsKey(key)) {
            duplicateConfigurationKeys.add(key);
        }
        configuration.put(key, value);
    }

    public Set<String> getDuplicateConfigurationKeys() {
        return duplicateConfigurationKeys;
    }

    public List<AppQuantumDefinition> getQuantumDefinitions() {
        return List.copyOf(quantumDefinitions);
    }

    public void addQuantumDefinition(AppQuantumDefinition definition) {
        for (AppQuantumDefinition existing : quantumDefinitions) {
            if (existing.getAttributeId().equals(definition.getAttributeId())) {
                duplicateQuantumDefinitionAttributeIds.add(definition.getAttributeId());
                break;
            }
        }
        quantumDefinitions.add(definition);
    }

    public Set<String> getDuplicateQuantumDefinitionAttributeIds() {
        return Set.copyOf(duplicateQuantumDefinitionAttributeIds);
    }
}
