package com.servpp.xboddemo;

import org.junit.Test;
import java.util.Map;
import java.sql.DriverManager;
import static org.junit.Assert.*;
import static com.servpp.xboddemo.XbodTransactionTest.*;

public class XbodDailyCashTest {
    @Test public void storedDailyTotalsTriggerWarningAndResetOnNextBusinessDay() throws Exception {
        try (var demo=new XbodTestSupport()) {
            open(demo);
            int[] payments={300,300,100};String[] balances={"300","600","700"};String[] daily={"300","600","100"};
            for (int i=0;i<3;i++) {
                int day=i<2 ? 20261007 : 20261008;
                var input=deposit(payments[i]);
                input.put("businessDay",Map.of("date",day));input.put("depositRequest",Map.of("amount",payments[i],"currency","CNY","valueDate",day));
                input.put("cashAuthorizationPolicy",Map.of("warningThreshold",500,"tellerLimit",100000));
                input.put("cashChannel",Map.of("teller",true,"channel","Teller","transactionId","GSA01102","businessCode","SA0"));
                var result=demo.call("depositCash",input);
                var stored=demo.readAccounts().path("accountEligibility");
                amount(daily[i],stored.path("cashDepositedToday"));assertEquals(i+1,stored.path("detailCount").asInt());
                assertEquals(day,stored.path("lastTransactionDate").asInt());amount("0",stored.path("transferredToday"));
                amount(balances[i],demo.readAccounts().path("depositAccount").path("interestBase"));
                var original=demo.call("readCashDeposit",Map.of("cashDepositAgreement",Map.of("depositNumber",result.path("cashDepositAgreement").path("depositNumber").asText()))).path("cashDepositAgreement");
                assertEquals(i==1 ? "F0554" : "",original.path("admission").path("warningCode").asText());
                amount(daily[i],original.path("admission").path("dayCashTotal"));
            }
            amount("600",demo.readAccounts().path("depositAccrualState").path("normalProduct"));
        }
    }
    @Test public void invalidAccrualUnitRejectsBeforeAnyTradeOrMoney() throws Exception {
        try (var demo=new XbodTestSupport()) {
            open(demo);var input=deposit(100);
            input.put("depositInterestPolicy",Map.of("normalEnabled",true,"normalUnit",0,"overdraftUnit",1));
            assertEquals("INVALID_ACCRUAL_UNIT",assertThrows(IllegalStateException.class,()->demo.call("depositCash",input)).getMessage());
            amount("0",demo.readAccounts().path("depositAccount").path("interestBase"));
            var accrual=demo.readAccounts().path("depositAccrualState");
            amount("0",accrual.path("normalProduct"));assertEquals(20261007,accrual.path("lastProductDate").asInt());
            try (var connection=DriverManager.getConnection(System.getProperty("spp.xbod.datasource-url"));var statement=connection.createStatement()) {
                for (String table:new String[]{"CASH_DEPOSITS","CASH_POSTINGS"}) try (var rows=statement.executeQuery("SELECT COUNT(*) FROM "+table)) { assertTrue(rows.next());assertEquals(0,rows.getInt(1)); }
            }
        }
    }
}
