package com.servpp.service.transaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.servpp.service.Service;
import com.servpp.service.quantum.settlement.ProvisionCommand;
import com.servpp.service.quantum.settlement.ProvisionResponse;
import com.servpp.service.quantum.settlement.QuantumDefinition;
import com.servpp.service.quantum.settlement.QuantumSettlementService;
import com.servpp.service.quantum.settlement.SettlementCommand;
import com.servpp.service.quantum.settlement.SettlementKey;
import com.servpp.service.quantum.settlement.SettlementResponse;
import com.servpp.service.quantum.settlement.SettlementResponse.Outcome;
import com.servpp.service.scenario.IExecutor;
import com.servpp.service.scenario.ITransactionGroup;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * A managed source transaction under UNIFIED_SERVICE settles its quantum boundary through the
 * settlement service, before the ordinary writes, and reverses it when a later stage fails.
 */
public class SourceTransactionQuantumServiceTest {
    public static class Stock {
        private int available = 10, reserved;
        public Integer getAvailable() { return available; }
        public void setAvailable(Integer value) { available = value; }
        public Integer getReserved() { return reserved; }
        public void setReserved(Integer value) { reserved = value; }
    }

    private record StageService(String name, TransactionMode mode)
            implements Service, TransactionalService {
        public String getName() { return name; }
        public Object execute(Object... args) { return null; }
        public TransactionMode getTransactionMode() { return mode; }
        public String getTransactionResourceId() { return mode == TransactionMode.QUANTUM ? "ledger" : "records"; }

        public TransactionParticipant getTransactionParticipant() {
            return mode == TransactionMode.QUANTUM
                    ? new QuantumConservationParticipant("ledger")
                            .conserveArgument(0, "inventory", "Stock.available", "available")
                            .conserveArgument(0, "inventory", "Stock.reserved", "reserved")
                    : TransactionParticipant.noop("records");
        }
    }

    private final List<String> events = new ArrayList<>();
    private final List<SettlementCommand> settled = new ArrayList<>();
    private final List<SettlementCommand> reversed = new ArrayList<>();
    private Outcome settleOutcome = Outcome.SUCCESS;
    private DefaultTransactionManager manager;
    private QuantumAccessAdapter previousAdapter;

    @Before
    public void install() {
        manager = DefaultTransactionManager.getInstance();
        previousAdapter = manager.getQuantumAccessAdapter();
        QuantumDefinition definition = new QuantumDefinition(QuantumDefinition.NumberType.LONG, "0", "1000", 0);
        QuantumSettlementService service = new QuantumSettlementService() {
            public String name() { return "ledger"; }
            public Set<Capability> capabilities() { return EnumSet.of(Capability.ONE_PHASE); }

            public SettlementResponse settle(SettlementCommand command) {
                events.add("settle");
                settled.add(command);
                return answer(command, SettlementResponse.Operation.SETTLE, settleOutcome);
            }

            public SettlementResponse reverse(SettlementCommand command) {
                events.add("reverse");
                reversed.add(command);
                return answer(command, SettlementResponse.Operation.REVERSE, Outcome.SUCCESS);
            }

            public SettlementResponse query(SettlementKey key) { throw new UnsupportedOperationException(); }
            public ProvisionResponse provision(ProvisionCommand command) { throw new UnsupportedOperationException(); }

            private SettlementResponse answer(SettlementCommand command, SettlementResponse.Operation operation,
                                              Outcome outcome) {
                return new SettlementResponse(command.key(), command.originalRequestKey(), operation, outcome,
                        Optional.of("1"), outcome == Outcome.BUSINESS_FAILED
                                ? Optional.of(SettlementResponse.FailureReason.of("LOWER_BOUND", "below"))
                                : Optional.empty());
            }
        };
        manager.setQuantumAccessAdapter(new SettlementQuantumAccessAdapter(service, "ns",
                Map.of("Stock.available", definition, "Stock.reserved", definition)));
    }

    @After
    public void restore() {
        manager.setQuantumAccessAdapter(previousAdapter);
        DefaultTransactionManager.clearLastContext();
    }

    private ITransactionGroup group(boolean ordinaryFails) {
        Stock stock = new Stock();
        IExecutor quantum = new IExecutor() {
            final StageService service = new StageService("reserveStock", TransactionMode.QUANTUM);
            public Service getService() { return service; }
            public Object[] getArguments() { return new Object[] {stock}; }
            public boolean isQuantumUpdate() { return true; }
            public void execute() { stock.available -= 2; stock.reserved += 2; }
        };
        IExecutor ordinary = new IExecutor() {
            final StageService service = new StageService("saveReservation", TransactionMode.AT);
            public Service getService() { return service; }
            public void execute() {
                events.add("ordinary");
                if (ordinaryFails) throw new IllegalStateException("RESERVATION_SAVE_FAILED");
            }
        };
        return new ITransactionGroup() {
            public boolean usesSourceTransactionPlan() { return true; }
            public TransactionMode getDeclaredTransactionMode() { return TransactionMode.SAGA; }
            public IExecutor[] getServices() { return new IExecutor[] {quantum, ordinary}; }
        };
    }

    @Test
    public void quantumSettlesThroughTheServiceBeforeTheOrdinaryWrites() {
        BusinessTraceContext.execute("TRACE-QS-1", () -> {
            group(false).execute();
            return null;
        });
        assertEquals(List.of("settle", "ordinary"), events);
        assertEquals(SettlementCommand.AtomicGroupShape.NONE, settled.get(0).shape());
        assertEquals("TRACE-QS-1", settled.get(0).key().requestKey());
        assertEquals(List.of("Stock.available", "Stock.reserved"),
                settled.get(0).mutations().stream().map(m -> m.key().attributeId()).toList());
        assertEquals(List.of("-2", "2"), settled.get(0).mutations().stream().map(m -> m.delta()).toList());
    }

    @Test
    public void aLaterStageFailureReversesTheSettledBoundaryThroughTheService() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BusinessTraceContext.execute("TRACE-QS-2", () -> {
                    group(true).execute();
                    return null;
                }));
        assertEquals("RESERVATION_SAVE_FAILED", failure.getMessage());
        assertEquals(List.of("settle", "ordinary", "reverse"), events);
        assertEquals("TRACE-QS-2:reversal", reversed.get(0).key().requestKey());
        assertEquals(Optional.of("TRACE-QS-2"), reversed.get(0).originalRequestKey());
        assertEquals(List.of("-2", "2"), reversed.get(0).mutations().stream().map(m -> m.delta()).toList());
    }

    @Test
    public void aBoundRejectionFromTheServicePrecedesTheOrdinaryWrites() {
        settleOutcome = Outcome.BUSINESS_FAILED;
        QuantumServiceOutcomeException failure = assertThrows(QuantumServiceOutcomeException.class,
                () -> BusinessTraceContext.execute("TRACE-QS-3", () -> {
                    group(false).execute();
                    return null;
                }));
        assertEquals(LogicalQuantumServiceResponse.Outcome.BUSINESS_FAILED, failure.getOutcome());
        assertEquals(List.of("settle"), events);
    }
}
