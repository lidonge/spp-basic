package com.servpp.xboddemo;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Historical eligibility rules, executed through generated check services. */
public class XbodReversalRuleTest {
    private void check(XbodTestSupport demo, String status, int cancelled, String prefix, String expected) throws Exception {
        var input = Map.of("AccountEligibility", Map.of("status", status),
                "CashAccountControl", Map.of("cancelledOn", cancelled),
                "DepositOperatorFacts", Map.of("transactionPrefix", prefix));
        if (expected == null) demo.call("checkCoreDepositReversalEligibility", input);
        else assertEquals(expected, assertThrows(IllegalStateException.class,
                () -> demo.call("checkCoreDepositReversalEligibility", input)).getMessage());
    }
    @Test public void coreReversalKeepsClosureCancellationAndFrozenExceptionPrecedence() throws Exception {
        try (var demo = new XbodTestSupport()) {
            check(demo, "CLOSED", 0, "SA01102", "EO082");
            check(demo, "ACTIVE", 20261007, "SA01102", "EN113");
            check(demo, "FROZEN", 0, "SA01102", "EN111");
            check(demo, "FROZEN", 0, "SA01116", null);
            check(demo, "FROZEN", 20261007, "SA01116", "EN113");
        }
    }
    @Test public void coreReversalAndFrontControlRemainIndependentChecks() throws Exception {
        try (var demo = new XbodTestSupport()) {
            var control = Map.of("controllingInstitution", "001", "controlStatus", "Locked");
            demo.call("checkCoreDepositReversalEligibility", Map.of("CashAccountControl", control,
                    "AccountEligibility", Map.of("status", "ACTIVE"), "DepositOperatorFacts", Map.of("transactionPrefix", "SA01102")));
            assertEquals("EN432", assertThrows(IllegalStateException.class, () -> demo.call("checkCashControl",
                    Map.of("CashAccountControl", control, "AccountEligibility", Map.of("status", "ACTIVE")))).getMessage());
        }
    }
    @Test public void reversalRangeAndDocumentNoticeRulesStayVisible() throws Exception {
        try (var demo = new XbodTestSupport()) {
            var profile = Map.of("normalSavings", true, "openingBranch", "001", "depositRange", "SameBranch", "passbookLoss", "Written");
            var operator = Map.of("branch", "002", "transactionPrefix", "SA01116", "mode", "Online", "numericTeller", true);
            var input = Map.of("DepositBankProfile", profile, "DepositOperatorFacts", operator);
            var result = demo.call("assessDepositDocumentNotices", input);
            assertEquals("WN201", result.path("DepositNotices").path("passbookWarning").asText());
            assertEquals("EN134", assertThrows(IllegalStateException.class,
                    () -> demo.call("checkDepositBranchRange", input)).getMessage());
        }
    }
}
