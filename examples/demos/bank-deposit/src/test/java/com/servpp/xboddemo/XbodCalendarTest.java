package com.servpp.xboddemo;

import org.junit.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.Assert.*;

public class XbodCalendarTest {
    @Test public void preservesAllThirtyArchivedCalendarCases() throws Exception {
        try (var demo = new XbodTestSupport();
             var input = getClass().getResourceAsStream("/bank-calendar-cases.json")) {
            var cases = demo.mapper.readTree(input);
            assertEquals(30, cases.size());
            for (var row : cases) {
                var request = Map.of("start", Map.of("encoded", row.path("start").asInt()),
                        "end", Map.of("encoded", row.path("end").asInt()),
                        "InterestCalendar", Map.of("convention", row.path("convention").asText()),
                        "InterestPeriod", Map.of("amount", 100, "unit", 1));
                String code = row.path("expectedCode").asText();
                if (code.isEmpty()) {
                    var result = demo.call("previewInterestProduct", request);
                    assertEquals(row.path("name").asText(), row.path("expectedDays").asInt(),
                            result.path("InterestPeriod").path("days").asInt());
                } else {
                    var failure = assertThrows(row.path("name").asText(), IllegalStateException.class,
                            () -> demo.call("previewInterestProduct", request));
                    assertEquals(row.path("name").asText(), code, failure.getMessage());
                }
            }
        }
    }

    @Test public void preservesArchivedInterestUnitTruncation() throws Exception {
        try (var demo = new XbodTestSupport()) {
            String[][] cases = {{"199.999", "100", "100", "200"}, {"10", "3", "9", "18"}};
            for (var row : cases) {
                var result = demo.call("previewUnitInterest", Map.of("InterestPeriod",
                        Map.of("amount", new BigDecimal(row[0]), "unit", new BigDecimal(row[1]), "days", 2)));
                assertEquals(0, new BigDecimal(row[2]).compareTo(result.path("InterestProduct").path("qualifiedAmount").decimalValue()));
                assertEquals(0, new BigDecimal(row[3]).compareTo(result.path("InterestProduct").path("product").decimalValue()));
            }
        }
    }
}
