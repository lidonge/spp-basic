package com.servpp.service.transaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.servpp.service.quantum.settlement.LogicalQuantumKey;
import com.servpp.service.quantum.settlement.QuantumDefinition;
import com.servpp.service.quantum.settlement.QuantumSettlementService.Capability;
import com.servpp.service.quantum.settlement.SettlementKey;
import com.servpp.service.quantum.settlement.SettlementResponse;
import com.servpp.service.quantum.settlement.SettlementResponse.Operation;
import com.servpp.service.quantum.settlement.SettlementResponse.Outcome;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.After;
import org.junit.Test;

/** The query, receipt, failureReason and capabilities side of QService contract spp-qservice-logical/1. */
public class QServiceLogicalQueryTest {
    private HttpServer server;
    private final List<String> requests = new ArrayList<>();

    @After
    public void stop() {
        if (server != null) server.stop(0);
    }

    private String serve(Function<HttpExchange, String> handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath());
            String body = handler.apply(exchange);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(body.startsWith("ERROR") ? 500 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static QServiceQuantumSettlement settlement(String endpoint, boolean provisioning) {
        return new QServiceQuantumSettlement("globalQuantum", new HttpLogicalQuantumServiceClient(endpoint, 3000),
                provisioning ? request -> null : null);
    }

    private static String envelope(String key, String status, String extra) {
        return """
                {"namespace":"commerce","requestKey":"%s","originalRequestKey":null,
                 "boundaryResponse":{"conservationID":42,"originalConservationID":null,
                   "operation":"ENTANGLE","status":"%s"}%s}""".formatted(key, status, extra);
    }

    @Test
    public void queryPercentEncodesEverySegmentByteByByte() throws Exception {
        String endpoint = serve(exchange -> """
                {"namespace":"commerce/é","requestKey":"a b/c~d","originalRequestKey":null}""");
        SettlementResponse response = settlement(endpoint, false)
                .query(new SettlementKey("commerce/é", "a b/c~d"));
        assertEquals(List.of("GET /logical/commands/commerce%2F%C3%A9/a%20b%2Fc~d"), requests);
        assertEquals(Outcome.NOT_RECEIVED, response.outcome());
        assertEquals(Operation.QUERY, response.operation());
        assertEquals(Optional.empty(), response.receipt());
        assertEquals(Optional.empty(), response.failureReason());
    }

    @Test
    public void queryMapsReceiptOutcomeAndStructuredFailureReason() throws Exception {
        String endpoint = serve(exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            if (path.endsWith("/ok")) return envelope("ok", "SUCCESS", ",\"receipt\":\"42\"");
            if (path.endsWith("/unknown")) return envelope("unknown", "UNKNOWN", ",\"receipt\":\"42\"");
            return envelope("bad", "BUSINESS_FAILED", """
                    ,"receipt":"42","failureReason":{"code":"LOWER_BOUND",
                    "quantum":{"namespace":"commerce","attributeId":"Account.balance","objectId":"A"},
                    "message":"below the minimum"}""");
        });
        QServiceQuantumSettlement service = settlement(endpoint, false);

        SettlementResponse ok = service.query(new SettlementKey("commerce", "ok"));
        assertEquals(Outcome.SUCCESS, ok.outcome());
        assertEquals(Optional.of("42"), ok.receipt());
        assertEquals(Outcome.UNKNOWN, service.query(new SettlementKey("commerce", "unknown")).outcome());

        SettlementResponse bad = service.query(new SettlementKey("commerce", "bad"));
        assertEquals(Outcome.BUSINESS_FAILED, bad.outcome());
        SettlementResponse.FailureReason reason = bad.failureReason().orElseThrow();
        assertEquals("LOWER_BOUND", reason.code());
        assertEquals(Optional.of(new LogicalQuantumKey("commerce", "Account.balance", "A")), reason.quantum());
        assertEquals("below the minimum", reason.message());
    }

