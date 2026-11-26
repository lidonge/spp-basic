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
    private volatile Set<Capability> advertised;

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

    /**
     * Asks the service once ({@code GET /capabilities}), accepts only ONE_PHASE and PROVISIONING
     * and fails fast unless the contract is {@code spp-qservice-logical/1}. PROVISIONING also
     * needs a provisioning client. A client that does not advertise keeps the local default.
     */
    @Override
    public Set<Capability> capabilities() {
        Set<Capability> known = advertised;
        if (known == null) {
            synchronized (this) {
                if (advertised == null) {
                    advertised = discover();
                }
                known = advertised;
            }
        }
        return known;
    }

    private Set<Capability> discover() {
        LogicalQuantumCapabilities answer = client.capabilities();
        Set<Capability> capabilities = EnumSet.of(Capability.ONE_PHASE);
        if (answer != null) {
            if (!LogicalQuantumCapabilities.CONTRACT.equals(answer.contract())) {
                throw new IllegalStateException("Quantum service " + name + " speaks contract "
                        + answer.contract() + ", expected " + LogicalQuantumCapabilities.CONTRACT + ".");
            }
            Set<String> names = answer.capabilities() == null ? Set.of() : new java.util.HashSet<>(answer.capabilities());
            for (String capability : names) {
                if (!capability.equals("ONE_PHASE") && !capability.equals("PROVISIONING")) {
                    throw new IllegalStateException("Quantum service " + name
                            + " advertises unsupported capability " + capability + ".");
                }
            }
            if (!names.contains("ONE_PHASE")) {
                throw new IllegalStateException("Quantum service " + name + " does not advertise ONE_PHASE.");
            }
            if (names.contains("PROVISIONING") && provisioningClient != null) {
                capabilities.add(Capability.PROVISIONING);
            }
        } else if (provisioningClient != null) {
            capabilities.add(Capability.PROVISIONING);
        }
        return Set.copyOf(capabilities);
    }

    @Override
    public SettlementResponse settle(SettlementCommand command) {
        return execute(LogicalQuantumServiceCommand.Operation.ENTANGLE, command.requireForward());
    }

    @Override
    public SettlementResponse reverse(SettlementCommand reversal) {
        return execute(LogicalQuantumServiceCommand.Operation.REVERSE_ENTANGLE, reversal.requireReversal());
    }

    /**
     * Read-only lookup under {@code key}. SUCCESS and BUSINESS_FAILED are the durable result,
     * UNKNOWN means a fact exists without a determinate result, NOT_RECEIVED means a same-key
     * retry is allowed. A transport failure is UNKNOWN.
     */
    @Override
    public SettlementResponse query(SettlementKey key) {
        LogicalQuantumServiceResponse response;
        try {
            response = client.query(key.namespace(), key.requestKey());
            response.requireIdentity(key.namespace(), key.requestKey());
        } catch (UnsupportedOperationException unsupported) {
            throw unsupported;
        } catch (RuntimeException failure) {
            return new SettlementResponse(key, Optional.empty(), Operation.QUERY, Outcome.UNKNOWN,
                    Optional.empty(), Optional.of(transportFailure(failure)));
        }
        return new SettlementResponse(key, Optional.ofNullable(response.originalRequestKey()), Operation.QUERY,
                Outcome.valueOf(response.outcome().name()), Optional.ofNullable(response.receipt()),
                reason(response));
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
                    Outcome.UNKNOWN, Optional.empty(), Optional.of(transportFailure(failure)));
        }
        response.requireIdentity(wire);
        LogicalQuantumServiceResponse.BoundaryResponse boundary = response.boundaryResponse();
        return new SettlementResponse(
                new SettlementKey(response.namespace(), response.requestKey()),
                Optional.ofNullable(response.originalRequestKey()), operation(boundary.operation()),
                Outcome.valueOf(boundary.status().name()),
                Optional.ofNullable(response.receipt()), reason(response));
    }

    private static SettlementResponse.FailureReason transportFailure(RuntimeException failure) {
        return SettlementResponse.FailureReason.of("TRANSPORT_FAILURE", failure.getMessage());
    }

    private static Optional<SettlementResponse.FailureReason> reason(LogicalQuantumServiceResponse response) {
        LogicalQuantumServiceResponse.FailureReason reason = response.failureReason();
        if (reason == null) return Optional.empty();
        Optional<LogicalQuantumKey> quantum = reason.quantum() == null ? Optional.empty()
                : Optional.of(new LogicalQuantumKey(reason.quantum().namespace(),
                        reason.quantum().attributeId(), reason.quantum().objectId()));
        return Optional.of(new SettlementResponse.FailureReason(reason.code().name(), quantum, reason.message()));
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
