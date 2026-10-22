package com.servpp.service.transaction;

import com.servpp.service.jdbc.JdbcTransactionScope;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;

public class ExactQuantumDecimalStorageTest {
    private JdbcDataSource source() {
        JdbcDataSource source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:exact_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1"); return source;
    }
    private void append(JdbcQuantumJournalStore store, String id, String before, String delta) {
        BigDecimal initial = new BigDecimal(before), change = new BigDecimal(delta);
        var boundary = new QuantumEntanglementBoundary("movement", List.of("records"));
        boundary.addRecord(new QuantumConservationRecord("total", "Quantity.current", id, initial, change, initial.add(change)));
        BigDecimal offset = store.findCurrentValue("Offset.amount", id).orElse(BigDecimal.ZERO);
        boundary.addRecord(new QuantumConservationRecord("total", "Offset.amount", id, offset, change.negate(), offset.subtract(change)));
        boundary.validateConservation(); boundary.markCommitted(); store.append(new TransactionContext(Map.of("records", TransactionMode.QUANTUM)), boundary);
    }
    private void number(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
    @Test public void textStorageRetainsTinyAmountsAndIndependentIdentitiesAfterReopen() {
        var source = source(); var store = new JdbcQuantumJournalStore(source, "journal", "current_value", "text");
        append(store, "one", "100", ".0000000000000000000000001"); append(store, "two", "0", "25");
        var reopened = new JdbcQuantumJournalStore(source, "journal", "current_value", "text");
        number("100.0000000000000000000000001", reopened.findCurrentValue("Quantity.current", "one").orElseThrow());
        number("-.0000000000000000000000001", reopened.findCurrentValue("Offset.amount", "one").orElseThrow());
        number("25", reopened.findCurrentValue("Quantity.current", "two").orElseThrow());
        assertEquals(4, reopened.findAll().size());
        assertTrue(reopened.findAll().stream().anyMatch(row -> row.getDeltaValue().compareTo(new BigDecimal("1e-25")) == 0));
    }
    @Test public void callerRollbackRemovesJournalAndValuesAndWrongBeforeKeepsEarlierCommit() throws Exception {
        var source = source(); var store = new JdbcQuantumJournalStore(source, "journal", "current_value", "text");
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            JdbcTransactionScope.withConnection(source, connection, bound -> { append(store, "one", "100", "10"); return null; });
            connection.rollback();
        }
        assertTrue(store.findAll().isEmpty()); assertTrue(store.findCurrentValues().isEmpty());
        append(store, "one", "100", "10");
        assertThrows(IllegalStateException.class, () -> append(store, "one", "100", "20"));
        assertEquals(2, store.findAll().size()); number("110", store.findCurrentValue("Quantity.current", "one").orElseThrow());
        assertTrue(JdbcTransactionScope.currentConnection(source).isEmpty());
        append(store, "one", "110", "1"); number("111", store.findCurrentValue("Quantity.current", "one").orElseThrow());
    }
    @Test public void invalidOrIncompatibleStorageFailsWithoutChangingExistingRows() {
        var source = source(); var decimal = new JdbcQuantumJournalStore(source, "journal", "current_value");
        append(decimal, "one", "100", "10");
        assertThrows(IllegalArgumentException.class, () -> new JdbcQuantumJournalStore(source, "unused", "unused_values", "float"));
        var failure = assertThrows(IllegalStateException.class, () -> new JdbcQuantumJournalStore(source, "journal", "current_value", "text"));
        assertTrue(failure.getMessage().contains("QUANTUM_DECIMAL_STORAGE_MISMATCH"));
        assertEquals(2, decimal.findAll().size()); number("110", decimal.findCurrentValue("Quantity.current", "one").orElseThrow());
    }
}
