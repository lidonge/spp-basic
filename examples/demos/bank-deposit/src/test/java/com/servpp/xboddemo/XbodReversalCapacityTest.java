package com.servpp.xboddemo;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class XbodReversalCapacityTest {
    @Test public void preservesCapacityAndNegativeBalanceRejectionOrder() throws Exception {
        try (var demo = new XbodTestSupport()) {
            Object[][] cases = {
                    {Map.of("interestBase",5),Map.of("amount",10,"interestBaseDelta",10),Map.of("overdraftPermitted",false),"ES020"},
                    {Map.of("interestBase",5),Map.of("amount",10,"interestBaseDelta",10),Map.of("overdraftPermitted",true,"overdraftLimit",10),"EN249"},
                    {Map.of("interestBase",100,"overdraftPrincipal",50),Map.of("amount",50,"interestBaseDelta",0,"principalPaid",50),Map.of("overdraftPermitted",true,"overdraftLimit",100),"EN177"}};
            for (var row : cases) {
                var request = demo.request("checkCashReversalCapacity",Map.of("DepositAccount",row[0],"CashDepositAgreement",row[1],"DepositReversalCapacity",row[2]));
                var before = demo.mapper.valueToTree(request);
                assertEquals(row[3],assertThrows(IllegalStateException.class,() -> demo.execute("checkCashReversalCapacity",request)).getMessage());
                assertEquals(before,demo.mapper.valueToTree(request));
            }
        }
    }
    @Test public void rejectsForgedOriginalMovementBeforeCalculation() throws Exception {
        try (var demo = new XbodTestSupport()) {
            var request = demo.request("checkOriginalCashMovement",Map.of("CashDepositAgreement",Map.of("amount",11,"interestBaseDelta",10)));
            var before = demo.mapper.valueToTree(request);
            assertEquals("ORIGINAL_DEPOSIT_MOVEMENT_NOT_BALANCED",assertThrows(IllegalStateException.class,
                    () -> demo.execute("checkOriginalCashMovement",request)).getMessage());
            assertEquals(before,demo.mapper.valueToTree(request));
        }
    }
}
