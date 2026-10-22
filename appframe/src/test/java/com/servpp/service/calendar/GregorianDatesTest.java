package com.servpp.service.calendar;
import java.math.BigDecimal;
import org.junit.Test;
import static org.junit.Assert.*;
public class GregorianDatesTest {
    private BigDecimal date(String value) { return new BigDecimal(value); }
    @Test public void leapRulesEpochAndComponentsAreExact() {
        assertTrue(GregorianDates.isGregorianDate(date("20000229")));
        assertFalse(GregorianDates.isGregorianDate(date("19000229")));
        assertEquals(BigDecimal.ZERO,GregorianDates.dateEpochDay(date("19700101")));
        assertEquals(new BigDecimal("2024"),GregorianDates.dateYear(date("20240229")));
        assertEquals(new BigDecimal("2"),GregorianDates.dateMonth(date("20240229")));
        assertEquals(new BigDecimal("29"),GregorianDates.dateDay(date("20240229")));
        assertEquals(BigDecimal.ONE,GregorianDates.dateEpochDay(date("20240301")).subtract(GregorianDates.dateEpochDay(date("20240229"))));
    }
    @Test public void malformedAndNonIntegralRepresentationsAreRejected() {
        for(String invalid:new String[]{"0","20241301","20240431","20230229","20240101.5","9999999999999999999999999","-20240101"}) {
            assertFalse(invalid,GregorianDates.isGregorianDate(date(invalid)));
        }
        assertFalse(GregorianDates.isGregorianDate(null));
        assertThrows(IllegalArgumentException.class,()->GregorianDates.dateYear(date("20240431")));
    }
}
