package com.servpp.service.calendar;

import org.junit.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.Assert.*;

public class UtcMonthsTest {
    private void adds(String start,int months,String end) {
        assertEquals(new BigDecimal(Instant.parse(end).getEpochSecond()), GregorianDates.addUtcMonths(
                new BigDecimal(Instant.parse(start).getEpochSecond()),new BigDecimal(months)));
    }
    @Test public void calendarArithmeticRetainsUtcTimeAndClampsMonthEnd() {
        adds("2026-01-31T10:23:45Z",1,"2026-02-28T10:23:45Z");
        adds("2024-01-31T10:23:45Z",1,"2024-02-29T10:23:45Z");
        adds("2026-10-18T10:23:45Z",3,"2027-01-18T10:23:45Z");
        adds("2023-11-30T23:59:59Z",3,"2024-02-29T23:59:59Z");
        adds("2024-11-30T23:59:59Z",3,"2025-02-28T23:59:59Z");
        adds("2024-02-29T12:00:00Z",12,"2025-02-28T12:00:00Z");
        adds("2026-01-31T00:00:00Z",-2,"2025-11-30T00:00:00Z");
    }
    @Test public void rejectsFractionalArgumentsInsteadOfTruncation() {
        for(BigDecimal[] arguments:new BigDecimal[][]{{new BigDecimal("1.5"),BigDecimal.ONE},{BigDecimal.ONE,new BigDecimal("1.5")}}) {
            try {GregorianDates.addUtcMonths(arguments[0],arguments[1]);fail("Expected invalid calendar argument");}
            catch(IllegalArgumentException expected){assertEquals("INVALID_CALENDAR_ARGUMENT",expected.getMessage());}
        }
    }
}