    @Test
    public void strictMirrorRejectsMalformedEnvelopes() throws Exception {
        for (String body : List.of(
                envelope("k", "SUCCESS", ",\"receipt\":42"),
                envelope("k", "SUCCESS", ",\"receipt\":\"41\""),
                envelope("k", "SUCCESS", ",\"surprise\":1"),
                envelope("k", "BUSINESS_FAILED", ",\"receipt\":\"42\""),
                envelope("k", "SUCCESS", ",\"failureReason\":{\"code\":\"LOWER_BOUND\",\"quantum\":null,\"message\":\"x\"}"),
                envelope("k", "BUSINESS_FAILED", ",\"failureReason\":{\"code\":\"NOPE\",\"quantum\":null,\"message\":\"x\"}"))) {
            String endpoint = serve(exchange -> body);
            // A malformed answer is an I/O style failure of the client, which the bridge reads as UNKNOWN.
            SettlementResponse response = settlement(endpoint, false).query(new SettlementKey("commerce", "k"));
            assertEquals(body, Outcome.UNKNOWN, response.outcome());
            assertEquals("TRANSPORT_FAILURE", response.failureReason().orElseThrow().code());
            server.stop(0);
        }
    }

    @Test
    public void capabilitiesAreAskedOnceAndMappedToTheEnum() throws Exception {
        String endpoint = serve(exchange ->
                "{\"capabilities\":[\"ONE_PHASE\",\"PROVISIONING\"],\"contract\":\"spp-qservice-logical/1\"}");
        QServiceQuantumSettlement service = settlement(endpoint, true);
        assertEquals(Set.of(Capability.ONE_PHASE, Capability.PROVISIONING), service.capabilities());
        service.capabilities();
        assertEquals(List.of("GET /capabilities"), requests);
    }

    @Test
    public void capabilitiesFailFastOnForeignContractOrCapability() throws Exception {
        for (String body : List.of(
                "{\"capabilities\":[\"ONE_PHASE\"],\"contract\":\"spp-qservice-logical/2\"}",
                "{\"capabilities\":[\"ONE_PHASE\",\"TWO_PHASE\"],\"contract\":\"spp-qservice-logical/1\"}",
                "{\"capabilities\":[\"PROVISIONING\"],\"contract\":\"spp-qservice-logical/1\"}")) {
            String endpoint = serve(exchange -> body);
            assertThrows(body, RuntimeException.class, () -> settlement(endpoint, true).capabilities());
            server.stop(0);
        }
    }

    @Test
    public void provisioningNeedsTheServerAndTheLocalClient() throws Exception {
        String endpoint = serve(exchange -> "{\"capabilities\":[\"ONE_PHASE\"],\"contract\":\"spp-qservice-logical/1\"}");
        assertEquals(Set.of(Capability.ONE_PHASE), settlement(endpoint, true).capabilities());
    }

    @Test
    public void bridgeQueriesAfterAnUnknownInsteadOfResending() throws Exception {
        String endpoint = serve(exchange -> {
            if (exchange.getRequestMethod().equals("POST")) return "ERROR timeout";
            return envelope(exchange.getRequestURI().getRawPath().replaceAll(".*/", ""), "SUCCESS", ",\"receipt\":\"42\"");
        });
        SettlementQuantumAccessAdapter adapter = new SettlementQuantumAccessAdapter(
                settlement(endpoint, false), "commerce", Map.of(
                "Account.balance", new QuantumDefinition(QuantumDefinition.NumberType.LONG, "0", "1000000", 0),
                "Order.balance", new QuantumDefinition(QuantumDefinition.NumberType.LONG, "0", "1000000", 0)), 3);
        QuantumEntanglementBoundary boundary = new QuantumEntanglementBoundary("accounting", List.of("accounting"));
        boundary.addRecord(record("Account.balance", "A", "-10"));
        boundary.addRecord(record("Order.balance", "B", "10"));

        adapter.commit(new TransactionContext(Map.of("globalQuantum", TransactionMode.QUANTUM)), boundary);

        assertEquals(QuantumEntanglementBoundary.Status.COMMITTED, boundary.getStatus());
        assertEquals(2, requests.size());
        assertTrue(requests.get(0), requests.get(0).startsWith("POST /logical/commands"));
        assertTrue(requests.get(1), requests.get(1).startsWith("GET /logical/commands/commerce/"));
    }

    private static QuantumConservationRecord record(String attribute, String object, String delta) {
        BigDecimal change = new BigDecimal(delta);
        BigDecimal before = new BigDecimal("100");
        return new QuantumConservationRecord("g", attribute, object, before, change, before.add(change));
    }
}
