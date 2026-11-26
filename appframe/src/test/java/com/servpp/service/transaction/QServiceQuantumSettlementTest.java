package com.servpp.service.transaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.servpp.service.quantum.settlement.LogicalQuantumKey;
import com.servpp.service.quantum.settlement.ProvisionCommand;
import com.servpp.service.quantum.settlement.ProvisionResponse;
import com.servpp.service.quantum.settlement.QuantumDefinition;
import com.servpp.service.quantum.settlement.QuantumMutation;
import com.servpp.service.quantum.settlement.QuantumSettlementService.Capability;
import com.servpp.service.quantum.settlement.SettlementCommand;
import com.servpp.service.quantum.settlement.SettlementKey;
import com.servpp.service.quantum.settlement.SettlementResponse;
import com.servpp.service.quantum.settlement.SettlementResponse.Operation;
import com.servpp.service.quantum.settlement.SettlementResponse.Outcome;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.Test;

public class QServiceQuantumSettlementTest {
    private static final QuantumDefinition DEFINITION =
            new QuantumDefinition(QuantumDefinition.NumberType.LONG, "0", "1000000", 2);

    private static SettlementCommand command(String requestKey, String original) {
        return new SettlementCommand(new SettlementKey("commerce", requestKey), Optional.ofNullable(original),
                "accounting", SettlementCommand.AtomicGroupShape.NONE,
                List.of(new QuantumMutation(new LogicalQuantumKey("commerce", "Account.balance", "A"),
                                DEFINITION, "-10.5"),
                        new QuantumMutation(new LogicalQuantumKey("commerce", "Order.balance", "B"),
                                DEFINITION, "10.5")));
    }

    private static LogicalQuantumServiceResponse wire(LogicalQuantumServiceCommand command,
                                                      LogicalQuantumServiceResponse.Outcome outcome) {
        return new LogicalQuantumServiceResponse(command.namespace(), command.requestKey(),
                command.originalRequestKey(), new LogicalQuantumServiceResponse.BoundaryResponse(
                        42, command.originalRequestKey() == null ? null : 41L, command.operation(), outcome),
                null, outcome == LogicalQuantumServiceResponse.Outcome.BUSINESS_FAILED
                        ? new LogicalQuantumServiceResponse.FailureReason(
                                LogicalQuantumServiceResponse.FailureCode.LOWER_BOUND, null, "below") : null);
    }

    private static QServiceQuantumSettlement service(
            Function<LogicalQuantumServiceCommand, LogicalQuantumServiceResponse> client) {
        return new QServiceQuantumSettlement("globalQuantum", client::apply, null);
    }

    @Test
    public void mapsOneToOneToTheWireCommand() {
        List<LogicalQuantumServiceCommand> sent = new ArrayList<>();
        QServiceQuantumSettlement service = service(wire -> {
            sent.add(wire);
            return wire(wire, LogicalQuantumServiceResponse.Outcome.SUCCESS);
        });

        SettlementResponse settled = service.settle(command("r1", null));
        SettlementResponse reversed = service.reverse(command("r1:reversal", "r1"));

        LogicalQuantumServiceCommand forward = sent.get(0);
        assertEquals(LogicalQuantumServiceCommand.Operation.ENTANGLE, forward.operation());
        assertEquals("commerce", forward.namespace());
        assertEquals("accounting", forward.resourceName());
        assertEquals("r1", forward.requestKey());
        assertEquals(null, forward.originalRequestKey());
        assertEquals(LogicalQuantumServiceCommand.AtomicGroupShape.NONE, forward.atomicGroupShape());
        assertEquals(new LogicalQuantumServiceCommand.Mutation(
                new LogicalQuantumServiceCommand.LogicalKey("commerce", "Account.balance", "A"),
                new LogicalQuantumServiceCommand.Definition("LONG", "0", "1000000", 2), "-10.5"),
                forward.mutations().get(0));
        assertEquals(LogicalQuantumServiceCommand.Operation.REVERSE_ENTANGLE, sent.get(1).operation());
        assertEquals("r1", sent.get(1).originalRequestKey());
        assertEquals(Operation.SETTLE, settled.operation());
        assertEquals(Operation.REVERSE, reversed.operation());
        assertEquals(Optional.of("r1"), reversed.originalRequestKey());
        assertEquals(Optional.of("42"), settled.receipt());
        assertEquals(Outcome.SUCCESS, settled.outcome());
    }

