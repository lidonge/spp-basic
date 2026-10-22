package com.servpp.service.transaction;

public record QuantumServiceDefinition(
        String attributeId,
        String numberType,
        String minValue,
        String maxValue,
        int scale) {

    public QuantumServiceDefinition {
        attributeId = requireText(attributeId, "attributeId");
        numberType = requireText(numberType, "numberType");
        minValue = requireText(minValue, "minValue");
        maxValue = requireText(maxValue, "maxValue");
        if (scale < 0) {
            throw new IllegalArgumentException("scale must not be negative");
        }
        Number min = parse(numberType, minValue, "minValue");
        Number max = parse(numberType, maxValue, "maxValue");
        if (compare(numberType, min, max) > 0) {
            throw new IllegalArgumentException("minValue must not be greater than maxValue");
        }
        if (scale > 0 && ("FLOAT".equals(numberType) || "DOUBLE".equals(numberType))) {
            throw new IllegalArgumentException("fixed scale requires an integer numberType");
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
                default -> throw new IllegalArgumentException("unsupported numberType " + numberType);
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " is not a valid " + numberType
                    + " value: " + value, e);
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
            default -> throw new IllegalArgumentException("unsupported numberType " + numberType);
        };
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
