package com.servpp.service.transaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.servpp.service.quantum.settlement.ProvisionCommand;
import com.servpp.service.quantum.settlement.ProvisionResponse;
import com.servpp.service.quantum.settlement.QuantumDefinition;
import com.servpp.service.quantum.settlement.QuantumSettlementService;
import com.servpp.service.quantum.settlement.SettlementCommand;
import com.servpp.service.quantum.settlement.SettlementCommand.AtomicGroupShape;
import com.servpp.service.quantum.settlement.SettlementKey;
import com.servpp.service.quantum.settlement.SettlementResponse;
import com.servpp.service.quantum.settlement.SettlementResponse.Operation;
import com.servpp.service.quantum.settlement.SettlementResponse.Outcome;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.Test;

public class SettlementQuantumAccessAdapterTest {
    private static final QuantumDefinition DEFINITION =
            new QuantumDefinition(QuantumDefinition.NumberType.LONG, "0", "1000000", 0);
    private static final Map<String, QuantumDefinition> DEFINITIONS = Map.of(
            "Account.balance", DEFINITION, "Order.balance", DEFINITION, "Inventory.quantity", DEFINITION);

    /** Records every command and answers with the scripted outcomes (the last one repeats). */
    private static class FakeService implements QuantumSettlementService {
        final List<SettlementCommand> commands = new ArrayList<>();
        final List<Outcome> outcomes;
        Function<SettlementCommand, SettlementKey> echoKey = SettlementCommand::key;

        FakeService(Outcome... outcomes) {
            this.outcomes = List.of(outcomes);
        }

        public String name() { return "fake"; }
        public Set<Capability> capabilities() { return EnumSet.of(Capability.ONE_PHASE, Capability.PROVISIONING); }

        public SettlementResponse settle(SettlementCommand command) { return answer(command, Operation.SETTLE); }
        public SettlementResponse reverse(SettlementCommand command) { return answer(command, Operation.REVERSE); }
        public SettlementResponse query(SettlementKey key) { throw new UnsupportedOperationException(); }

        public ProvisionResponse provision(ProvisionCommand command) {
            return new ProvisionResponse(command.key(), command.definition(),
                    command.expectedPhysicalInitialValue(), Outcome.SUCCESS);
        }

        private SettlementResponse answer(SettlementCommand command, Operation operation) {
            commands.add(command);
            Outcome outcome = outcomes.get(Math.min(commands.size(), outcomes.size()) - 1);
            return new SettlementResponse(echoKey.apply(command), command.originalRequestKey(), operation, outcome,
                    Optional.of("receipt"), outcome == Outcome.BUSINESS_FAILED ? Optional.of("bound") : Optional.empty());
        }
    }

    private static QuantumEntanglementBoundary boundary(QuantumConservationRecord... records) {
        QuantumEntanglementBoundary boundary = new QuantumEntanglementBoundary("accounting", List.of("accounting"));
        for (QuantumConservationRecord record : records) boundary.addRecord(record);
        return boundary;
    }

    private static QuantumConservationRecord record(String attribute, String object, String delta) {
        BigDecimal change = new BigDecimal(delta);
        BigDecimal before = new BigDecimal("100");
        return new QuantumConservationRecord("g", attribute, object, before, change, before.add(change));
    }

    private static TransactionContext context() {
        return BusinessTraceContext.execute("TRACE-1",
                () -> new TransactionContext(Map.of("globalQuantum", TransactionMode.QUANTUM)));
    }

    private static SettlementQuantumAccessAdapter adapter(QuantumSettlementService service, int retries) {
        return new SettlementQuantumAccessAdapter(service, "commerce", DEFINITIONS, retries);
    }

    @Test
    public void netsRecordsDropsZeroNetAndDerivesTheShape() {
        FakeService service = new FakeService(Outcome.SUCCESS);
        QuantumEntanglementBoundary boundary = boundary(
                record("Account.balance", "A", "-10"), record("Order.balance", "B", "15"),
                record("Order.balance", "B", "-5"), record("Inventory.quantity", "C", "0"),
                record("Inventory.quantity", "D", "3"), record("Inventory.quantity", "D", "-3"));

        adapter(service, 0).commit(context(), boundary);

        SettlementCommand command = service.commands.get(0);
        assertEquals(new SettlementKey("commerce", "TRACE-1"), command.key());
        assertEquals(Optional.empty(), command.originalRequestKey());
        assertEquals("accounting", command.resourceName());
        assertEquals(AtomicGroupShape.NONE, command.shape());
        assertEquals(2, command.mutations().size());
        assertEquals("-10", command.mutations().get(0).delta());
        assertEquals("10", command.mutations().get(1).delta());
        assertEquals(DEFINITION, command.mutations().get(0).definition());
        assertEquals(QuantumEntanglementBoundary.Status.COMMITTED, boundary.getStatus());
    }

