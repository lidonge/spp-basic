package com.servpp.xboddemo;

import org.junit.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.Assert.*;

/** Literal expectations from the archived XBOD cash-policy example. */
public class XbodCashPolicyTest {
    @Test public void preservesReversalDatesCountsAndProductsWithoutMutatingInputs() throws Exception {
        // count, daily cash, day, mode2, backdated product; expected count, date, daily cash, normal, actual, institution flag
        int[][] cases = {
                {3,100,20261008,0,0,5,20261008,0,2200,2100,0},
                {1,900,20261008,0,50,3,20261006,300,2150,2050,0},
                {1,600,20261007,1,0,3,20261007,0,2200,2100,1},
                {2,600,20261007,0,0,4,20261007,0,2200,2100,0}};
        try (var demo = new XbodTestSupport()) {
            for (var row : cases) {
                var request = demo.request("calculateXbodCashReversalMetadata", Map.of(
                        "CashDepositAgreement", Map.of("amount",600,"transactionDate",20261007,
                                "previousTransactionDate",20261006,"settledDetailCount",1,"addedBackdatedProduct",row[4]),
                        "CashReversalAgreement", Map.of("newDetailItems",2,"businessDate",row[2],"overnightMode2",row[3]==1),
                        "AccountEligibility", Map.of("detailCount",row[0],"cashDepositedToday",row[1]),
                        "DepositAccrualState", Map.of("normalProduct",2200,"overdraftProduct",100)));
                var before = demo.mapper.valueToTree(request);
                var result = demo.execute("calculateXbodCashReversalMetadata",request);
                for (String participant : new String[]{"CashDepositAgreement","CashReversalAgreement","AccountEligibility","DepositAccrualState"})
                    assertEquals(participant,before.path(participant),result.path(participant));
                var actual = result.path("CashReversalMetadataPlan");
                assertEquals(row[5],actual.path("detailCount").asInt());
                assertEquals(row[6],actual.path("lastTransactionDate").asInt());
                assertEquals(row[7],actual.path("cashDepositedToday").asInt());
                assertEquals(row[8],actual.path("normalProduct").asInt());
                assertEquals(row[9],actual.path("actualProduct").asInt());
                assertEquals(row[10]==1,actual.path("reverseInstitutionLimit").asBoolean());
            }
        }
    }
    @Test public void rejectsInsufficientProductBeforeCalculation() throws Exception {
        try (var demo = new XbodTestSupport()) {
            var request = demo.request("checkCashReversalFacts",Map.of(
                    "CashDepositAgreement",Map.of("addedBackdatedProduct",new BigDecimal("2200.001")),
                    "CashReversalAgreement",Map.of("newDetailItems",2),
                    "DepositAccrualState",Map.of("normalProduct",2200,"overdraftProduct",100)));
            var before = demo.mapper.valueToTree(request);
            assertEquals("EN097",assertThrows(IllegalStateException.class,
                    () -> demo.execute("checkCashReversalFacts",request)).getMessage());
            assertEquals(before,demo.mapper.valueToTree(request));
        }
    }
    @Test public void preservesOriginalFeeIncludingFractionalAmount() throws Exception {
        try (var demo = new XbodTestSupport()) {
            for (String fee : new String[]{"0","2","1.005"}) {
                var result = demo.call("prepareCashReversalAgreement",Map.of("CashDepositAgreement",Map.of("fee",new BigDecimal(fee))));
                XbodTransactionTest.amount(fee,result.path("CashReversalAgreement").path("refundAmount"));
                XbodTransactionTest.amount(fee,result.path("CashDepositAgreement").path("fee"));
            }
        }
    }
}
