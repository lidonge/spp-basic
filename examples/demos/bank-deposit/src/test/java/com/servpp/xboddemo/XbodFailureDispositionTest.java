package com.servpp.xboddemo;

import org.junit.Test;
import org.h2.api.Trigger;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.servpp.service.runtime.ExampleServiceFaults;
import static org.junit.Assert.*;
import static com.servpp.xboddemo.XbodTransactionTest.*;

public class XbodFailureDispositionTest {
    public static class RejectFailureRecord implements Trigger {
        static final AtomicInteger attempts = new AtomicInteger();
        @Override public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
            attempts.incrementAndGet();
            throw new SQLException("TEST_FAILURE_RECORD_UNAVAILABLE", "45000");
        }
    }
    @Test public void depositFailureRecordUnavailablePreservesPrimaryFailure() throws Exception { verify(false); }
    @Test public void reversalFailureRecordUnavailablePreservesPrimaryFailure() throws Exception { verify(true); }
    private void verify(boolean reversing) throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo);
            var paid = reversing ? demo.call("depositCash",deposit(100)) : null;
            String url = System.getProperty("spp.xbod.datasource-url");
            RejectFailureRecord.attempts.set(0);
            try (var connection=DriverManager.getConnection(url);var statement=connection.createStatement()) {
                statement.execute("CREATE TRIGGER FAIL_HANDLING BEFORE INSERT ON CASH_FAILURES FOR EACH ROW CALL 'com.servpp.xboddemo.XbodFailureDispositionTest$RejectFailureRecord'");
            }
            String scene=reversing ? "reverseCash" : "depositCash";
            Object request=demo.request(scene,reversing ? reversal(paid.path("CashDepositAgreement"),20261007,false) : deposit(100));
            try (var fault=ExampleServiceFaults.activate(reversing ? "cboddeposit.insertCashReversalPostingLine" : "cboddeposit.insertCashDepositAgreement","before")) {
                var thrown=assertThrows(IllegalStateException.class,()->demo.execute(scene,request));
                assertTrue(thrown.toString(),thrown.toString().contains("EXAMPLE_SERVICE_FAULT"));
                fault.verifyTriggered();
            }
            assertEquals(1,RejectFailureRecord.attempts.get());
            var info=demo.mapper.valueToTree(request).path(reversing ? "CashReversalAgreement" : "CashDepositAgreement").path("resultInfo");
            assertEquals("FAILED",info.path("status").asText());assertFalse(info.path("handled").asBoolean());
            assertEquals("testing".equals(System.getProperty("spp.environment")) ? "RECOVERY_REQUIRED" : "ROLLED_BACK",
                    info.path("failure").path("recoveryStatus").asText());
            assertTrue(info.toString(),info.path("failure").path("code").asText().contains("EXAMPLE_SERVICE_FAULT"));
            assertTrue("Secondary disposition failure must remain visible: "+info,info.path("failure").path("suppressedCodes").size()>0);
            amount(reversing ? "100" : "0",demo.call("findDepositAccount",Map.of("DepositAccount",Map.of("accountNumber","ACCOUNT-001"))).path("DepositAccount").path("interestBase"));
            amount(reversing ? "-100" : "0",demo.call("findCashSettlementAccount",Map.of("CashSettlementAccount",Map.of("accountNumber","CASH-001"))).path("CashSettlementAccount").path("balance"));
            try (var connection=DriverManager.getConnection(url);var statement=connection.createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM CASH_FAILURES")) {
                assertTrue(rows.next());assertEquals(0,rows.getInt(1));
            }
        }
    }
}
