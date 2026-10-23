package com.servpp.xboddemo;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static com.servpp.xboddemo.XbodTransactionTest.amount;

public class XbodDatedAgreementTest {
    private List<Map<String,Integer>> bands() {
        return List.of(Map.of("slot",5,"threshold",500),Map.of("slot",4,"threshold",400),
                Map.of("slot",3,"threshold",300),Map.of("slot",2,"threshold",200),Map.of("slot",1,"threshold",100));
    }
    @Test public void preservesAllSevenArchivedDateWindowsAndProducts() throws Exception {
        // effective, cancelled, days, normal product, agreement product, clipped start, clipped end
        int[][] cases = {
                {20261003,20261007,4,4300,2200,20261003,20261007},
                {20260901,0,10,1000,5500,20261001,20261011},
                {20261007,20261101,4,4300,2200,20261007,20261011},
                {0,0,0,6500,0,20261001,20261001},
                {20261101,0,0,6500,0,20261001,20261001},
                {20260901,20260930,0,6500,0,20261001,20261001},
                {20261007,20261007,0,6500,0,20261001,20261001}};
        try (var demo = new XbodTestSupport()) {
            for (var row : cases) {
                var term = Map.of("effectiveDate",row[0],"cancelDate",row[1]);
                var policy = Map.of("enabled",true,"mode","2","currency","CNY");
                var window = demo.call("prepareAgreementWindow",Map.of("normalStart",Map.of("encoded",20261001),
                        "normalEnd",Map.of("encoded",20261011),"AgreementTerm",term,"AgreementPolicy",policy,
                        "normal",Map.of("amount",650,"unit",1,"days",10)));
                assertEquals(row[5],window.path("clippedStart").path("encoded").asInt());
                assertEquals(row[6],window.path("clippedEnd").path("encoded").asInt());
                var period = demo.call("previewInterestProduct",Map.of("start",Map.of("encoded",row[5]),
                        "end",Map.of("encoded",row[6]),"InterestCalendar",Map.of("convention","ACT"),
                        "InterestPeriod",Map.of("amount",650,"unit",1)));
                assertEquals(row[2],period.path("InterestPeriod").path("days").asInt());
                var result = demo.call("calculateDepositAccrual",Map.of("DepositAccount",Map.of("interestBase",650),
                        "BusinessDay",Map.of("date",20261011),"DepositAccrualState",Map.of("lastProductDate",20261001),
                        "DepositInterestPolicy",Map.of("normalEnabled",true,"normalUnit",1,"overdraftUnit",1),
                        "InterestCalendar",Map.of("convention","ACT"),"AgreementTerm",term,"AgreementPolicy",policy,
                        "AgreementBandArray",bands()));
                assertEquals(10,result.path("DepositAccrualPlan").path("normalDays").asInt());
                amount(Integer.toString(row[3]),result.path("DepositAccrualPlan").path("normalIncrement"));
                amount(Integer.toString(row[4]),result.path("DepositAccrualPlan").path("agreementIncrement"));
            }
        }
    }
    @Test public void invalidEffectiveDateIsRejectedByExplicitCheck() throws Exception {
        try (var demo = new XbodTestSupport()) {
            var request = demo.request("checkAccrualDates",Map.of("DepositAccrualState",Map.of("lastProductDate",20260201),
                    "BusinessDay",Map.of("date",20260310),"InterestCalendar",Map.of("convention","ACT"),
                    "AgreementTerm",Map.of("effectiveDate",20260230),"AgreementBandArray",bands()));
            var before = demo.mapper.valueToTree(request);
            assertEquals("EE374",assertThrows(IllegalStateException.class,() -> demo.execute("checkAccrualDates",request)).getMessage());
            assertEquals(before,demo.mapper.valueToTree(request));
        }
    }
}
