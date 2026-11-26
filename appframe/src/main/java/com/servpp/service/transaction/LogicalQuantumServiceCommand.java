package com.servpp.service.transaction;

import java.util.List;

public record LogicalQuantumServiceCommand(
        Operation operation,
        String namespace,
        String resourceName,
        String requestKey,
        String originalRequestKey,
        AtomicGroupShape atomicGroupShape,
        List<Mutation> mutations) {

    public enum Operation {
        ENTANGLE,
        REVERSE_ENTANGLE
    }

    public enum AtomicGroupShape {
        NONE,
        ONE_CONTRIBUTOR_MANY_RECIPIENTS,
        MANY_CONTRIBUTORS_ONE_RECIPIENT,
        NETTED_GROUP
    }

    public record LogicalKey(String namespace, String attributeId, String objectId) {
    }

    public record Definition(String numberType, String minValue, String maxValue, int scale) {
    }

    public record Mutation(LogicalKey logicalKey, Definition definition, String delta) {
    }

    public LogicalQuantumServiceCommand {
        operation = requireNonNull(operation, "operation");
        namespace = requireText(namespace, "namespace");
        resourceName = requireText(resourceName, "resourceName");
        requestKey = requireText(requestKey, "requestKey");
        if (operation == Operation.ENTANGLE) {
            if (originalRequestKey != null) {
                throw new IllegalArgumentException("ENTANGLE must not declare originalRequestKey");
            }
        } else {
            originalRequestKey = requireText(originalRequestKey, "originalRequestKey");
            if (requestKey.equals(originalRequestKey)) {
                throw new IllegalArgumentException("reversal requestKey must differ from originalRequestKey");
            }
        }
        atomicGroupShape = requireNonNull(atomicGroupShape, "atomicGroupShape");
        if (mutations == null || mutations.size() < 2) {
            throw new IllegalArgumentException("logical quantum command requires at least two mutations");
        }
        mutations = List.copyOf(mutations);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
