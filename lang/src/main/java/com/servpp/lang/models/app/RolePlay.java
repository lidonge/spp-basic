package com.servpp.lang.models.app;

import java.util.List;
import java.util.Map;

/** An APP-selected provider of a consumer role, with consumer-to-provider field names. */
public record RolePlay(String role, String providerDomain, String providerType, Map<String, String> fields,
                       Map<String, Map<String, List<String>>> codeTables) {
    public RolePlay {
        codeTables = Map.copyOf(codeTables);
        fields = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(fields));
    }
}
