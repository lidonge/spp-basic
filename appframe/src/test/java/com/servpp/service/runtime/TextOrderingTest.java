package com.servpp.service.runtime;

import org.junit.Test;
import static com.servpp.service.runtime.TextOrdering.Collation.BINARY;
import static com.servpp.service.runtime.TextOrdering.Collation.EBCDIC;
import static com.servpp.service.runtime.TextOrdering.compareText;
import static org.junit.Assert.*;

public class TextOrderingTest {
    @Test public void spacePaddingAndCodePointOrdering() {
        for (var collation : TextOrdering.Collation.values()) {
            assertEquals(0, compareText("AB", "AB ", collation));
            assertEquals(0, compareText("", " ", collation));
            assertTrue(compareText("01", "02", collation) < 0);
            assertTrue(compareText("🌱", "�", collation) > 0);
        }
        assertTrue(compareText("1", "A", BINARY) < 0);
        assertTrue(compareText("1", "A", EBCDIC) > 0);
        assertTrue(compareText("�", "9", EBCDIC) > 0);
    }

    @Test public void rejectsOrdinaryNullOnEitherSide() {
        assertThrows(IllegalArgumentException.class, () -> compareText(null, "A", BINARY));
        assertThrows(IllegalArgumentException.class, () -> compareText("A", null, EBCDIC));
    }
}
