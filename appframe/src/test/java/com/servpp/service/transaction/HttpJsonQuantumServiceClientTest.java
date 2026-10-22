package com.servpp.service.transaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.sun.net.httpserver.HttpServer;

public class HttpJsonQuantumServiceClientTest {
    @Test
    public void requiresPositiveTimeoutMillis() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new HttpJsonQuantumServiceClient("https://quantum.example.test/commit", 0));

        assertEquals("Quantum service timeoutMillis must be positive.", error.getMessage());
    }

    @Test
    public void keepsConfiguredPositiveTimeoutMillis() {
        HttpJsonQuantumServiceClient client =
                new HttpJsonQuantumServiceClient(" https://quantum.example.test/commit ", 3000);

        assertEquals("https://quantum.example.test/commit", client.getEndpoint());
        assertEquals(3000, client.getTimeoutMillis());
    }

    @Test
    public void defaultConstructorUsesDefaultPositiveTimeoutMillis() {
        HttpJsonQuantumServiceClient client =
                new HttpJsonQuantumServiceClient("https://quantum.example.test/commit");

        assertTrue(client.getTimeoutMillis() > 0);
    }

    @Test
    public void quantumServiceAccessAdapterNormalizesRuntimeKeys() {
        QuantumServiceAccessAdapter adapter = new QuantumServiceAccessAdapter(
                " globalQuantum ", " https://quantum.example.test/commit ", " NEGATIVE_DELTA ");

        assertEquals("globalQuantum", adapter.getStoreName());
        assertEquals("https://quantum.example.test/commit", adapter.getEndpoint());
        assertEquals("NEGATIVE_DELTA", adapter.getReversalMode());
    }

    @Test
    public void wrapsInvalidEndpointAtExecution() {
        HttpJsonQuantumServiceClient client =
                new HttpJsonQuantumServiceClient("${spp.test.quantum.endpoint", 3000);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client.commit(new QuantumServiceCommitRequest("globalQuantum",
                        "${spp.test.quantum.endpoint", "TX-1", "REQ-1", "REQ-1",
                        "reserveCredit", List.of("creditAccounting"), "COMMITTED",
                        "NEGATIVE_DELTA", List.of())));

        assertTrue(error.getMessage().contains(
                "Quantum service endpoint is invalid: ${spp.test.quantum.endpoint."));
    }

    @Test
    public void acceptsEmptyJsonSuccessResponse() throws Exception {
        HttpServer server = quantumServiceServer(200, "{}");
        server.start();
        try {
            HttpJsonQuantumServiceClient client = new HttpJsonQuantumServiceClient(
                    "http://localhost:" + server.getAddress().getPort() + "/quantum/commit", 3000);

            client.commit(new QuantumServiceCommitRequest("globalQuantum",
                    client.getEndpoint(), "TX-1", "REQ-1", "REQ-1",
                    "reserveCredit", List.of("creditAccounting"), "COMMITTED",
                    "NEGATIVE_DELTA", List.of()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void rejectsExplicitQuantumServiceFailureResponse() throws Exception {
        HttpServer server = quantumServiceServer(200,
                "{\"accepted\":false,\"status\":\"REJECTED\",\"error\":\"conflicting boundary\"}");
        server.start();
        try {
            HttpJsonQuantumServiceClient client = new HttpJsonQuantumServiceClient(
                    "http://localhost:" + server.getAddress().getPort() + "/quantum/commit", 3000);

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> client.commit(new QuantumServiceCommitRequest("globalQuantum",
                            client.getEndpoint(), "TX-1", "REQ-1", "REQ-1",
                            "reserveCredit", List.of("creditAccounting"), "COMMITTED",
                            "NEGATIVE_DELTA", List.of())));

            assertTrue(error.getMessage(), error.getMessage().contains("Quantum service rejected boundary"));
            assertTrue(error.getMessage(), error.getMessage().contains("conflicting boundary"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void rejectsInvalidJsonQuantumServiceResponse() throws Exception {
        HttpServer server = quantumServiceServer(200, "not-json");
        server.start();
        try {
            HttpJsonQuantumServiceClient client = new HttpJsonQuantumServiceClient(
                    "http://localhost:" + server.getAddress().getPort() + "/quantum/commit", 3000);

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> client.commit(new QuantumServiceCommitRequest("globalQuantum",
                            client.getEndpoint(), "TX-1", "REQ-1", "REQ-1",
                            "reserveCredit", List.of("creditAccounting"), "COMMITTED",
                            "NEGATIVE_DELTA", List.of())));

            assertEquals("Quantum service response is not valid JSON.", error.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void rejectsNonObjectQuantumServiceResponse() throws Exception {
        HttpServer server = quantumServiceServer(200, "[]");
        server.start();
        try {
            HttpJsonQuantumServiceClient client = new HttpJsonQuantumServiceClient(
                    "http://localhost:" + server.getAddress().getPort() + "/quantum/commit", 3000);

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> client.commit(new QuantumServiceCommitRequest("globalQuantum",
                            client.getEndpoint(), "TX-1", "REQ-1", "REQ-1",
                            "reserveCredit", List.of("creditAccounting"), "COMMITTED",
                            "NEGATIVE_DELTA", List.of())));

            assertEquals("Quantum service response must be a JSON object: []", error.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void commitRequestPreservesObjectIdentityPayloads() {
        QuantumServiceCommitRequest request = BusinessTraceContext.execute("REQ-QS-OBJECT-1", () -> {
            TransactionContext context =
                    new TransactionContext(Map.of("globalQuantum", TransactionMode.QUANTUM));
            return QuantumServiceCommitRequest.from("globalQuantum",
                    "https://quantum.example.test/commit", "NEGATIVE_DELTA", context, objectIdentityBoundary());
        });

        assertEquals("REQ-QS-OBJECT-1", request.businessTraceId());
        assertEquals("REQ-QS-OBJECT-1", request.idempotencyKey());
        assertEquals("inventory+salesItem", request.boundaryServiceId());
        assertEquals(List.of("inventory", "salesItem"), request.resourceIds());
        assertEquals("COMMITTED", request.targetStatus());
        assertEquals("NEGATIVE_DELTA", request.reversalMode());
        assertEquals(2, request.records().size());

        QuantumServiceCommitRequest.Record inventory = request.records().get(0);
        assertEquals("inventory.qty", inventory.groupId());
        assertEquals("Inventory.availableQty", inventory.attributeId());
        assertEquals("spp-composite:MAIN.SKU-BOOK-001", inventory.objectId());
        assertEquals(new BigDecimal("20"), inventory.beforeValue());
        assertEquals(new BigDecimal("-2"), inventory.deltaValue());
        assertEquals(new BigDecimal("18"), inventory.afterValue());

        QuantumServiceCommitRequest.Record salesItem = request.records().get(1);
        assertEquals("sales.item.qty", salesItem.groupId());
        assertEquals("SalesItem.orderQty", salesItem.attributeId());
        assertEquals("SO-1:SKU-BOOK-001", salesItem.objectId());
        assertEquals(new BigDecimal("0"), salesItem.beforeValue());
        assertEquals(new BigDecimal("2"), salesItem.deltaValue());
        assertEquals(new BigDecimal("2"), salesItem.afterValue());
    }

    @Test
    public void reversalRequestPreservesObjectIdentityAndNegatesDelta() {
        QuantumServiceReversalRequest request = BusinessTraceContext.execute("REQ-QS-REVERSAL-1", () -> {
            TransactionContext context =
                    new TransactionContext(Map.of("globalQuantum", TransactionMode.QUANTUM));
            return QuantumServiceReversalRequest.from("globalQuantum",
                    "https://quantum.example.test/commit", "NEGATIVE_DELTA", context, objectIdentityBoundary());
        });

        assertEquals("REVERSAL", request.operation());
        assertEquals("REQ-QS-REVERSAL-1", request.businessTraceId());
        assertEquals("REQ-QS-REVERSAL-1", request.idempotencyKey());
        assertEquals("inventory+salesItem", request.boundaryServiceId());
        assertEquals(List.of("inventory", "salesItem"), request.resourceIds());
        assertEquals("ROLLED_BACK", request.targetStatus());
        assertEquals("NEGATIVE_DELTA", request.reversalMode());
        assertEquals(2, request.records().size());

        QuantumServiceReversalRequest.Record inventory = request.records().get(0);
        assertEquals("Inventory.availableQty", inventory.attributeId());
        assertEquals("spp-composite:MAIN.SKU-BOOK-001", inventory.objectId());
        assertEquals(new BigDecimal("18"), inventory.beforeValue());
        assertEquals(new BigDecimal("2"), inventory.deltaValue());
        assertEquals(new BigDecimal("20"), inventory.afterValue());

        QuantumServiceReversalRequest.Record salesItem = request.records().get(1);
        assertEquals("SalesItem.orderQty", salesItem.attributeId());
        assertEquals("SO-1:SKU-BOOK-001", salesItem.objectId());
        assertEquals(new BigDecimal("2"), salesItem.beforeValue());
        assertEquals(new BigDecimal("-2"), salesItem.deltaValue());
        assertEquals(new BigDecimal("0"), salesItem.afterValue());
    }

    private QuantumEntanglementBoundary objectIdentityBoundary() {
        QuantumEntanglementBoundary boundary =
                new QuantumEntanglementBoundary("inventory+salesItem", List.of("inventory", "salesItem"));
        boundary.addRecord(new QuantumConservationRecord("inventory.qty", "Inventory.availableQty",
                "spp-composite:MAIN.SKU-BOOK-001",
                new BigDecimal("20"), new BigDecimal("-2"), new BigDecimal("18")));
        boundary.addRecord(new QuantumConservationRecord("sales.item.qty", "SalesItem.orderQty",
                "SO-1:SKU-BOOK-001",
                new BigDecimal("0"), new BigDecimal("2"), new BigDecimal("2")));
        return boundary;
    }

    private HttpServer quantumServiceServer(int status, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/quantum/commit", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        return server;
    }
}
