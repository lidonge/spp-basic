package com.servpp.xboddemo;

import org.junit.Test;
import java.util.Map;
import java.sql.DriverManager;
import com.servpp.service.runtime.ExampleServiceFaults;
import static org.junit.Assert.*;
import static com.servpp.xboddemo.XbodTransactionTest.*;

public class XbodAccrualFailureTest {
    @Test public void nonzeroAccrualRespectsLocalRollbackAndSplitRecoveryBoundary() throws Exception {
        try(var demo=new XbodTestSupport()) {
            open(demo);demo.call("depositCash",deposit(100));
            var input=deposit(50);input.put("BusinessDay",Map.of("date",20261008));
            input.put("DepositRequest",Map.of("amount",50,"currency","CNY","valueDate",20261008));
            Object request=demo.request("depositCash",input);
            boolean split="testing".equals(System.getProperty("spp.environment"));
            try(var fault=ExampleServiceFaults.activate("cboddeposit.insertCashDepositAgreement","before")) {
                if(split) assertThrows(IllegalStateException.class,()->demo.execute("depositCash",request));
                else demo.execute("depositCash",request);
                fault.verifyTriggered();
            }
            var failed=demo.mapper.valueToTree(request);
            var info=failed.path("CashDepositAgreement").path("resultInfo");
            assertEquals("FAILED",info.path("status").asText());assertEquals(!split,info.path("handled").asBoolean());
            assertEquals(split ? "RECOVERY_REQUIRED" : "ROLLED_BACK",info.path("failure").path("recoveryStatus").asText());
            verifyStoredFailure(demo,failed,"CashDepositAgreement");
            amount("100",demo.call("findDepositAccount",Map.of("DepositAccount",Map.of("accountNumber","ACCOUNT-001"))).path("DepositAccount").path("interestBase"));
            amount("-100",demo.call("findCashSettlementAccount",Map.of("CashSettlementAccount",Map.of("accountNumber","CASH-001"))).path("CashSettlementAccount").path("balance"));
            var accrual=demo.call("findDepositAccrualState",Map.of("DepositAccrualState",Map.of("accountNumber","ACCOUNT-001"))).path("DepositAccrualState");
            amount(split ? "100" : "0",accrual.path("normalProduct"));
            assertEquals(split ? 20261008 : 20261007,accrual.path("lastProductDate").asInt());
            try(var connection=DriverManager.getConnection(System.getProperty("spp.xbod.datasource-url"));var statement=connection.createStatement()) {
                for(String table:new String[]{"CASH_DEPOSITS","CASH_POSTINGS"})try(var rows=statement.executeQuery("SELECT COUNT(*) FROM "+table)) {
                    assertTrue(rows.next());assertEquals(table.equals("CASH_DEPOSITS")?1:8,rows.getInt(1));
                }
            }
            if(!split) {
                var retried=demo.call("depositCash",input);assertEquals("SUCCEEDED",retried.path("CashDepositAgreement").path("resultInfo").path("status").asText());
                amount("150",retried.path("DepositAccount").path("interestBase"));
                amount("100",demo.call("findDepositAccrualState",Map.of("DepositAccrualState",Map.of("accountNumber","ACCOUNT-001"))).path("DepositAccrualState").path("normalProduct"));
            }
        }
    }
}
