package com.servpp.xboddemo;

import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import com.servpp.service.transaction.JdbcQuantumJournalStore;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Claim origination, repayment and reversal execute generated SPP against the JDBC ledger. */
public class XbodRepaymentTest {
    private JdbcQuantumJournalStore ledger() {
        String url = System.getProperty("testing".equals(System.getProperty("spp.environment"))
                ? "spp.xbod.testing.ledger-url" : "spp.xbod.datasource-url");
        return new JdbcQuantumJournalStore(new DriverManagerJdbcDataSource("org.h2.Driver", url, null, null),
                "xbod_quantum_journal", "xbod_quantum_value", "text");
    }
    @Test public void exposureFollowsPartialRepaymentAndReversal() throws Exception {
        try (var demo = new XbodTestSupport()) {
            XbodTransactionTest.open(demo);
            bookClaims(demo, "0", "0", "20");
            var paid = demo.call("depositCash",XbodTransactionTest.deposit(10));
            assertEquals("SUCCEEDED",paid.path("CashDepositAgreement").path("resultInfo").path("status").asText());
            XbodTransactionTest.amount("-10",paid.path("CashDepositAgreement").path("exposureDelta"));
            XbodTransactionTest.amount("10",paid.path("CashDepositAgreement").path("principalPaid"));
            XbodTransactionTest.amount("10",paid.path("DepositAccount").path("overdraftExposure"));
            var input = XbodTransactionTest.reversal(paid.path("CashDepositAgreement"),20261007,false);
            input.put("DepositReversalCapacity",Map.of("overdraftPermitted",true,"overdraftLimit",20));
            var reversed = demo.call("reverseCash",input);
            assertEquals("SUCCEEDED",reversed.path("CashReversalAgreement").path("resultInfo").path("status").asText());
            var account = demo.readAccounts().path("DepositAccount");
            XbodTransactionTest.amount("-20",account.path("overdraftPrincipal"));
            XbodTransactionTest.amount("20",account.path("overdraftExposure"));
            XbodTransactionTest.amount("0",demo.readAccounts().path("PrincipalRecoveryAccount").path("balance"));
        }
    }
    @Test public void openingCannotCreateAnUnbookedClaim() throws Exception {
        try (var demo = new XbodTestSupport()) {
            var input = Map.of("BankAccount", Map.of("accountNumber", "ACCOUNT-001"),
                    "DepositAccount", Map.of("overdraftInterest", -10));
            var failure = assertThrows(IllegalStateException.class, () -> demo.call("openDepositAccount", input));
            assertEquals("E1155", failure.getMessage());
        }
    }

    private void bookClaims(XbodTestSupport demo, String interest, String collection, String principal) throws Exception {
        demo.call("bookOverdraftClaims", Map.of(
                "OverdraftCharge", Map.of("interest", interest, "collection", collection, "principal", principal),
                "DepositAccount", Map.of("accountNumber", "ACCOUNT-001"),
                "CashSettlementAccount", Map.of("accountNumber", "CASH-001"),
                "InterestIncomeAccount", Map.of("accountNumber", "BANK-INTEREST"),
                "CollectionInterestIncomeAccount", Map.of("accountNumber", "BANK-COLLECTION")));
        assertConserved();
    }

    private void assertConserved() {
        var totals = new java.util.HashMap<String, BigDecimal>();
        for (var position : ledger().findCurrentValues()) {
            totals.merge(position.groupId(), position.currentValue(), BigDecimal::add);
        }
        assertFalse(totals.isEmpty());
        totals.forEach((group, total) -> assertEquals(group, 0, total.signum()));
    }

