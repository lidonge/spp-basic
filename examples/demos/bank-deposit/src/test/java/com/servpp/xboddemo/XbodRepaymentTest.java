package com.servpp.xboddemo;

import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.transaction.JdbcQuantumJournalStore;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Existing debt is test data. All repayment and reversal operations execute generated SPP. */
public class XbodRepaymentTest {
    private JdbcQuantumJournalStore ledger() {
        String url = System.getProperty("testing".equals(System.getProperty("spp.environment"))
                ? "spp.xbod.testing.ledger-url" : "spp.xbod.datasource-url");
        return new JdbcQuantumJournalStore(new DriverManagerJdbcDataSource("org.h2.Driver", url, null, null),
                "xbod_quantum_journal", "xbod_quantum_value", "text");
    }
    @Test public void cashReversalKeepsXbodPrincipalBasedExposureRule() throws Exception {
        try (var demo = new XbodTestSupport()) {
            XbodTransactionTest.open(demo);
            var store = ledger();
            for (var entry : Map.of("overdraftPrincipal","20","overdraftExposure","5").entrySet()) {
                String attribute = "DepositAccount."+entry.getKey();
                var value = store.findCurrentValues().stream().filter(v -> v.attributeId().equals(attribute)).findFirst().orElseThrow();
                store.seedCurrentValue(value.groupId(),attribute,value.objectId(),new BigDecimal(entry.getValue()));
            }
            var paid = demo.call("depositCash",XbodTransactionTest.deposit(10));
            assertEquals("SUCCEEDED",paid.path("CashDepositAgreement").path("resultInfo").path("status").asText());
            XbodTransactionTest.amount("-5",paid.path("CashDepositAgreement").path("exposureDelta"));
            XbodTransactionTest.amount("10",paid.path("CashDepositAgreement").path("principalPaid"));
            XbodTransactionTest.amount("0",paid.path("DepositAccount").path("overdraftExposure"));
            var input = XbodTransactionTest.reversal(paid.path("CashDepositAgreement"),20261007,false);
            input.put("DepositReversalCapacity",Map.of("overdraftPermitted",true,"overdraftLimit",20));
            var reversed = demo.call("reverseCash",input);
            assertEquals("SUCCEEDED",reversed.path("CashReversalAgreement").path("resultInfo").path("status").asText());
            var account = demo.call("findDepositAccount",Map.of("DepositAccount",Map.of("accountNumber","ACCOUNT-001"))).path("DepositAccount");
            XbodTransactionTest.amount("20",account.path("overdraftPrincipal"));
            XbodTransactionTest.amount("10",account.path("overdraftExposure"));
            XbodTransactionTest.amount("0",demo.call("findPrincipalRecoveryAccount",Map.of("PrincipalRecoveryAccount",
                    Map.of("accountNumber","BANK-PRINCIPAL"))).path("PrincipalRecoveryAccount").path("balance"));
        }
    }
    private void seedDebt() {
        var store = ledger();
        for (var entry : Map.of("overdraftInterest", "10", "collectionInterest", "5",
                "overdraftPrincipal", "20", "overdraftExposure", "20").entrySet()) {
            String attribute = "DepositAccount." + entry.getKey();
            var value = store.findCurrentValues().stream().filter(v -> v.attributeId().equals(attribute)).findFirst().orElseThrow();
            store.seedCurrentValue(value.groupId(), attribute, value.objectId(), new BigDecimal(entry.getValue()));
        }
    }
    private void bankBalances(XbodTestSupport demo, String interest, String collection, String principal) throws Exception {
        var i = demo.call("findInterestIncomeAccount", Map.of("InterestIncomeAccount", Map.of("accountNumber", "BANK-INTEREST")));
        var c = demo.call("findCollectionInterestIncomeAccount", Map.of("CollectionInterestIncomeAccount", Map.of("accountNumber", "BANK-COLLECTION")));
        var p = demo.call("findPrincipalRecoveryAccount", Map.of("PrincipalRecoveryAccount", Map.of("accountNumber", "BANK-PRINCIPAL")));
        XbodTransactionTest.amount(interest, i.path("InterestIncomeAccount").path("balance"));
        XbodTransactionTest.amount(collection, c.path("CollectionInterestIncomeAccount").path("balance"));
        XbodTransactionTest.amount(principal, p.path("PrincipalRecoveryAccount").path("balance"));
    }
    @Test public void repaymentCreditsActualBankAccountsAndReversalRestoresDebt() throws Exception {
        try (var demo = new XbodTestSupport()) {
            XbodTransactionTest.open(demo); seedDebt();
            var paid = demo.call("depositCash", XbodTransactionTest.deposit(100));
            assertEquals(paid.toString(), "SUCCEEDED", paid.path("CashDepositAgreement").path("resultInfo").path("status").asText());
            XbodTransactionTest.amount("65", paid.path("DepositAccount").path("interestBase"));
            XbodTransactionTest.amount("0", paid.path("DepositAccount").path("overdraftInterest"));
            XbodTransactionTest.amount("10", paid.path("InterestIncomeAccount").path("balance"));
            bankBalances(demo, "10", "5", "20");
            var input = XbodTransactionTest.reversal(paid.path("CashDepositAgreement"), 20261008, false);
            input.put("DepositReversalCapacity", Map.of("overdraftPermitted", true, "overdraftLimit", 1000));
            var reversed = demo.call("reverseCash", input);
            assertEquals(reversed.toString(), "SUCCEEDED", reversed.path("CashReversalAgreement").path("resultInfo").path("status").asText());
            bankBalances(demo, "0", "0", "0");
            var account = demo.call("findDepositAccount", Map.of("DepositAccount", Map.of("accountNumber", "ACCOUNT-001")));
            XbodTransactionTest.amount("0", account.path("DepositAccount").path("interestBase"));
            XbodTransactionTest.amount("10", account.path("DepositAccount").path("overdraftInterest"));
            XbodTransactionTest.amount("5", account.path("DepositAccount").path("collectionInterest"));
            XbodTransactionTest.amount("20", account.path("DepositAccount").path("overdraftPrincipal"));
            XbodTransactionTest.amount("20", account.path("DepositAccount").path("overdraftExposure"));
        }
    }
    @Test public void laterOrdinaryFailureReversesBankCreditsAndDebtTogether() throws Exception {
        try (var demo = new XbodTestSupport()) {
            XbodTransactionTest.open(demo); seedDebt();
            Object request = demo.request("depositCash", XbodTransactionTest.deposit(100));
            try (var fault = com.servpp.service.runtime.ExampleServiceFaults.activate("cboddeposit.insertDepositPostingLine", "before")) {
                if ("testing".equals(System.getProperty("spp.environment")))
                    assertThrows(IllegalStateException.class, () -> demo.execute("depositCash", request));
                else demo.execute("depositCash", request);
                fault.verifyTriggered();
            }
            bankBalances(demo, "0", "0", "0");
            var account = demo.call("findDepositAccount", Map.of("DepositAccount", Map.of("accountNumber", "ACCOUNT-001")));
            XbodTransactionTest.amount("0", account.path("DepositAccount").path("interestBase"));
            XbodTransactionTest.amount("10", account.path("DepositAccount").path("overdraftInterest"));
            XbodTransactionTest.amount("20", account.path("DepositAccount").path("overdraftPrincipal"));
        }
    }
    @Test public void internalAccountBoundRejectsReversalWithoutRestoringDebt() throws Exception {
        try (var demo = new XbodTestSupport()) {
            XbodTransactionTest.open(demo); seedDebt();
            var paid = demo.call("depositCash", XbodTransactionTest.deposit(100));
            var store = ledger();
            var income = store.findCurrentValues().stream()
                    .filter(v -> v.attributeId().equals("InterestIncomeAccount.balance")).findFirst().orElseThrow();
            // Simulate a subsequent transaction consuming the available income balance.
            store.seedCurrentValue(income.groupId(), income.attributeId(), income.objectId(), BigDecimal.ZERO);
            var input = XbodTransactionTest.reversal(paid.path("CashDepositAgreement"), 20261008, false);
            input.put("DepositReversalCapacity", Map.of("overdraftPermitted", true, "overdraftLimit", 1000));
            var failed = demo.call("reverseCash", input);
            assertEquals(failed.toString(), "FAILED", failed.path("CashReversalAgreement").path("resultInfo").path("status").asText());
            bankBalances(demo, "0", "5", "20");
            var account = demo.call("findDepositAccount", Map.of("DepositAccount", Map.of("accountNumber", "ACCOUNT-001")));
            XbodTransactionTest.amount("65", account.path("DepositAccount").path("interestBase"));
            XbodTransactionTest.amount("0", account.path("DepositAccount").path("overdraftInterest"));
            var original = demo.call("findCashDepositAgreement", Map.of("CashDepositAgreement",
                    Map.of("depositNumber", paid.path("CashDepositAgreement").path("depositNumber").asText())));
            assertFalse(original.path("CashDepositAgreement").path("reversed").asBoolean());
        }
    }
    private void replayRecordedDebtCase(String state, boolean fractional) throws Exception {
        try (var demo = new XbodTestSupport()) {
            XbodTransactionTest.open(demo);
            var debts = fractional
                    ? Map.of("overdraftInterest", "0.005", "collectionInterest", "0.004", "overdraftPrincipal", "0.01", "overdraftExposure", "0.01")
                    : Map.of("overdraftInterest", "30", "collectionInterest", "40", "overdraftPrincipal", "100", "overdraftExposure", "100");
            var store = ledger();
            for (var field : debts.entrySet()) {
                String attribute = "DepositAccount." + field.getKey();
                var value = store.findCurrentValues().stream().filter(v -> v.attributeId().equals(attribute)).findFirst().orElseThrow();
                store.seedCurrentValue(value.groupId(), attribute, value.objectId(), new BigDecimal(field.getValue()));
            }
            // Persist a pre-existing account fixture; this is not a maintenance implementation.
            try (var connection = java.sql.DriverManager.getConnection(System.getProperty("spp.xbod.datasource-url"));
                 var loan = connection.prepareStatement("update DEPOSIT_ACCOUNTS set loanState = ? where accountNumber = ?");
                 var facts = connection.prepareStatement("update ACCOUNT_ELIGIBILITY set detailCount = 10, lastTransactionDate = 20261006 where accountNumber = ?");
                 var dates = connection.prepareStatement("update ACCOUNT_ACCRUAL set lastProductDate = 20261006 where accountNumber = ?")) {
                loan.setString(1, state); loan.setString(2, "ACCOUNT-001"); assertEquals(1, loan.executeUpdate());
                facts.setString(1, "ACCOUNT-001"); assertEquals(1, facts.executeUpdate());
                dates.setString(1, "ACCOUNT-001"); assertEquals(1, dates.executeUpdate());
            }
            var snapshots = new com.servpp.service.repository.JdbcObjectSnapshotStore(
                    new DriverManagerJdbcDataSource("org.h2.Driver", System.getProperty("spp.xbod.datasource-url"), null, null),
                    value -> demo.mapper.valueToTree(value).toString(), "xbod_cash_record");
            var ordinaryFixture = Map.of(
                    "DepositAccount", Map.of("loanState", state),
                    "AccountEligibility", Map.of("detailCount", 10, "lastTransactionDate", 20261006),
                    "DepositAccrualState", Map.of("lastProductDate", 20261006));
            for (var entry : ordinaryFixture.entrySet()) {
                var saved = snapshots.findAll().stream().filter(row -> row.objectType().equals(entry.getKey())
                        || row.objectType().endsWith("." + entry.getKey())).findFirst().orElseThrow();
                var payload = (com.fasterxml.jackson.databind.node.ObjectNode) demo.mapper.readTree(saved.payloadJson());
                for (var field : entry.getValue().entrySet()) payload.set(field.getKey(), demo.mapper.valueToTree(field.getValue()));
                snapshots.save(saved.objectType(), saved.objectId(), payload);
            }
            demo.call("registerFeeSchedule", Map.of("DepositFeeRate", Map.of("customerNumber", "DEBT-CUSTOMER",
                    "tariff", "SAC00001", "fixedAmount", 2, "minimum", 0, "maximum", 100, "discount", 1)));
            var input = XbodTransactionTest.deposit(50);
            input.put("DepositRequest", Map.of("amount", new BigDecimal(fractional ? "0.01" : "50"), "currency", "CNY", "valueDate", 20261007));
            input.put("DepositInterestPolicy", Map.of("normalEnabled", true, "normalUnit", 1, "overdraftUnit", new BigDecimal(fractional ? "0.01" : "1")));
            input.put("DepositFeeParty", Map.of("accountKind", "NormalSavings", "customerNumber", "DEBT-CUSTOMER", "branchNumber", "001", "regionCode", "R001"));
            input.put("DepositFeeContext", Map.of("channel", "Teller", "branchNumber", "002", "regionCode", "R001"));
            input.put("DepositOperatorFacts", Map.of("branch", "002", "area", "R001", "province", "P1", "accountingCentre", true,
                    "operationCode", "1102", "transactionPrefix", "SA01102"));
            var paid = demo.call("depositCash", input);
            assertEquals(paid.toString(), "SUCCEEDED", paid.path("CashDepositAgreement").path("resultInfo").path("status").asText());
            assertEquals(state, paid.path("DepositAccount").path("loanState").asText());
            bankBalances(demo, fractional ? "0.005" : state.equals("NORMAL") ? "30" : "0",
                    fractional ? "0.004" : state.equals("NORMAL") ? "20" : "0",
                    fractional ? "0.001" : state.equals("NORMAL") ? "0" : "50");
            XbodTransactionTest.amount(fractional ? "-0.009" : "-120", paid.path("AccountEligibility").path("accountBalance"));
            var reverseInput = XbodTransactionTest.reversal(paid.path("CashDepositAgreement"), 20261007, false);
            reverseInput.put("DepositReversalCapacity", Map.of("overdraftPermitted", true, "overdraftLimit", 1000));
            var reversed = demo.call("reverseCash", reverseInput);
            assertEquals(reversed.toString(), "SUCCEEDED", reversed.path("CashReversalAgreement").path("resultInfo").path("status").asText());
            bankBalances(demo, "0", "0", "0");
            var account = demo.call("findDepositAccount", Map.of("DepositAccount", Map.of("accountNumber", "ACCOUNT-001")));
            for (var field : debts.entrySet()) XbodTransactionTest.amount(field.getValue(), account.path("DepositAccount").path(field.getKey()));
            var facts = demo.call("findAccountEligibility", Map.of("AccountEligibility", Map.of("accountNumber", "ACCOUNT-001")));
            assertEquals(12, facts.path("AccountEligibility").path("detailCount").asInt());
            assertEquals(20261006, facts.path("AccountEligibility").path("lastTransactionDate").asInt());
            XbodTransactionTest.amount(fractional ? "-0.019" : "-170", facts.path("AccountEligibility").path("accountBalance"));
            var accrual = demo.call("findDepositAccrualState", Map.of("DepositAccrualState", Map.of("accountNumber", "ACCOUNT-001")));
            XbodTransactionTest.amount(fractional ? "0.01" : "100", accrual.path("DepositAccrualState").path("overdraftProduct"));
            XbodTransactionTest.amount(fractional ? "0" : "70", accrual.path("DepositAccrualState").path("overdraftInterestProduct"));
            XbodTransactionTest.amount("2", reversed.path("CashReversalAgreement").path("refundAmount"));
            var postings = demo.call("readCashReversal", Map.of("CashReversalAgreement", Map.of("reversalNumber", reversed.path("CashReversalAgreement").path("reversalNumber").asText())));
            assertEquals(8, postings.path("CashReversalPostingLineArray").size());
        }
    }
    @Test public void recordedOrdinaryDebtRepaymentAndReversal() throws Exception { replayRecordedDebtCase("NORMAL", false); }
    @Test public void recordedBadDebtRepaymentAndReversal() throws Exception { replayRecordedDebtCase("BAD_DEBT", false); }
    @Test public void recordedWrittenOffDebtRepaymentAndReversal() throws Exception { replayRecordedDebtCase("WRITTEN_OFF", false); }
    @Test public void recordedFractionalDebtRepaymentAndReversal() throws Exception { replayRecordedDebtCase("NORMAL", true); }
}
