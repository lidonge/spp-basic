package com.servpp.service.transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public static Optional<LogicalQuantumServiceCommand> from(
            Operation operation,
            String namespace,
            TransactionContext context,
            QuantumEntanglementBoundary boundary,
            List<QuantumServiceDefinition> definitions) {
        if (context == null) {
            throw new IllegalArgumentException("transaction context must not be null");
        }
        if (boundary == null) {
            return Optional.empty();
        }
        Map<String, QuantumServiceDefinition> definitionsByAttribute = definitionsByAttribute(definitions);
        Map<LogicalKey, BigDecimal> netDeltas = new LinkedHashMap<>();
        for (QuantumConservationRecord record : boundary.getRecords()) {
            LogicalKey key = new LogicalKey(requireText(namespace, "namespace"),
                    record.getAttributeId(), record.getObjectId());
            netDeltas.merge(key, record.getDeltaValue(), BigDecimal::add);
        }
        List<Mutation> mutations = new ArrayList<>();
        for (Map.Entry<LogicalKey, BigDecimal> entry : netDeltas.entrySet()) {
            if (entry.getValue().signum() == 0) {
                continue;
            }
            QuantumServiceDefinition definition = definitionsByAttribute.get(entry.getKey().attributeId());
            if (definition == null) {
                throw new IllegalStateException("Quantum service has no binding definition for "
                        + entry.getKey().attributeId() + ".");
            }
            mutations.add(new Mutation(entry.getKey(),
                    new Definition(definition.numberType(), definition.minValue(),
                            definition.maxValue(), definition.scale()),
                    entry.getValue().stripTrailingZeros().toPlainString()));
        }
        if (mutations.isEmpty()) {
            return Optional.empty();
        }
        if (mutations.size() < 2) {
            throw new IllegalStateException("Quantum service boundary has only one non-zero logical mutation.");
        }
        BigDecimal netDelta = mutations.stream()
                .map(mutation -> new BigDecimal(mutation.delta()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (netDelta.signum() != 0) {
            throw new IllegalStateException("Quantum service logical mutations are not conserved: " + netDelta + ".");
        }
        String originalRequestKey = context.getTraceId();
        String requestKey = operation == Operation.ENTANGLE
                ? originalRequestKey
                : originalRequestKey + ":reversal";
        return Optional.of(new LogicalQuantumServiceCommand(
                operation,
                namespace,
                boundary.getServiceId(),
                requestKey,
                operation == Operation.ENTANGLE ? null : originalRequestKey,
                shape(mutations),
                mutations));
    }

    private static Map<String, QuantumServiceDefinition> definitionsByAttribute(
            List<QuantumServiceDefinition> definitions) {
        Map<String, QuantumServiceDefinition> byAttribute = new LinkedHashMap<>();
        for (QuantumServiceDefinition definition : definitions == null ? List.<QuantumServiceDefinition>of()
                : definitions) {
            QuantumServiceDefinition previous = byAttribute.putIfAbsent(definition.attributeId(), definition);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate quantum service definition "
                        + definition.attributeId() + ".");
            }
        }
        return byAttribute;
    }

    private static AtomicGroupShape shape(List<Mutation> mutations) {
        if (mutations.size() == 2) {
            return AtomicGroupShape.NONE;
        }
        long contributors = mutations.stream()
                .filter(mutation -> new BigDecimal(mutation.delta()).signum() < 0)
                .count();
        long recipients = mutations.size() - contributors;
        if (contributors == 1 && recipients > 1) {
            return AtomicGroupShape.ONE_CONTRIBUTOR_MANY_RECIPIENTS;
        }
        if (contributors > 1 && recipients == 1) {
            return AtomicGroupShape.MANY_CONTRIBUTORS_ONE_RECIPIENT;
        }
        return AtomicGroupShape.NETTED_GROUP;
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
