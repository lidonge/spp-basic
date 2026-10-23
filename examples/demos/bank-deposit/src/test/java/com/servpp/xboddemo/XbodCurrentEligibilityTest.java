package com.servpp.xboddemo;

import org.junit.Test;
import java.sql.DriverManager;
import java.util.Map;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.servpp.service.repository.JdbcObjectSnapshotStore;
import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import static org.junit.Assert.*;
import static com.servpp.xboddemo.XbodTransactionTest.*;

public class XbodCurrentEligibilityTest {
    @Test public void currentFrozenStatusOverridesCallerAndOriginalActiveFacts() throws Exception {
        try (var demo=new XbodTestSupport()) {
            open(demo);var paid=demo.call("depositCash",deposit(100));
            String url=System.getProperty("spp.xbod.datasource-url");
            var snapshots=new JdbcObjectSnapshotStore(new DriverManagerJdbcDataSource("org.h2.Driver",url,null,null),
                    value->demo.mapper.valueToTree(value).toString(),"xbod_cash_record");
            int changed=0;
            for (var row:snapshots.findAll()) if (row.objectType().equals("AccountEligibility")) {
                var payload=(ObjectNode)demo.mapper.readTree(row.payloadJson());
                if (payload.path("accountNumber").asText().equals("ACCOUNT-001")) {
                    payload.put("status","FROZEN");snapshots.save(row.objectType(),row.objectId(),payload);changed++;
                }
            }
            assertEquals(1,changed);
            try(var connection=DriverManager.getConnection(url);var statement=connection.createStatement()) {
                assertEquals(1,statement.executeUpdate("UPDATE ACCOUNT_ELIGIBILITY SET status='FROZEN' WHERE accountNumber='ACCOUNT-001'"));
            }
            var input=reversal(paid.path("CashDepositAgreement"),20261007,false);
            input.put("AccountEligibility",Map.of("accountNumber","ACCOUNT-001","status","ACTIVE"));
            assertEquals("EN111",assertThrows(IllegalStateException.class,()->demo.call("reverseCash",input)).getMessage());
            // SA01116 remains a core-rule exception, not an admitted cash reversal path.
            input.put("DepositOperatorFacts",Map.of("operationCode","1102","transactionPrefix","SA01116"));
            assertEquals("UNSUPPORTED_CASH_REVERSAL_PATH",assertThrows(IllegalStateException.class,()->demo.call("reverseCash",input)).getMessage());
            amount("100",demo.call("findDepositAccount",Map.of("DepositAccount",Map.of("accountNumber","ACCOUNT-001"))).path("DepositAccount").path("interestBase"));
            assertEquals("FROZEN",demo.call("findAccountEligibility",Map.of("AccountEligibility",Map.of("accountNumber","ACCOUNT-001"))).path("AccountEligibility").path("status").asText());
            assertFalse(demo.call("findCashDepositAgreement",Map.of("CashDepositAgreement",Map.of("depositNumber",paid.path("CashDepositAgreement").path("depositNumber").asText()))).path("CashDepositAgreement").path("reversed").asBoolean());
            try(var connection=DriverManager.getConnection(url);var statement=connection.createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM CASH_REVERSALS")) {
                assertTrue(rows.next());assertEquals(0,rows.getInt(1));
            }
        }
    }
}
