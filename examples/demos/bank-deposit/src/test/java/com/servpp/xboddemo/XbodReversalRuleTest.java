package com.servpp.xboddemo;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Historical eligibility rules, executed through generated check services. */
public class XbodReversalRuleTest {
    private void check(XbodTestSupport demo, String status, int cancelled, String prefix, String expected) throws Exception {
        var input = Map.of("accountEligibility", Map.of("status", status),
                "cashAccountControl", Map.of("cancelledOn", cancelled),
                "depositOperatorFacts", Map.of("transactionPrefix", prefix));
        if (expected == null) demo.call("assessReversalEligibility", input);
        else assertEquals(expected, assertThrows(IllegalStateException.class,
                () -> demo.call("assessReversalEligibility", input)).getMessage());
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
            demo.call("assessReversalEligibility", Map.of("cashAccountControl", control,
                    "accountEligibility", Map.of("status", "ACTIVE"), "depositOperatorFacts", Map.of("transactionPrefix", "SA01102")));
            assertEquals("EN432", assertThrows(IllegalStateException.class, () -> demo.call("assessCashDeposit",
                    Map.of("depositRequest", Map.of("amount", 1), "cashAccountControl", control, "accountEligibility", Map.of("status", "ACTIVE")))).getMessage());
        }
    }
    @Test public void reversalRangeAndDocumentNoticeRulesStayVisible() throws Exception {
        try (var demo = new XbodTestSupport()) {
            var profile = Map.of("normalSavings", true, "openingBranch", "001", "depositRange", "SameBranch", "passbookLoss", "Written");
            var operator = Map.of("branch", "002", "transactionPrefix", "SA01116", "mode", "Online", "numericTeller", true);
            var input = Map.of("depositBankProfile", profile, "depositOperatorFacts", operator);
            var result = demo.call("assessBankQualification", Map.of("depositBankProfile", profile,
                    "depositOperatorFacts", Map.of("branch", "001", "transactionPrefix", "SA01116", "mode", "Online", "numericTeller", true)));
            assertEquals("WN201", result.path("depositNotices").path("passbookWarning").asText());
            assertEquals("EN134", assertThrows(IllegalStateException.class,
                    () -> demo.call("assessBankQualification", input)).getMessage());
        }
    }
}
