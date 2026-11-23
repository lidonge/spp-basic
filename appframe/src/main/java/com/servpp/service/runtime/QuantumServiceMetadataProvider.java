package com.servpp.service.runtime;

import java.util.List;

public interface QuantumServiceMetadataProvider {
    List<String> getQuantumAttributeIds();

    default boolean hasQuantumTransfers() { return false; }
}
