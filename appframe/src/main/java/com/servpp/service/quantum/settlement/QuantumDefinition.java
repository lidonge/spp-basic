package com.servpp.service.quantum.settlement;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Immutable definition per quantum. Exact decimals are scaled integers, never FLOAT or DOUBLE. */
public record QuantumDefinition(NumberType numberType, String minValue, String maxValue, int scale) {
    public enum NumberType {
        BYTE, SHORT, INT, LONG, FLOAT, DOUBLE;

        boolean integral() { return this != FLOAT && this != DOUBLE; }
    }

    public QuantumDefinition {
        numberType = Values.required(numberType, "numberType");
        minValue = Values.text(minValue, "minValue");
        maxValue = Values.text(maxValue, "maxValue");
        if (scale < 0) {
            throw new IllegalArgumentException("scale must not be negative");
        }
        if (scale > 0 && !numberType.integral()) {
            throw new IllegalArgumentException("fixed scale requires an integer numberType");
        }
        BigDecimal min = parse(numberType, minValue, "minValue");
        BigDecimal max = parse(numberType, maxValue, "maxValue");
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException("minValue must not be greater than maxValue");
        }
    }

    /** Converts a business value to the physical value, rejecting what cannot be represented exactly. */
    public String physicalValue(String logicalValue) {
        BigDecimal scaled = Values.number(logicalValue, "initialValue").movePointRight(scale);
        String physical;
        if (numberType.integral()) {
            try {
                physical = scaled.toBigIntegerExact().toString();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("initialValue " + logicalValue
                        + " cannot be represented exactly with scale " + scale, e);
            }
        } else {
            physical = scaled.stripTrailingZeros().toPlainString();
        }
        requirePhysicalValue(physical);
        return physical;
    }

    /** Checks that a physical value is of this type and inside the binding range. */
    public String requirePhysicalValue(String physicalValue) {
        BigDecimal value = parse(numberType, Values.text(physicalValue, "physicalInitialValue"),
                "physicalInitialValue");
        if (value.compareTo(new BigDecimal(minValue)) < 0 || value.compareTo(new BigDecimal(maxValue)) > 0) {
            throw new IllegalArgumentException("physicalInitialValue must be within the binding range");
        }
        return physicalValue.trim();
    }

    private static BigDecimal parse(NumberType type, String value, String field) {
        try {
            return switch (type) {
                case BYTE -> new BigDecimal(Byte.valueOf(value));
                case SHORT -> new BigDecimal(Short.valueOf(value));
                case INT -> new BigDecimal(Integer.valueOf(value));
                case LONG -> new BigDecimal(BigInteger.valueOf(Long.valueOf(value)));
                case FLOAT -> finite(Float.parseFloat(value), value);
                case DOUBLE -> finite(Double.parseDouble(value), value);
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " is not a valid " + type + " value: " + value, e);
        }
    }

    private static BigDecimal finite(double parsed, String text) {
        if (!Double.isFinite(parsed)) {
            throw new NumberFormatException("non-finite " + text);
        }
        return new BigDecimal(text);
    }
}
