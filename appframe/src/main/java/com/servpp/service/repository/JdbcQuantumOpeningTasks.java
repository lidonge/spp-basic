package com.servpp.service.repository;

import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.transaction.JdbcQuantumJournalStore;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Durable technical work, committed with the ordinary owner row. No business compensation. */
final class JdbcQuantumOpeningTasks {
    private final JdbcObjectSnapshotStore store;
    private final JdbcQuantumJournalStore ledger;
    private final String table;

    JdbcQuantumOpeningTasks(JdbcObjectSnapshotStore store, JdbcQuantumJournalStore ledger) {
        this.store = store; this.ledger = ledger;
        table = store.getTableName() + "_quantum_openings";
        try (var connection = store.getDataSource().getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("create table if not exists " + table + " (object_type varchar(128) not null, "
                    + "object_id varchar(256) not null, attribute_id varchar(256) not null, "
                    + "primary key (object_type, object_id, attribute_id))");
        } catch (SQLException failure) { throw new IllegalStateException("QUANTUM_OPENING_SCHEMA_FAILED", failure); }
    }

    void enqueue(String type, String id, String attribute) {
        var connection = JdbcTransactionScope.currentConnection(store.getDataSource()).orElseThrow(
                () -> new IllegalStateException("QUANTUM_OPENING_REQUIRES_OWNER_TRANSACTION"));
        try {
            if (!pending(type, id, attribute)) {
                try (var statement = connection.prepareStatement("insert into " + table + " values (?, ?, ?)")) {
                    statement.setString(1, type); statement.setString(2, id); statement.setString(3, attribute);
                    statement.executeUpdate();
                }
            }
            JdbcTransactionScope.afterCommit(store.getDataSource(), this::recover);
        } catch (SQLException failure) { throw new IllegalStateException("QUANTUM_OPENING_ENQUEUE_FAILED", failure); }
    }

    boolean pending(String type, String id, String attribute) {
        try {
            return JdbcTransactionScope.execute(store.getDataSource(), connection -> {
                try (var statement = connection.prepareStatement("select 1 from " + table
                        + " where object_type=? and object_id=? and attribute_id=?")) {
                    statement.setString(1, type); statement.setString(2, id); statement.setString(3, attribute);
                    try (var rows = statement.executeQuery()) { return rows.next(); }
                }
            });
        } catch (SQLException failure) { throw new IllegalStateException("QUANTUM_OPENING_READ_FAILED", failure); }
    }

    void recover() {
        // Never send an uncommitted task to the remote ledger. Reads in this scope see its pending zero.
        if (JdbcTransactionScope.currentConnection(store.getDataSource()).isPresent()) return;
        try {
            List<String[]> tasks = JdbcTransactionScope.execute(store.getDataSource(), connection -> {
                var result = new ArrayList<String[]>();
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("select object_type, object_id, attribute_id from " + table)) {
                    while (rows.next()) result.add(new String[]{rows.getString(1), rows.getString(2), rows.getString(3)});
                }
                return result;
            });
            for (String[] task : tasks) {
                // A removed owner cancels unfinished technical provisioning.
                if (store.find(task[0], task[1]).isPresent())
                    JdbcTransactionScope.suspend(() -> ledger.initializeZeroValueIfAbsent(task[2], task[1]));
                JdbcTransactionScope.execute(store.getDataSource(), connection -> {
                    try (var statement = connection.prepareStatement("delete from " + table
                            + " where object_type=? and object_id=? and attribute_id=?")) {
                        statement.setString(1, task[0]); statement.setString(2, task[1]); statement.setString(3, task[2]);
                        statement.executeUpdate();
                    }
                    return null;
                });
            }
        } catch (SQLException | RuntimeException failure) { throw new QuantumOpeningRecoveryException(failure); }
    }
}
