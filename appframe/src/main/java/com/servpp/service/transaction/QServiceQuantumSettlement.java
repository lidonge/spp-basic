package com.servpp.service.transaction;

import com.servpp.service.quantum.settlement.LogicalQuantumKey;
import com.servpp.service.quantum.settlement.ProvisionCommand;
import com.servpp.service.quantum.settlement.ProvisionResponse;
import com.servpp.service.quantum.settlement.QuantumDefinition;
import com.servpp.service.quantum.settlement.QuantumMutation;
import com.servpp.service.quantum.settlement.QuantumSettlementService;
import com.servpp.service.quantum.settlement.SettlementCommand;
import com.servpp.service.quantum.settlement.SettlementKey;
import com.servpp.service.quantum.settlement.SettlementResponse;
import com.servpp.service.quantum.settlement.SettlementResponse.Operation;
import com.servpp.service.quantum.settlement.SettlementResponse.Outcome;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * QService's logical protocol behind the unified settlement interface. The mapping to the
 * wire DTOs ({@link LogicalQuantumServiceCommand}, {@link LogicalQuantumProvisioningRequest} and
 * their responses) is one to one, and the four outcomes are carried over by name.
 */
public class QServiceQuantumSettlement implements QuantumSettlementService {
    private final String name;
    private final LogicalQuantumServiceClient client;
    private final LogicalQuantumProvisioningClient provisioningClient;

    public QServiceQuantumSettlement(String name, LogicalQuantumServiceClient client,
                                     LogicalQuantumProvisioningClient provisioningClient) {
        this.name = Objects.requireNonNull(name, "name");
        this.client = Objects.requireNonNull(client, "client");
        this.provisioningClient = provisioningClient;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Set<Capability> capabilities() {
        Set<Capability> capabilities = EnumSet.of(Capability.ONE_PHASE);
        if (provisioningClient != null) capabilities.add(Capability.PROVISIONING);
        return capabilities;
    }

    @Override
    public SettlementResponse settle(SettlementCommand command) {
        return execute(LogicalQuantumServiceCommand.Operation.ENTANGLE, command.requireForward());
    }

    @Override
    public SettlementResponse reverse(SettlementCommand reversal) {
        return execute(LogicalQuantumServiceCommand.Operation.REVERSE_ENTANGLE, reversal.requireReversal());
    }

    /** QService v1 has no query endpoint: UNKNOWN is recovered by replaying the same key. */
    @Override
    public SettlementResponse query(SettlementKey key) {
        throw new UnsupportedOperationException("QService logical protocol has no query endpoint.");
    }

    @Override
    public ProvisionResponse provision(ProvisionCommand command) {
        if (provisioningClient == null) {
            throw new IllegalStateException("Quantum service " + name + " requires a provisioning client.");
        }
        LogicalQuantumProvisioningRequest request = new LogicalQuantumProvisioningRequest(
                wire(command.key()), wire(command.definition()), command.initialValue());
        LogicalQuantumProvisioningResponse response = provisioningClient.provision(request);
        response.requireIdentity(request);
        return new ProvisionResponse(command.key(), command.definition(),
                response.physicalInitialValue(), Outcome.SUCCESS);
    }

    private SettlementResponse execute(LogicalQuantumServiceCommand.Operation operation,
                                       SettlementCommand command) {
        LogicalQuantumServiceCommand wire = new LogicalQuantumServiceCommand(
                operation, command.key().namespace(), command.resourceName(), command.key().requestKey(),
                command.originalRequestKey().orElse(null), wire(command.shape()),
                command.mutations().stream().map(QServiceQuantumSettlement::wire).toList());
        LogicalQuantumServiceResponse response;
        try {
            response = client.execute(wire);
        } catch (RuntimeException failure) {
            return new SettlementResponse(command.key(), command.originalRequestKey(), operation(operation),
                    Outcome.UNKNOWN, Optional.empty(), Optional.ofNullable(failure.getMessage()));
        }
        response.requireIdentity(wire);
        LogicalQuantumServiceResponse.BoundaryResponse boundary = response.boundaryResponse();
        return new SettlementResponse(
                new SettlementKey(response.namespace(), response.requestKey()),
                Optional.ofNullable(response.originalRequestKey()), operation(boundary.operation()),
                Outcome.valueOf(boundary.status().name()),
                Optional.of(Long.toString(boundary.conservationID())), Optional.empty());
    }

    private static Operation operation(LogicalQuantumServiceCommand.Operation operation) {
        return operation == LogicalQuantumServiceCommand.Operation.ENTANGLE ? Operation.SETTLE : Operation.REVERSE;
    }

    private static LogicalQuantumServiceCommand.AtomicGroupShape wire(SettlementCommand.AtomicGroupShape shape) {
        return switch (shape) {
            case NONE -> LogicalQuantumServiceCommand.AtomicGroupShape.NONE;
            case ONE_TO_MANY -> LogicalQuantumServiceCommand.AtomicGroupShape.ONE_CONTRIBUTOR_MANY_RECIPIENTS;
            case MANY_TO_ONE -> LogicalQuantumServiceCommand.AtomicGroupShape.MANY_CONTRIBUTORS_ONE_RECIPIENT;
            case NETTED -> LogicalQuantumServiceCommand.AtomicGroupShape.NETTED_GROUP;
        };
    }

    private static LogicalQuantumServiceCommand.Mutation wire(QuantumMutation mutation) {
        return new LogicalQuantumServiceCommand.Mutation(
                wire(mutation.key()), wire(mutation.definition()), mutation.delta());
    }

    private static LogicalQuantumServiceCommand.LogicalKey wire(LogicalQuantumKey key) {
        return new LogicalQuantumServiceCommand.LogicalKey(key.namespace(), key.attributeId(), key.objectId());
    }

    private static LogicalQuantumServiceCommand.Definition wire(QuantumDefinition definition) {
        return new LogicalQuantumServiceCommand.Definition(definition.numberType().name(),
                definition.minValue(), definition.maxValue(), definition.scale());
    }
}
