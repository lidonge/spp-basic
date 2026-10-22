package com.servpp.service.transaction;

import java.math.BigDecimal;
import java.math.BigInteger;

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
        requireDefinition(definition);
        BigDecimal logical;
        try {
            logical = new BigDecimal(requireText(logicalValue, "initialValue"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "initialValue must be a decimal value: " + logicalValue, e);
        }
        BigDecimal scaled = logical.movePointRight(definition.scale());
        String physical;
        if ("FLOAT".equals(definition.numberType())
                || "DOUBLE".equals(definition.numberType())) {
            physical = scaled.stripTrailingZeros().toPlainString();
        } else {
            BigInteger integer;
            try {
                integer = scaled.toBigIntegerExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("initialValue " + logical
                        + " cannot be represented exactly with scale "
                        + definition.scale(), e);
            }
            physical = integer.toString();
        }
        return requirePhysicalValue(physical, definition);
    }

    static String requirePhysicalValue(
            String value, LogicalQuantumServiceCommand.Definition definition) {
        requireDefinition(definition);
        String stableValue = requireText(value, "physicalInitialValue");
        Number parsed = parse(definition.numberType(), stableValue, "physicalInitialValue");
        Number min = parse(definition.numberType(), definition.minValue(), "minValue");
        Number max = parse(definition.numberType(), definition.maxValue(), "maxValue");
        if (compare(definition.numberType(), parsed, min) < 0
                || compare(definition.numberType(), parsed, max) > 0) {
            throw new IllegalArgumentException(
                    "physicalInitialValue must be within the binding range");
        }
        return stableValue;
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

    private static Number parse(String numberType, String value, String fieldName) {
        try {
            return switch (numberType) {
                case "BYTE" -> Byte.valueOf(value);
                case "SHORT" -> Short.valueOf(value);
                case "INT" -> Integer.valueOf(value);
                case "LONG" -> Long.valueOf(value);
                case "FLOAT" -> {
                    float parsed = Float.parseFloat(value);
                    if (!Float.isFinite(parsed)) {
                        throw new NumberFormatException("non-finite float");
                    }
                    yield parsed;
                }
                case "DOUBLE" -> {
                    double parsed = Double.parseDouble(value);
                    if (!Double.isFinite(parsed)) {
                        throw new NumberFormatException("non-finite double");
                    }
                    yield parsed;
                }
                default -> throw new IllegalArgumentException(
                        "unsupported numberType " + numberType);
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " is not a valid "
                    + numberType + " value: " + value, e);
        }
    }

    private static int compare(String numberType, Number first, Number second) {
        return switch (numberType) {
            case "BYTE" -> Byte.compare(first.byteValue(), second.byteValue());
            case "SHORT" -> Short.compare(first.shortValue(), second.shortValue());
            case "INT" -> Integer.compare(first.intValue(), second.intValue());
            case "LONG" -> Long.compare(first.longValue(), second.longValue());
            case "FLOAT" -> Float.compare(first.floatValue(), second.floatValue());
            case "DOUBLE" -> Double.compare(first.doubleValue(), second.doubleValue());
            default -> throw new IllegalArgumentException(
                    "unsupported numberType " + numberType);
        };
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
