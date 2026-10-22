package com.servpp.service.runtime;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RemoteServiceAdapter {
    Set<String> serviceNames();

    default boolean supportsService(String serviceName) {
        return serviceNames().contains(serviceName);
    }

    default Object execute(String serviceName, Object... arguments) {
        throw new UnsupportedOperationException("Remote service " + serviceName
                + " adapter " + implementationType() + " does not implement execute.");
    }

    default boolean executable() {
        try {
            return getClass().getMethod("execute", String.class, Object[].class)
                    .getDeclaringClass() != RemoteServiceAdapter.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    default List<String> validationErrors(String serviceName) {
        return List.of();
    }

    default String implementationType() {
        return getClass().getName();
    }

    default Map<String, String> configuration() {
        return Map.of();
    }
}
