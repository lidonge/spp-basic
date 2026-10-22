package com.servpp.service.runtime;

import java.util.Map;
import java.util.Set;

public class CatalogRemoteServiceAdapter implements RemoteServiceAdapter {
    private final PhysicalRuntimeCatalog.RemoteAdapter adapter;

    public CatalogRemoteServiceAdapter(PhysicalRuntimeCatalog.RemoteAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public Set<String> serviceNames() {
        return Set.of(adapter.serviceName());
    }

    @Override
    public String implementationType() {
        return adapter.adapterName();
    }

    @Override
    public Map<String, String> configuration() {
        return adapter.configuration();
    }
}
