package com.servpp.xboddemo;

import org.junit.Test;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static com.servpp.xboddemo.XbodTransactionTest.*;

public class XbodStaleReversalTest {
    @Test(timeout=60000) public void interveningDepositInvalidatesPreparedReversalWithoutOverwritingIt() throws Exception { verify(false); }
    @Test(timeout=60000) public void changedOriginalReceiptInvalidatesPreparedReversal() throws Exception { verify(true); }
    private void verify(boolean changedReceipt) throws Exception {
        try (var demo=new XbodTestSupport()) {
            open(demo);var paid=demo.call("depositCash",deposit(100));
            Object request=demo.request("reverseCash",reversal(paid.path("CashDepositAgreement"),20261007,false));
            var prepared=new CountDownLatch(1);var resume=new CountDownLatch(1);var armed=new AtomicBoolean(true);
            var services=com.servpp.service.server.DefaultServiceContainer.getInstance();
            var originalService=services.getService("cboddeposit", "prepareCashReversalPostings");
            var gated=(com.servpp.service.Service)java.lang.reflect.Proxy.newProxyInstance(
                    originalService.getClass().getClassLoader(), originalService.getClass().getInterfaces(), (proxy, method, arguments) -> {
                    if (method.getName().equals("prepareCashReversalPostings") && armed.compareAndSet(true,false)) {
                        prepared.countDown();
                        try { if (!resume.await(30,TimeUnit.SECONDS)) throw new IllegalStateException("TEST_REVERSAL_GATE_TIMEOUT"); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt();throw new IllegalStateException(interrupted); }
                    }
                    try { return method.invoke(originalService, arguments); }
                    catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                });
            services.registerService("cboddeposit.prepareCashReversalPostings", gated);
            demo.runtime.registerGeneratedService("/META-INF/spp/domains/cboddeposit/services.properties", "cboddeposit.reverseCash");
            var worker=Executors.newSingleThreadExecutor();
            try {
                var attempt=worker.submit(()->demo.execute("reverseCash",request));
                assertTrue("Reversal reached its prepared boundary",prepared.await(20,TimeUnit.SECONDS));
                com.fasterxml.jackson.databind.JsonNode later=null;
                if (changedReceipt) changeStoredReceiptFee(demo,paid.path("CashDepositAgreement").path("depositNumber").asText(),1);
                else {
                    later=demo.call("depositCash",deposit(5));
                    assertEquals("SUCCEEDED",later.path("CashDepositAgreement").path("resultInfo").path("status").asText());
                }
                resume.countDown();
                boolean partial=changedReceipt && "testing".equals(System.getProperty("spp.environment"));
                com.fasterxml.jackson.databind.JsonNode response;
                if (partial) {
                    var error=assertThrows(ExecutionException.class,()->attempt.get(30,TimeUnit.SECONDS));
                    assertEquals("PERSISTENCE_VERSION_CONFLICT",error.getCause().getMessage());
                    response=demo.mapper.valueToTree(request);
                } else response=attempt.get(30,TimeUnit.SECONDS);
                var failed=response.path("CashReversalAgreement").path("resultInfo");
                assertEquals(failed.toString(),"FAILED",failed.path("status").asText());
                assertEquals(failed.toString(),!partial,failed.path("handled").asBoolean());
                if (partial) assertEquals("RECOVERY_REQUIRED",failed.path("failure").path("recoveryStatus").asText());
                amount(changedReceipt ? "100" : "105",demo.call("findDepositAccount",Map.of("DepositAccount",Map.of("accountNumber","ACCOUNT-001"))).path("DepositAccount").path("interestBase"));
                assertFalse(demo.call("findCashDepositAgreement",Map.of("CashDepositAgreement",Map.of("depositNumber",paid.path("CashDepositAgreement").path("depositNumber").asText()))).path("CashDepositAgreement").path("reversed").asBoolean());
                if (changedReceipt) {
                    var original=demo.call("findCashDepositAgreement",Map.of("CashDepositAgreement",Map.of("depositNumber",paid.path("CashDepositAgreement").path("depositNumber").asText())));
                    amount("1",original.path("CashDepositAgreement").path("fee"));
                    if (partial) {
                        assertEquals("RECONCILIATION_REQUIRED",response.path("CashFailureRecord").path("handlingState").asText());
                        return;
                    }
                    changeStoredReceiptFee(demo,paid.path("CashDepositAgreement").path("depositNumber").asText(),0);
                }
                var retried=demo.call("reverseCash",reversal(paid.path("CashDepositAgreement"),20261007,false));
                assertEquals("SUCCEEDED",retried.path("CashReversalAgreement").path("resultInfo").path("status").asText());
                amount(changedReceipt ? "0" : "5",retried.path("DepositAccount").path("interestBase"));
                if (!changedReceipt) {
                var remaining=demo.call("listDepositPostings",Map.of("CashDepositAgreement",Map.of("depositNumber",later.path("CashDepositAgreement").path("depositNumber").asText())));
                assertEquals(8,remaining.path("DepositPostingLineArray").size());
                }
            } finally { resume.countDown();worker.shutdownNow();assertTrue(worker.awaitTermination(5,TimeUnit.SECONDS)); }
        }
    }
    private void changeStoredReceiptFee(XbodTestSupport demo,String number,int fee) throws Exception {
        String url=System.getProperty("spp.xbod.datasource-url");
        var store=new com.servpp.service.repository.JdbcObjectSnapshotStore(
            new com.servpp.service.jdbc.DriverManagerJdbcDataSource("org.h2.Driver",url,null,null),
            value->demo.mapper.valueToTree(value).toString(),"xbod_cash_record");
        int changed=0;
        for (var row:store.findAll()) {
            if (!row.objectType().equals("CashDepositAgreement")) continue;
            var payload=(com.fasterxml.jackson.databind.node.ObjectNode)demo.mapper.readTree(row.payloadJson());
            if (!number.equals(payload.path("depositNumber").asText())) continue;
            payload.put("fee",fee);store.save(row.objectType(),row.objectId(),payload);changed++;
        }
        assertEquals(1,changed);
        try(var connection=java.sql.DriverManager.getConnection(url);var statement=connection.prepareStatement("UPDATE CASH_DEPOSITS SET fee=? WHERE depositNumber=?")) {
            statement.setInt(1,fee);statement.setString(2,number);assertEquals(1,statement.executeUpdate());
        }
    }

}
