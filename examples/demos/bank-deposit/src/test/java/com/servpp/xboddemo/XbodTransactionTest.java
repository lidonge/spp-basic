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
        demo.call("openDepositAccount", Map.of("BankAccount", Map.of("accountNumber", "ACCOUNT-001"), "DepositAccount", Map.of("loanState", "NORMAL"), "AccountEligibility", Map.of("status", "ACTIVE", "openedOn", 20261007), "CashSettlementAccount", Map.of("accountNumber", "CASH-001")));
    }
    static Map<String,Object> deposit(int amount) {
        Map<String,Object> input = new LinkedHashMap<>();
        input.put("DepositAccount", Map.of("accountNumber", "ACCOUNT-001"));
        input.put("AccountEligibility", Map.of("accountNumber", "ACCOUNT-001"));
        input.put("CashSettlementAccount", Map.of("accountNumber", "CASH-001"));
        input.put("InterestIncomeAccount", Map.of("accountNumber", "BANK-INTEREST"));
        input.put("CollectionInterestIncomeAccount", Map.of("accountNumber", "BANK-COLLECTION"));
        input.put("PrincipalRecoveryAccount", Map.of("accountNumber", "BANK-PRINCIPAL"));
        input.put("DepositRequest", Map.of("amount", amount, "currency", "USD", "valueDate", 20261007));
        input.put("BusinessDay", Map.of("date", 20261007));
        input.put("DepositAccrualState", Map.of("accountNumber", "ACCOUNT-001"));
        input.put("DepositInterestPolicy", Map.of("normalEnabled", true, "normalUnit", 1, "overdraftUnit", 1));
        input.put("InterestCalendar", Map.of("convention", "ACT"));
        input.put("AgreementPolicy", Map.of("enabled", false, "mode", "2", "currency", "USD"));
        List<Map<String,?>> bands = new ArrayList<>();
        for (int slot=5;slot>=1;slot--) bands.add(Map.of("slot",slot,"threshold",0));
        input.put("AgreementBandArray", bands);
        input.put("DepositDetailPolicy", Map.of("itemCount", 1));
        input.put("CashChannel", Map.of("teller", true, "channel", "Teller", "transactionId", "GSA01102"));
        input.put("CashAuthorizationPolicy", Map.of("tellerLimit", 100000));
        input.put("DepositFeeParty", Map.of("accountKind", "NormalSavings", "branchNumber", "001", "regionCode", "R001"));
        input.put("DepositFeeContext", Map.of("channel", "Teller", "branchNumber", "001", "regionCode", "R001"));
        input.put("DepositOperatorFacts", Map.of("branch", "001", "area", "R001", "province", "P1", "accountingCentre", true, "operationCode", "1102", "transactionPrefix", "SA01102"));
        input.put("DepositBankProfile", Map.of("normalSavings", true, "depositRange", "All", "openingProvince", "P1"));
        List<Map<String,?>> lines = new ArrayList<>();
        for (int slot=1;slot<=8;slot++) lines.add(Map.of("slot",slot,"subject","DEMO-SUBJECT-"+slot,"side",slot==1||slot==2||slot==7?"Debit":"Credit"));
        input.put("DepositPostingLineArray", lines);
        return input;
    }
    static Map<String,Object> reversal(JsonNode original, int day, boolean overnight) {
        Map<String,Object> input = new LinkedHashMap<>();
        input.put("DepositAccount", Map.of("accountNumber", "ACCOUNT-001"));
        input.put("AccountEligibility", Map.of("accountNumber", "ACCOUNT-001"));
        input.put("CashSettlementAccount", Map.of("accountNumber", "CASH-001"));
        input.put("InterestIncomeAccount", Map.of("accountNumber", "BANK-INTEREST"));
        input.put("CollectionInterestIncomeAccount", Map.of("accountNumber", "BANK-COLLECTION"));
        input.put("PrincipalRecoveryAccount", Map.of("accountNumber", "BANK-PRINCIPAL"));
        input.put("DepositAccrualState", Map.of("accountNumber", "ACCOUNT-001"));
        input.put("CashDepositAgreement", Map.of("depositNumber", original.path("depositNumber").asText()));
        input.put("CashReversalAgreement", Map.of("businessDate", day, "newDetailItems", 1, "overnightMode2", overnight));
        input.put("CashChannel", Map.of("teller", true, "channel", "Teller", "transactionId", "GSA01102"));
        input.put("DepositOperatorFacts", Map.of("operationCode", "1102", "transactionPrefix", "SA01102"));
        return input;
    }
    static void amount(String expected, JsonNode node) { assertEquals(0,new java.math.BigDecimal(expected).compareTo(node.decimalValue())); }
    static void openSecondAccount(XbodTestSupport demo) throws Exception {
        demo.call("openDepositAccount",Map.of("BankAccount",Map.of("accountNumber","ACCOUNT-002"),
                "AccountEligibility",Map.of("openedOn",20261007),"CashSettlementAccount",Map.of("accountNumber","CASH-002")));
    }
    static void verifyStoredFailure(XbodTestSupport demo, JsonNode failed, String agreementField) throws Exception {
        String handlingNumber = failed.path("CashFailureRecord").path("handlingNumber").asText();
        assertFalse(handlingNumber.isEmpty());
        var stored = demo.call("findCashFailureRecord",Map.of("CashFailureRecord",Map.of("handlingNumber",handlingNumber)))
                .path("CashFailureRecord");
        assertEquals(failed.path("CashFailureRecord").path("handlingState"),stored.path("handlingState"));
        String link = agreementField.equals("CashDepositAgreement") ? "deposit" : "reversal";
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
            Object request = demo.request("reverseCash",reversal(paid.path("CashDepositAgreement"),20261007,false));
            boolean split = "testing".equals(System.getProperty("spp.environment"));
            JsonNode failed;
            try (var fault = com.servpp.service.runtime.ExampleServiceFaults.activate(receiptInsert ? "cboddeposit.insertCashReversalAgreement" : "cboddeposit.insertCashReversalPostingLine","before")) {
                if (split) { assertThrows(IllegalStateException.class, () -> demo.execute("reverseCash",request)); failed = demo.mapper.valueToTree(request); }
                else { failed = demo.execute("reverseCash",request); }
                fault.verifyTriggered();
            }
            var info = failed.path("CashReversalAgreement").path("resultInfo");
            assertEquals("FAILED",info.path("status").asText());
            assertEquals(split ? "RECOVERY_REQUIRED" : "ROLLED_BACK",info.path("failure").path("recoveryStatus").asText());
            assertEquals(!split,info.path("handled").asBoolean());
            assertEquals(split ? "RECONCILIATION_REQUIRED" : "NOT_COMPLETED",failed.path("CashFailureRecord").path("handlingState").asText());
            verifyStoredFailure(demo,failed,"CashReversalAgreement");
            var account = demo.call("findDepositAccount",Map.of("DepositAccount",Map.of("accountNumber","ACCOUNT-001")));
            amount("100",account.path("DepositAccount").path("interestBase"));
            var cash = demo.call("findCashSettlementAccount",Map.of("CashSettlementAccount",Map.of("accountNumber","CASH-001")));
            amount("-100",cash.path("CashSettlementAccount").path("balance"));
            var original = demo.call("findCashDepositAgreement",Map.of("CashDepositAgreement",Map.of("depositNumber",paid.path("CashDepositAgreement").path("depositNumber").asText())));
            assertEquals(split,original.path("CashDepositAgreement").path("reversed").asBoolean());
            var read = Map.of("CashReversalAgreement",Map.of("reversalNumber",failed.path("CashReversalAgreement").path("reversalNumber").asText()));
            if (split && !receiptInsert) {
                var persisted = demo.call("readCashReversal",read);
                assertEquals(0,persisted.path("CashReversalPostingLineArray").size());
            } else {
                if (receiptInsert) {
                    try (var connection=java.sql.DriverManager.getConnection(System.getProperty("spp.xbod.datasource-url"));
                         var statement=connection.createStatement();var rows=statement.executeQuery("SELECT COUNT(*) FROM CASH_REVERSALS")) {
                        assertTrue(rows.next());assertEquals(0,rows.getInt(1));
                    }
                } else assertEquals("PARTICIPANT_NOT_IDENTIFIED",assertThrows(IllegalStateException.class, () -> demo.call("readCashReversal",read)).getMessage());
                if (split) return;
                var retried = demo.call("reverseCash",reversal(paid.path("CashDepositAgreement"),20261007,false));
                assertEquals(retried.toString(),"SUCCEEDED",retried.path("CashReversalAgreement").path("resultInfo").path("status").asText());
                amount("0",retried.path("DepositAccount").path("interestBase"));
            }
        }
    }
    @Test public void reversalPersistsEightInverseLinesIncludingOriginalCashFee() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo);
            demo.call("registerFeeSchedule",Map.of("DepositFeeRate",Map.of("customerNumber","FEE-CUSTOMER","tariff","SAC00001","rate",0.001,"discount",0.5,"fixedAmount",1,"minimum",2,"maximum",50)));
            var input = deposit(10000);
            input.put("DepositFeeParty",Map.of("accountKind","NormalSavings","customerNumber","FEE-CUSTOMER","branchNumber","001","regionCode","R001"));
            input.put("DepositFeeContext",Map.of("channel","Teller","branchNumber","002","regionCode","R001"));
            input.put("DepositOperatorFacts",Map.of("branch","002","area","R001","province","P1","accountingCentre",true,"operationCode","1102","transactionPrefix","SA01102"));
            var paid = demo.call("depositCash",input);
            assertEquals(paid.toString(),"SUCCEEDED",paid.path("CashDepositAgreement").path("resultInfo").path("status").asText());
            amount("6",paid.path("CashDepositAgreement").path("fee"));
            var reversed = demo.call("reverseCash",reversal(paid.path("CashDepositAgreement"),20261007,false));
            assertEquals(reversed.toString(),"SUCCEEDED",reversed.path("CashReversalAgreement").path("resultInfo").path("status").asText());
            amount("6",reversed.path("CashReversalAgreement").path("refundAmount"));
            var saved = demo.call("readCashReversal",Map.of("CashReversalAgreement",Map.of("reversalNumber",reversed.path("CashReversalAgreement").path("reversalNumber").asText())));
            var lines = saved.path("CashReversalPostingLineArray"); assertEquals(8,lines.size());
            Set<Integer> slots = new HashSet<>();
            for (var line : lines) {
                int slot = line.path("slot").asInt(); assertTrue(slots.add(slot));
                assertEquals("DEMO-SUBJECT-"+slot,line.path("subject").asText());
                assertEquals(slot==1||slot==2||slot==7 ? "Credit" : "Debit",line.path("side").asText());
                amount(slot==1||slot==3 ? "10000" : slot==7||slot==8 ? "6" : "0",line.path("amount"));
            }
            amount("0",reversed.path("DepositAccount").path("interestBase"));
            amount("0",reversed.path("AccountEligibility").path("accountBalance"));
        }
    }
    @Test public void depositRejectsFactsFromAnotherAccountBeforePosting() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo); openSecondAccount(demo);
            var input = deposit(100); input.put("AccountEligibility",Map.of("accountNumber","ACCOUNT-002"));
            var failure = assertThrows(IllegalStateException.class, () -> demo.call("depositCash",input));
            assertEquals("ACCOUNT_PARTICIPANTS_MISMATCH",failure.getMessage());
            var account = demo.call("findDepositAccount",Map.of("DepositAccount",Map.of("accountNumber","ACCOUNT-001")));
            amount("0",account.path("DepositAccount").path("interestBase"));
        }
    }
    @Test public void reversalRejectsAnotherAccountsOriginalReceipt() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo); openSecondAccount(demo); var paid = demo.call("depositCash",deposit(100));
            var input = reversal(paid.path("CashDepositAgreement"),20261007,false);
            input.put("DepositAccount",Map.of("accountNumber","ACCOUNT-002"));
            input.put("AccountEligibility",Map.of("accountNumber","ACCOUNT-002"));
            input.put("DepositAccrualState",Map.of("accountNumber","ACCOUNT-002"));
            var failure = assertThrows(IllegalStateException.class, () -> demo.call("reverseCash",input));
            assertEquals("ORIGINAL_DEPOSIT_ACCOUNT_MISMATCH",failure.getMessage());
            var account = demo.call("findDepositAccount",Map.of("DepositAccount",Map.of("accountNumber","ACCOUNT-001")));
            amount("100",account.path("DepositAccount").path("interestBase"));
        }
    }
    @Test public void openingWithLimitedFactsGetsBusinessDefaultsFromSource() throws Exception {
        try (var demo = new XbodTestSupport()) {
            openInternalAccounts(demo);
            Object request = demo.mapper.convertValue(Map.of(
                    "BankAccount",Map.of("accountNumber","ACCOUNT-001"),
                    "DepositAccount",Map.of(),
                    "AccountEligibility",Map.of("openedOn",20261007),
                    "CashSettlementAccount",Map.of("accountNumber","CASH-001"),
                    "DepositAccrualState",Map.of(),
                    "AccountOpeningAgreement",Map.of()),
                    Class.forName("com.servpp.xbod.generated.cboddeposit.model.OpenDepositAccountRequest"));
            demo.execute("openDepositAccount",request);
            var account = demo.call("findDepositAccount",Map.of("DepositAccount",Map.of("accountNumber","ACCOUNT-001")));
            assertEquals("NORMAL",account.path("DepositAccount").path("loanState").asText());
            amount("0",account.path("DepositAccount").path("interestBase"));
            var facts = demo.call("findAccountEligibility",Map.of("AccountEligibility",Map.of("accountNumber","ACCOUNT-001")));
            assertEquals("ACTIVE",facts.path("AccountEligibility").path("status").asText());
            assertEquals(20261007,facts.path("AccountEligibility").path("lastTransactionDate").asInt());
            var accrual = demo.call("findDepositAccrualState",Map.of("DepositAccrualState",Map.of("accountNumber","ACCOUNT-001")));
            amount("0",accrual.path("DepositAccrualState").path("normalProduct"));
            var paid = demo.call("depositCash",deposit(100));
            assertEquals(paid.toString(),"SUCCEEDED",paid.path("CashDepositAgreement").path("resultInfo").path("status").asText());
            amount("100",paid.path("DepositAccount").path("interestBase"));
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
            var info = failed.path("CashDepositAgreement").path("resultInfo");
            assertEquals(failed.toString(),"FAILED",info.path("status").asText());
            assertEquals(split ? "RECOVERY_REQUIRED" : "ROLLED_BACK",info.path("failure").path("recoveryStatus").asText());
            assertEquals(!split,info.path("handled").asBoolean());
            assertEquals(split ? "RECONCILIATION_REQUIRED" : "NOT_COMPLETED",failed.path("CashFailureRecord").path("handlingState").asText());
            verifyStoredFailure(demo,failed,"CashDepositAgreement");
            var account = demo.call("findDepositAccount",Map.of("DepositAccount",Map.of("accountNumber","ACCOUNT-001")));
            amount("0",account.path("DepositAccount").path("interestBase"));
            var clearing = demo.call("findCashSettlementAccount",Map.of("CashSettlementAccount",Map.of("accountNumber","CASH-001")));
            amount("0",clearing.path("CashSettlementAccount").path("balance"));
            assertFalse(failed.path("CashFailureRecord").path("handlingNumber").asText().isEmpty());
        }
    }
    @Test public void overnightMode2UsesProcessingDayForRestoredMetadata() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo); var paid = demo.call("depositCash",deposit(100));
            var reversed = demo.call("reverseCash",reversal(paid.path("CashDepositAgreement"),20261008,true));
            assertEquals(reversed.toString(),"SUCCEEDED",reversed.path("CashReversalAgreement").path("resultInfo").path("status").asText());
            assertEquals(20261008,reversed.path("AccountEligibility").path("lastTransactionDate").asInt());
            assertFalse(reversed.path("CashReversalAgreement").path("reverseInstitutionLimit").asBoolean());
        }
    }
    @Test public void zeroOpeningThenCashDepositAndSameDayReversal() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo);
            var paid = demo.call("depositCash",deposit(100));
            assertEquals(paid.toString(), "SUCCEEDED", paid.path("CashDepositAgreement").path("resultInfo").path("status").asText());
            amount("100",paid.path("DepositAccount").path("interestBase"));
            amount("-100",paid.path("CashSettlementAccount").path("balance"));
            var reversed = demo.call("reverseCash",reversal(paid.path("CashDepositAgreement"),20261007,false));
            assertEquals(reversed.toString(),"SUCCEEDED",reversed.path("CashReversalAgreement").path("resultInfo").path("status").asText());
            amount("0",reversed.path("DepositAccount").path("interestBase"));
            amount("0",reversed.path("CashSettlementAccount").path("balance"));
            assertTrue(reversed.path("CashReversalAgreement").path("reverseInstitutionLimit").asBoolean());
        }
    }
    @Test public void crossDayReversalPreservesLaterDeposit() throws Exception {
        try (var demo = new XbodTestSupport()) {
            open(demo); var first = demo.call("depositCash",deposit(100)); demo.call("depositCash",deposit(50));
            var reversed = demo.call("reverseCash",reversal(first.path("CashDepositAgreement"),20261008,false));
            assertEquals(reversed.toString(),"SUCCEEDED",reversed.path("CashReversalAgreement").path("resultInfo").path("status").asText());
            amount("50",reversed.path("DepositAccount").path("interestBase"));
            amount("-50",reversed.path("CashSettlementAccount").path("balance"));
            assertFalse(reversed.path("CashReversalAgreement").path("reverseInstitutionLimit").asBoolean());
            assertEquals(20261008,reversed.path("AccountEligibility").path("lastTransactionDate").asInt());
        }
    }
}
