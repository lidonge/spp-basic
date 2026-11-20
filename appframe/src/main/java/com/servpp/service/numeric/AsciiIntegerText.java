package com.servpp.service.numeric;

import java.math.BigDecimal;

/** Exact lexical operations defined by TEXT-1 v1. */
public final class AsciiIntegerText {
    private AsciiIntegerText() { }

    public static boolean isAsciiDigits(String text) {
        if (text == null || text.isEmpty()) { return false; }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') { return false; }
        }
        return true;
    }

    public static BigDecimal parseUnsignedInteger(String text) {
        if (!isAsciiDigits(text)) {
            throw new IllegalArgumentException("Expected nonempty ASCII unsigned integer text.");
        }
        return new BigDecimal(text);
    }
}
