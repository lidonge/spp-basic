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
            secondInput.put("businessDay",Map.of("date",20261008));
            secondInput.put("depositRequest",Map.of("amount",50,"currency","CNY","valueDate",20261008));
            var second = demo.call("depositCash",secondInput);
            assertEquals(second.toString(),"SUCCEEDED",second.path("cashDepositAgreement").path("resultInfo").path("status").asText());
            assertEquals(1,second.path("depositAccrualPlan").path("normalDays").asInt());
            XbodTransactionTest.amount("100",second.path("depositAccrualPlan").path("normalIncrement"));
            var stored = demo.readAccounts();
            XbodTransactionTest.amount("100",stored.path("depositAccrualState").path("normalProduct"));
            assertEquals(20261008,stored.path("depositAccrualState").path("lastProductDate").asInt());
            var reversed = demo.call("reverseCash",XbodTransactionTest.reversal(first.path("cashDepositAgreement"),20261008,false));
            assertEquals(reversed.toString(),"SUCCEEDED",reversed.path("cashReversalAgreement").path("resultInfo").path("status").asText());
            XbodTransactionTest.amount("50",reversed.path("depositAccount").path("interestBase"));
            XbodTransactionTest.amount("100",reversed.path("depositAccrualState").path("normalProduct"));
        }
    }
    @Test public void agreementWindowAndFiveBandsAccrueThroughStoredDepositScenario() throws Exception {
        try (var demo = new XbodTestSupport()) {
            XbodTransactionTest.open(demo); demo.call("depositCash",XbodTransactionTest.deposit(650));
            var input = XbodTransactionTest.deposit(50);
            input.put("businessDay",Map.of("date",20261017));
            input.put("depositRequest",Map.of("amount",50,"currency","CNY","valueDate",20261017));
            input.put("agreementTerm",Map.of("effectiveDate",20261013,"cancelDate",0));
            input.put("agreementPolicy",Map.of("enabled",true,"mode","2","currency","CNY"));
            input.put("agreementBands",List.of(Map.of("slot",5,"threshold",500),Map.of("slot",4,"threshold",400),Map.of("slot",3,"threshold",300),Map.of("slot",2,"threshold",200),Map.of("slot",1,"threshold",100)));
            var paid = demo.call("depositCash",input);
            assertEquals(paid.toString(),"SUCCEEDED",paid.path("cashDepositAgreement").path("resultInfo").path("status").asText());
            assertEquals(10,paid.path("depositAccrualPlan").path("normalDays").asInt());
            XbodTransactionTest.amount("4300",paid.path("depositAccrualPlan").path("normalIncrement"));
            XbodTransactionTest.amount("2200",paid.path("depositAccrualPlan").path("agreementIncrement"));
            var stored = demo.readAccounts();
            XbodTransactionTest.amount("4300",stored.path("depositAccrualState").path("normalProduct"));
            XbodTransactionTest.amount("2200",stored.path("depositAccrualState").path("agreementProduct"));
            XbodTransactionTest.amount("700",paid.path("depositAccount").path("interestBase"));
        }
    }
}
