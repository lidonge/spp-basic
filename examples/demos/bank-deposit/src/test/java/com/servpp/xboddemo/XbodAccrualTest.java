package com.servpp.xboddemo;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class XbodAccrualTest {
    @Test public void nextDayDepositAccruesPriorBalanceAndPersistsIt() throws Exception {
        try (var demo = new XbodTestSupport()) {
            XbodTransactionTest.open(demo);
            var first = demo.call("depositCash",XbodTransactionTest.deposit(100));
            var secondInput = XbodTransactionTest.deposit(50);
            secondInput.put("BusinessDay",Map.of("date",20261008));
            secondInput.put("DepositRequest",Map.of("amount",50,"currency","CNY","valueDate",20261008));
            var second = demo.call("depositCash",secondInput);
            assertEquals(second.toString(),"SUCCEEDED",second.path("CashDepositAgreement").path("resultInfo").path("status").asText());
            assertEquals(1,second.path("DepositAccrualPlan").path("normalDays").asInt());
            XbodTransactionTest.amount("100",second.path("DepositAccrualPlan").path("normalIncrement"));
            var stored = demo.call("findDepositAccrualState",Map.of("DepositAccrualState",Map.of("accountNumber","ACCOUNT-001")));
            XbodTransactionTest.amount("100",stored.path("DepositAccrualState").path("normalProduct"));
            assertEquals(20261008,stored.path("DepositAccrualState").path("lastProductDate").asInt());
            var reversed = demo.call("reverseCash",XbodTransactionTest.reversal(first.path("CashDepositAgreement"),20261008,false));
            assertEquals(reversed.toString(),"SUCCEEDED",reversed.path("CashReversalAgreement").path("resultInfo").path("status").asText());
            XbodTransactionTest.amount("50",reversed.path("DepositAccount").path("interestBase"));
            XbodTransactionTest.amount("100",reversed.path("DepositAccrualState").path("normalProduct"));
        }
    }
    @Test public void agreementWindowAndFiveBandsAccrueThroughStoredDepositScenario() throws Exception {
        try (var demo = new XbodTestSupport()) {
            XbodTransactionTest.open(demo); demo.call("depositCash",XbodTransactionTest.deposit(650));
            var input = XbodTransactionTest.deposit(50);
            input.put("BusinessDay",Map.of("date",20261017));
            input.put("DepositRequest",Map.of("amount",50,"currency","CNY","valueDate",20261017));
            input.put("AgreementTerm",Map.of("effectiveDate",20261013,"cancelDate",0));
            input.put("AgreementPolicy",Map.of("enabled",true,"mode","2","currency","CNY"));
            input.put("AgreementBandArray",List.of(Map.of("slot",5,"threshold",500),Map.of("slot",4,"threshold",400),Map.of("slot",3,"threshold",300),Map.of("slot",2,"threshold",200),Map.of("slot",1,"threshold",100)));
            var paid = demo.call("depositCash",input);
            assertEquals(paid.toString(),"SUCCEEDED",paid.path("CashDepositAgreement").path("resultInfo").path("status").asText());
            assertEquals(10,paid.path("DepositAccrualPlan").path("normalDays").asInt());
            XbodTransactionTest.amount("4300",paid.path("DepositAccrualPlan").path("normalIncrement"));
            XbodTransactionTest.amount("2200",paid.path("DepositAccrualPlan").path("agreementIncrement"));
            var stored = demo.call("findDepositAccrualState",Map.of("DepositAccrualState",Map.of("accountNumber","ACCOUNT-001")));
            XbodTransactionTest.amount("4300",stored.path("DepositAccrualState").path("normalProduct"));
            XbodTransactionTest.amount("2200",stored.path("DepositAccrualState").path("agreementProduct"));
            XbodTransactionTest.amount("700",paid.path("DepositAccount").path("interestBase"));
        }
    }
}
