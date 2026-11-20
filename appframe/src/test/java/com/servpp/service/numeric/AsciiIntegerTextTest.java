package com.servpp.service.numeric;

import java.math.BigDecimal;
import org.junit.Test;
import static org.junit.Assert.*;

public class AsciiIntegerTextTest {
    @Test public void preservesExactMagnitudeAndLeadingZeroes() {
        for (String text : new String[]{"0", "000", "000123", "9999999999999999999999999999999999999999"}) {
            assertTrue(text, AsciiIntegerText.isAsciiDigits(text));
            assertEquals(new BigDecimal(text), AsciiIntegerText.parseUnsignedInteger(text));
            assertEquals(0, AsciiIntegerText.parseUnsignedInteger(text).scale());
        }
    }
    @Test public void rejectsMalformedTextWithoutNormalizationOrDefaults() {
        for (String text : new String[]{null,""," "," 1","1 ","+1","-1","1.0","1e2","1_000","1\n","\u0661","\uff11","\ud835\udfd9"}) {
            assertFalse(String.valueOf(text), AsciiIntegerText.isAsciiDigits(text));
            assertThrows(IllegalArgumentException.class, () -> AsciiIntegerText.parseUnsignedInteger(text));
        }
    }
}