    @Test
    public void derivesTheManyShapes() {
        FakeService split = new FakeService(Outcome.SUCCESS);
        adapter(split, 0).commit(context(), boundary(record("Account.balance", "A", "-10"),
                record("Order.balance", "B", "6"), record("Inventory.quantity", "C", "4")));
        assertEquals(AtomicGroupShape.ONE_TO_MANY, split.commands.get(0).shape());

        FakeService merge = new FakeService(Outcome.SUCCESS);
        adapter(merge, 0).commit(context(), boundary(record("Account.balance", "A", "-6"),
                record("Account.balance", "B", "-4"), record("Order.balance", "C", "10")));
        assertEquals(AtomicGroupShape.MANY_TO_ONE, merge.commands.get(0).shape());

        FakeService netted = new FakeService(Outcome.SUCCESS);
        adapter(netted, 0).commit(context(), boundary(record("Account.balance", "A", "-6"),
                record("Account.balance", "B", "-4"), record("Order.balance", "C", "9"),
                record("Order.balance", "D", "1")));
        assertEquals(AtomicGroupShape.NETTED, netted.commands.get(0).shape());
    }

    @Test
    public void reversalUsesANewKeyAndTheOriginalKeyWithForwardDeltas() {
        FakeService service = new FakeService(Outcome.SUCCESS);
        QuantumEntanglementBoundary boundary = boundary(
                record("Account.balance", "A", "-10"), record("Order.balance", "B", "10"));

        adapter(service, 0).reverse(context(), boundary);

        SettlementCommand command = service.commands.get(0);
        assertEquals("TRACE-1:reversal", command.key().requestKey());
        assertEquals(Optional.of("TRACE-1"), command.originalRequestKey());
        assertEquals("-10", command.mutations().get(0).delta());
        assertEquals(QuantumEntanglementBoundary.Status.ROLLED_BACK, boundary.getStatus());
    }

    @Test
    public void nothingToSettleSucceedsWithoutCallingTheService() {
        FakeService service = new FakeService(Outcome.UNKNOWN);
        QuantumEntanglementBoundary boundary = boundary(
                record("Account.balance", "A", "5"), record("Account.balance", "A", "-5"));
        adapter(service, 0).commit(context(), boundary);
        assertTrue(service.commands.isEmpty());
        assertEquals(QuantumEntanglementBoundary.Status.COMMITTED, boundary.getStatus());
    }

    @Test
    public void rejectsUnbalancedSingleOrUndefinedBoundaries() {
        FakeService service = new FakeService(Outcome.SUCCESS);
        assertThrows(IllegalStateException.class, () -> adapter(service, 0).commit(context(),
                boundary(record("Account.balance", "A", "-10"), record("Order.balance", "B", "9"))));
        assertThrows(IllegalStateException.class, () -> adapter(service, 0).commit(context(),
                boundary(record("Account.balance", "A", "-10"))));
        assertThrows(IllegalStateException.class, () -> adapter(service, 0).commit(context(),
                boundary(record("Unknown.balance", "A", "-10"), record("Order.balance", "B", "10"))));
        assertTrue(service.commands.isEmpty());
    }

    @Test
    public void nonSuccessOutcomesAreRecordedRaisedAndLeaveTheBoundaryOpen() {
        for (Outcome outcome : List.of(Outcome.BUSINESS_FAILED, Outcome.NOT_RECEIVED, Outcome.UNKNOWN)) {
            FakeService service = new FakeService(outcome);
            QuantumEntanglementBoundary boundary = boundary(
                    record("Account.balance", "A", "-10"), record("Order.balance", "B", "10"));
            SettlementQuantumAccessAdapter adapter = adapter(service, 0);

            QuantumServiceOutcomeException failure = assertThrows(QuantumServiceOutcomeException.class,
                    () -> adapter.commit(context(), boundary));

            assertEquals(LogicalQuantumServiceResponse.Outcome.valueOf(outcome.name()), failure.getOutcome());
            assertEquals(QuantumEntanglementBoundary.Status.OPEN, boundary.getStatus());
            assertEquals(failure.getOutcome(), boundary.getQuantumServiceOutcome());
            adapter.rollback(context(), boundary);
            assertEquals(QuantumEntanglementBoundary.Status.OPEN, boundary.getStatus());
        }
    }

    @Test
    public void businessFailureCarriesTheReason() {
        QuantumServiceOutcomeException failure = assertThrows(QuantumServiceOutcomeException.class,
                () -> adapter(new FakeService(Outcome.BUSINESS_FAILED), 0).commit(context(),
                        boundary(record("Account.balance", "A", "-10"), record("Order.balance", "B", "10"))));
        assertTrue(failure.getMessage(), failure.getMessage().contains("bound"));
    }

    @Test
    public void notReceivedRetriesUnderTheSameKeyThenSucceeds() {
        FakeService service = new FakeService(Outcome.NOT_RECEIVED, Outcome.NOT_RECEIVED, Outcome.SUCCESS);
        QuantumEntanglementBoundary boundary = boundary(
                record("Account.balance", "A", "-10"), record("Order.balance", "B", "10"));

        adapter(service, 2).commit(context(), boundary);

        assertEquals(3, service.commands.size());
        assertSame(service.commands.get(0), service.commands.get(2));
        assertEquals(QuantumEntanglementBoundary.Status.COMMITTED, boundary.getStatus());
    }

