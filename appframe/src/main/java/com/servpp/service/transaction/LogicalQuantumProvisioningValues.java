package com.servpp.service.transaction;

import com.servpp.service.quantum.settlement.QuantumDefinition;

final class LogicalQuantumProvisioningValues {
    private LogicalQuantumProvisioningValues() {
    }

    static LogicalQuantumServiceCommand.LogicalKey requireLogicalKey(
            LogicalQuantumServiceCommand.LogicalKey logicalKey) {
        if (logicalKey == null) {
            throw new IllegalArgumentException("logicalKey must not be null");
        }
        return new LogicalQuantumServiceCommand.LogicalKey(
                requireText(logicalKey.namespace(), "namespace"),
                requireText(logicalKey.attributeId(), "attributeId"),
                requireText(logicalKey.objectId(), "objectId"));
    }

    static String requireLogicalValue(
            String value, LogicalQuantumServiceCommand.Definition definition) {
        String stableValue = requireText(value, "initialValue");
        physicalValue(stableValue, definition);
        return stableValue;
    }

    static String physicalValue(
            String logicalValue, LogicalQuantumServiceCommand.Definition definition) {
        return settlementDefinition(definition).physicalValue(logicalValue);
    }

    static String requirePhysicalValue(
            String value, LogicalQuantumServiceCommand.Definition definition) {
        return settlementDefinition(definition).requirePhysicalValue(value);
    }

    private static QuantumDefinition settlementDefinition(
            LogicalQuantumServiceCommand.Definition definition) {
        requireDefinition(definition);
        try {
            return new QuantumDefinition(
                    QuantumDefinition.NumberType.valueOf(definition.numberType()),
                    definition.minValue(), definition.maxValue(), definition.scale());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unsupported or invalid definition: "
                    + e.getMessage(), e);
        }
    }

    private static void requireDefinition(LogicalQuantumServiceCommand.Definition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
        requireText(definition.numberType(), "numberType");
        requireText(definition.minValue(), "minValue");
        requireText(definition.maxValue(), "maxValue");
        if (definition.scale() < 0) {
            throw new IllegalArgumentException("scale must not be negative");
        }
        if (definition.scale() > 0
                && ("FLOAT".equals(definition.numberType())
                || "DOUBLE".equals(definition.numberType()))) {
            throw new IllegalArgumentException(
                    "fixed scale requires an integer numberType");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
