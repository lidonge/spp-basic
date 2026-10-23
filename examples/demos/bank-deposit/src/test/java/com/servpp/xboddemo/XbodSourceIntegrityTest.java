package com.servpp.xboddemo;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;
import static com.servpp.xboddemo.XbodTransactionTest.*;

public class XbodSourceIntegrityTest {
    @Test public void depositQueriesReplaceForgedAccountAndComputedFacts() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo);
            var input = deposit(100);
            input.put("DepositAccount", Map.of("accountNumber", "ACCOUNT-001", "interestBase", 999,
                    "overdraftInterest", 888, "overdraftPrincipal", 777));
            input.put("AccountEligibility", Map.of("accountNumber", "ACCOUNT-001", "status", "CLOSED",
                    "detailCount", 999, "accountBalance", 999));
            input.put("CashSettlementAccount", Map.of("accountNumber", "CASH-001", "balance", 999));
            input.put("DepositPlan", Map.of("interestBaseIncrease", 999, "principalPaid", 888));
            input.put("CashDepositAgreement", Map.of("amount", 999, "interestBaseDelta", 999, "interestPaid", 888));
            var result = demo.call("depositCash", input);
            assertEquals("SUCCEEDED", result.path("CashDepositAgreement").path("resultInfo").path("status").asText());
            amount("100", result.path("CashDepositAgreement").path("amount"));
            amount("100", result.path("CashDepositAgreement").path("interestBaseDelta"));
            amount("0", result.path("CashDepositAgreement").path("interestPaid"));
            assertEquals(1, result.path("AccountEligibility").path("detailCount").asInt());
            amount("100", demo.call("findDepositAccount", Map.of("DepositAccount",
                    Map.of("accountNumber", "ACCOUNT-001"))).path("DepositAccount").path("interestBase"));
            amount("-100", demo.call("findCashSettlementAccount", Map.of("CashSettlementAccount",
                    Map.of("accountNumber", "CASH-001"))).path("CashSettlementAccount").path("balance"));
        }
    }

    @Test public void reversalQueriesReplaceForgedOriginalAmount() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo);
            var paid = demo.call("depositCash", deposit(100));
            var input = reversal(paid.path("CashDepositAgreement"), 20261007, false);
            input.put("CashDepositAgreement", Map.of("depositNumber", paid.path("CashDepositAgreement").path("depositNumber").asText(),
                    "amount", 999, "interestBaseDelta", 999, "reversed", true));
            var result = demo.call("reverseCash", input);
            assertEquals("SUCCEEDED", result.path("CashReversalAgreement").path("resultInfo").path("status").asText());
            amount("100", result.path("CashDepositAgreement").path("amount"));
            amount("0", result.path("DepositAccount").path("interestBase"));
            amount("0", result.path("CashSettlementAccount").path("balance"));
        }
    }

    @Test public void reversalRejectsEveryChangedSettlementParticipant() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo); openSecondAccount(demo);
            demo.call("openBankRepaymentAccounts", Map.of(
                    "interestSubject", Map.of("accountNumber", "OTHER-INTEREST"),
                    "collectionSubject", Map.of("accountNumber", "OTHER-COLLECTION"),
                    "principalSubject", Map.of("accountNumber", "OTHER-PRINCIPAL")));
            var paid = demo.call("depositCash", deposit(100));
            var replacements = Map.of("CashSettlementAccount", "CASH-002", "InterestIncomeAccount", "OTHER-INTEREST",
                    "CollectionInterestIncomeAccount", "OTHER-COLLECTION", "PrincipalRecoveryAccount", "OTHER-PRINCIPAL");
            for (var entry : replacements.entrySet()) {
                var input = reversal(paid.path("CashDepositAgreement"), 20261007, false);
                input.put(entry.getKey(), Map.of("accountNumber", entry.getValue()));
                assertEquals(entry.getKey(), "ORIGINAL_SETTLEMENT_ACCOUNTS_MISMATCH",
                        assertThrows(IllegalStateException.class, () -> demo.call("reverseCash", input)).getMessage());
                var original = demo.call("findCashDepositAgreement", Map.of("CashDepositAgreement",
                        Map.of("depositNumber", paid.path("CashDepositAgreement").path("depositNumber").asText())));
                assertFalse(original.path("CashDepositAgreement").path("reversed").asBoolean());
                amount("100", demo.call("findDepositAccount", Map.of("DepositAccount",
                        Map.of("accountNumber", "ACCOUNT-001"))).path("DepositAccount").path("interestBase"));
                amount("0", demo.call("find" + entry.getKey(), Map.of(entry.getKey(),
                        Map.of("accountNumber", entry.getValue()))).path(entry.getKey()).path("balance"));
            }
        }
    }
}