    @Test
    public void notReceivedWithoutRetriesKeepsTheExistingBehaviour() {
        FakeService service = new FakeService(Outcome.NOT_RECEIVED);
        assertThrows(QuantumServiceOutcomeException.class, () -> adapter(service, 0).commit(context(),
                boundary(record("Account.balance", "A", "-10"), record("Order.balance", "B", "10"))));
        assertEquals(1, service.commands.size());
        FakeService exhausted = new FakeService(Outcome.NOT_RECEIVED);
        assertThrows(QuantumServiceOutcomeException.class, () -> adapter(exhausted, 1).commit(context(),
                boundary(record("Account.balance", "A", "-10"), record("Order.balance", "B", "10"))));
        assertEquals(2, exhausted.commands.size());
    }

    @Test
    public void serviceFailureAndIdentityMismatchAreUnknown() {
        QuantumSettlementService failing = new FakeService(Outcome.SUCCESS) {
            public SettlementResponse settle(SettlementCommand command) { throw new IllegalStateException("down"); }
        };
        QuantumEntanglementBoundary boundary = boundary(
                record("Account.balance", "A", "-10"), record("Order.balance", "B", "10"));
        QuantumServiceOutcomeException failure = assertThrows(QuantumServiceOutcomeException.class,
                () -> adapter(failing, 3).commit(context(), boundary));
        assertEquals(LogicalQuantumServiceResponse.Outcome.UNKNOWN, failure.getOutcome());
        assertEquals(LogicalQuantumServiceResponse.Outcome.UNKNOWN, boundary.getQuantumServiceOutcome());

        FakeService mismatch = new FakeService(Outcome.SUCCESS);
        mismatch.echoKey = command -> new SettlementKey("commerce", "OTHER");
        QuantumEntanglementBoundary other = boundary(
                record("Account.balance", "A", "-10"), record("Order.balance", "B", "10"));
        assertEquals(LogicalQuantumServiceResponse.Outcome.UNKNOWN, assertThrows(
                QuantumServiceOutcomeException.class, () -> adapter(mismatch, 0).commit(context(), other)).getOutcome());
        assertEquals(QuantumEntanglementBoundary.Status.OPEN, other.getStatus());
    }

    @Test
    public void provisionsOnlyDefinedAttributesAfterValidatingTheWholeRequest() {
        FakeService service = new FakeService(Outcome.SUCCESS);
        SettlementQuantumAccessAdapter adapter = adapter(service, 0);
        List<ProvisionResponse> responses = adapter.provisionObject("OBJ-1",
                Map.of("Account.balance", new BigDecimal("12.0")));
        assertEquals(1, responses.size());
        assertEquals("12", responses.get(0).physicalInitialValue());
        assertThrows(IllegalArgumentException.class, () -> adapter.provisionObject("OBJ-2",
                Map.of("Account.balance", "1", "Unknown.balance", "1")));
    }

    @Test
    public void exposesTheServiceCapabilities() {
        assertFalse(adapter(new FakeService(Outcome.SUCCESS), 0).supportsInvocationBounds());
        assertTrue(SettlementQuantumAccessAdapter.journal(new JdbcQuantumJournalStore(source()))
                .supportsInvocationBounds());
        assertFalse(SettlementQuantumAccessAdapter.journal(new InMemoryQuantumJournalStore())
                .supportsInvocationBounds());
    }

    @Test
    public void journalLedgerPathKeepsTheBoundRejectionAndIdempotentRetry() {
        JdbcQuantumJournalStore store = new JdbcQuantumJournalStore(source());
        SettlementQuantumAccessAdapter adapter = SettlementQuantumAccessAdapter.journal(store);
        TransactionContext context = new TransactionContext(
                Map.of("ledger", TransactionMode.LOCAL), store);
        QuantumEntanglementBoundary boundary = context.getQuantumEntanglementBoundary();
        boundary.addRecord(new QuantumConservationRecord("g", "Account.balance", "A",
                BigDecimal.ZERO, new BigDecimal("-5"), new BigDecimal("-5"), BigDecimal.ZERO, null));
        boundary.addRecord(new QuantumConservationRecord("g", "Order.balance", "B",
                BigDecimal.ZERO, new BigDecimal("5"), new BigDecimal("5")));

        assertThrows(QuantumBoundRejection.class, () -> adapter.executeInAccessScope(
                () -> adapter.commit(context, boundary)));
        assertTrue(store.findByTransactionId(context.getId()).isEmpty());
    }

    private static javax.sql.DataSource source() {
        org.h2.jdbcx.JdbcDataSource source = new org.h2.jdbcx.JdbcDataSource();
        source.setURL("jdbc:h2:mem:bridge_" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        return source;
    }
}
