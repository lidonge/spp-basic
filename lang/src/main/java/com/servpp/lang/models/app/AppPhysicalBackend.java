package com.servpp.lang.models.app;

import com.servpp.lang.models.Annotation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppPhysicalBackend extends Annotation implements INamedObject {
    private String name;
    private String adapterName;
    private String quantumStoreName;
    private final List<AppPhysicalBinding> bindings = new ArrayList<>();
    private final Map<String, String> configuration = new LinkedHashMap<>();
    private final Set<String> duplicateBindingKeys = new LinkedHashSet<>();
    private final Set<String> duplicateConfigurationKeys = new LinkedHashSet<>();
    private final Set<String> duplicateQuantumStoreReferences = new LinkedHashSet<>();

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

    public String getQuantumStoreName() {
        return quantumStoreName;
    }

    public void setQuantumStoreName(String quantumStoreName) {
        if (this.quantumStoreName != null) {
            duplicateQuantumStoreReferences.add("quantumStore");
        }
        this.quantumStoreName = quantumStoreName;
    }

    public Set<String> getDuplicateQuantumStoreReferences() {
        return duplicateQuantumStoreReferences;
    }

    public List<AppPhysicalBinding> getBindings() {
        return bindings;
    }

    public void addBinding(AppPhysicalBinding binding) {
        for (AppPhysicalBinding existing : bindings) {
            if (existing.key().equals(binding.key())) {
                duplicateBindingKeys.add(binding.key());
                break;
            }
        }
        bindings.add(binding);
    }

    public Set<String> getDuplicateBindingKeys() {
        return duplicateBindingKeys;
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
}
