package com.servpp.xboddemo;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static com.servpp.xboddemo.XbodTransactionTest.amount;

public class XbodAccrualRuleTest {
    @Test public void archivedAccrualCalendarsUnitsAndCollectionCounters() throws Exception {
        // Literal expectations from cboddeposit-99z-accrual-rule-examples.spp.
        Object[][] cases = {
            {650,100,30,40,20261001,20261011,"ACT",true,1,true,"4300","2200","1000","700",10,10,12,13,"4312","1007","709","3305"},
            {100,10,0,0,20260228,20260301,"360360",true,1,false,"300","0","10","0",3,1,3,3,"312","17","9","295"},
            {100,0.019,0.005,0.009,20261001,20261003,"ACT",true,3,false,"198","0","0.02","0.02",2,2,4,5,"210","7.02","9.02","202.98"},
            {100,0,0,0.02,20261001,20261003,"ACT",false,3,false,"0","0","0","0.04",2,2,2,3,"12","7","9.04","5"}
        };
        try (var demo = new XbodTestSupport()) {
            for (var c : cases) {
                var state = Map.of("lastProductDate",c[4],"normalProduct",12,"overdraftProduct",7,
                    "overdraftInterestProduct",9,"actualProduct",5,"overdraftDays",2,"overdraftInterestDays",3);
                var input = Map.of("DepositAccount",Map.of("interestBase",c[0],"overdraftPrincipal",c[1],"overdraftInterest",c[2],"collectionInterest",c[3]),
                    "BusinessDay",Map.of("date",c[5]),"DepositAccrualState",state,
                    "DepositInterestPolicy",Map.of("normalEnabled",c[7],"normalUnit",c[8],"overdraftUnit",0.01),
                    "InterestCalendar",Map.of("convention",c[6]),"AgreementTerm",Map.of("effectiveDate",20261003,"cancelDate",20261007),
                    "AgreementPolicy",Map.of("enabled",c[9],"mode","2","currency","CNY"),
                    "AgreementBandArray",List.of(Map.of("slot",5,"threshold",500),Map.of("slot",4,"threshold",400),Map.of("slot",3,"threshold",300),Map.of("slot",2,"threshold",200),Map.of("slot",1,"threshold",100)));
                var result = demo.call("calculateDepositAccrual",input);
                var plan = result.path("DepositAccrualPlan");
                String[] increments = {"normalIncrement","agreementIncrement","overdraftIncrement","overdraftInterestIncrement"};
                for (int i=0;i<increments.length;i++) amount((String)c[10+i],plan.path(increments[i]));
                assertEquals(c[14],plan.path("normalDays").asInt());
                assertEquals(c[15],plan.path("overdraftDays").asInt());
                var accumulated = demo.call("accumulateAccrual",Map.of("DepositAccrualState",state,"DepositAccrualPlan",plan)).path("DepositAccrualState");
                assertEquals(c[16],accumulated.path("overdraftDays").asInt());
                assertEquals(c[17],accumulated.path("overdraftInterestDays").asInt());
                assertEquals(c[5],accumulated.path("lastProductDate").asInt());
                String[] totals = {"normalProduct","overdraftProduct","overdraftInterestProduct","actualProduct"};
                for (int i=0;i<totals.length;i++) amount((String)c[18+i],accumulated.path(totals[i]));
            }
        }
    }
}
