package com.servpp.xboddemo;

import org.junit.Test;
import java.sql.*;
import java.util.Map;
import com.servpp.service.runtime.ExampleJdbcFaults;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;
import static com.servpp.xboddemo.XbodTransactionTest.*;

public class XbodCommitOutcomeTest {
    @Test public void localDepositCommitFaultsReportActualCertainty() throws Exception { verify(false); }
    @Test public void localReversalCommitFaultsReportActualCertainty() throws Exception { verify(true); }
    private void verify(boolean reversing) throws Exception {
        assumeFalse("This verifies the single-schema LOCAL boundary", "testing".equals(System.getProperty("spp.environment")));
        String[] points={"beforeCommit","commitBefore","commitAfter","afterCommit"};
        String[] outcomes={"ROLLED_BACK","UNKNOWN","UNKNOWN","COMMITTED"};
        for (int i=0;i<points.length;i++) {
            try (var demo=new XbodTestSupport()) {
                open(demo);
                var paid = reversing ? demo.call("depositCash",deposit(100)) : null;
                String scene = reversing ? "reverseCash" : "depositCash";
                Object request=demo.request(scene,reversing ? reversal(paid.path("CashDepositAgreement"),20261007,false) : deposit(100));
                try (var fault=ExampleJdbcFaults.activate(reversing ? "cboddeposit.insertCashReversalAgreement" : "cboddeposit.insertCashDepositAgreement",points[i])) {
                    if (i==0) demo.execute(scene,request);
                    else assertThrows(IllegalStateException.class,()->demo.execute(scene,request));
                    fault.verifyTriggered();
                }
                var info=demo.mapper.valueToTree(request).path(reversing ? "CashReversalAgreement" : "CashDepositAgreement").path("resultInfo");
                assertEquals(points[i]+": "+info,outcomes[i],info.path("failure").path("transactionOutcome").asText());
                assertEquals(i==0,info.path("handled").asBoolean());
                amount(reversing ? (i<2 ? "100" : "0") : (i<2 ? "0" : "100"),demo.call("findDepositAccount",Map.of("DepositAccount",Map.of("accountNumber","ACCOUNT-001"))).path("DepositAccount").path("interestBase"));
                amount(reversing ? (i<2 ? "-100" : "0") : (i<2 ? "0" : "-100"),demo.call("findCashSettlementAccount",Map.of("CashSettlementAccount",Map.of("accountNumber","CASH-001"))).path("CashSettlementAccount").path("balance"));
                try (var connection=DriverManager.getConnection(System.getProperty("spp.xbod.datasource-url"));var statement=connection.createStatement()) {
                    for (var table: (reversing ? new String[]{"CASH_REVERSALS","CASH_REVERSAL_POSTINGS"} : new String[]{"CASH_DEPOSITS","CASH_POSTINGS"})) {
                        try (var rows=statement.executeQuery("SELECT COUNT(*) FROM "+table)) {
                            assertTrue(rows.next());assertEquals(points[i]+" "+table,i<2 ? 0 : table.endsWith("POSTINGS") ? 8 : 1,rows.getInt(1));
                        }
                    }
                }
            }
        }
    }
}
