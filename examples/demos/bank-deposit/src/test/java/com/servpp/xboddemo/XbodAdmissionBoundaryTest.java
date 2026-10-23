package com.servpp.xboddemo;

import org.junit.Test;
import java.sql.DriverManager;
import java.util.Map;
import static org.junit.Assert.*;
import static com.servpp.xboddemo.XbodTransactionTest.*;

public class XbodAdmissionBoundaryTest {
    @Test public void preservesArchivedAccountControlChecksAndFrozenAmountCalculation() throws Exception {
        try (var demo = new XbodTestSupport()) {
            demo.call("checkCashControl", Map.of("CashAccountControl", Map.of("controlStatus","Normal","productKind","DemandDeposit"),
                    "AccountEligibility", Map.of("status","ACTIVE")));
            Object[][] cases = {
                    {Map.of("controllingInstitution","branch-1"),"ACTIVE","EN432"},
                    {Map.of("controlStatus","PayNoReceive"),"ACTIVE","EN432"},
                    {Map.of("controlStatus","Locked"),"ACTIVE","EN432"},
                    {Map.of(),"FROZEN","EG877"},
                    {Map.of("cancelledOn",20261007),"ACTIVE","EN113"},
                    {Map.of("productKind","RealEstate"),"ACTIVE","EI090"}};
            for (var row : cases) assertEquals(row[2], assertThrows(IllegalStateException.class,
                    () -> demo.call("checkCashControl", Map.of("CashAccountControl",row[0],
                            "AccountEligibility",Map.of("status",row[1])))).getMessage());
            var calculated = demo.call("previewDeposit", Map.of("DepositRequest",Map.of("amount",1),
                    "DepositAccount",Map.of("interestBase",10,"frozenAmount",10,"loanState","NORMAL")));
            amount("1",calculated.path("DepositPlan").path("newAvailableBalance"));
        }
    }
    private Map<String,Object> crossBranchInput() {
        var input = deposit(600);
        input.put("DepositFeeParty",Map.of("accountKind","NormalSavings","customerNumber","FEE-CUSTOMER","branchNumber","001","regionCode","R001"));
        input.put("DepositFeeContext",Map.of("channel","Teller","branchNumber","002","regionCode","R001"));
        input.put("DepositOperatorFacts",Map.of("branch","002","area","R001","province","P1","accountingCentre",true,"operationCode","1102","transactionPrefix","SA01102"));
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
                    () -> demo.call("quoteDepositFee",Map.of("DepositRequest",quoted.get("DepositRequest"),
                            "DepositFeeParty",quoted.get("DepositFeeParty"),"DepositFeeContext",quoted.get("DepositFeeContext")))).getMessage());
            assertEquals("DEPOSIT_FEE_RATE_NOT_FOUND",assertThrows(IllegalStateException.class,
                    () -> demo.call("depositCash",crossBranchInput())).getMessage());
            var input = deposit(600); input.put("CashAccountControl",Map.of("controlStatus","Locked"));
            assertEquals("EN432",assertThrows(IllegalStateException.class,() -> demo.call("depositCash",input)).getMessage());
            amount("0",demo.call("findDepositAccount",Map.of("DepositAccount",Map.of("accountNumber","ACCOUNT-001"))).path("DepositAccount").path("interestBase"));
            amount("0",demo.call("findCashSettlementAccount",Map.of("CashSettlementAccount",Map.of("accountNumber","CASH-001"))).path("CashSettlementAccount").path("balance"));
            assertEquals(0,demo.call("findAccountEligibility",Map.of("AccountEligibility",Map.of("accountNumber","ACCOUNT-001"))).path("AccountEligibility").path("detailCount").asInt());
            noTradeRows();
        }
    }
    @Test public void crossBranchMinimumFeeIsPersistedInBothFeeLines() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo);
            demo.call("registerFeeSchedule",Map.of("DepositFeeRate",Map.of("customerNumber","FEE-CUSTOMER","tariff","SAC00001",
                    "rate",0.001,"discount",1,"fixedAmount",1,"minimum",2,"maximum",50)));
            var result = demo.call("depositCash",crossBranchInput());
            assertEquals("SUCCEEDED",result.path("CashDepositAgreement").path("resultInfo").path("status").asText());
            amount("2",result.path("CashDepositAgreement").path("fee"));
            var lines = demo.call("listDepositPostings",Map.of("CashDepositAgreement",Map.of("depositNumber",
                    result.path("CashDepositAgreement").path("depositNumber").asText()))).path("DepositPostingLineArray");
            assertEquals(8,lines.size());
            for (var line : lines) if (line.path("slot").asInt()==7 || line.path("slot").asInt()==8) amount("2",line.path("amount"));
            amount("602",result.path("DepositPostingTotals").path("debit"));
            amount("602",result.path("DepositPostingTotals").path("credit"));
        }
    }
}
