package com.servpp.xboddemo;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class XbodConcurrencyTest {
    @Test(timeout=60000) public void concurrentSameAccountRejectsStaleFactsAndAllowsFreshRetry() throws Exception {
        try (var demo = new XbodTestSupport()) {
            XbodTransactionTest.open(demo);
            Object firstRequest = demo.request("depositCash", XbodTransactionTest.deposit(100));
            Object secondRequest = demo.request("depositCash", XbodTransactionTest.deposit(200));
            var executor = Executors.newFixedThreadPool(2);
            com.fasterxml.jackson.databind.JsonNode first, second;
            try (var barrier = com.servpp.service.runtime.ExampleServiceBarrier.activate("cboddeposit.prepareDepositAgreement", 2)) {
                var one = executor.submit(() -> demo.execute("depositCash", firstRequest));
                var two = executor.submit(() -> demo.execute("depositCash", secondRequest));
                first = one.get(40, TimeUnit.SECONDS); second = two.get(40, TimeUnit.SECONDS);
                barrier.verifyReached();
            } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
            boolean firstWon = "SUCCEEDED".equals(first.path("CashDepositAgreement").path("resultInfo").path("status").asText());
            var winner = firstWon ? first : second;
            var loser = firstWon ? second : first;
            assertEquals(winner.toString(), "SUCCEEDED", winner.path("CashDepositAgreement").path("resultInfo").path("status").asText());
            var failure = loser.path("CashDepositAgreement").path("resultInfo");
            assertEquals(loser.toString(), "FAILED", failure.path("status").asText());
            assertTrue(failure.toString(), failure.path("handled").asBoolean());
            String committed = firstWon ? "100" : "200";
            XbodTransactionTest.amount(committed, demo.call("findDepositAccount", Map.of("DepositAccount",
                    Map.of("accountNumber", "ACCOUNT-001"))).path("DepositAccount").path("interestBase"));
            XbodTransactionTest.amount("-" + committed, demo.call("findCashSettlementAccount", Map.of("CashSettlementAccount",
                    Map.of("accountNumber", "CASH-001"))).path("CashSettlementAccount").path("balance"));
            var facts = demo.call("findAccountEligibility", Map.of("AccountEligibility", Map.of("accountNumber", "ACCOUNT-001")));
            assertEquals(1, facts.path("AccountEligibility").path("detailCount").asInt());
            XbodTransactionTest.amount(committed, facts.path("AccountEligibility").path("accountBalance"));
            var retried = demo.call("depositCash", XbodTransactionTest.deposit(firstWon ? 200 : 100));
            assertEquals(retried.toString(), "SUCCEEDED", retried.path("CashDepositAgreement").path("resultInfo").path("status").asText());
            XbodTransactionTest.amount("300", retried.path("DepositAccount").path("interestBase"));
            assertEquals(2, retried.path("AccountEligibility").path("detailCount").asInt());
        }
    }
    @Test(timeout=60000) public void concurrentReversalsCannotRemoveOriginalFundsTwice() throws Exception {
        try (var demo = new XbodTestSupport()) {
            XbodTransactionTest.open(demo); var paid = demo.call("depositCash",XbodTransactionTest.deposit(100));
            var input = XbodTransactionTest.reversal(paid.path("CashDepositAgreement"),20261007,false);
            Object firstRequest = demo.request("reverseCash",input); Object secondRequest = demo.request("reverseCash",input);
            var executor = Executors.newFixedThreadPool(2);
            try (var barrier = com.servpp.service.runtime.ExampleServiceBarrier.activate("cboddeposit.prepareCashReversalPostings",2)) {
                var firstFuture = executor.submit(() -> demo.execute("reverseCash",firstRequest));
                var secondFuture = executor.submit(() -> demo.execute("reverseCash",secondRequest));
                var first = firstFuture.get(40,TimeUnit.SECONDS); var second = secondFuture.get(40,TimeUnit.SECONDS);
                barrier.verifyReached();
                var firstInfo = first.path("CashReversalAgreement").path("resultInfo");
                var secondInfo = second.path("CashReversalAgreement").path("resultInfo");
                assertEquals(1,(firstInfo.path("status").asText().equals("SUCCEEDED")?1:0)+(secondInfo.path("status").asText().equals("SUCCEEDED")?1:0));
                var failed = firstInfo.path("status").asText().equals("FAILED") ? firstInfo : secondInfo;
                assertEquals("FAILED",failed.path("status").asText());
                assertEquals("ROLLED_BACK",failed.path("failure").path("recoveryStatus").asText());
                assertTrue(failed.path("handled").asBoolean());
                var winner = firstInfo.path("status").asText().equals("SUCCEEDED") ? first : second;
                var saved = demo.call("readCashReversal",Map.of("CashReversalAgreement",Map.of("reversalNumber",winner.path("CashReversalAgreement").path("reversalNumber").asText())));
                assertEquals(8,saved.path("CashReversalPostingLineArray").size());
            } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS)); }
            var account = demo.call("findDepositAccount",Map.of("DepositAccount",Map.of("accountNumber","ACCOUNT-001")));
            var cash = demo.call("findCashSettlementAccount",Map.of("CashSettlementAccount",Map.of("accountNumber","CASH-001")));
            XbodTransactionTest.amount("0",account.path("DepositAccount").path("interestBase"));
            XbodTransactionTest.amount("0",cash.path("CashSettlementAccount").path("balance"));
        }
    }
    @Test(timeout=60000) public void concurrentIndependentAccountsKeepFundsAndRecordsSeparate() throws Exception {
        // Customer accounts differ, but both postings legitimately lock shared bank income accounts.
        try (var demo = new XbodTestSupport(10000)) {
            XbodTransactionTest.open(demo); XbodTransactionTest.openSecondAccount(demo);
            var second = XbodTransactionTest.deposit(200);
            second.put("DepositAccount",Map.of("accountNumber","ACCOUNT-002"));
            second.put("AccountEligibility",Map.of("accountNumber","ACCOUNT-002"));
            second.put("DepositAccrualState",Map.of("accountNumber","ACCOUNT-002"));
            second.put("CashSettlementAccount",Map.of("accountNumber","CASH-002"));
            Object firstRequest = demo.request("depositCash",XbodTransactionTest.deposit(100));
            Object secondRequest = demo.request("depositCash",second);
            var executor = Executors.newFixedThreadPool(2);
            try (var barrier = com.servpp.service.runtime.ExampleServiceBarrier.activate("cboddeposit.prepareDepositAgreement",2)) {
                var firstFuture = executor.submit(() -> demo.execute("depositCash",firstRequest));
                var secondFuture = executor.submit(() -> demo.execute("depositCash",secondRequest));
                var first = firstFuture.get(40,TimeUnit.SECONDS); var other = secondFuture.get(40,TimeUnit.SECONDS);
                barrier.verifyReached();
                assertEquals(first.toString(),"SUCCEEDED",first.path("CashDepositAgreement").path("resultInfo").path("status").asText());
                assertEquals(other.toString(),"SUCCEEDED",other.path("CashDepositAgreement").path("resultInfo").path("status").asText());
                assertNotEquals(first.path("CashDepositAgreement").path("depositNumber"),other.path("CashDepositAgreement").path("depositNumber"));
                assertEquals("ACCOUNT-001",first.path("CashDepositAgreement").path("account").path("accountNumber").asText());
                assertEquals("ACCOUNT-002",other.path("CashDepositAgreement").path("account").path("accountNumber").asText());
            } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS)); }
            var one = demo.call("findDepositAccount",Map.of("DepositAccount",Map.of("accountNumber","ACCOUNT-001")));
            var two = demo.call("findDepositAccount",Map.of("DepositAccount",Map.of("accountNumber","ACCOUNT-002")));
            XbodTransactionTest.amount("100",one.path("DepositAccount").path("interestBase"));
            XbodTransactionTest.amount("200",two.path("DepositAccount").path("interestBase"));
            var cashOne = demo.call("findCashSettlementAccount",Map.of("CashSettlementAccount",Map.of("accountNumber","CASH-001")));
            var cashTwo = demo.call("findCashSettlementAccount",Map.of("CashSettlementAccount",Map.of("accountNumber","CASH-002")));
            XbodTransactionTest.amount("-100",cashOne.path("CashSettlementAccount").path("balance"));
            XbodTransactionTest.amount("-200",cashTwo.path("CashSettlementAccount").path("balance"));
            for (String account : new String[]{"ACCOUNT-001","ACCOUNT-002"}) {
                var facts = demo.call("findAccountEligibility",Map.of("AccountEligibility",Map.of("accountNumber",account)));
                assertEquals(1,facts.path("AccountEligibility").path("detailCount").asInt());
            }
        }
    }
}
