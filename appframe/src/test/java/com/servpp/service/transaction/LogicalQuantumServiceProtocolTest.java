package com.servpp.service.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class LogicalQuantumServiceProtocolTest {
    @Test
    public void netsRepeatedLogicalKeysBeforeChoosingAtomicShape() {
        LogicalQuantumServiceCommand command = BusinessTraceContext.execute("REQ-NET-1", () ->
                LogicalQuantumServiceCommand.from(
                        LogicalQuantumServiceCommand.Operation.ENTANGLE,
                        "commerce",
                        context(),
                        boundary(
                                record("credit", "Account.balance", "A", "-10"),
                                record("credit", "Order.balance", "B", "15"),
                                record("credit", "Order.balance", "B", "-5")),
                        definitions()).orElseThrow());

        assertEquals(LogicalQuantumServiceCommand.AtomicGroupShape.NONE, command.atomicGroupShape());
        assertEquals(2, command.mutations().size());
        assertEquals("-10", command.mutations().get(0).delta());
        assertEquals("10", command.mutations().get(1).delta());
    }

    @Test
    public void detectsOneContributorManyRecipientsAfterNetting() {
        LogicalQuantumServiceCommand command = BusinessTraceContext.execute("REQ-SPLIT-1", () ->
                LogicalQuantumServiceCommand.from(
                        LogicalQuantumServiceCommand.Operation.ENTANGLE,
                        "commerce",
                        context(),
                        boundary(
                                record("split", "Account.balance", "A", "-10"),
                                record("split", "Order.balance", "B", "6"),
                                record("split", "Inventory.quantity", "C", "4")),
                        definitions()).orElseThrow());

        assertEquals(LogicalQuantumServiceCommand.AtomicGroupShape.ONE_CONTRIBUTOR_MANY_RECIPIENTS,
                command.atomicGroupShape());
    }

    @Test
    public void reversalUsesIndependentKeyAndOriginalPositiveCommandDeltas() {
        LogicalQuantumServiceCommand command = BusinessTraceContext.execute("REQ-REV-1", () ->
                LogicalQuantumServiceCommand.from(
                        LogicalQuantumServiceCommand.Operation.REVERSE_ENTANGLE,
                        "commerce",
                        context(),
                        boundary(
                                record("credit", "Account.balance", "A", "-10"),
                                record("credit", "Order.balance", "B", "10")),
                        definitions()).orElseThrow());

        assertEquals("REQ-REV-1:reversal", command.requestKey());
        assertEquals("REQ-REV-1", command.originalRequestKey());
        assertEquals("-10", command.mutations().get(0).delta());
        assertEquals("10", command.mutations().get(1).delta());
    }

    @Test
    public void adapterChangesBoundaryOnlyForSuccessfulOutcomes() {
        for (LogicalQuantumServiceResponse.Outcome outcome : List.of(
                LogicalQuantumServiceResponse.Outcome.BUSINESS_FAILED,
                LogicalQuantumServiceResponse.Outcome.NOT_RECEIVED,
                LogicalQuantumServiceResponse.Outcome.UNKNOWN)) {
            QuantumEntanglementBoundary boundary = boundary(
                    record("credit", "Account.balance", "A", "-10"),
                    record("credit", "Order.balance", "B", "10"));
            QuantumServiceAccessAdapter adapter = adapter(command -> response(command, outcome));

            QuantumServiceOutcomeException failure = assertThrows(QuantumServiceOutcomeException.class,
                    () -> BusinessTraceContext.execute("REQ-" + outcome, () -> {
                        adapter.commit(context(), boundary);
                        return null;
                    }));

            assertEquals(outcome, failure.getOutcome());
            assertEquals(QuantumEntanglementBoundary.Status.OPEN, boundary.getStatus());
            assertEquals(outcome, boundary.getQuantumServiceOutcome());
            adapter.rollback(context(), boundary);
            assertEquals(QuantumEntanglementBoundary.Status.OPEN, boundary.getStatus());
        }

        QuantumEntanglementBoundary successful = boundary(
                record("credit", "Account.balance", "A", "-10"),
                record("credit", "Order.balance", "B", "10"));
        BusinessTraceContext.execute("REQ-SUCCESS", () -> {
            adapter(command -> response(command,
                    LogicalQuantumServiceResponse.Outcome.SUCCESS)).commit(context(), successful);
            return null;
        });
        assertEquals(QuantumEntanglementBoundary.Status.COMMITTED, successful.getStatus());
        assertEquals(LogicalQuantumServiceResponse.Outcome.SUCCESS, successful.getQuantumServiceOutcome());
    }

    @Test
    public void transportFailureRecordsUnknownAndRollbackDoesNotOverwriteIt() {
        QuantumEntanglementBoundary boundary = boundary(
                record("credit", "Account.balance", "A", "-10"),
                record("credit", "Order.balance", "B", "10"));
        QuantumServiceAccessAdapter adapter = adapter(command -> {
            throw new IllegalStateException("timeout");
        });

        QuantumServiceOutcomeException failure = assertThrows(QuantumServiceOutcomeException.class,
                () -> BusinessTraceContext.execute("REQ-TIMEOUT", () -> {
                    adapter.commit(context(), boundary);
                    return null;
                }));
        adapter.rollback(context(), boundary);

        assertEquals(LogicalQuantumServiceResponse.Outcome.UNKNOWN, failure.getOutcome());
        assertEquals(LogicalQuantumServiceResponse.Outcome.UNKNOWN, boundary.getQuantumServiceOutcome());
        assertEquals(QuantumEntanglementBoundary.Status.OPEN, boundary.getStatus());
    }

    @Test
    public void httpClientPostsStrictLogicalCommandAndChecksResponseIdentity() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/logical/commands", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            JsonNode request = new ObjectMapper().readTree(requestBody.get());
            String body = """
                    {
                      "namespace":"%s",
                      "requestKey":"%s",
                      "originalRequestKey":null,
                      "boundaryResponse":{
                        "conservationID":42,
                        "originalConservationID":null,
                        "operation":"ENTANGLE",
                        "status":"SUCCESS"
                      }
                    }
                    """.formatted(request.get("namespace").asText(), request.get("requestKey").asText());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            LogicalQuantumServiceCommand command = BusinessTraceContext.execute("REQ-HTTP-1", () ->
                    LogicalQuantumServiceCommand.from(
                            LogicalQuantumServiceCommand.Operation.ENTANGLE,
                            "commerce",
                            context(),
                            boundary(
                                    record("credit", "Account.balance", "A", "-10"),
                                    record("credit", "Order.balance", "B", "10")),
                            definitions()).orElseThrow());
            HttpLogicalQuantumServiceClient client = new HttpLogicalQuantumServiceClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(), 3000);

            LogicalQuantumServiceResponse response = client.execute(command);

            assertEquals("/logical/commands", path.get());
            assertEquals(LogicalQuantumServiceResponse.Outcome.SUCCESS, response.boundaryResponse().status());
            assertTrue(requestBody.get(), requestBody.get().contains("\"atomicGroupShape\":\"NONE\""));
            assertTrue(requestBody.get(), requestBody.get().contains("\"numberType\":\"LONG\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void adapterProvisionsOnlyExplicitObjectAttributesAfterValidatingTheWholeRequest() {
        List<LogicalQuantumProvisioningRequest> requests = new ArrayList<>();
        QuantumServiceAccessAdapter adapter = provisioningAdapter(request -> {
            requests.add(request);
            return new LogicalQuantumProvisioningResponse(
                    request.logicalKey(), requests.size() - 1L, request.definition(),
                    request.expectedPhysicalInitialValue());
        });
        Map<String, Object> initialValues = new LinkedHashMap<>();
        initialValues.put("Account.balance", new BigDecimal("12.34"));
        initialValues.put("Inventory.quantity", 7);

        List<LogicalQuantumProvisioningResponse> responses =
                adapter.provisionObject("OBJECT-1", initialValues);

        assertEquals(2, responses.size());
        assertEquals("commerce", requests.get(0).logicalKey().namespace());
        assertEquals("OBJECT-1", requests.get(0).logicalKey().objectId());
        assertEquals("Account.balance", requests.get(0).logicalKey().attributeId());
        assertEquals("12.34", requests.get(0).initialValue());
        assertEquals("1234", responses.get(0).physicalInitialValue());
        assertEquals("Inventory.quantity", requests.get(1).logicalKey().attributeId());
        assertEquals("7", responses.get(1).physicalInitialValue());
        assertTrue(requests.stream().noneMatch(request ->
                "Order.balance".equals(request.logicalKey().attributeId())));

        requests.clear();
        Map<String, Object> invalid = new LinkedHashMap<>();
        invalid.put("Account.balance", "10.00");
        invalid.put("Unknown.balance", "1");
        assertThrows(IllegalArgumentException.class,
                () -> adapter.provisionObject("OBJECT-2", invalid));
        assertTrue(requests.isEmpty());
    }

    @Test
    public void provisioningHttpClientPostsStrictRequestAndChecksPhysicalInitialValue()
            throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/logical/provisions", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            JsonNode request = new ObjectMapper().readTree(requestBody.get());
            String body = """
                    {
                      "logicalKey":%s,
                      "quantumID":17,
                      "definition":%s,
                      "physicalInitialValue":"1234"
                    }
                    """.formatted(request.get("logicalKey"), request.get("definition"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            HttpLogicalQuantumProvisioningClient client =
                    new HttpLogicalQuantumProvisioningClient(
                            "http://127.0.0.1:" + server.getAddress().getPort(), 3000);
            LogicalQuantumProvisioningRequest request =
                    new LogicalQuantumProvisioningRequest(
                            new LogicalQuantumServiceCommand.LogicalKey(
                                    "commerce", "Account.balance", "A"),
                            new LogicalQuantumServiceCommand.Definition(
                                    "LONG", "0", "1000000", 2),
                            "12.34");

            LogicalQuantumProvisioningResponse response = client.provision(request);

            assertEquals("/logical/provisions", path.get());
            assertEquals(17, response.quantumID());
            assertEquals("1234", response.physicalInitialValue());
            assertTrue(requestBody.get(),
                    requestBody.get().contains("\"initialValue\":\"12.34\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void provisioningResponseRejectsWrongScaleInterpretation() {
        LogicalQuantumProvisioningRequest request =
                new LogicalQuantumProvisioningRequest(
                        new LogicalQuantumServiceCommand.LogicalKey(
                                "commerce", "Account.balance", "A"),
                        new LogicalQuantumServiceCommand.Definition(
                                "LONG", "0", "1000000", 2),
                        "12.34");
        LogicalQuantumProvisioningResponse wrong =
                new LogicalQuantumProvisioningResponse(
                        request.logicalKey(), 17, request.definition(), "1235");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> wrong.requireIdentity(request));
        assertEquals("QService provisioning response identity mismatch",
                failure.getMessage());
    }

    @Test
    public void provisioningHttpClientRejectsNullPrimitiveIdentity() throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/logical/provisions", exchange -> {
            byte[] bytes = """
                    {
                      "logicalKey":{
                        "namespace":"commerce",
                        "attributeId":"Account.balance",
                        "objectId":"A"
                      },
                      "quantumID":null,
                      "definition":{
                        "numberType":"LONG",
                        "minValue":"0",
                        "maxValue":"1000000",
                        "scale":2
                      },
                      "physicalInitialValue":"1234"
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            HttpLogicalQuantumProvisioningClient client =
                    new HttpLogicalQuantumProvisioningClient(
                            "http://127.0.0.1:" + server.getAddress().getPort(), 3000);
            LogicalQuantumProvisioningRequest request =
                    new LogicalQuantumProvisioningRequest(
                            new LogicalQuantumServiceCommand.LogicalKey(
                                    "commerce", "Account.balance", "A"),
                            new LogicalQuantumServiceCommand.Definition(
                                    "LONG", "0", "1000000", 2),
                            "12.34");

            assertThrows(IllegalStateException.class,
                    () -> client.provision(request));
        } finally {
            server.stop(0);
        }
    }

    private QuantumServiceAccessAdapter adapter(LogicalQuantumServiceClient client) {
        return new QuantumServiceAccessAdapter("globalQuantum", "https://quantum.example.test",
                "SERVICE_REVERSAL", "commerce", definitions(), client);
    }

    private QuantumServiceAccessAdapter provisioningAdapter(
            LogicalQuantumProvisioningClient client) {
        return new QuantumServiceAccessAdapter(
                "globalQuantum", "https://quantum.example.test",
                "SERVICE_REVERSAL", "commerce",
                List.of(
                        new QuantumServiceDefinition(
                                "Account.balance", "LONG", "0", "1000000", 2),
                        new QuantumServiceDefinition(
                                "Order.balance", "LONG", "0", "1000000", 2),
                        new QuantumServiceDefinition(
                                "Inventory.quantity", "LONG", "0", "1000000", 0)),
                command -> response(command,
                        LogicalQuantumServiceResponse.Outcome.SUCCESS),
                client);
    }

    private LogicalQuantumServiceResponse response(
            LogicalQuantumServiceCommand command, LogicalQuantumServiceResponse.Outcome outcome) {
        return new LogicalQuantumServiceResponse(
                command.namespace(), command.requestKey(), command.originalRequestKey(),
                new LogicalQuantumServiceResponse.BoundaryResponse(
                        42,
                        command.operation() == LogicalQuantumServiceCommand.Operation.REVERSE_ENTANGLE ? 41L : null,
                        command.operation(),
                        outcome));
    }

    private TransactionContext context() {
        return new TransactionContext(Map.of("globalQuantum", TransactionMode.QUANTUM));
    }

    private QuantumEntanglementBoundary boundary(QuantumConservationRecord... records) {
        QuantumEntanglementBoundary boundary =
                new QuantumEntanglementBoundary("accounting", List.of("accounting"));
        for (QuantumConservationRecord record : records) {
            boundary.addRecord(record);
        }
        return boundary;
    }

    private QuantumConservationRecord record(
            String groupId, String attributeId, String objectId, String delta) {
        BigDecimal before = new BigDecimal("100");
        BigDecimal change = new BigDecimal(delta);
        return new QuantumConservationRecord(
                groupId, attributeId, objectId, before, change, before.add(change));
    }

    private List<QuantumServiceDefinition> definitions() {
        return List.of(
                new QuantumServiceDefinition("Account.balance", "LONG", "0", "1000000", 0),
                new QuantumServiceDefinition("Order.balance", "LONG", "0", "1000000", 0),
                new QuantumServiceDefinition("Inventory.quantity", "LONG", "0", "1000000", 0));
    }
}
