package com.servpp.service.runtime;

import java.util.Map;

public record PhysicalBackendAssignment(String serviceName,
                                        String transactionKind,
                                        String targetName,
                                        String backendName,
                                        String adapterName,
                                        boolean explicitBinding,
                                        String bindingScope,
                                        String bindingKey,
                                        boolean resolved,
                                        boolean adapterAvailable,
                                        boolean adapterSupportsTransaction,
                                        String adapterImplementationType,
                                        Map<String, String> adapterConfiguration) {
    public PhysicalBackendAssignment {
        serviceName = normalize(serviceName);
        transactionKind = normalize(transactionKind);
        targetName = normalize(targetName);
        backendName = normalizeNullable(backendName);
        adapterName = normalizeNullable(adapterName);
        bindingScope = normalizeWithDefault(bindingScope, "UNRESOLVED");
        bindingKey = normalize(bindingKey);
        adapterImplementationType = normalizeNullable(adapterImplementationType);
        adapterConfiguration = adapterConfiguration == null ? Map.of() : Map.copyOf(adapterConfiguration);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeWithDefault(String value, String defaultValue) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? defaultValue : normalized;
    }
}
