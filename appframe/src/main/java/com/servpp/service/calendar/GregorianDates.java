package com.servpp.service.calendar;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;

/** Gregorian representation and UTC arithmetic, independent of business policies. */
public final class GregorianDates {
    private GregorianDates() { }
    private static LocalDate decode(BigDecimal encoded) {
        if (encoded == null) { throw new IllegalArgumentException("MISSING_CALENDAR_DATE"); }
        int value;
        try { value = encoded.intValueExact(); }
        catch (ArithmeticException failure) { throw new IllegalArgumentException("INVALID_CALENDAR_DATE",failure); }
        if (value < 10101 || value > 99991231) { throw new IllegalArgumentException("INVALID_CALENDAR_DATE"); }
        try { return LocalDate.of(value / 10000, value / 100 % 100, value % 100); }
        catch (DateTimeException failure) { throw new IllegalArgumentException("INVALID_CALENDAR_DATE",failure); }
    }
    public static boolean isGregorianDate(BigDecimal encoded) {
        try { decode(encoded); return true; } catch(IllegalArgumentException failure) { return false; }
    }
    public static BigDecimal dateYear(BigDecimal encoded) { return BigDecimal.valueOf(decode(encoded).getYear()); }
    public static BigDecimal dateMonth(BigDecimal encoded) { return BigDecimal.valueOf(decode(encoded).getMonthValue()); }
    public static BigDecimal dateDay(BigDecimal encoded) { return BigDecimal.valueOf(decode(encoded).getDayOfMonth()); }
    public static BigDecimal dateEpochDay(BigDecimal encoded) { return BigDecimal.valueOf(decode(encoded).toEpochDay()); }
    /** Adds calendar months in UTC, retaining time and clamping the day to the destination month. */
    public static BigDecimal addUtcMonths(BigDecimal epochSeconds, BigDecimal months) {
        if (epochSeconds == null || months == null) throw new IllegalArgumentException("MISSING_CALENDAR_ARGUMENT");
        try {
            return BigDecimal.valueOf(java.time.Instant.ofEpochSecond(epochSeconds.longValueExact())
                    .atZone(java.time.ZoneOffset.UTC).plusMonths(months.longValueExact()).toEpochSecond());
        } catch (ArithmeticException | DateTimeException failure) {
            throw new IllegalArgumentException("INVALID_CALENDAR_ARGUMENT", failure);
        }
    }
}
