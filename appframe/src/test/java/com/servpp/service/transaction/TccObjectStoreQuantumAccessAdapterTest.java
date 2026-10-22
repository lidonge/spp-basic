package com.servpp.service.transaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class TccObjectStoreQuantumAccessAdapterTest {
    @Test
    public void requiresStoreName() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new TccObjectStoreQuantumAccessAdapter(" "));

        assertEquals("TCC object store quantum access adapter requires a store name.", error.getMessage());
    }

    @Test
    public void normalizesStoreNameAndMarksBoundaries() {
        TccObjectStoreQuantumAccessAdapter adapter =
                new TccObjectStoreQuantumAccessAdapter(" objectQuantum ");
        TransactionContext context = new TransactionContext(Map.of("inventoryAccounting", TransactionMode.QUANTUM));
        QuantumEntanglementBoundary boundary = new QuantumEntanglementBoundary(
                "inventoryAccounting", List.of("inventoryAccounting"));
        boundary.addRecord(new QuantumConservationRecord(
                "inventoryAccounting", "Inventory.availableQty",
                BigDecimal.valueOf(20), BigDecimal.valueOf(-2), BigDecimal.valueOf(18)));

        adapter.commit(context, boundary);
        assertEquals("objectQuantum", adapter.getStoreName());
        assertEquals(QuantumEntanglementBoundary.Status.COMMITTED, boundary.getStatus());

        adapter.reverse(context, boundary);
        assertEquals(QuantumEntanglementBoundary.Status.ROLLED_BACK, boundary.getStatus());
    }

    @Test
    public void delegatesCommitAndNegativeDeltaReversalToObjectStoreCoordinator() {
        AtomicReference<String> committedStore = new AtomicReference<>();
        AtomicReference<TransactionContext> committedContext = new AtomicReference<>();
        AtomicReference<QuantumEntanglementBoundary> committedBoundary = new AtomicReference<>();
        AtomicReference<String> reversedStore = new AtomicReference<>();
        AtomicReference<TransactionContext> reversedContext = new AtomicReference<>();
        AtomicReference<QuantumEntanglementBoundary> reversedBoundary = new AtomicReference<>();
        TccObjectStoreQuantumCoordinator coordinator = new TccObjectStoreQuantumCoordinator() {
            @Override
            public void commit(String storeName, TransactionContext context, QuantumEntanglementBoundary boundary) {
                committedStore.set(storeName);
                committedContext.set(context);
                committedBoundary.set(boundary);
            }

            @Override
            public void reverse(String storeName, TransactionContext context,
                                QuantumEntanglementBoundary reversalBoundary) {
                reversedStore.set(storeName);
                reversedContext.set(context);
                reversedBoundary.set(reversalBoundary);
            }
        };
        TccObjectStoreQuantumAccessAdapter adapter =
                new TccObjectStoreQuantumAccessAdapter(" objectQuantum ", coordinator);
        TransactionContext context = new TransactionContext(Map.of("inventoryAccounting", TransactionMode.QUANTUM));
        QuantumEntanglementBoundary boundary = new QuantumEntanglementBoundary(
                "inventoryAccounting", List.of("inventoryAccounting"));
        boundary.addRecord(new QuantumConservationRecord(
                "inventoryAccounting", "Inventory.availableQty", "MAIN|SKU-1",
                BigDecimal.valueOf(20), BigDecimal.valueOf(-2), BigDecimal.valueOf(18)));
        boundary.addRecord(new QuantumConservationRecord(
                "inventoryAccounting", "SalesItem.orderQty", "SO-1|SKU-1",
                BigDecimal.ZERO, BigDecimal.valueOf(2), BigDecimal.valueOf(2)));

        adapter.commit(context, boundary);
        adapter.reverse(context, boundary);

        assertEquals("objectQuantum", committedStore.get());
        assertEquals(context, committedContext.get());
        assertEquals(boundary, committedBoundary.get());
        assertEquals("objectQuantum", reversedStore.get());
        assertEquals(context, reversedContext.get());
        QuantumEntanglementBoundary reversal = reversedBoundary.get();
        assertEquals("inventoryAccounting", reversal.getServiceId());
        assertEquals(List.of("inventoryAccounting"), reversal.getResourceIds());
        assertEquals(QuantumEntanglementBoundary.Status.ROLLED_BACK, reversal.getStatus());
        assertEquals(2, reversal.getRecords().size());
        QuantumConservationRecord inventory = reversal.getRecords().get(0);
        assertEquals("Inventory.availableQty", inventory.getAttributeId());
        assertEquals("MAIN|SKU-1", inventory.getObjectId());
        assertEquals(BigDecimal.valueOf(18), inventory.getBeforeValue());
        assertEquals(BigDecimal.valueOf(2), inventory.getDeltaValue());
        assertEquals(BigDecimal.valueOf(20), inventory.getAfterValue());
        QuantumConservationRecord salesItem = reversal.getRecords().get(1);
        assertEquals("SalesItem.orderQty", salesItem.getAttributeId());
        assertEquals("SO-1|SKU-1", salesItem.getObjectId());
        assertEquals(BigDecimal.valueOf(2), salesItem.getBeforeValue());
        assertEquals(BigDecimal.valueOf(-2), salesItem.getDeltaValue());
        assertEquals(BigDecimal.ZERO, salesItem.getAfterValue());
        assertEquals(QuantumEntanglementBoundary.Status.ROLLED_BACK, boundary.getStatus());
    }
}