    private void bankBalances(XbodTestSupport demo, String interest, String collection, String principal) throws Exception {
        var account = demo.readAccounts();
        XbodTransactionTest.amount(interest, account.path("InterestIncomeAccount").path("balance"));
        XbodTransactionTest.amount(collection, account.path("CollectionInterestIncomeAccount").path("balance"));
        XbodTransactionTest.amount(principal, account.path("PrincipalRecoveryAccount").path("balance"));
    }
    @Test public void repaymentClearsClaimsAndReversalRestoresDebt() throws Exception {
        try (var demo = new XbodTestSupport()) {
            XbodTransactionTest.open(demo); bookClaims(demo, "10", "5", "20");
            var paid = demo.call("depositCash", XbodTransactionTest.deposit(100));
            assertEquals(paid.toString(), "SUCCEEDED", paid.path("CashDepositAgreement").path("resultInfo").path("status").asText());
            XbodTransactionTest.amount("65", paid.path("DepositAccount").path("interestBase"));
            XbodTransactionTest.amount("0", paid.path("DepositAccount").path("overdraftInterest"));
            XbodTransactionTest.amount("10", paid.path("InterestIncomeAccount").path("balance"));
            bankBalances(demo, "10", "5", "0");
            assertConserved();
            var input = XbodTransactionTest.reversal(paid.path("CashDepositAgreement"), 20261008, false);
            input.put("DepositReversalCapacity", Map.of("overdraftPermitted", true, "overdraftLimit", 1000));
            var reversed = demo.call("reverseCash", input);
            assertEquals(reversed.toString(), "SUCCEEDED", reversed.path("CashReversalAgreement").path("resultInfo").path("status").asText());
            bankBalances(demo, "10", "5", "0");
            assertConserved();
            var account = demo.readAccounts();
            XbodTransactionTest.amount("0", account.path("DepositAccount").path("interestBase"));
            XbodTransactionTest.amount("-10", account.path("DepositAccount").path("overdraftInterest"));
            XbodTransactionTest.amount("-5", account.path("DepositAccount").path("collectionInterest"));
            XbodTransactionTest.amount("-20", account.path("DepositAccount").path("overdraftPrincipal"));
            XbodTransactionTest.amount("20", account.path("DepositAccount").path("overdraftExposure"));
        }
    }
    @Test public void laterOrdinaryFailureRestoresClaimsAndCashTogether() throws Exception {
        try (var demo = new XbodTestSupport()) {
            XbodTransactionTest.open(demo); bookClaims(demo, "10", "5", "20");
            Object request = demo.request("depositCash", XbodTransactionTest.deposit(100));
            try (var fault = com.servpp.service.runtime.ExampleServiceFaults.activate("cboddeposit.insertDepositPostingLine", "before")) {
                if ("testing".equals(System.getProperty("spp.environment")))
                    assertThrows(IllegalStateException.class, () -> demo.execute("depositCash", request));
                else demo.execute("depositCash", request);
                fault.verifyTriggered();
            }
            bankBalances(demo, "10", "5", "0");
            assertConserved();
            var account = demo.readAccounts();
            XbodTransactionTest.amount("0", account.path("DepositAccount").path("interestBase"));
            XbodTransactionTest.amount("-10", account.path("DepositAccount").path("overdraftInterest"));
            XbodTransactionTest.amount("-20", account.path("DepositAccount").path("overdraftPrincipal"));
        }
    }
    @Test public void reversalRestoresDebtAfterIncomeIsSwept() throws Exception {
        try (var demo = new XbodTestSupport()) {
            XbodTransactionTest.open(demo); bookClaims(demo, "10", "5", "20");
            var paid = demo.call("depositCash", XbodTransactionTest.deposit(100));
            var store = ledger();
            var income = store.findCurrentValues().stream()
                    .filter(v -> v.attributeId().equals("InterestIncomeAccount.balance")).findFirst().orElseThrow();
            // Simulate a subsequent transaction consuming the available income balance.
            store.seedCurrentValue(income.groupId(), income.attributeId(), income.objectId(), BigDecimal.ZERO);
            var input = XbodTransactionTest.reversal(paid.path("CashDepositAgreement"), 20261008, false);
            input.put("DepositReversalCapacity", Map.of("overdraftPermitted", true, "overdraftLimit", 1000));
            var reversed = demo.call("reverseCash", input);
            assertEquals("SUCCEEDED", reversed.path("CashReversalAgreement").path("resultInfo").path("status").asText());
            bankBalances(demo, "0", "5", "0");
            var account = demo.readAccounts();
            XbodTransactionTest.amount("0", account.path("DepositAccount").path("interestBase"));
            XbodTransactionTest.amount("-10", account.path("DepositAccount").path("overdraftInterest"));
        }
    }
    private void replayRecordedDebtCase(String state, boolean fractional) throws Exception {
        try (var demo = new XbodTestSupport()) {
            XbodTransactionTest.open(demo);
            String interest = fractional ? "0.005" : "30";
            String collection = fractional ? "0.004" : "40";
            String principal = fractional ? "0.01" : "100";
            bookClaims(demo, interest, collection, principal);
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
            bankBalances(demo, interest, collection, "0");
            assertConserved();
            var agreement = paid.path("CashDepositAgreement");
            XbodTransactionTest.amount(fractional ? "0.005" : state.equals("NORMAL") ? "30" : "0", agreement.path("interestPaid"));
            XbodTransactionTest.amount(fractional ? "0.004" : state.equals("NORMAL") ? "20" : "0", agreement.path("collectionPaid"));
            XbodTransactionTest.amount(fractional ? "0.001" : state.equals("NORMAL") ? "0" : "50", agreement.path("principalPaid"));
            XbodTransactionTest.amount(fractional ? "-0.009" : "-120", paid.path("AccountEligibility").path("accountBalance"));
            var reverseInput = XbodTransactionTest.reversal(paid.path("CashDepositAgreement"), 20261007, false);
            reverseInput.put("DepositReversalCapacity", Map.of("overdraftPermitted", true, "overdraftLimit", 1000));
            var reversed = demo.call("reverseCash", reverseInput);
            assertEquals(reversed.toString(), "SUCCEEDED", reversed.path("CashReversalAgreement").path("resultInfo").path("status").asText());
            bankBalances(demo, interest, collection, "0");
            assertConserved();
            var account = demo.readAccounts();
            XbodTransactionTest.amount("-" + interest, account.path("DepositAccount").path("overdraftInterest"));
            XbodTransactionTest.amount("-" + collection, account.path("DepositAccount").path("collectionInterest"));
            XbodTransactionTest.amount("-" + principal, account.path("DepositAccount").path("overdraftPrincipal"));
            XbodTransactionTest.amount(principal, account.path("DepositAccount").path("overdraftExposure"));
            var facts = demo.readAccounts();
            assertEquals(12, facts.path("AccountEligibility").path("detailCount").asInt());
            assertEquals(20261006, facts.path("AccountEligibility").path("lastTransactionDate").asInt());
            XbodTransactionTest.amount(fractional ? "-0.019" : "-170", facts.path("AccountEligibility").path("accountBalance"));
            var accrual = demo.readAccounts();
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
