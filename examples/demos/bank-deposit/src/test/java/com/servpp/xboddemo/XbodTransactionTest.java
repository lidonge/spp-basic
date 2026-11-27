package com.servpp.xboddemo;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;

public class XbodTransactionTest {
    static void openInternalAccounts(XbodTestSupport demo) throws Exception {
        demo.call("openBankRepaymentAccounts", Map.of(
                "interestSubject", Map.of("accountNumber", "BANK-INTEREST"),
                "collectionSubject", Map.of("accountNumber", "BANK-COLLECTION"),
                "principalSubject", Map.of("accountNumber", "BANK-PRINCIPAL")));
    }
    static void open(XbodTestSupport demo) throws Exception {
        openInternalAccounts(demo);
        demo.call("openDepositAccount", Map.of("bankAccount", Map.of("accountNumber", "ACCOUNT-001"), "depositAccount", Map.of("loanState", "NORMAL"), "accountEligibility", Map.of("status", "ACTIVE", "openedOn", 20261007), "cashSettlementAccount", Map.of("accountNumber", "CASH-001")));
    }
    static Map<String,Object> deposit(int amount) {
        Map<String,Object> input = new LinkedHashMap<>();
        input.put("depositAccount", Map.of("accountNumber", "ACCOUNT-001"));
        input.put("accountEligibility", Map.of("accountNumber", "ACCOUNT-001"));
        input.put("cashSettlementAccount", Map.of("accountNumber", "CASH-001"));
        input.put("interestIncomeAccount", Map.of("accountNumber", "BANK-INTEREST"));
        input.put("collectionInterestIncomeAccount", Map.of("accountNumber", "BANK-COLLECTION"));
        input.put("principalRecoveryAccount", Map.of("accountNumber", "BANK-PRINCIPAL"));
        input.put("depositRequest", Map.of("amount", amount, "currency", "USD", "valueDate", 20261007));
        input.put("businessDay", Map.of("date", 20261007));
        input.put("depositAccrualState", Map.of("accountNumber", "ACCOUNT-001"));
        input.put("depositInterestPolicy", Map.of("normalEnabled", true, "normalUnit", 1, "overdraftUnit", 1));
        input.put("interestCalendar", Map.of("convention", "ACT"));
        input.put("agreementPolicy", Map.of("enabled", false, "mode", "2", "currency", "USD"));
        List<Map<String,?>> bands = new ArrayList<>();
        for (int slot=5;slot>=1;slot--) bands.add(Map.of("slot",slot,"threshold",0));
        input.put("agreementBands", bands);
        input.put("depositDetailPolicy", Map.of("itemCount", 1));
        input.put("cashChannel", Map.of("teller", true, "channel", "Teller", "transactionId", "GSA01102"));
        input.put("cashAuthorizationPolicy", Map.of("tellerLimit", 100000));
        input.put("depositFeeParty", Map.of("accountKind", "NormalSavings", "branchNumber", "001", "regionCode", "R001"));
        input.put("depositFeeContext", Map.of("channel", "Teller", "branchNumber", "001", "regionCode", "R001"));
        input.put("depositOperatorFacts", Map.of("branch", "001", "area", "R001", "province", "P1", "accountingCentre", true, "operationCode", "1102", "transactionPrefix", "SA01102"));
        input.put("depositBankProfile", Map.of("normalSavings", true, "depositRange", "All", "openingProvince", "P1"));
        List<Map<String,?>> lines = new ArrayList<>();
        for (int slot=1;slot<=8;slot++) lines.add(Map.of("slot",slot,"subject","DEMO-SUBJECT-"+slot,"side",slot==1||slot==2||slot==7?"Debit":"Credit"));
        input.put("depositPostingLines", lines);
        return input;
    }
    static Map<String,Object> reversal(JsonNode original, int day, boolean overnight) {
        Map<String,Object> input = new LinkedHashMap<>();
        input.put("depositAccount", Map.of("accountNumber", "ACCOUNT-001"));
        input.put("accountEligibility", Map.of("accountNumber", "ACCOUNT-001"));
        input.put("cashSettlementAccount", Map.of("accountNumber", "CASH-001"));
        input.put("interestIncomeAccount", Map.of("accountNumber", "BANK-INTEREST"));
        input.put("collectionInterestIncomeAccount", Map.of("accountNumber", "BANK-COLLECTION"));
        input.put("principalRecoveryAccount", Map.of("accountNumber", "BANK-PRINCIPAL"));
        input.put("depositAccrualState", Map.of("accountNumber", "ACCOUNT-001"));
        input.put("cashDepositAgreement", Map.of("depositNumber", original.path("depositNumber").asText()));
        input.put("cashReversalAgreement", Map.of("businessDate", day, "newDetailItems", 1, "overnightMode2", overnight));
        input.put("cashChannel", Map.of("teller", true, "channel", "Teller", "transactionId", "GSA01102"));
        input.put("depositOperatorFacts", Map.of("operationCode", "1102", "transactionPrefix", "SA01102"));
        return input;
    }
    static void amount(String expected, JsonNode node) { assertEquals(0,new java.math.BigDecimal(expected).compareTo(node.decimalValue())); }
    static void openSecondAccount(XbodTestSupport demo) throws Exception {
        demo.call("openDepositAccount",Map.of("bankAccount",Map.of("accountNumber","ACCOUNT-002"),
                "accountEligibility",Map.of("openedOn",20261007),"cashSettlementAccount",Map.of("accountNumber","CASH-002")));
    }
    static void verifyStoredFailure(XbodTestSupport demo, JsonNode failed, String agreementField) throws Exception {
        String handlingNumber = failed.path("cashFailureRecord").path("handlingNumber").asText();
        assertFalse(handlingNumber.isEmpty());
        var stored = demo.call("readCashFailure",Map.of("cashFailureRecord",Map.of("handlingNumber",handlingNumber)))
                .path("cashFailureRecord");
        assertEquals(failed.path("cashFailureRecord").path("handlingState"),stored.path("handlingState"));
        String link = agreementField.equals("cashDepositAgreement") ? "deposit" : "reversal";
        var expected = failed.path(agreementField).path("resultInfo").path("failure");
        var actual = stored.path(link).path("resultInfo").path("failure");
        assertFalse(expected.path("code").asText().isEmpty());
        for (String field : new String[]{"code","reason","serviceName","resourceName","phase","transactionOutcome","recoveryStatus"})
            assertEquals("persisted failure " + field,expected.path(field),actual.path(field));
    }
    @Test public void reversalPostingFailureRestoresFundsAndExposesOrdinaryStageOutcome() throws Exception { verifyReversalWriteFailure(false); }
    @Test public void reversalReceiptInsertFailurePreservesFundsAndReportsRecovery() throws Exception { verifyReversalWriteFailure(true); }
    private void verifyReversalWriteFailure(boolean receiptInsert) throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo); var paid = demo.call("depositCash",deposit(100));
            Object request = demo.request("reverseCash",reversal(paid.path("cashDepositAgreement"),20261007,false));
            boolean split = "testing".equals(System.getProperty("spp.environment"));
            JsonNode failed;
            try (var fault = com.servpp.service.runtime.ExampleServiceFaults.activate(receiptInsert ? "cboddeposit.insertCashReversalAgreement" : "cboddeposit.insertCashReversalPostingLine","before")) {
                if (split) { assertThrows(IllegalStateException.class, () -> demo.execute("reverseCash",request)); failed = demo.mapper.valueToTree(request); }
                else { failed = demo.execute("reverseCash",request); }
                fault.verifyTriggered();
            }
            var info = failed.path("cashReversalAgreement").path("resultInfo");
            assertEquals("FAILED",info.path("status").asText());
            assertEquals(split ? "RECOVERY_REQUIRED" : "ROLLED_BACK",info.path("failure").path("recoveryStatus").asText());
            assertEquals(!split,info.path("handled").asBoolean());
            assertEquals(split ? "RECONCILIATION_REQUIRED" : "NOT_COMPLETED",failed.path("cashFailureRecord").path("handlingState").asText());
            verifyStoredFailure(demo,failed,"cashReversalAgreement");
            var account = demo.readAccounts();
            amount("100",account.path("depositAccount").path("interestBase"));
            var cash = demo.readAccounts();
            amount("-100",cash.path("cashSettlementAccount").path("balance"));
            var original = demo.call("readCashDeposit",Map.of("cashDepositAgreement",Map.of("depositNumber",paid.path("cashDepositAgreement").path("depositNumber").asText())));
            assertEquals(split,original.path("cashDepositAgreement").path("reversed").asBoolean());
            var read = Map.of("cashReversalAgreement",Map.of("reversalNumber",failed.path("cashReversalAgreement").path("reversalNumber").asText()));
            if (split && !receiptInsert) {
                var persisted = demo.call("readCashReversal",read);
                assertEquals(0,persisted.path("cashReversalPostingLines").size());
            } else {
                if (receiptInsert) {
                    try (var connection=java.sql.DriverManager.getConnection(System.getProperty("spp.xbod.datasource-url"));
                         var statement=connection.createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM CASH_REVERSALS")) {
                        assertTrue(rows.next());assertEquals(0,rows.getInt(1));
                    }
                } else assertEquals("PARTICIPANT_NOT_IDENTIFIED",assertThrows(IllegalStateException.class, () -> demo.call("readCashReversal",read)).getMessage());
                if (split) return;
                var retried = demo.call("reverseCash",reversal(paid.path("cashDepositAgreement"),20261007,false));
                assertEquals(retried.toString(),"SUCCEEDED",retried.path("cashReversalAgreement").path("resultInfo").path("status").asText());
                amount("0",retried.path("depositAccount").path("interestBase"));
            }
        }
    }
    @Test public void reversalPersistsEightInverseLinesIncludingOriginalCashFee() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo);
            demo.call("registerFeeSchedule",Map.of("depositFeeRate",Map.of("customerNumber","FEE-CUSTOMER","tariff","SAC00001","rate",0.001,"discount",0.5,"fixedAmount",1,"minimum",2,"maximum",50)));
            var input = deposit(10000);
            input.put("depositFeeParty",Map.of("accountKind","NormalSavings","customerNumber","FEE-CUSTOMER","branchNumber","001","regionCode","R001"));
            input.put("depositFeeContext",Map.of("channel","Teller","branchNumber","002","regionCode","R001"));
            input.put("depositOperatorFacts",Map.of("branch","002","area","R001","province","P1","accountingCentre",true,"operationCode","1102","transactionPrefix","SA01102"));
            var paid = demo.call("depositCash",input);
            assertEquals(paid.toString(),"SUCCEEDED",paid.path("cashDepositAgreement").path("resultInfo").path("status").asText());
            amount("6",paid.path("cashDepositAgreement").path("fee"));
            var reversed = demo.call("reverseCash",reversal(paid.path("cashDepositAgreement"),20261007,false));
            assertEquals(reversed.toString(),"SUCCEEDED",reversed.path("cashReversalAgreement").path("resultInfo").path("status").asText());
            amount("6",reversed.path("cashReversalAgreement").path("refundAmount"));
            var saved = demo.call("readCashReversal",Map.of("cashReversalAgreement",Map.of("reversalNumber",reversed.path("cashReversalAgreement").path("reversalNumber").asText())));
            var lines = saved.path("cashReversalPostingLines"); assertEquals(8,lines.size());
            Set<Integer> slots = new HashSet<>();
            for (var line : lines) {
                int slot = line.path("slot").asInt(); assertTrue(slots.add(slot));
                assertEquals("DEMO-SUBJECT-"+slot,line.path("subject").asText());
                assertEquals(slot==1||slot==2||slot==7 ? "Credit" : "Debit",line.path("side").asText());
                amount(slot==1||slot==3 ? "10000" : slot==7||slot==8 ? "6" : "0",line.path("amount"));
            }
            amount("0",reversed.path("depositAccount").path("interestBase"));
            amount("0",reversed.path("accountEligibility").path("accountBalance"));
        }
    }
    @Test public void depositRejectsFactsFromAnotherAccountBeforePosting() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo); openSecondAccount(demo);
            var input = deposit(100); input.put("accountEligibility",Map.of("accountNumber","ACCOUNT-002"));
            var failure = assertThrows(IllegalStateException.class, () -> demo.call("depositCash",input));
            assertEquals("ACCOUNT_PARTICIPANTS_MISMATCH",failure.getMessage());
            var account = demo.readAccounts();
            amount("0",account.path("depositAccount").path("interestBase"));
        }
    }
    @Test public void reversalRejectsAnotherAccountsOriginalReceipt() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo); openSecondAccount(demo); var paid = demo.call("depositCash",deposit(100));
            var input = reversal(paid.path("cashDepositAgreement"),20261007,false);
            input.put("depositAccount",Map.of("accountNumber","ACCOUNT-002"));
            input.put("accountEligibility",Map.of("accountNumber","ACCOUNT-002"));
            input.put("depositAccrualState",Map.of("accountNumber","ACCOUNT-002"));
            var failure = assertThrows(IllegalStateException.class, () -> demo.call("reverseCash",input));
            assertEquals("ORIGINAL_DEPOSIT_ACCOUNT_MISMATCH",failure.getMessage());
            var account = demo.readAccounts();
            amount("100",account.path("depositAccount").path("interestBase"));
        }
    }
    @Test public void openingWithLimitedFactsGetsBusinessDefaultsFromSource() throws Exception {
        try (var demo = new XbodTestSupport()) {
            openInternalAccounts(demo);
            Object request = demo.mapper.convertValue(Map.of(
                    "bankAccount",Map.of("accountNumber","ACCOUNT-001"),
                    "depositAccount",Map.of(),
                    "accountEligibility",Map.of("openedOn",20261007),
                    "cashSettlementAccount",Map.of("accountNumber","CASH-001"),
                    "depositAccrualState",Map.of(),
                    "accountOpeningAgreement",Map.of()),
                    Class.forName("com.servpp.xbod.generated.cboddeposit.model.OpenDepositAccountRequest"));
            demo.execute("openDepositAccount",request);
            var account = demo.readAccounts();
            assertEquals("NORMAL",account.path("depositAccount").path("loanState").asText());
            amount("0",account.path("depositAccount").path("interestBase"));
            var facts = demo.readAccounts();
            assertEquals("ACTIVE",facts.path("accountEligibility").path("status").asText());
            assertEquals(20261007,facts.path("accountEligibility").path("lastTransactionDate").asInt());
            var accrual = demo.readAccounts();
            amount("0",accrual.path("depositAccrualState").path("normalProduct"));
            var paid = demo.call("depositCash",deposit(100));
            assertEquals(paid.toString(),"SUCCEEDED",paid.path("cashDepositAgreement").path("resultInfo").path("status").asText());
            amount("100",paid.path("depositAccount").path("interestBase"));
        }
    }
    @Test public void storageFailureRollsBackFundsAndRunsSourceFailureDisposition() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo);
            com.fasterxml.jackson.databind.JsonNode failed;
            Object request = demo.request("depositCash",deposit(100));
            boolean split = "testing".equals(System.getProperty("spp.environment"));
            try (var fault = com.servpp.service.runtime.ExampleServiceFaults.activate("cboddeposit.insertCashDepositAgreement", "before")) {
                if (split) {
                    assertThrows(IllegalStateException.class, () -> demo.execute("depositCash",request));
                    failed = demo.mapper.valueToTree(request);
                } else { failed = demo.execute("depositCash",request); }
                fault.verifyTriggered();
            }
            var info = failed.path("cashDepositAgreement").path("resultInfo");
            assertEquals(failed.toString(),"FAILED",info.path("status").asText());
            assertEquals(split ? "RECOVERY_REQUIRED" : "ROLLED_BACK",info.path("failure").path("recoveryStatus").asText());
            assertEquals(!split,info.path("handled").asBoolean());
            assertEquals(split ? "RECONCILIATION_REQUIRED" : "NOT_COMPLETED",failed.path("cashFailureRecord").path("handlingState").asText());
            verifyStoredFailure(demo,failed,"cashDepositAgreement");
            var account = demo.readAccounts();
            amount("0",account.path("depositAccount").path("interestBase"));
            var clearing = demo.readAccounts();
            amount("0",clearing.path("cashSettlementAccount").path("balance"));
            assertFalse(failed.path("cashFailureRecord").path("handlingNumber").asText().isEmpty());
        }
    }
    @Test public void overnightMode2UsesProcessingDayForRestoredMetadata() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo); var paid = demo.call("depositCash",deposit(100));
            var reversed = demo.call("reverseCash",reversal(paid.path("cashDepositAgreement"),20261008,true));
            assertEquals(reversed.toString(),"SUCCEEDED",reversed.path("cashReversalAgreement").path("resultInfo").path("status").asText());
            assertEquals(20261008,reversed.path("accountEligibility").path("lastTransactionDate").asInt());
            assertFalse(reversed.path("cashReversalAgreement").path("reverseInstitutionLimit").asBoolean());
        }
    }
    @Test public void zeroOpeningThenCashDepositAndSameDayReversal() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo);
            var paid = demo.call("depositCash",deposit(100));
            assertEquals(paid.toString(), "SUCCEEDED", paid.path("cashDepositAgreement").path("resultInfo").path("status").asText());
            amount("100",paid.path("depositAccount").path("interestBase"));
            amount("-100",paid.path("cashSettlementAccount").path("balance"));
            var reversed = demo.call("reverseCash",reversal(paid.path("cashDepositAgreement"),20261007,false));
            assertEquals(reversed.toString(),"SUCCEEDED",reversed.path("cashReversalAgreement").path("resultInfo").path("status").asText());
            amount("0",reversed.path("depositAccount").path("interestBase"));
            amount("0",reversed.path("cashSettlementAccount").path("balance"));
            assertTrue(reversed.path("cashReversalAgreement").path("reverseInstitutionLimit").asBoolean());
        }
    }
    @Test public void crossDayReversalPreservesLaterDeposit() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo); var first = demo.call("depositCash",deposit(100)); demo.call("depositCash",deposit(50));
            var reversed = demo.call("reverseCash",reversal(first.path("cashDepositAgreement"),20261008,false));
            assertEquals(reversed.toString(),"SUCCEEDED",reversed.path("cashReversalAgreement").path("resultInfo").path("status").asText());
            amount("50",reversed.path("depositAccount").path("interestBase"));
            amount("-50",reversed.path("cashSettlementAccount").path("balance"));
            assertFalse(reversed.path("cashReversalAgreement").path("reverseInstitutionLimit").asBoolean());
            assertEquals(20261008,reversed.path("accountEligibility").path("lastTransactionDate").asInt());
        }
    }
}
