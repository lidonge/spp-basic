package com.servpp.xboddemo;

import org.junit.Test;
import java.sql.DriverManager;
import java.util.Map;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.repository.JdbcObjectSnapshotStore;
import static org.junit.Assert.*;
import static com.servpp.xboddemo.XbodTransactionTest.*;

public class XbodOriginalPostingIntegrityTest {
    @Test public void changedOriginalPostingAmountPreventsReversal() throws Exception { verify(false, false); }
    @Test public void missingOriginalPostingPreventsReversal() throws Exception { verify(true, false); }
    @Test public void changedOriginalPostingSubjectPreventsReversal() throws Exception { verify(false, true); }
    private void verify(boolean missing, boolean subject) throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo);
            var paid = demo.call("depositCash", deposit(100));
            String number = paid.path("CashDepositAgreement").path("depositNumber").asText();
            String url = System.getProperty("spp.xbod.datasource-url");
            // Deliberately damage the stored fixture in both persistence representations.
            try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
                assertEquals(1, statement.executeUpdate(missing
                        ? "DELETE FROM CASH_POSTINGS WHERE slot = 1"
                        : subject ? "UPDATE CASH_POSTINGS SET subject = 'WRONG_SUBJECT' WHERE slot = 1"
                        : "UPDATE CASH_POSTINGS SET amount = 999 WHERE slot = 1"));
            }
            var snapshots = new JdbcObjectSnapshotStore(new DriverManagerJdbcDataSource("org.h2.Driver", url, null, null),
                    value -> demo.mapper.valueToTree(value).toString(), "xbod_cash_record");
            int damaged = 0;
            for (var snapshot : snapshots.findAll()) {
                if (!snapshot.objectType().equals("DepositPostingLine") && !snapshot.objectType().endsWith(".DepositPostingLine")) continue;
                var payload = (ObjectNode) demo.mapper.readTree(snapshot.payloadJson());
                if (payload.path("slot").asInt() != 1) continue;
                assertEquals(number, payload.path("deposit").path("depositNumber").asText());
                if (missing) {
                    try (var connection = DriverManager.getConnection(url); var deletion = connection.prepareStatement(
                            "DELETE FROM xbod_cash_record WHERE object_type = ? AND object_id = ?")) {
                        deletion.setString(1, snapshot.objectType()); deletion.setString(2, snapshot.objectId());
                        assertEquals(1, deletion.executeUpdate());
                    }
                }
                else { if (subject) payload.put("subject", "WRONG_SUBJECT"); else payload.put("amount", 999); snapshots.save(snapshot.objectType(), snapshot.objectId(), payload); }
                damaged++;
            }
            assertEquals(1, damaged);
            var stored = demo.call("listDepositPostings", Map.of("CashDepositAgreement", Map.of("depositNumber", number)));
            assertEquals(missing ? 7 : 8, stored.path("DepositPostingLineArray").size());
            var failure = assertThrows(IllegalStateException.class,
                    () -> demo.call("reverseCash", reversal(paid.path("CashDepositAgreement"), 20261007, false)));
            assertEquals(missing ? "INVALID_ORIGINAL_DEPOSIT_POSTINGS" : "ORIGINAL_DEPOSIT_LEDGER_EVIDENCE_MISMATCH", failure.getMessage());
            amount("100", demo.call("findDepositAccount", Map.of("DepositAccount",
                    Map.of("accountNumber", "ACCOUNT-001"))).path("DepositAccount").path("interestBase"));
            amount("-100", demo.call("findCashSettlementAccount", Map.of("CashSettlementAccount",
                    Map.of("accountNumber", "CASH-001"))).path("CashSettlementAccount").path("balance"));
            assertFalse(demo.call("findCashDepositAgreement", Map.of("CashDepositAgreement",
                    Map.of("depositNumber", number))).path("CashDepositAgreement").path("reversed").asBoolean());
            try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM CASH_REVERSAL_POSTINGS")) {
                assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
            }
        }
    }
}
