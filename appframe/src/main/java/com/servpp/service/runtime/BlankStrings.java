package com.servpp.service.runtime;

/** Evaluates a generated APP read policy on the original wire or stored value. */
public final class BlankStrings {
    private BlankStrings() { }

    /** Normalizes the null representation when APP accepts it at an ordering comparison with BLANK. */
    public static String orderingValue(String value) {
        return value == null ? "" : value;
    }

    public static boolean matches(String value, boolean readNull, boolean readEmpty, boolean readSpaces) {
        if (value == null) return readNull;
        if (value.isEmpty()) return readEmpty;
        return readSpaces && value.chars().allMatch(character -> character == ' ');
    }
}
