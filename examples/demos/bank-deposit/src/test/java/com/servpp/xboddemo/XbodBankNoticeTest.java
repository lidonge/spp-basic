package com.servpp.xboddemo;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;
import static com.servpp.xboddemo.XbodTransactionTest.*;

public class XbodBankNoticeTest {
    @Test public void branchAndOperatorRejectionsLeaveNoTradeOrMoney() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo);
            var input = deposit(100);
            input.put("depositBankProfile",Map.of("normalSavings",true,"depositRange","SameBranch","openingBranch","002","openingProvince","P1"));
            input.put("depositOperatorFacts",Map.of("branch","001","area","R001","province","P1","accountingCentre",false,
                    "operationCode","1102","transactionPrefix","SA01102"));
            assertEquals("EN134",assertThrows(IllegalStateException.class,() -> demo.call("depositCash",input)).getMessage());
            input.put("depositOperatorFacts",Map.of("branch","001","area","R002","province","P1","accountingCentre",false,
                    "operationCode","1102","transactionPrefix","SA01102"));
            assertEquals("DEPOSIT_OPERATOR_CONTEXT_MISMATCH",assertThrows(IllegalStateException.class,() -> demo.call("depositCash",input)).getMessage());
            amount("0",demo.readAccounts().path("depositAccount").path("interestBase"));
            amount("0",demo.readAccounts().path("cashSettlementAccount").path("balance"));
            assertEquals(0,demo.readAccounts().path("accountEligibility").path("detailCount").asInt());
            try (var connection = java.sql.DriverManager.getConnection(System.getProperty("spp.xbod.datasource-url")); var statement = connection.createStatement()) {
                for (String table : new String[]{"CASH_DEPOSITS","CASH_POSTINGS"}) {
                    try (var rows = statement.executeQuery("SELECT COUNT(*) FROM "+table)) {
                        assertTrue(rows.next()); assertEquals(table,0,rows.getInt(1));
                    }
                }
            }
        }
    }
    @Test public void completedDepositRetainsWarningsAndOriginalOperatorFacts() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo);
            var input = deposit(600);
            input.put("depositBankProfile",Map.of("normalSavings",true,"depositRange","All","openingProvince","P1",
                    "accountSeries","49927","cardLinked",true,"cardRecordFound",true,"cardLoss","Oral","passbookLoss","Written"));
            input.put("depositOperatorFacts",Map.of("branch","001","area","R001","province","P1","accountingCentre",true,
                    "operationCode","1102","transactionPrefix","SA01102","numericTeller",true,"mode","Online","agencySigned",false));
            input.put("cashChannel",Map.of("teller",true,"channel","Teller","transactionId","GSA01102","businessCode","SA0"));
            input.put("cashAuthorizationPolicy",Map.of("tellerLimit",100000,"warningThreshold",500));
            var paid = demo.call("depositCash",input);
            assertEquals("SUCCEEDED",paid.path("cashDepositAgreement").path("resultInfo").path("status").asText());
            String number = paid.path("cashDepositAgreement").path("depositNumber").asText();
            var saved = demo.call("readCashDeposit",Map.of("cashDepositAgreement",Map.of("depositNumber",number))).path("cashDepositAgreement");
            assertEquals("WN200",saved.path("notices").path("cardWarning").asText());
            assertEquals("WN201",saved.path("notices").path("passbookWarning").asText());
            assertEquals("WARNING",saved.path("admission").path("status").asText());
            assertEquals("F0554",saved.path("admission").path("warningCode").asText());
            assertEquals("SA01102",saved.path("operator").path("transactionPrefix").asText());
            assertFalse(saved.path("operator").path("agencySigned").asBoolean());
            assertEquals("49927",saved.path("bankProfile").path("accountSeries").asText());
            input.put("depositBankProfile",Map.of("normalSavings",true,"depositRange","All","openingProvince","P1"));
            input.put("depositOperatorFacts",Map.of("branch","001","area","R001","province","P1","accountingCentre",true,
                    "operationCode","1102","transactionPrefix","SA01102","agencySigned",true));
            demo.call("depositCash",input);
            var reread = demo.call("readCashDeposit",Map.of("cashDepositAgreement",Map.of("depositNumber",number))).path("cashDepositAgreement");
            assertEquals(saved.path("notices"),reread.path("notices"));
            assertEquals(saved.path("operator"),reread.path("operator"));
            assertEquals(saved.path("bankProfile"),reread.path("bankProfile"));
            amount("1200",demo.readAccounts().path("depositAccount").path("interestBase"));
        }
    }
}
