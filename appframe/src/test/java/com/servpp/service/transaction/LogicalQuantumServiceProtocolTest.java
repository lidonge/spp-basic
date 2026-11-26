package com.servpp.service.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.servpp.service.quantum.settlement.ProvisionResponse;
import com.servpp.service.quantum.settlement.QuantumDefinition;
import com.servpp.service.quantum.settlement.SettlementCommand;
import com.servpp.service.quantum.settlement.SettlementResponse;
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
        SettlementCommand command = BusinessTraceContext.execute("REQ-NET-1", () ->
                bridge(c -> response(c, LogicalQuantumServiceResponse.Outcome.SUCCESS)).command(
                        SettlementResponse.Operation.SETTLE,
                        context(),
                        boundary(
                                record("credit", "Account.balance", "A", "-10"),
                                record("credit", "Order.balance", "B", "15"),
                                record("credit", "Order.balance", "B", "-5"))).orElseThrow());

        assertEquals(SettlementCommand.AtomicGroupShape.NONE, command.shape());
        assertEquals(2, command.mutations().size());
        assertEquals("-10", command.mutations().get(0).delta());
        assertEquals("10", command.mutations().get(1).delta());
    }

    @Test
    public void detectsOneContributorManyRecipientsAfterNetting() {
        SettlementCommand command = BusinessTraceContext.execute("REQ-SPLIT-1", () ->
                bridge(c -> response(c, LogicalQuantumServiceResponse.Outcome.SUCCESS)).command(
                        SettlementResponse.Operation.SETTLE,
                        context(),
                        boundary(
                                record("split", "Account.balance", "A", "-10"),
                                record("split", "Order.balance", "B", "6"),
                                record("split", "Inventory.quantity", "C", "4"))).orElseThrow());

        assertEquals(SettlementCommand.AtomicGroupShape.ONE_TO_MANY, command.shape());
    }

    @Test
    public void reversalUsesIndependentKeyAndOriginalPositiveCommandDeltas() {
        SettlementCommand command = BusinessTraceContext.execute("REQ-REV-1", () ->
                bridge(c -> response(c, LogicalQuantumServiceResponse.Outcome.SUCCESS)).command(
                        SettlementResponse.Operation.REVERSE,
                        context(),
                        boundary(
                                record("credit", "Account.balance", "A", "-10"),
                                record("credit", "Order.balance", "B", "10"))).orElseThrow());

        assertEquals("REQ-REV-1:reversal", command.key().requestKey());
        assertEquals("REQ-REV-1", command.originalRequestKey().orElseThrow());
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
            SettlementQuantumAccessAdapter adapter = bridge(command -> response(command, outcome));

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
            bridge(command -> response(command,
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
        SettlementQuantumAccessAdapter adapter = bridge(command -> {
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
            LogicalQuantumServiceCommand command = new LogicalQuantumServiceCommand(
                    LogicalQuantumServiceCommand.Operation.ENTANGLE, "commerce", "accounting", "REQ-HTTP-1", null,
                    LogicalQuantumServiceCommand.AtomicGroupShape.NONE,
                    List.of(wireMutation("Account.balance", "A", "-10"),
                            wireMutation("Order.balance", "B", "10")));
            HttpLogicalQuantumServiceClient client = new HttpLogicalQuantumServiceClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(), 3000, QServiceClientSecurity.insecure());

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
        SettlementQuantumAccessAdapter adapter = provisioningAdapter(request -> {
            requests.add(request);
            return new LogicalQuantumProvisioningResponse(
                    request.logicalKey(), requests.size() - 1L, request.definition(),
                    request.expectedPhysicalInitialValue());
        });
        Map<String, Object> initialValues = new LinkedHashMap<>();
        initialValues.put("Account.balance", new BigDecimal("12.34"));
        initialValues.put("Inventory.quantity", 7);

        List<ProvisionResponse> responses =
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
                            "http://127.0.0.1:" + server.getAddress().getPort(), 3000, QServiceClientSecurity.insecure());
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
                            "http://127.0.0.1:" + server.getAddress().getPort(), 3000, QServiceClientSecurity.insecure());
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

    private SettlementQuantumAccessAdapter bridge(LogicalQuantumServiceClient client) {
        return new SettlementQuantumAccessAdapter(
                new QServiceQuantumSettlement("globalQuantum", client, null), "commerce", definitions());
    }

    private SettlementQuantumAccessAdapter provisioningAdapter(
            LogicalQuantumProvisioningClient client) {
        return new SettlementQuantumAccessAdapter(
                new QServiceQuantumSettlement("globalQuantum",
                        command -> response(command, LogicalQuantumServiceResponse.Outcome.SUCCESS), client),
                "commerce",
                Map.of(
                        "Account.balance", new QuantumDefinition(QuantumDefinition.NumberType.LONG, "0", "1000000", 2),
                        "Order.balance", new QuantumDefinition(QuantumDefinition.NumberType.LONG, "0", "1000000", 2),
                        "Inventory.quantity", new QuantumDefinition(QuantumDefinition.NumberType.LONG, "0", "1000000", 0)));
    }

    private LogicalQuantumServiceCommand.Mutation wireMutation(String attributeId, String objectId, String delta) {
        return new LogicalQuantumServiceCommand.Mutation(
                new LogicalQuantumServiceCommand.LogicalKey("commerce", attributeId, objectId),
                new LogicalQuantumServiceCommand.Definition("LONG", "0", "1000000", 0), delta);
    }

    private LogicalQuantumServiceResponse response(
            LogicalQuantumServiceCommand command, LogicalQuantumServiceResponse.Outcome outcome) {
        return new LogicalQuantumServiceResponse(
                command.namespace(), command.requestKey(), command.originalRequestKey(),
                new LogicalQuantumServiceResponse.BoundaryResponse(
                        42,
                        command.operation() == LogicalQuantumServiceCommand.Operation.REVERSE_ENTANGLE ? 41L : null,
                        command.operation(),
                        outcome),
                null,
                outcome == LogicalQuantumServiceResponse.Outcome.BUSINESS_FAILED
                        ? new LogicalQuantumServiceResponse.FailureReason(
                                LogicalQuantumServiceResponse.FailureCode.LOWER_BOUND, null, "below") : null);
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

    private Map<String, QuantumDefinition> definitions() {
        QuantumDefinition definition = new QuantumDefinition(QuantumDefinition.NumberType.LONG, "0", "1000000", 0);
        return Map.of("Account.balance", definition, "Order.balance", definition, "Inventory.quantity", definition);
    }
}
