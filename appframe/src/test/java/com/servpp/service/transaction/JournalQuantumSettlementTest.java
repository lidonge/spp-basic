package com.servpp.service.transaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import com.servpp.service.quantum.settlement.SettlementResponse.Outcome;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Test;

public class JournalQuantumSettlementTest {
    private static final QuantumDefinition DEFINITION =
            new QuantumDefinition(QuantumDefinition.NumberType.LONG, "-1000000", "1000000", 0);

    private JdbcQuantumJournalStore store;
    private JournalQuantumSettlement journal;

    @Before
    public void setUp() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:settle_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        store = new JdbcQuantumJournalStore(source);
        journal = new JournalQuantumSettlement("localQuantum", store);
        provision("A", "100");
        provision("B", "5");
    }

    private void provision(String object, String value) {
        ProvisionResponse response = journal.provision(
                new ProvisionCommand(new LogicalQuantumKey("ns", "Account.balance", object), DEFINITION, value));
        assertEquals(Outcome.SUCCESS, response.outcome());
    }

    private static QuantumMutation mutation(String object, String delta, QuantumMutation.Interval bounds) {
        return new QuantumMutation(new LogicalQuantumKey("ns", "Account.balance", object), DEFINITION, delta,
                Optional.ofNullable(bounds));
    }

    private static SettlementCommand command(String requestKey, String original, String delta,
                                             QuantumMutation.Interval bounds) {
        return new SettlementCommand(new SettlementKey("ns", requestKey), Optional.ofNullable(original), "res",
                SettlementCommand.AtomicGroupShape.NONE,
                List.of(mutation("A", "-" + delta, bounds), mutation("B", delta, null)));
    }

    private BigDecimal value(String object) {
        return store.findCurrentValue("Account.balance", object).orElseThrow();
    }

    @Test
    public void declaresTheJournalCapabilities() {
        assertEquals("localQuantum", journal.name());
        assertTrue(journal.capabilities().containsAll(List.of(Capability.ONE_PHASE,
                Capability.INVOCATION_BOUNDS, Capability.PROVISIONING, Capability.SHARED_LOCAL_TRANSACTION)));
        assertFalse(journal.capabilities().contains(Capability.TWO_PHASE));
        assertThrows(UnsupportedOperationException.class, () -> journal.reserve(command("r", null, "1", null)));
    }

    @Test
    public void settlesOnTheJournalAndRetriesIdempotently() {
        SettlementCommand settle = command("s1", null, "10", null);
        SettlementResponse first = journal.settle(settle);
        assertEquals(Outcome.SUCCESS, first.outcome());
        assertEquals(SettlementResponse.Operation.SETTLE, first.operation());
        assertEquals(0, value("A").compareTo(new BigDecimal("90")));
        assertEquals(0, value("B").compareTo(new BigDecimal("15")));
        assertEquals(2, store.findAll().size());

        SettlementResponse retry = journal.settle(settle);
        assertEquals(Outcome.SUCCESS, retry.outcome());
        assertEquals(first.receipt(), retry.receipt());
        assertEquals(2, store.findAll().size());
        assertEquals(0, value("A").compareTo(new BigDecimal("90")));

        SettlementResponse drift = journal.settle(command("s1", null, "11", null));
        assertEquals(Outcome.BUSINESS_FAILED, drift.outcome());
        assertEquals(0, value("A").compareTo(new BigDecimal("90")));
        assertEquals(Outcome.SUCCESS, journal.query(settle.key()).outcome());
        assertEquals(Outcome.NOT_RECEIVED, journal.query(new SettlementKey("ns", "never")).outcome());
    }

    @Test
    public void reversesWithForwardDeltasAndRetriesIdempotently() {
        journal.settle(command("s1", null, "10", null));
        SettlementCommand reversal = command("s1:reversal", "s1", "10", null);

        SettlementResponse reversed = journal.reverse(reversal);
        assertEquals(Outcome.SUCCESS, reversed.outcome());
        assertEquals(SettlementResponse.Operation.REVERSE, reversed.operation());
        assertEquals(Optional.of("s1"), reversed.originalRequestKey());
        assertEquals(0, value("A").compareTo(new BigDecimal("100")));
        assertEquals(0, value("B").compareTo(new BigDecimal("5")));
        int rows = store.findAll().size();

        assertEquals(Outcome.SUCCESS, journal.reverse(reversal).outcome());
        assertEquals(rows, store.findAll().size());
        assertEquals(0, value("A").compareTo(new BigDecimal("100")));
    }

    @Test
    public void reversalOfAnUnknownOrDifferentOriginalFailsWithoutChange() {
        assertEquals(Outcome.BUSINESS_FAILED,
                journal.reverse(command("x:reversal", "x", "10", null)).outcome());
        journal.settle(command("s1", null, "10", null));
        SettlementResponse mismatch = journal.reverse(command("s1:reversal", "s1", "9", null));
        assertEquals(Outcome.BUSINESS_FAILED, mismatch.outcome());
        assertEquals(0, value("A").compareTo(new BigDecimal("90")));
        assertThrows(IllegalArgumentException.class, () -> journal.reverse(command("s2", null, "10", null)));
    }

    @Test
    public void invocationBoundsRejectAtomicallyAsBusinessFailure() {
        SettlementResponse rejected = journal.settle(command("b1", null, "150",
                new QuantumMutation.Interval("0", null)));
        assertEquals(Outcome.BUSINESS_FAILED, rejected.outcome());
        assertTrue(rejected.failureReason().orElseThrow().message().contains("Account.balance"));
        assertEquals(0, value("A").compareTo(new BigDecimal("100")));
        assertEquals(0, value("B").compareTo(new BigDecimal("5")));
        assertEquals(Outcome.NOT_RECEIVED, journal.query(new SettlementKey("ns", "b1")).outcome());

        assertEquals(Outcome.SUCCESS, journal.settle(command("b2", null, "50",
                new QuantumMutation.Interval("0", null))).outcome());
    }

    @Test
    public void settlementNeverAllocatesAQuantumImplicitly() {
        SettlementCommand missing = new SettlementCommand(new SettlementKey("ns", "m1"), Optional.empty(), "res",
                SettlementCommand.AtomicGroupShape.NONE,
                List.of(mutation("A", "-1", null), mutation("GHOST", "1", null)));
        SettlementResponse response = journal.settle(missing);
        assertEquals(Outcome.BUSINESS_FAILED, response.outcome());
        assertTrue("QUANTUM_NOT_PROVISIONED".equals(response.failureReason().orElseThrow().code()));
        assertEquals(0, value("A").compareTo(new BigDecimal("100")));
    }

    @Test
    public void provisioningIsIdempotentAndRejectsDrift() {
        LogicalQuantumKey key = new LogicalQuantumKey("ns", "Account.balance", "A");
        assertEquals(Outcome.SUCCESS, journal.provision(new ProvisionCommand(key, DEFINITION, "100")).outcome());
        assertEquals(Outcome.BUSINESS_FAILED, journal.provision(new ProvisionCommand(key, DEFINITION, "7")).outcome());
        assertEquals(0, value("A").compareTo(new BigDecimal("100")));
    }

    @Test
    public void inMemoryJournalOffersOnlyTheLedgerPath() {
        JournalQuantumSettlement memory = new JournalQuantumSettlement(new InMemoryQuantumJournalStore());
        assertFalse(memory.capabilities().contains(Capability.PROVISIONING));
        assertFalse(memory.capabilities().contains(Capability.INVOCATION_BOUNDS));
        assertThrows(UnsupportedOperationException.class, () -> memory.settle(command("s", null, "1", null)));
    }
}
