package com.servpp.xboddemo;

import java.sql.DriverManager;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.servpp.xboddemo.XbodTransactionTest.*;

/** Read-only preparation must not reserve money or mark the original as reversed. */
public class XbodReversalPreviewTest {
    @Test public void previewsCurrentFactsWithoutCommittingAndAllowsLaterReversal() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo);
            var paid = demo.call("depositCash", deposit(100));
            demo.call("depositCash", deposit(5));
            var input = reversal(paid.path("cashDepositAgreement"), 20261007, false);
            var preview = demo.call("previewCashReversal", input);
            assertEquals(8, preview.path("cashReversalPostingLines").size());
            amount("100", preview.path("depositPostingTotals").path("debit"));
            amount("100", preview.path("depositPostingTotals").path("credit"));
            amount("105", preview.path("depositAccount").path("interestBase"));
            assertFalse(preview.path("cashDepositAgreement").path("reversed").asBoolean());
            verifyNoReversal(demo, paid.path("cashDepositAgreement").path("depositNumber").asText());
            amount("105", demo.readAccounts()
                    .path("depositAccount").path("interestBase"));
            demo.call("reverseCash", input);
            amount("5", demo.readAccounts()
                    .path("depositAccount").path("interestBase"));
        }
    }

    @Test public void missingOriginalRejectsPreviewWithoutWrites() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo);
            var paid = demo.call("depositCash", deposit(100));
            var input = reversal(paid.path("cashDepositAgreement"), 20261007, false);
            input.put("cashDepositAgreement", Map.of("depositNumber", "MISSING"));
            assertEquals("PARTICIPANT_NOT_IDENTIFIED", assertThrows(IllegalStateException.class,
                    () -> demo.call("previewCashReversal", input)).getMessage());
            verifyNoReversal(demo, paid.path("cashDepositAgreement").path("depositNumber").asText());
            amount("100", demo.readAccounts()
                    .path("depositAccount").path("interestBase"));
        }
    }

    private void verifyNoReversal(XbodTestSupport demo, String depositNumber) throws Exception {
        var original = demo.call("readCashDeposit", Map.of("cashDepositAgreement", Map.of("depositNumber", depositNumber)));
        assertFalse(original.path("cashDepositAgreement").path("reversed").asBoolean());
        try (var connection = DriverManager.getConnection(System.getProperty("spp.xbod.datasource-url"));
             var statement = connection.createStatement()) {
            for (String table : new String[]{"CASH_REVERSALS", "CASH_REVERSAL_POSTINGS"}) {
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
                }
            }
        }
    }
}
