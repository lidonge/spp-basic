package com.servpp.lang.models.app;

import java.util.Map;

/** An APP-selected provider of a consumer role, with consumer-to-provider field names. */
public record RolePlay(String role, String providerDomain, String providerType, Map<String, String> fields) {
    public RolePlay {
        fields = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(fields));
    }
}
