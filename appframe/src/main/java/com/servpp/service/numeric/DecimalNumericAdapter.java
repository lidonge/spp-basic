package com.servpp.service.numeric;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DecimalNumericAdapter implements NumericAdapter<BigDecimal> {
    public DecimalNumericAdapter() { }
    public BigDecimal literal(String value) { return new BigDecimal(value); }
    public BigDecimal add(BigDecimal a, BigDecimal b) { return a.add(b); }
    public BigDecimal subtract(BigDecimal a, BigDecimal b) { return a.subtract(b); }
    public BigDecimal multiply(BigDecimal a, BigDecimal b) { return a.multiply(b); }
    public BigDecimal divide(BigDecimal a, BigDecimal b) { return a.divide(b); }
    public BigDecimal remainder(BigDecimal a, BigDecimal b) { return a.remainder(b); }
    public BigDecimal negate(BigDecimal value) { return value.negate(); }
    public int compare(BigDecimal a, BigDecimal b) { return a.compareTo(b); }
    public BigDecimal round(BigDecimal value, int places, RoundingMode mode) { return value.setScale(places, mode); }
    public BigDecimal divideRounded(BigDecimal a, BigDecimal b, int places, RoundingMode mode) { return a.divide(b, places, mode); }
    public BigDecimal toDecimal(BigDecimal value) { return value; }
}
