package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.transaction.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuantumOpeningRecoveryTest {
    public static class Account {
        public BigDecimal balance = BigDecimal.ZERO;
        public BigDecimal getBalance() { return balance; }
        public void setBalance(BigDecimal balance) { this.balance = balance; }
    }
    private JdbcDataSource source() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:opening_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        return source;
    }
    private JdbcObjectRepository repository(javax.sql.DataSource source, JdbcQuantumJournalStore ledger) {
        var mapper = new ObjectMapper();
        var store = new JdbcObjectSnapshotStore(source, value -> {
            try { return mapper.writeValueAsString(value); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        });
        var repository = new JdbcObjectRepository(store, mapper);
        repository.useQuantumFacts(ledger, List.of("Account.balance"));
        return repository;
    }
    private void transaction(JdbcObjectRepository repository, Runnable action) {
        DeclaredTransactionContext.execute(TransactionMode.SAGA, () -> repository.executeInTransaction(action));
    }
    private int pending(javax.sql.DataSource source) throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("select count(*) from spp_object_snapshot_quantum_openings")) {
            rows.next(); return rows.getInt(1);
        }
    }

    @Test public void omittedInitialValueCreatesZeroForLocalAndIndependentLedger() {
        for (boolean split : List.of(false, true)) {
            var owner = source(); var ledger = new JdbcQuantumJournalStore(split ? source() : owner, "journal", "values_table", "text");
            var repository = repository(owner, ledger);
            var account = new Account(); account.balance = null;
            transaction(repository, () -> repository.insert("Account", "limited", account));
            assertEquals(BigDecimal.ZERO, ledger.findCurrentValue("Account.balance", "limited").orElseThrow());
            assertEquals(BigDecimal.ZERO, repository.require("Account", "limited", Account.class).balance);
        }
    }

    @Test public void rollbackNeverCreatesRemoteZeroAndCommittedInsertCreatesItAfterOwnerCommit() throws Exception {
        var owner = source(); var remote = source();
        var ledger = new JdbcQuantumJournalStore(remote, "journal", "values_table", "text") {
            @Override public void initializeZeroValueIfAbsent(String attribute, String id) {
                // The independent owner connection must see the committed row before remote provisioning.
                try (var connection = owner.getConnection(); var statement = connection.prepareStatement(
                        "select 1 from spp_object_snapshot where object_type='Account' and object_id=?")) {
                    statement.setString(1, id);
                    try (var rows = statement.executeQuery()) { assertTrue(rows.next()); }
                } catch (Exception failure) { throw new AssertionError(failure); }
                super.initializeZeroValueIfAbsent(attribute, id);
            }
        };
        var repository = repository(owner, ledger);
        assertThrows(IllegalStateException.class, () -> transaction(repository, () -> {
            repository.insert("Account", "rolled-back", new Account());
            assertTrue(ledger.findCurrentValue("Account.balance", "rolled-back").isEmpty());
            assertEquals(BigDecimal.ZERO, repository.require("Account", "rolled-back", Account.class).balance);
            throw new IllegalStateException("later statement failed");
        }));
        assertTrue(repository.find("Account", "rolled-back").isEmpty());
        assertTrue(ledger.findCurrentValue("Account.balance", "rolled-back").isEmpty());
        assertEquals(0, pending(owner));
        transaction(repository, () -> repository.insert("Account", "ok", new Account()));
        assertEquals(BigDecimal.ZERO, ledger.findCurrentValue("Account.balance", "ok").orElseThrow());
        assertEquals(0, pending(owner)); assertTrue(ledger.findAll().isEmpty());
    }

    @Test public void outageLeavesDurableTaskAndRestartRepairsItWithoutBusinessCompensation() throws Exception {
        var owner = source(); var remote = source();
        var ledger = new JdbcQuantumJournalStore(remote, "journal", "values_table", "text") {
            @Override public void initializeZeroValueIfAbsent(String attribute, String id) {
                throw new IllegalStateException("ledger offline");
            }
        };
        var repository = repository(owner, ledger);
        var failure = assertThrows(IllegalStateException.class,
                () -> transaction(repository, () -> repository.insert("Account", "one", new Account())));
        assertEquals(TransactionOutcomes.Outcome.COMMITTED, TransactionOutcomes.outcome(failure));
        assertTrue(repository.find("Account", "one").isPresent());
        assertEquals(1, pending(owner));
        var healthy = new JdbcQuantumJournalStore(remote, "journal", "values_table", "text");
        var restarted = repository(owner, healthy);
        assertEquals(BigDecimal.ZERO, restarted.require("Account", "one", Account.class).balance);
        assertEquals(0, pending(owner)); assertTrue(healthy.findAll().isEmpty());
    }

    @Test public void lostRemoteResponseRetriesWithoutResettingTradedValue() throws Exception {
        var owner = source(); var remote = source();
        var ledger = new JdbcQuantumJournalStore(remote, "journal", "values_table", "text") {
            @Override public void initializeZeroValueIfAbsent(String attribute, String id) {
                super.initializeZeroValueIfAbsent(attribute, id);
                throw new IllegalStateException("response lost");
            }
        };
        var repository = repository(owner, ledger);
        assertThrows(IllegalStateException.class,
                () -> transaction(repository, () -> repository.insert("Account", "one", new Account())));
        assertEquals(1, pending(owner));
        var healthy = new JdbcQuantumJournalStore(remote, "journal", "values_table", "text");
        healthy.seedCurrentValue("test", "Account.balance", "one", new BigDecimal("12"));
        var restarted = repository(owner, healthy);
        restarted.recoverQuantumOpenings();
        assertEquals(new BigDecimal("12"), restarted.require("Account", "one", Account.class).balance);
        assertEquals(0, pending(owner));
    }

    @Test public void lostLocalCommitAcknowledgementRetainsTaskForRestart() throws Exception {
        var owner = source(); var remote = source();
        var armed = new java.util.concurrent.atomic.AtomicBoolean();
        javax.sql.DataSource faulty = (javax.sql.DataSource) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{javax.sql.DataSource.class}, (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(owner, args);
                        if (!(result instanceof java.sql.Connection connection)) return result;
                        return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                                new Class<?>[]{java.sql.Connection.class}, (p, m, a) -> {
                                    try {
                                        Object value = m.invoke(connection, a);
                                        if (m.getName().equals("commit") && armed.compareAndSet(true, false))
                                            throw new java.sql.SQLException("commit acknowledgement lost", "08006");
                                        return value;
                                    } catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                                });
                    } catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                });
        var ledger = new JdbcQuantumJournalStore(remote, "journal", "values_table", "text");
        var repository = repository(faulty, ledger);
        armed.set(true);
        assertThrows(IllegalStateException.class,
                () -> transaction(repository, () -> repository.insert("Account", "one", new Account())));
        assertTrue(repository.find("Account", "one").isPresent());
        assertTrue(ledger.findCurrentValue("Account.balance", "one").isEmpty());
        assertEquals(1, pending(owner));
        repository(owner, ledger);
        assertEquals(BigDecimal.ZERO, ledger.findCurrentValue("Account.balance", "one").orElseThrow());
        assertEquals(0, pending(owner));
    }

    @Test public void sameDatabaseOpeningRemainsPartOfLocalTransaction() {
        var source = source();
        var ledger = new JdbcQuantumJournalStore(source, "journal", "values_table", "text");
        var repository = repository(source, ledger);
        assertThrows(IllegalStateException.class, () -> transaction(repository, () -> {
            repository.insert("Account", "one", new Account());
            assertEquals(BigDecimal.ZERO, ledger.findCurrentValue("Account.balance", "one").orElseThrow());
            throw new IllegalStateException("rollback");
        }));
        assertTrue(repository.find("Account", "one").isEmpty());
        assertTrue(ledger.findCurrentValue("Account.balance", "one").isEmpty());
    }

    @Test public void technicalReplayDoesNotJoinAnUnrelatedQuantumTransaction() throws Exception {
        var owner = source(); var remote = source();
        var online = new java.util.concurrent.atomic.AtomicBoolean();
        var ledger = new JdbcQuantumJournalStore(remote, "journal", "values_table", "text") {
            @Override public void initializeZeroValueIfAbsent(String attribute, String id) {
                if (!online.get()) throw new IllegalStateException("offline");
                super.initializeZeroValueIfAbsent(attribute, id);
            }
        };
        var repository = repository(owner, ledger);
        assertThrows(IllegalStateException.class,
                () -> transaction(repository, () -> repository.insert("Account", "one", new Account())));
        online.set(true);
        assertThrows(IllegalStateException.class, () -> JdbcTransactionScope.execute(remote, connection -> {
            repository.recoverQuantumOpenings();
            throw new IllegalStateException("unrelated quantum rollback");
        }));
        assertEquals(BigDecimal.ZERO, ledger.findCurrentValue("Account.balance", "one").orElseThrow());
        assertEquals(0, pending(owner));
    }

    @Test public void externalConnectionOwnerDrivesRecoveryAfterItsCommit() throws Exception {
        var owner = source();
        var ledger = new JdbcQuantumJournalStore(source(), "journal", "values_table", "text");
        var repository = repository(owner, ledger);
        try (var connection = owner.getConnection()) {
            connection.setAutoCommit(false);
            DeclaredTransactionContext.execute(TransactionMode.SAGA, () -> {
                try { JdbcTransactionScope.withConnection(owner, connection, current -> {
                    repository.insert("Account", "external", new Account()); return null;
                }); } catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
            });
            assertTrue(ledger.findCurrentValue("Account.balance", "external").isEmpty());
            connection.commit();
        }
        repository.recoverQuantumOpenings();
        assertEquals(BigDecimal.ZERO, ledger.findCurrentValue("Account.balance", "external").orElseThrow());
        assertEquals(0, pending(owner));
    }

    @Test public void savepointRollbackDoesNotDispatchAbortedOpening() throws Exception {
        var owner = source();
        var ledger = new JdbcQuantumJournalStore(source(), "journal", "values_table", "text");
        var repository = repository(owner, ledger);
        transaction(repository, () -> {
            assertThrows(IllegalStateException.class, () -> JdbcTransactionScope.withSavepoints(() -> {
                repository.insert("Account", "nested", new Account());
                throw new IllegalStateException("nested rollback");
            }));
            repository.insert("Account", "outer", new Account());
        });
        assertTrue(ledger.findCurrentValue("Account.balance", "nested").isEmpty());
        assertEquals(BigDecimal.ZERO, ledger.findCurrentValue("Account.balance", "outer").orElseThrow());
        assertEquals(0, pending(owner));
    }
}
