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
            input.put("depositAccount", Map.of("accountNumber", "ACCOUNT-001", "interestBase", 999,
                    "overdraftInterest", 888, "overdraftPrincipal", 777));
            input.put("accountEligibility", Map.of("accountNumber", "ACCOUNT-001", "status", "CLOSED",
                    "detailCount", 999, "accountBalance", 999));
            input.put("cashSettlementAccount", Map.of("accountNumber", "CASH-001", "balance", 999));
            input.put("depositPlan", Map.of("interestBaseIncrease", 999, "principalPaid", 888));
            input.put("cashDepositAgreement", Map.of("amount", 999, "interestBaseDelta", 999, "interestPaid", 888));
            var result = demo.call("depositCash", input);
            assertEquals("SUCCEEDED", result.path("cashDepositAgreement").path("resultInfo").path("status").asText());
            amount("100", result.path("cashDepositAgreement").path("amount"));
            amount("100", result.path("cashDepositAgreement").path("interestBaseDelta"));
            amount("0", result.path("cashDepositAgreement").path("interestPaid"));
            assertEquals(1, result.path("accountEligibility").path("detailCount").asInt());
            amount("100", demo.readAccounts().path("depositAccount").path("interestBase"));
            amount("-100", demo.readAccounts().path("cashSettlementAccount").path("balance"));
        }
    }

    @Test public void reversalQueriesReplaceForgedOriginalAmount() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo);
            var paid = demo.call("depositCash", deposit(100));
            var input = reversal(paid.path("cashDepositAgreement"), 20261007, false);
            input.put("cashDepositAgreement", Map.of("depositNumber", paid.path("cashDepositAgreement").path("depositNumber").asText(),
                    "amount", 999, "interestBaseDelta", 999, "reversed", true));
            var result = demo.call("reverseCash", input);
            assertEquals("SUCCEEDED", result.path("cashReversalAgreement").path("resultInfo").path("status").asText());
            amount("100", result.path("cashDepositAgreement").path("amount"));
            amount("0", result.path("depositAccount").path("interestBase"));
            amount("0", result.path("cashSettlementAccount").path("balance"));
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
            var replacements = Map.of("cashSettlementAccount", "CASH-002", "interestIncomeAccount", "OTHER-INTEREST",
                    "collectionInterestIncomeAccount", "OTHER-COLLECTION", "principalRecoveryAccount", "OTHER-PRINCIPAL");
            for (var entry : replacements.entrySet()) {
                var input = reversal(paid.path("cashDepositAgreement"), 20261007, false);
                input.put(entry.getKey(), Map.of("accountNumber", entry.getValue()));
                assertEquals(entry.getKey(), "ORIGINAL_SETTLEMENT_ACCOUNTS_MISMATCH",
                        assertThrows(IllegalStateException.class, () -> demo.call("reverseCash", input)).getMessage());
                var original = demo.call("readCashDeposit", Map.of("cashDepositAgreement",
                        Map.of("depositNumber", paid.path("cashDepositAgreement").path("depositNumber").asText())));
                assertFalse(original.path("cashDepositAgreement").path("reversed").asBoolean());
                amount("100", demo.readAccounts().path("depositAccount").path("interestBase"));
                amount("0", demo.readAccounts("ACCOUNT-001", "CASH-001", Map.of(entry.getKey(),
                        Map.of("accountNumber", entry.getValue()))).path(entry.getKey()).path("balance"));
            }
        }
    }
}
