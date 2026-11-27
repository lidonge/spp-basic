package com.servpp.xboddemo;

import org.junit.Test;
import java.sql.DriverManager;
import java.util.Map;
import static org.junit.Assert.*;
import static com.servpp.xboddemo.XbodTransactionTest.*;

public class XbodAdmissionBoundaryTest {
    @Test public void preservesArchivedAccountControlChecksAndFrozenAmountCalculation() throws Exception {
        try (var demo = new XbodTestSupport()) {
            demo.call("assessCashDeposit", Map.of("depositRequest", Map.of("amount", 1), "cashAccountControl", Map.of("controlStatus","Normal","productKind","DemandDeposit"),
                    "accountEligibility", Map.of("status","ACTIVE")));
            Object[][] cases = {
                    {Map.of("controllingInstitution","branch-1"),"ACTIVE","EN432"},
                    {Map.of("controlStatus","PayNoReceive"),"ACTIVE","EN432"},
                    {Map.of("controlStatus","Locked"),"ACTIVE","EN432"},
                    {Map.of(),"FROZEN","EG877"},
                    {Map.of("cancelledOn",20261007),"ACTIVE","EN113"},
                    {Map.of("productKind","RealEstate"),"ACTIVE","EI090"}};
            for (var row : cases) assertEquals(row[2], assertThrows(IllegalStateException.class,
                    () -> demo.call("assessCashDeposit", Map.of("depositRequest", Map.of("amount", 1), "cashAccountControl",row[0],
                            "accountEligibility",Map.of("status",row[1])))).getMessage());
            var calculated = demo.call("previewDeposit", Map.of("depositRequest",Map.of("amount",1),
                    "depositAccount",Map.of("interestBase",10,"frozenAmount",10,"loanState","NORMAL")));
            amount("1",calculated.path("depositPlan").path("newAvailableBalance"));
        }
    }
    private Map<String,Object> crossBranchInput() {
        var input = deposit(600);
        input.put("depositFeeParty",Map.of("accountKind","NormalSavings","customerNumber","FEE-CUSTOMER","branchNumber","001","regionCode","R001"));
        input.put("depositFeeContext",Map.of("channel","Teller","branchNumber","002","regionCode","R001"));
        input.put("depositOperatorFacts",Map.of("branch","002","area","R001","province","P1","accountingCentre",true,"operationCode","1102","transactionPrefix","SA01102"));
        return input;
    }
    private void noTradeRows() throws Exception {
        try (var connection = DriverManager.getConnection(System.getProperty("spp.xbod.datasource-url")); var statement = connection.createStatement()) {
            for (String table : new String[]{"CASH_DEPOSITS","CASH_POSTINGS"}) {
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM "+table)) {
                    assertTrue(rows.next()); assertEquals(table,0,rows.getInt(1));
                }
            }
        }
    }
    @Test public void missingFeeAndControlRejectionsLeaveNoMoneyOrTrade() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo);
            var quoted = crossBranchInput();
            assertEquals("DEPOSIT_FEE_RATE_NOT_FOUND",assertThrows(IllegalStateException.class,
                    () -> demo.call("quoteDepositFee",Map.of("depositRequest",quoted.get("depositRequest"),
                            "depositFeeParty",quoted.get("depositFeeParty"),"depositFeeContext",quoted.get("depositFeeContext")))).getMessage());
            assertEquals("DEPOSIT_FEE_RATE_NOT_FOUND",assertThrows(IllegalStateException.class,
                    () -> demo.call("depositCash",crossBranchInput())).getMessage());
            var input = deposit(600); input.put("cashAccountControl",Map.of("controlStatus","Locked"));
            assertEquals("EN432",assertThrows(IllegalStateException.class,() -> demo.call("depositCash",input)).getMessage());
            amount("0",demo.readAccounts().path("depositAccount").path("interestBase"));
            amount("0",demo.readAccounts().path("cashSettlementAccount").path("balance"));
            assertEquals(0,demo.readAccounts().path("accountEligibility").path("detailCount").asInt());
            noTradeRows();
        }
    }
    @Test public void crossBranchMinimumFeeIsPersistedInBothFeeLines() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo);
            demo.call("registerFeeSchedule",Map.of("depositFeeRate",Map.of("customerNumber","FEE-CUSTOMER","tariff","SAC00001",
                    "rate",0.001,"discount",1,"fixedAmount",1,"minimum",2,"maximum",50)));
            var result = demo.call("depositCash",crossBranchInput());
            assertEquals("SUCCEEDED",result.path("cashDepositAgreement").path("resultInfo").path("status").asText());
            amount("2",result.path("cashDepositAgreement").path("fee"));
            var lines = demo.call("readCashDeposit",Map.of("cashDepositAgreement",Map.of("depositNumber",
                    result.path("cashDepositAgreement").path("depositNumber").asText()))).path("depositPostingLines");
            assertEquals(8,lines.size());
            for (var line : lines) if (line.path("slot").asInt()==7 || line.path("slot").asInt()==8) amount("2",line.path("amount"));
            amount("602",result.path("depositPostingTotals").path("debit"));
            amount("602",result.path("depositPostingTotals").path("credit"));
        }
    }
}