    @Test
    public void mapsAllShapes() {
        List<QuantumMutation> mutations = List.of(
                new QuantumMutation(new LogicalQuantumKey("commerce", "A.x", "1"), DEFINITION, "-5"),
                new QuantumMutation(new LogicalQuantumKey("commerce", "B.x", "1"), DEFINITION, "3"),
                new QuantumMutation(new LogicalQuantumKey("commerce", "C.x", "1"), DEFINITION, "2"));
        List<LogicalQuantumServiceCommand> sent = new ArrayList<>();
        service(wire -> {
            sent.add(wire);
            return wire(wire, LogicalQuantumServiceResponse.Outcome.SUCCESS);
        }).settle(new SettlementCommand(new SettlementKey("commerce", "r"), Optional.empty(), "res",
                SettlementCommand.AtomicGroupShape.ONE_TO_MANY, mutations));
        assertEquals(LogicalQuantumServiceCommand.AtomicGroupShape.ONE_CONTRIBUTOR_MANY_RECIPIENTS,
                sent.get(0).atomicGroupShape());
    }

    @Test
    public void carriesAllFourOutcomesWithoutCollapsingThem() {
        for (LogicalQuantumServiceResponse.Outcome outcome : LogicalQuantumServiceResponse.Outcome.values()) {
            SettlementResponse response = service(wire -> wire(wire, outcome)).settle(command("r1", null));
            assertEquals(Outcome.valueOf(outcome.name()), response.outcome());
        }
    }

    @Test
    public void aClientFailureIsUnknownNotAnException() {
        SettlementResponse response = service(wire -> {
            throw new IllegalStateException("timeout");
        }).settle(command("r1", null));
        assertEquals(Outcome.UNKNOWN, response.outcome());
        assertEquals("TRANSPORT_FAILURE", response.failureReason().orElseThrow().code());
        assertEquals("timeout", response.failureReason().orElseThrow().message());
        assertEquals(command("r1", null).key(), response.key());
    }

    @Test
    public void rejectsAResponseWhoseIdentityDoesNotEchoTheRequest() {
        assertThrows(IllegalStateException.class, () -> service(wire -> new LogicalQuantumServiceResponse(
                wire.namespace(), "other", null, new LogicalQuantumServiceResponse.BoundaryResponse(
                        42, null, wire.operation(), LogicalQuantumServiceResponse.Outcome.SUCCESS), null, null))
                .settle(command("r1", null)));
        assertThrows(IllegalStateException.class, () -> service(wire -> new LogicalQuantumServiceResponse(
                wire.namespace(), wire.requestKey(), null, new LogicalQuantumServiceResponse.BoundaryResponse(
                        42, null, LogicalQuantumServiceCommand.Operation.REVERSE_ENTANGLE,
                        LogicalQuantumServiceResponse.Outcome.SUCCESS), null, null))
                .settle(command("r1", null)));
    }

    @Test
    public void guardsForwardAndReversalCommands() {
        QServiceQuantumSettlement service = service(wire -> wire(wire, LogicalQuantumServiceResponse.Outcome.SUCCESS));
        assertThrows(IllegalArgumentException.class, () -> service.settle(command("r1:reversal", "r1")));
        assertThrows(IllegalArgumentException.class, () -> service.reverse(command("r1", null)));
        assertThrows(UnsupportedOperationException.class, () -> service.query(new SettlementKey("commerce", "r1")));
        assertThrows(UnsupportedOperationException.class, () -> service.reserve(command("r1", null)));
    }

    @Test
    public void provisionsThroughTheProvisioningClientAndChecksTheEcho() {
        List<LogicalQuantumProvisioningRequest> requests = new ArrayList<>();
        QServiceQuantumSettlement service = new QServiceQuantumSettlement("globalQuantum",
                wire -> wire(wire, LogicalQuantumServiceResponse.Outcome.SUCCESS), request -> {
                    requests.add(request);
                    return new LogicalQuantumProvisioningResponse(request.logicalKey(), 7, request.definition(),
                            request.expectedPhysicalInitialValue());
                });
        assertTrue(service.capabilities().contains(Capability.PROVISIONING));
        assertEquals(java.util.Set.of(Capability.ONE_PHASE), service(wire -> null).capabilities());

        ProvisionResponse response = service.provision(new ProvisionCommand(
                new LogicalQuantumKey("commerce", "Account.balance", "A"), DEFINITION, "12.34"));

        assertEquals("12.34", requests.get(0).initialValue());
        assertEquals("1234", response.physicalInitialValue());
        assertEquals(Outcome.SUCCESS, response.outcome());

        QServiceQuantumSettlement drifting = new QServiceQuantumSettlement("globalQuantum",
                wire -> wire(wire, LogicalQuantumServiceResponse.Outcome.SUCCESS),
                request -> new LogicalQuantumProvisioningResponse(request.logicalKey(), 7, request.definition(), "1"));
        assertThrows(IllegalStateException.class, () -> drifting.provision(new ProvisionCommand(
                new LogicalQuantumKey("commerce", "Account.balance", "A"), DEFINITION, "12.34")));
        assertThrows(IllegalStateException.class, () -> service(wire -> null).provision(new ProvisionCommand(
                new LogicalQuantumKey("commerce", "Account.balance", "A"), DEFINITION, "12.34")));
    }
}
