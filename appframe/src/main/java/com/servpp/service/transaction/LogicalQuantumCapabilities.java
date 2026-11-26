package com.servpp.service.transaction;

import java.util.List;

/** QService's {@code GET /capabilities} answer. */
public record LogicalQuantumCapabilities(List<String> capabilities, String contract) {
    public static final String CONTRACT = "spp-qservice-logical/1";

    public LogicalQuantumCapabilities {
        capabilities = capabilities == null ? null : List.copyOf(capabilities);
    }
}
