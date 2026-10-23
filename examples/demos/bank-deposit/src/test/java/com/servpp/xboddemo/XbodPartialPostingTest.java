package com.servpp.xboddemo;

import org.junit.Test;
import org.h2.api.Trigger;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static com.servpp.xboddemo.XbodTransactionTest.*;

/** Technical storage fault only; all business work runs through generated scenarios. */
public class XbodPartialPostingTest {
    public static class FourthInsertFault implements Trigger {
        static final AtomicInteger writes = new AtomicInteger();
        @Override public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
            if (writes.incrementAndGet() == 4) throw new SQLException("TEST_FOURTH_POSTING_WRITE", "45000");
        }
    }
    private Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getProperty("spp.xbod.datasource-url"));
    }
    private void arm(String table) throws Exception {
        FourthInsertFault.writes.set(0);
        try (var connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER FAIL_FOURTH AFTER INSERT ON " + table
                    + " FOR EACH ROW CALL 'com.servpp.xboddemo.XbodPartialPostingTest$FourthInsertFault'");
        }
    }
    private int count(String table) throws Exception {
        try (var connection = connect(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertTrue(rows.next()); return rows.getInt(1);
        }
    }
    @Test public void partialDepositPostingFailureRestoresMoney() throws Exception { verify(false); }
    @Test public void partialReversalPostingFailureRestoresMoney() throws Exception { verify(true); }
    private void verify(boolean reversing) throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo);
            var paid = reversing ? demo.call("depositCash", deposit(100)) : null;
            String table = reversing ? "CASH_REVERSAL_POSTINGS" : "CASH_POSTINGS";
            arm(table);
            String service = reversing ? "reverseCash" : "depositCash";
            Object request = demo.request(service, reversing
                    ? reversal(paid.path("CashDepositAgreement"), 20261007, false) : deposit(100));
            boolean split = "testing".equals(System.getProperty("spp.environment"));
            if (split) assertThrows(IllegalStateException.class, () -> demo.execute(service, request));
            else demo.execute(service, request);
            assertEquals("Failure must occur after three successful row writes", 4, FourthInsertFault.writes.get());
            var failed = demo.mapper.valueToTree(request);
            var info = failed.path(reversing ? "CashReversalAgreement" : "CashDepositAgreement").path("resultInfo");
            assertEquals("FAILED", info.path("status").asText());
            assertEquals(split ? "RECOVERY_REQUIRED" : "ROLLED_BACK", info.path("failure").path("recoveryStatus").asText());
            assertEquals(0, count(table));
            amount(reversing ? "100" : "0", demo.call("findDepositAccount",
                    Map.of("DepositAccount", Map.of("accountNumber", "ACCOUNT-001"))).path("DepositAccount").path("interestBase"));
            amount(reversing ? "-100" : "0", demo.call("findCashSettlementAccount",
                    Map.of("CashSettlementAccount", Map.of("accountNumber", "CASH-001"))).path("CashSettlementAccount").path("balance"));
            if (reversing) {
                var original = demo.call("findCashDepositAgreement", Map.of("CashDepositAgreement",
                        Map.of("depositNumber", paid.path("CashDepositAgreement").path("depositNumber").asText())));
                assertEquals(split, original.path("CashDepositAgreement").path("reversed").asBoolean());
                assertEquals(8, count("CASH_POSTINGS"));
            }
            if (!split) {
                try (var connection = connect(); var statement = connection.createStatement()) {
                    statement.execute("DROP TRIGGER FAIL_FOURTH");
                }
                var retried = demo.call(service, reversing
                        ? reversal(paid.path("CashDepositAgreement"), 20261007, false) : deposit(100));
                assertEquals("SUCCEEDED", retried.path(reversing ? "CashReversalAgreement" : "CashDepositAgreement")
                        .path("resultInfo").path("status").asText());
                assertEquals(8, count(table));
                amount(reversing ? "0" : "100", retried.path("DepositAccount").path("interestBase"));
            }
        }
    }
}
