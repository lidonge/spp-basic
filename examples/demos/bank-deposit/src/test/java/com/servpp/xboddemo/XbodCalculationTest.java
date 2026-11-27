package com.servpp.xboddemo;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class XbodCalculationTest {
    @Test public void preservesRecordedAgreementProductVectors() throws Exception {
        try (var demo = new XbodTestSupport(); var input = getClass().getResourceAsStream("/agreement-product-cases.json")) {
            var cases = demo.mapper.readTree(input);
            assertEquals(12, cases.size());
            for (var row : cases) {
                var result = demo.call("previewAgreementProduct", demo.mapper.convertValue(row.path("input"), Map.class));
                var fields = row.path("expected").fields();
                while (fields.hasNext()) {
                    var field = fields.next(); var actual = result.path("AgreementProduct").path(field.getKey());
                    if (field.getValue().isNumber()) assertEquals(row.path("name").asText() + ":" + field.getKey(), 0, field.getValue().decimalValue().compareTo(actual.decimalValue()));
                    else assertEquals(row.path("name").asText(), field.getValue().asText(), actual.asText());
                }
                for (int index=0; index<5; index++) assertEquals(row.path("name").asText(), 0, row.path("bands").get(index).decimalValue().compareTo(result.path("AgreementBandArray").get(index).path("product").decimalValue()));
            }
        }
    }
    @Test public void preservesRecordedKernelVectors() throws Exception {
        try (var demo = new XbodTestSupport(); var input = getClass().getResourceAsStream("/deposit-kernel-cases.json")) {
            var cases = demo.mapper.readTree(input);
            assertEquals(15, cases.size());
            for (var row : cases) {
                var result = demo.call("previewDeposit", Map.of("DepositRequest", demo.mapper.convertValue(row.path("request"), Map.class), "DepositAccount", demo.mapper.convertValue(row.path("account"), Map.class)));
                var fields = row.path("expected").fields();
                while (fields.hasNext()) { var field = fields.next(); assertEquals(row.path("name").asText() + ":" + field.getKey(), 0, field.getValue().decimalValue().compareTo(result.path("DepositPlan").path(field.getKey()).decimalValue())); }
            }
        }
    }
    @Test public void testEntryInvokesOneGeneratedAtomicService() throws Exception {
        try (var demo = new XbodTestSupport()) {
            var request = demo.request("calculateDepositPlan", Map.of("DepositRequest", Map.of("amount", 25)));
            String atomic = "cboddeposit.calculateDepositPlan";
            com.servpp.service.runtime.AtomicServiceTesting.invokeAtomic(demo.runtime, atomic, request);
            XbodTransactionTest.amount("25", demo.mapper.valueToTree(request).path("DepositPlan").path("newInterestBase"));
            assertTrue(assertThrows(IllegalStateException.class,
                    () -> demo.runtime.executeGeneratedService(atomic, request)).getMessage().contains("P0-b"));
            assertThrows(IllegalArgumentException.class, () -> com.servpp.service.runtime.AtomicServiceTesting
                    .invokeAtomic(demo.runtime, "cboddeposit.previewDeposit", request));
        }
    }

    @Test public void negativeCashIsRejectedBeforeCalculation() throws Exception {
        try (var demo = new XbodTestSupport()) {
            var failure = assertThrows(IllegalStateException.class, () -> demo.call("previewDeposit", Map.of("DepositRequest", Map.of("amount", -1))));
            assertEquals("E1095", failure.getMessage());
        }
    }
    @Test public void calendarUsesStartIncludedAndEndExcluded() throws Exception {
        try (var demo = new XbodTestSupport()) {
            var result = demo.call("previewInterestProduct", Map.of("start", Map.of("encoded", 20260228), "end", Map.of("encoded", 20260302), "InterestCalendar", Map.of("convention", "ACT"), "normal", Map.of("amount", 123.45, "unit", 10)));
            assertEquals(2, result.path("normal").path("days").asInt());
            assertEquals(0, result.path("InterestProduct").path("product").decimalValue().compareTo(new java.math.BigDecimal("240")));
        }
    }
    @Test public void invalidCalendarIsBusinessCheck() throws Exception {
        try (var demo = new XbodTestSupport()) {
            var failure = assertThrows(IllegalStateException.class, () -> demo.call("previewInterestProduct", Map.of("start", Map.of("encoded", 20260230), "end", Map.of("encoded", 20260302), "InterestCalendar", Map.of("convention", "ACT"), "normal", Map.of("amount", 10, "unit", 1))));
            assertEquals("EE374", failure.getMessage());
        }
    }
    @Test public void repaymentPostingsIncludeBankInterestAndPrincipalCounterparts() throws Exception {
        try (var demo = new XbodTestSupport()) {
            var request = Map.of("amount", 100);
            var calculation = demo.call("previewDeposit", Map.of(
                    "DepositRequest", request,
                    "DepositAccount", Map.of("loanState", "NORMAL", "overdraftInterest", -10,
                            "collectionInterest", -5, "overdraftPrincipal", -20, "overdraftExposure", 20)));
            var classified = demo.call("previewDepositAllocation", Map.of("DepositRequest", request,
                    "DepositPlan", demo.mapper.convertValue(calculation.path("DepositPlan"), Map.class)));
            var accounting = demo.mapper.convertValue(classified.path("DepositAccounting"), Map.class);

            var lines = List.of(
                    Map.of("slot", 1, "subject", "CASH_SETTLEMENT", "side", "Debit"),
                    Map.of("slot", 2, "subject", "DEPOSIT_DECREASE", "side", "Debit"),
                    Map.of("slot", 3, "subject", "CUSTOMER_DEPOSIT", "side", "Credit"),
                    Map.of("slot", 4, "subject", "OVERDRAFT_PRINCIPAL_RECEIVABLE", "side", "Credit"),
                    Map.of("slot", 5, "subject", "OVERDRAFT_INTEREST_RECEIVABLE", "side", "Credit"),
                    Map.of("slot", 6, "subject", "COLLECTION_INTEREST_RECEIVABLE", "side", "Credit"),
                    Map.of("slot", 7, "subject", "FEE_SETTLEMENT", "side", "Debit"),
                    Map.of("slot", 8, "subject", "FEE_INCOME", "side", "Credit"));

            var posted = demo.call("previewDepositPostings", Map.of("DepositAccounting", accounting,
                    "DepositPostingLineArray", lines));
            var actual = posted.path("DepositPostingLineArray");
            int[] expected = {100, 0, 65, 20, 10, 5, 0, 0};
            for (int index = 0; index < expected.length; index++) {
                assertEquals("posting slot " + (index + 1), 0,
                        java.math.BigDecimal.valueOf(expected[index]).compareTo(actual.get(index).path("amount").decimalValue()));
            }
            assertEquals(0, new java.math.BigDecimal("100").compareTo(posted.path("DepositPostingTotals").path("debit").decimalValue()));
            assertEquals(0, new java.math.BigDecimal("100").compareTo(posted.path("DepositPostingTotals").path("credit").decimalValue()));

        }
    }
}
