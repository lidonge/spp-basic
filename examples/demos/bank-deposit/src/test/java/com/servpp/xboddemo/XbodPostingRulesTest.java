package com.servpp.xboddemo;

import org.junit.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.Assert.*;

public class XbodPostingRulesTest {
    private List<Map<String, Object>> lines() {
        var result = new ArrayList<Map<String, Object>>();
        String[] subjects = {"DEMO.CASH", "DEMO.DEPOSIT_LIABILITY", "DEMO.DEPOSIT_LIABILITY",
                "DEMO.OVERDRAFT_PRINCIPAL", "DEMO.OVERDRAFT_INTEREST", "DEMO.COLLECTION_INTEREST",
                "DEMO.CASH", "DEMO.FEE_INCOME"};
        for (int slot = 1; slot <= 8; slot++) {
            result.add(new HashMap<>(Map.of("slot", slot, "subject", subjects[slot - 1],
                    "side", slot == 1 || slot == 2 || slot == 7 ? "Debit" : "Credit", "amount", 999)));
        }
        return result;
    }

    @Test public void preservesSixArchivedPostingAllocations() throws Exception {
        try (var demo = new XbodTestSupport(); var input = getClass().getResourceAsStream("/posting-rules-cases.json")) {
            var cases = demo.mapper.readTree(input);
            assertEquals(6, cases.size());
            for (var row : cases) {
                var request = demo.mapper.convertValue(row.path("request"), Map.class);
                var plan = demo.call("previewDeposit", Map.of("DepositRequest", request,
                        "DepositAccount", demo.mapper.convertValue(row.path("account"), Map.class)));
                var classified = demo.call("classifyDepositAccounting", Map.of("DepositRequest", request,
                        "DepositPlan", demo.mapper.convertValue(plan.path("DepositPlan"), Map.class)));
                var accounting = demo.mapper.convertValue(classified.path("DepositAccounting"), Map.class);
                demo.call("checkDepositAccounting", Map.of("DepositAccounting", accounting));
                var fee = Map.of("fee", new BigDecimal(row.path("fee").asText()));
                var lines = lines();
                demo.call("checkPostingRules", Map.of("DepositPostingLineArray", lines, "DepositFeePlan", fee));
                var result = demo.call("calculateDepositPostings", Map.of("DepositAccounting", accounting,
                        "DepositFeePlan", fee, "DepositPostingLineArray", lines));
                for (int index = 0; index < 8; index++) {
                    var line = result.path("DepositPostingLineArray").get(index);
                    assertEquals(row.path("name").asText() + " slot " + (index + 1), 0,
                            new BigDecimal(row.path("expected").get(index).asText()).compareTo(line.path("amount").decimalValue()));
                    assertEquals(lines.get(index).get("subject"), line.path("subject").asText());
                    assertEquals(lines.get(index).get("side"), line.path("side").asText());
                    assertEquals(index + 1, line.path("slot").asInt());
                }
                for (String side : List.of("debit", "credit")) assertEquals(row.path("name").asText(), 0,
                        new BigDecimal(row.path("total").asText()).compareTo(result.path("DepositPostingTotals").path(side).decimalValue()));
                demo.call("checkDepositPostings", Map.of("DepositPostingTotals",
                        demo.mapper.convertValue(result.path("DepositPostingTotals"), Map.class)));
            }
        }
    }

    @Test public void rejectsWrongSideAndMissingSubjectBeforeCalculation() throws Exception {
        try (var demo = new XbodTestSupport()) {
            for (String invalidField : List.of("side", "subject")) {
                var lines = lines();
                lines.get(0).put(invalidField, invalidField.equals("side") ? "Credit" : null);
                var request = demo.request("checkPostingRules", Map.of("DepositPostingLineArray", lines));
                if (invalidField.equals("subject")) {
                    var participants = (List<?>) request.getClass().getMethod("getDepositPostingLineArray").invoke(request);
                    participants.get(0).getClass().getMethod("setSubject", String.class).invoke(participants.get(0), (Object) null);
                }
                var before = demo.mapper.valueToTree(request);
                var failure = assertThrows(IllegalStateException.class, () -> demo.execute("checkPostingRules", request));
                assertEquals(invalidField.equals("side") ? "INVALID_DEPOSIT_POSTING_SIDE" : "INVALID_DEPOSIT_POSTING_RULES", failure.getMessage());
                assertEquals(before, demo.mapper.valueToTree(request));
            }
        }
    }

    @Test public void rejectsForgedTotalsAndMissingAllocation() throws Exception {
        try (var demo = new XbodTestSupport()) {
            assertEquals("DEPOSIT_ACCOUNTING_TOTAL_MISMATCH", assertThrows(IllegalStateException.class,
                    () -> demo.call("checkDepositAccounting", Map.of("DepositAccounting", Map.of("transactionAmount", 10)))).getMessage());
            assertEquals("DEPOSIT_ACCOUNTING_NOT_BALANCED", assertThrows(IllegalStateException.class,
                    () -> demo.call("checkDepositAccounting", Map.of("DepositAccounting", Map.of("transactionAmount", 10, "fundingTotal", 10)))).getMessage());
        }
    }
}
