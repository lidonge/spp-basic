package com.servpp.lang.models.app;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class AppRemoteAdapter implements INamedObject {
    private String name;
    private String adapterName;
    private final Map<String, String> configuration = new LinkedHashMap<>();
    private final Set<String> duplicateConfigurationKeys = new LinkedHashSet<>();

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
}
