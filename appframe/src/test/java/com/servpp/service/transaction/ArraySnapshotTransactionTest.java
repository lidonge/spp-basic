package com.servpp.service.transaction;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArraySnapshotTransactionTest {
    public static class Item { public int value; public int[] values; }
    @Test public void restoresArrayItemsNullsAndChangedNestedLengths() {
        Item item = new Item(); item.value = 4; item.values = new int[]{1, 2};
        Item[] values = {item, null};
        var participant = new ObjectSnapshotTransactionParticipant("array");
        participant.setArguments(new Object[]{values});
        participant.beforeExecute(null);
        item.value = 9; item.values = new int[]{7}; values[1] = new Item();
        participant.rollback(null, new IllegalStateException("fail"));
        assertSame(item, values[0]); assertEquals(4, item.value);
        assertArrayEquals(new int[]{1, 2}, item.values); assertNull(values[1]);
    }
    @Test public void snapshotsCyclesAndPrimitiveArrayArguments() {
        Object[] cyclic = new Object[2]; cyclic[0] = cyclic; cyclic[1] = "before";
        int[] numbers = {3, 4};
        var participant = new ObjectSnapshotTransactionParticipant("array");
        participant.setArguments(new Object[]{cyclic, numbers}); participant.beforeExecute(null);
        cyclic[1] = "after"; numbers[0] = 100;
        participant.rollback(null, new IllegalStateException("fail"));
        assertSame(cyclic, cyclic[0]); assertEquals("before", cyclic[1]);
        assertArrayEquals(new int[]{3, 4}, numbers);
    }
    @Test public void restoresMapFactsAndCyclesWithoutReflectingIntoJdkInternals() {
        var facts = new java.util.LinkedHashMap<String, Object>();
        var item = new Item(); item.value = 4;
        facts.put("item", item); facts.put("self", facts);
        var participant = new ObjectSnapshotTransactionParticipant("map");
        participant.setArguments(new Object[]{facts}); participant.beforeExecute(null);
        item.value = 9; facts.put("extra", "after");
        participant.rollback(null, new IllegalStateException("fail"));
        assertSame(item, facts.get("item")); assertEquals(4, item.value);
        assertSame(facts, facts.get("self")); assertFalse(facts.containsKey("extra"));
    }
}
