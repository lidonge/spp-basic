package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.runtime.DatabaseInitializationContext;
import com.servpp.service.transaction.JdbcQuantumJournalStore;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import static org.junit.Assert.*;

public class DatabaseInitializationTest {
    public static class Account {
        public String name; public BigDecimal balance;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public BigDecimal getBalance() { return balance; }
        public void setBalance(BigDecimal balance) { this.balance = balance; }
    }
    private JdbcDataSource source() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:init_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        return source;
    }
    private void insert(JdbcObjectRepository repository, String id, Account account) {
        com.servpp.service.transaction.DeclaredTransactionContext.execute(
                com.servpp.service.transaction.TransactionMode.SAGA,
                () -> repository.executeInTransaction(() -> repository.insert("Account", id, account)));
    }
    @Test public void splitDatabaseSeedsResumeWithoutOverwritingCommittedFacts() {
        var mapper = new ObjectMapper();
        var store = new JdbcObjectSnapshotStore(source(), value -> {
            try { return mapper.writeValueAsString(value); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        });
        var repository = new JdbcObjectRepository(store, mapper);
        var ledger = new JdbcQuantumJournalStore(source(), "journal", "values_table", "text");
        repository.useQuantumFacts(ledger, List.of("Account.balance"));
        var account = new Account(); account.name = "original"; account.balance = BigDecimal.TEN;
        // Simulate a partial older startup with the ordinary row already committed.
        repository.save("Account", "one", account);
        assertThrows(IllegalStateException.class, () -> ledger.insertInitialValue("Account.balance", "one", BigDecimal.TEN));
        var injected = new IllegalStateException("startup interrupted");
        assertSame(injected, assertThrows(IllegalStateException.class, () -> DatabaseInitializationContext.execute(() -> {
            insert(repository, "one", account);
            throw injected;
        })));
        assertFalse(DatabaseInitializationContext.isActive());
        assertEquals(0, BigDecimal.TEN.compareTo(ledger.findCurrentValue("Account.balance", "one").orElseThrow()));
        ledger.seedCurrentValue("trade", "Account.balance", "one", new BigDecimal("7"));
        account.name = "replacement"; account.balance = new BigDecimal("99");
        DatabaseInitializationContext.execute(() -> {
            insert(repository, "one", account);
            insert(repository, "two", account);
        });
        assertEquals(0, new BigDecimal("7").compareTo(ledger.findCurrentValue("Account.balance", "one").orElseThrow()));
        assertEquals(0, new BigDecimal("99").compareTo(ledger.findCurrentValue("Account.balance", "two").orElseThrow()));
        assertTrue(store.find("Account", "one").orElseThrow().payloadJson().contains("original"));
        assertThrows(IllegalStateException.class, () -> repository.insert("Account", "one", account));
        assertThrows(IllegalStateException.class, () -> ledger.initializeValueIfAbsent("Account.balance", "three", BigDecimal.ZERO));
        assertFalse(DatabaseInitializationContext.isActive());
    }

    @Test public void zeroOpeningNeedsNoFundingAndNeverResetsTradedBalance() {
        var ledger = new JdbcQuantumJournalStore(source(), "journal", "values_table", "text");
        ledger.initializeZeroValueIfAbsent("Account.balance", "one");
        assertEquals(0, ledger.findCurrentValue("Account.balance", "one").orElseThrow().signum());
        ledger.seedCurrentValue("trade", "Account.balance", "one", new BigDecimal("-5000"));
        ledger.initializeZeroValueIfAbsent("Account.balance", "one");
        assertEquals(0, new BigDecimal("-5000").compareTo(ledger.findCurrentValue("Account.balance", "one").orElseThrow()));
        assertTrue(ledger.findAll().isEmpty());
    }
}
