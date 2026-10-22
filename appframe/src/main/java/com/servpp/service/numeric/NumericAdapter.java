package com.servpp.service.numeric;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** PHY extension contract. Implementations must be immutable or thread safe. */
public interface NumericAdapter<T> {
    T literal(String value);
    T add(T a, T b);
    T subtract(T a, T b);
    T multiply(T a, T b);
    T divide(T a, T b);
    T remainder(T a, T b);
    T negate(T value);
    int compare(T a, T b);
    T round(T value, int places, RoundingMode mode);
    default T abs(T value) { return compare(value, literal("0")) < 0 ? negate(value) : value; }
    default T min(T a, T b) { return compare(a, b) <= 0 ? a : b; }
    default T max(T a, T b) { return compare(a, b) >= 0 ? a : b; }

    default T divideRounded(T a, T b, int places, RoundingMode mode) {
        return round(divide(a, b), places, mode);
    }

    /** Optional bridge. A custom representation need not support a decimal codec. */
    default BigDecimal toDecimal(T value) {
        if (value instanceof Number number) { return ExactNumbers.decimal(number); }
        throw new ArithmeticException("Numeric representation has no decimal conversion: " + getClass().getName());
    }

    @SuppressWarnings("unchecked")
    default T convert(Object value, NumericAdapter<?> source) {
        if (source == this) { return (T) value; }
        return literal(NumericAdapters.toDecimal(value, source).toPlainString());
    }
}
