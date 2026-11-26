package com.servpp.service.serial;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.Map;

/**
 * Gapless engine: a locked {@code UPDATE ... SET v = v + 1} on a counter row, executed on the business
 * transaction's connection. A rollback of the business transaction rolls the allocation back, so no number is
 * lost. It coexists with a legacy counter through {@code table}, {@code keyColumn} and {@code valueColumn}:
 * <pre>
 * create table SPP_SERIAL_COUNTER (COUNTER_KEY varchar(200) primary key, COUNTER_VALUE bigint not null)
 * </pre>
 * Rows are keyed by {@link SerialRequest#counter()}; a missing row is created with value 1. The legacy value is
 * the last number issued.
 */
public final class GaplessCounterEngine implements GaplessSerialEngine {
    private String update, select, insert;

    @Override public void configure(Map<String, String> configuration) {
        String table = SerialDataSources.identifier(configuration.getOrDefault("table", "SPP_SERIAL_COUNTER"), "table");
        String key = SerialDataSources.identifier(configuration.getOrDefault("keyColumn", "COUNTER_KEY"), "keyColumn");
        String value = SerialDataSources.identifier(configuration.getOrDefault("valueColumn", "COUNTER_VALUE"), "valueColumn");
        update = "update " + table + " set " + value + " = " + value + " + 1 where " + key + " = ?";
        select = "select " + value + " from " + table + " where " + key + " = ?";
        insert = "insert into " + table + " (" + key + ", " + value + ") values (?, 1)";
    }

    @Override public long next(SerialRequest request) {
        Connection connection = request.connection().orElseThrow(() -> new IllegalStateException(
                "SERIAL_GAPLESS_REQUIRES_TRANSACTION: rule " + request.rule()));
        try {
            if (!locked(connection, request.counter())) {
                Savepoint point = connection.setSavepoint();
                try (PreparedStatement create = connection.prepareStatement(insert)) {
                    create.setString(1, request.counter());
                    create.executeUpdate();
                    connection.releaseSavepoint(point);
                    return 1;
                } catch (SQLException duplicate) {
                    // A concurrent first allocation created the row; ours waits on its lock below.
                    connection.rollback(point);
                    if (!locked(connection, request.counter())) throw duplicate;
                }
            }
            try (PreparedStatement read = connection.prepareStatement(select)) {
                read.setString(1, request.counter());
                try (ResultSet row = read.executeQuery()) {
                    if (!row.next()) throw new IllegalStateException("SERIAL_COUNTER_MISSING: " + request.counter());
                    return row.getLong(1);
                }
            }
        } catch (SQLException failure) { throw new IllegalStateException("SERIAL_GAPLESS_FAILED: " + request.counter(), failure); }
    }
    private boolean locked(Connection connection, String counter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            statement.setString(1, counter);
            return statement.executeUpdate() == 1;
        }
    }
}
