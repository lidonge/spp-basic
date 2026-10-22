package com.servpp.service.transaction;

import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuantumStateWithConservationTest {
    private QuantumEntanglementBoundary movement(int income, int debt) {
        var boundary = new QuantumEntanglementBoundary("settle", List.of("ledger"));
        boundary.addRecord(record("funds", "Cash.balance", 0, -100, null));
        boundary.addRecord(record("funds", "Account.balance", 0, 90, null));
        boundary.addRecord(record("funds", "Income.balance", 0, income, null));
        boundary.addRecord(record(QuantumConservationRecord.STATE_GROUP, "Account.debt", 10, debt, BigDecimal.ZERO));
        return boundary;
    }
    private QuantumConservationRecord record(String group, String field, int before, int delta, BigDecimal minimum) {
        return new QuantumConservationRecord(group, field, "one", BigDecimal.valueOf(before),
                BigDecimal.valueOf(delta), BigDecimal.valueOf(before + delta), minimum, null);
    }
    private JdbcQuantumJournalStore store() {
        return new JdbcQuantumJournalStore(new DriverManagerJdbcDataSource("org.h2.Driver",
                "jdbc:h2:mem:quantum_state_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""), "journal", "values_store", "text");
    }
    @Test public void persistsAndReversesStateAlongWithBalancedFunds() {
        var store = store();
        store.seedCurrentValue(QuantumConservationRecord.STATE_GROUP, "Account.debt", "one", BigDecimal.TEN);
        var context = new TransactionContext(Map.of("ledger", TransactionMode.QUANTUM), store);
        var boundary = movement(10, -10);
        boundary.validateConservation(); boundary.markCommitted(); store.append(context, boundary);
        assertEquals(0, store.findCurrentValue("Account.debt", "one").orElseThrow().compareTo(BigDecimal.ZERO));
        assertEquals(0, store.findCurrentValue("Income.balance", "one").orElseThrow().compareTo(BigDecimal.TEN));
        assertEquals(4, store.findByTransactionId(context.getId()).size());
        var reverse = TransactionContext.reversal(context.getId() + ":reverse", null, List.of("ledger"), store);
        store.reverseDeltas(reverse, boundary); store.reverseDeltas(reverse, boundary);
        assertEquals(0, store.findCurrentValue("Account.debt", "one").orElseThrow().compareTo(BigDecimal.TEN));
        assertEquals(0, store.findCurrentValue("Income.balance", "one").orElseThrow().compareTo(BigDecimal.ZERO));
        assertEquals(4, store.findByTransactionId(reverse.getId()).size());
    }
    @Test public void stateCannotHideUnbalancedFunds() {
        assertThrows(IllegalStateException.class, () -> movement(20, -10).validateConservation());
    }
    @Test public void stateBoundsAbortAllMoneyChanges() {
        var store = store();
        store.seedCurrentValue(QuantumConservationRecord.STATE_GROUP, "Account.debt", "one", BigDecimal.TEN);
        var context = new TransactionContext(Map.of("ledger", TransactionMode.QUANTUM), store);
        var boundary = movement(10, -11);
        boundary.validateConservation(); boundary.markCommitted();
        assertThrows(RuntimeException.class, () -> store.append(context, boundary));
        assertEquals(0, store.findCurrentValue("Account.debt", "one").orElseThrow().compareTo(BigDecimal.TEN));
        assertTrue(store.findCurrentValue("Cash.balance", "one").isEmpty());
        assertTrue(store.findByTransactionId(context.getId()).isEmpty());
    }
}
