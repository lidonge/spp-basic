package com.servpp.service.transaction;

import com.servpp.service.jdbc.DriverManagerJdbcDataSource;
import java.math.BigDecimal;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuantumDeltaReversalTest {
    private QuantumEntanglementBoundary settle(JdbcQuantumJournalStore store, TransactionContext context, int delta) {
        var boundary = new QuantumEntanglementBoundary("transfer",List.of("ledger"));
        for (var item : Map.of("Buyer.wallet",delta,"Merchant.cash",-delta).entrySet()) {
            var before = store.findCurrentValue(item.getKey(),"one").orElse(BigDecimal.ZERO);
            boundary.addRecord(new QuantumConservationRecord("funds",item.getKey(),"one",before,
                    BigDecimal.valueOf(item.getValue()),before.add(BigDecimal.valueOf(item.getValue()))));
        }
        boundary.markCommitted(); store.append(context,boundary); return boundary;
    }
    @Test public void reversalPreservesLaterSettlementAndRetryDoesNotReverseTwice() {
        var source = new DriverManagerJdbcDataSource("org.h2.Driver","jdbc:h2:mem:reversal"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        var store = new JdbcQuantumJournalStore(source,"journal","quantum_values","text");
        var first = new TransactionContext(Map.of("ledger",TransactionMode.QUANTUM),store);
        var original = settle(store,first,10);
        settle(store,new TransactionContext(Map.of("ledger",TransactionMode.QUANTUM),store),-100);
        var inverse = TransactionContext.reversal(first.getId()+":reversal",null,List.of("ledger"),store);
        store.reverseDeltas(inverse,original); store.reverseDeltas(inverse,original);
        assertEquals(0,new BigDecimal("-100").compareTo(store.findCurrentValue("Buyer.wallet","one").orElseThrow()));
        assertEquals(0,new BigDecimal("100").compareTo(store.findCurrentValue("Merchant.cash","one").orElseThrow()));
        assertEquals(2,store.findByTransactionId(inverse.getId()).size());
    }
}
