package com.servpp.service.quantum.settlement;

import java.math.BigDecimal;

/** Shared constructor validation for the settlement records. */
final class Values {
    private Values() { }

    static String text(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

    /** A wire number: a plain decimal string, finite by construction. */
    static BigDecimal number(String value, String field) {
        try {
            return new BigDecimal(text(value, field));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " is not a finite decimal string: " + value, e);
        }
    }
}
