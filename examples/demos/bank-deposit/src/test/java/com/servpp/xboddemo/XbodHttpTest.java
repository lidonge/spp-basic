package com.servpp.xboddemo;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public class XbodHttpTest {
    @Test public void appHttpBoundaryProjectsHeadersAndGuardsRepeatedUiRequests() throws Exception {
        try (var demo = new XbodTestSupport()) {
            XbodTransactionTest.open(demo);
            Properties catalog = new Properties();
            try (var input = getClass().getResourceAsStream("/META-INF/spp/domains/cboddeposit/web.properties")) { catalog.load(input); }
            var previousStore = com.servpp.service.idempotency.BusinessIdempotencyManager.requestStore();
            boolean coordinated = com.servpp.service.idempotency.BusinessIdempotencyManager.isAvailable();
            // This HTTP test verifies APP's five-second business-key window, independently
            // of host speed; business facts still use the generated JDBC deployment.
            if (coordinated) demo.runtime.getPhysicalRuntime().setRequestStore(
                    new com.servpp.service.idempotency.InMemoryBusinessIdempotencyStore(
                            java.time.Clock.fixed(java.time.Instant.parse("2026-10-01T00:00:00Z"),java.time.ZoneOffset.UTC)));
            try (var server = new com.servpp.service.runtime.GeneratedWebServer(catalog,demo.mapper,new InetSocketAddress("127.0.0.1",0))) {
                server.start(); com.servpp.service.runtime.DomainServiceRouter.markReady();
                var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                var home = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.port()+"/"))
                        .GET().build(),HttpResponse.BodyHandlers.ofString());
                assertEquals(200,home.statusCode()); assertTrue(home.body().contains("Deposit Operations"));
                assertTrue(home.body().contains("lang=\"en-US\""));
                assertTrue(home.body().contains("USD — US dollars"));
                String cookie = "spp-client="+UUID.randomUUID();
                URI depositUrl = URI.create("http://127.0.0.1:"+server.port()+"/api/cboddeposit.depositCash");
                var values = XbodTransactionTest.deposit(100);
                values.put("AppHeader",Map.of("callerId","UNTRUSTED","callMode","LOCAL","requestSerialNumber","UNTRUSTED"));
                String body = demo.mapper.writeValueAsString(demo.request("depositCash",values));
                var request = HttpRequest.newBuilder(depositUrl).timeout(Duration.ofSeconds(30)).header("Cookie",cookie)
                        .header("X-SPP-Request-Serial","HTTP-DEPOSIT").header("Content-Type","application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                var first = client.send(request,HttpResponse.BodyHandlers.ofString());
                var replay = client.send(request,HttpResponse.BodyHandlers.ofString());
                assertEquals(first.body(),200,first.statusCode()); assertEquals(replay.body(),200,replay.statusCode());
                var a = demo.mapper.readTree(first.body()); var b = demo.mapper.readTree(replay.body());
                assertFalse(first.body(),a.path("replayed").asBoolean());
                assertEquals(replay.body(),coordinated,b.path("replayed").asBoolean());
                assertEquals("REMOTE",a.path("participants").path("AppHeader").path("callMode").asText());
                assertEquals("HTTP-DEPOSIT",a.path("participants").path("AppHeader").path("requestSerialNumber").asText());
                assertTrue(a.path("participants").path("AppHeader").path("callerId").asText().startsWith("browser:"));
                assertEquals(coordinated, a.path("participants").path("CashDepositAgreement").path("depositNumber")
                        .equals(b.path("participants").path("CashDepositAgreement").path("depositNumber")));
                var changed = XbodTransactionTest.deposit(100);
                changed.put("DepositFeeParty",Map.of("accountKind","NormalSavings","customerNumber","CHANGED","branchNumber","001","regionCode","R001"));
                // APP identifies this operation by the account pair, even with another HTTP serial.
                var conflict = client.send(HttpRequest.newBuilder(depositUrl).timeout(Duration.ofSeconds(30)).header("Cookie",cookie)
                        .header("X-SPP-Request-Serial","HTTP-CHANGED").POST(HttpRequest.BodyPublishers.ofString(demo.mapper.writeValueAsString(demo.request("depositCash",changed)))).build(),HttpResponse.BodyHandlers.ofString());
                assertEquals(conflict.body(),coordinated ? 400 : 200,conflict.statusCode());
                if (coordinated) assertEquals("REQUEST_ID_REUSED",demo.mapper.readTree(conflict.body()).path("error").asText());
                var stored = demo.call("findDepositAccount",Map.of("DepositAccount",Map.of("accountNumber","ACCOUNT-001")));
                XbodTransactionTest.amount(coordinated ? "100" : "300",stored.path("DepositAccount").path("interestBase"));
                var readRequest = demo.request("readDepositAccount", XbodTransactionTest.reversal(a.path("participants").path("CashDepositAgreement"),20261007,false).entrySet().stream()
                        .filter(entry -> java.util.Set.of("DepositAccount","AccountEligibility","DepositAccrualState","CashSettlementAccount","InterestIncomeAccount","CollectionInterestIncomeAccount","PrincipalRecoveryAccount").contains(entry.getKey()))
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue)));
                var queried = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.port()+"/api/cboddeposit.readDepositAccount"))
                        .POST(HttpRequest.BodyPublishers.ofString(demo.mapper.writeValueAsString(readRequest))).build(),HttpResponse.BodyHandlers.ofString());
                assertEquals(queried.body(),200,queried.statusCode());
                XbodTransactionTest.amount(coordinated ? "100" : "300",demo.mapper.readTree(queried.body()).path("participants").path("DepositAccount").path("interestBase"));
                var inverse = demo.request("reverseCash",XbodTransactionTest.reversal(a.path("participants").path("CashDepositAgreement"),20261007,false));
                var reverseRequest = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.port()+"/api/cboddeposit.reverseCash"))
                        .timeout(Duration.ofSeconds(30)).header("Cookie",cookie).header("X-SPP-Request-Serial","HTTP-REVERSE")
                        .POST(HttpRequest.BodyPublishers.ofString(demo.mapper.writeValueAsString(inverse))).build();
                var reversed = client.send(reverseRequest,HttpResponse.BodyHandlers.ofString());
                var reversedReplay = client.send(reverseRequest,HttpResponse.BodyHandlers.ofString());
                assertEquals(reversed.body(),200,reversed.statusCode());
                assertEquals(reversedReplay.body(),coordinated ? 200 : 400,reversedReplay.statusCode());
                if (coordinated) assertTrue(demo.mapper.readTree(reversedReplay.body()).path("replayed").asBoolean());
                else assertEquals("DEPOSIT_ALREADY_REVERSED",demo.mapper.readTree(reversedReplay.body()).path("error").asText());
                var after = demo.call("findDepositAccount",Map.of("DepositAccount",Map.of("accountNumber","ACCOUNT-001")));
                XbodTransactionTest.amount(coordinated ? "0" : "200",after.path("DepositAccount").path("interestBase"));
                var hidden = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.port()+"/api/cboddeposit.findDepositAccount"))
                        .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString());
                assertEquals(404,hidden.statusCode());
            } finally {
                demo.runtime.getPhysicalRuntime().setRequestStore(null);
                com.servpp.service.idempotency.BusinessIdempotencyManager.setRequestStore(previousStore);
            }
        }
    }
}
