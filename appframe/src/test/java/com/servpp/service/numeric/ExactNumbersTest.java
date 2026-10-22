package com.servpp.service.numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExactNumbersTest {
    @Test
    public void physicalFloatingRepresentationRoundTripsItsCanonicalDecimalValue() {
        assertEquals(new BigDecimal("0.1"), ExactNumbers.decimal(0.1d));
        assertEquals(new BigDecimal("0.1"), ExactNumbers.decimal(0.1f));
        assertEquals(0.1d, ExactNumbers.doubleExact(new BigDecimal("0.1")), 0);
        assertEquals(0.1f, ExactNumbers.floatExact(new BigDecimal("0.1")), 0);
        assertEquals(new BigDecimal("9223372036854775807"), ExactNumbers.decimal(Long.MAX_VALUE));
        assertEquals(new BigDecimal("123456789012345678901234567890"),
                ExactNumbers.decimal(new BigInteger("123456789012345678901234567890")));
    }

    @Test
    public void physicalCapacityFailuresNeverSilentlyRoundOrOverflow() {
        assertThrows(ArithmeticException.class, () -> ExactNumbers.doubleExact(new BigDecimal("0.1234567890123456789")));
        assertThrows(ArithmeticException.class, () -> ExactNumbers.doubleExact(new BigDecimal("1e1000")));
        assertThrows(ArithmeticException.class, () -> ExactNumbers.doubleExact(new BigDecimal("1e-1000")));
        assertThrows(ArithmeticException.class, () -> ExactNumbers.floatExact(new BigDecimal("0.123456789")));
        assertThrows(ArithmeticException.class, () -> ExactNumbers.decimal(Double.NaN));
        assertThrows(ArithmeticException.class, () -> ExactNumbers.decimal(Float.POSITIVE_INFINITY));
    }
}
