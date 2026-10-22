package com.servpp.service.transaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;

public class JdbcTccObjectStoreQuantumCoordinatorTest {
    @Test
    public void persistsIdempotentCommitAndNegativeDeltaReversalInObjectStore() {
        JdbcTccObjectStoreQuantumCoordinator coordinator =
                new JdbcTccObjectStoreQuantumCoordinator(h2DataSource());
        TccObjectStoreQuantumAccessAdapter adapter =
                new TccObjectStoreQuantumAccessAdapter("globalQuantum", coordinator);
        TransactionContext context =
                new TransactionContext(Map.of("inventoryAccounting", TransactionMode.QUANTUM));
        QuantumEntanglementBoundary boundary = inventoryBoundary(2);

        adapter.commit(context, boundary);
        adapter.commit(context, boundary);

        assertTrue(coordinator.executable());
        assertEquals(JdbcTccObjectStoreQuantumCoordinator.DEFAULT_JOURNAL_TABLE,
                coordinator.journalTableName());
        assertEquals(JdbcTccObjectStoreQuantumCoordinator.DEFAULT_VALUE_TABLE,
                coordinator.valueTableName());
        assertEquals(2, coordinator.getStore().findAll().size());
        assertDecimal("18", coordinator.getStore()
                .findCurrentValue("Inventory.availableQty", "MAIN|SKU-1").orElseThrow());
        assertDecimal("2", coordinator.getStore()
                .findCurrentValue("SalesItem.orderQty", "SO-1|SKU-1").orElseThrow());

        adapter.reverse(context, boundary);
        adapter.reverse(context, boundary);

        assertEquals(QuantumEntanglementBoundary.Status.ROLLED_BACK, boundary.getStatus());
        assertEquals(4, coordinator.getStore().findAll().size());
        assertDecimal("20", coordinator.getStore()
                .findCurrentValue("Inventory.availableQty", "MAIN|SKU-1").orElseThrow());
        assertDecimal("0", coordinator.getStore()
                .findCurrentValue("SalesItem.orderQty", "SO-1|SKU-1").orElseThrow());
    }

    @Test
    public void rejectsIdempotencyKeyReuseWithDifferentConservationRecords() {
        JdbcTccObjectStoreQuantumCoordinator coordinator =
                new JdbcTccObjectStoreQuantumCoordinator(h2DataSource());
        TransactionContext context =
                new TransactionContext(Map.of("inventoryAccounting", TransactionMode.QUANTUM));

        coordinator.commit("globalQuantum", context, inventoryBoundary(2));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> coordinator.commit("globalQuantum", context, inventoryBoundary(3)));

        assertTrue(error.getMessage().contains(
                "reused an idempotency key with different conservation records"));
    }

    @Test
    public void rejectsReversalWithoutPersistedCommitEvidence() {
        JdbcTccObjectStoreQuantumCoordinator coordinator =
                new JdbcTccObjectStoreQuantumCoordinator(h2DataSource());
        TransactionContext context =
                new TransactionContext(Map.of("inventoryAccounting", TransactionMode.QUANTUM));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> coordinator.reverse("globalQuantum", context,
                        inventoryBoundary(2).negativeDeltaReversal()));

        assertTrue(error.getMessage().contains("before its commit evidence exists"));
    }

    private QuantumEntanglementBoundary inventoryBoundary(int quantity) {
        QuantumEntanglementBoundary boundary = new QuantumEntanglementBoundary(
                "inventoryAccounting", List.of("inventoryAccounting"));
        boundary.addRecord(new QuantumConservationRecord(
                "inventoryAccounting", "Inventory.availableQty", "MAIN|SKU-1",
                BigDecimal.valueOf(20), BigDecimal.valueOf(-quantity),
                BigDecimal.valueOf(20 - quantity)));
        boundary.addRecord(new QuantumConservationRecord(
                "inventoryAccounting", "SalesItem.orderQty", "SO-1|SKU-1",
                BigDecimal.ZERO, BigDecimal.valueOf(quantity), BigDecimal.valueOf(quantity)));
        return boundary;
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private JdbcDataSource h2DataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:spp-tcc-quantum-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }
}
