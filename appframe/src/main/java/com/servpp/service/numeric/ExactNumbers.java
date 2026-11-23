package com.servpp.service.numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/** Physical conversions either preserve the exact value or fail. */
public final class ExactNumbers {
    private ExactNumbers() { }

    public static BigDecimal specification(BigDecimal value, int precision, int scale) {
        return specification(value, precision, scale, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal specification(BigDecimal value, int precision, int scale, RoundingMode rounding) {
        if (value == null) return null;
        BigDecimal represented = value.setScale(scale, rounding);
        if (represented.precision() > precision) throw new ArithmeticException("PHYSICAL_NUMERIC_CAPACITY_EXCEEDED");
        return represented;
    }

    /** Keeps terminating quotients exact and evaluates repeating quotients at decimal128 precision. */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        try {
            return dividend.divide(divisor);
        } catch (ArithmeticException nonExact) {
            return dividend.divide(divisor, MathContext.DECIMAL128);
        }
    }

    public static BigDecimal decimal(Number value) {
        if (value instanceof BigDecimal decimal) { return decimal; }
        if (value instanceof BigInteger integer) { return new BigDecimal(integer); }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(value.longValue());
        }
        if (value instanceof Float || value instanceof Double) {
            double floating = value.doubleValue();
            if (!Double.isFinite(floating)) { throw new ArithmeticException("Non-finite physical numeric value"); }
            return value instanceof Float ? new BigDecimal(Float.toString(value.floatValue())) : BigDecimal.valueOf(floating);
        }
        throw new IllegalArgumentException("Unsupported physical numeric value " + value);
    }

    public static double doubleExact(BigDecimal value) {
        double result = value.doubleValue();
        if (!Double.isFinite(result) || BigDecimal.valueOf(result).compareTo(value) != 0) {
            throw new ArithmeticException("Physical double cannot exactly represent " + value);
        }
        return result;
    }

    public static float floatExact(BigDecimal value) {
        float result = value.floatValue();
        if (!Float.isFinite(result) || new BigDecimal(Float.toString(result)).compareTo(value) != 0) {
            throw new ArithmeticException("Physical float cannot exactly represent " + value);
        }
        return result;
    }
}
