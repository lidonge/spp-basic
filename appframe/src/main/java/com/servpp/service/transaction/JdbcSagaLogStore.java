package com.servpp.service.transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

public class JdbcSagaLogStore implements SagaLogStore {
    public static final String DEFAULT_TABLE_NAME = "saga_log";

    private final DataSource dataSource;
    private final String tableName;
    private final String createTableSql;
    private final String insertSql;
    private final String selectAllSql;
    private final String deleteAllSql;

    public JdbcSagaLogStore(DataSource dataSource) {
        this(dataSource, DEFAULT_TABLE_NAME);
    }

    public JdbcSagaLogStore(DataSource dataSource, String tableName) {
        this.dataSource = dataSource;
        this.tableName = normalizeTableName(tableName);
        this.createTableSql = """
                create table if not exists %s (
                    saga_id varchar(128) primary key,
                    global_serial_number varchar(256) not null,
                    service_name varchar(256) not null,
                    status varchar(64) not null,
                    message varchar(1024) not null,
                    transaction_id varchar(128) not null,
                    quantum_boundary_service_id varchar(512) not null,
                    created_at varchar(64) not null,
                    recovery_payload clob
                )
                """.formatted(this.tableName);
        this.insertSql = """
                insert into %s (
                    saga_id, global_serial_number, service_name, status, message,
                    transaction_id, quantum_boundary_service_id, created_at, recovery_payload
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(this.tableName);
        this.selectAllSql = """
                select saga_id, global_serial_number, service_name, status, message,
                       transaction_id, quantum_boundary_service_id, created_at, recovery_payload
                  from %s
                 order by created_at, saga_id
                """.formatted(this.tableName);
        this.deleteAllSql = "delete from " + this.tableName;
        ensureTable();
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public String getTableName() {
        return tableName;
    }

    @Override
    public void append(SagaLogEntry entry) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                insert(connection, entry);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot append saga log", e);
        }
    }

    @Override
    public List<SagaLogEntry> findAll() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectAllSql);
             ResultSet resultSet = statement.executeQuery()) {
            List<SagaLogEntry> entries = new ArrayList<>();
            while (resultSet.next()) {
                entries.add(new SagaLogEntry(
                        resultSet.getString("saga_id"),
                        resultSet.getString("global_serial_number"),
                        resultSet.getString("service_name"),
                        resultSet.getString("status"),
                        resultSet.getString("message"),
                        resultSet.getString("transaction_id"),
                        resultSet.getString("quantum_boundary_service_id"),
                        resultSet.getString("created_at"),
                        resultSet.getString("recovery_payload")));
            }
            return entries;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query saga log", e);
        }
    }

    @Override
    public void clear() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(deleteAllSql);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot clear saga log", e);
        }
    }

    private void ensureTable() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(createTableSql);
            ensureRecoveryPayloadColumn(statement);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create saga log table", e);
        }
    }

    private void ensureRecoveryPayloadColumn(Statement statement) throws SQLException {
        try {
            statement.execute("alter table " + tableName + " add column recovery_payload clob");
        } catch (SQLException e) {
            String state = e.getSQLState();
            if (!"42S21".equals(state) && !"S0021".equals(state)) {
                String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
                if (!message.contains("duplicate") && !message.contains("already exists")) {
                    throw e;
                }
            }
        }
    }

    private void insert(Connection connection, SagaLogEntry entry) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
            statement.setString(1, entry.sagaId());
            statement.setString(2, entry.globalSerialNumber());
            statement.setString(3, entry.serviceName());
            statement.setString(4, entry.status());
            statement.setString(5, entry.message());
            statement.setString(6, valueOrEmpty(entry.transactionId()));
            statement.setString(7, valueOrEmpty(entry.quantumBoundaryServiceId()));
            statement.setString(8, entry.createdAt());
            statement.setString(9, entry.recoveryPayload());
            statement.executeUpdate();
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeTableName(String tableName) {
        String value = tableName == null || tableName.isBlank() ? DEFAULT_TABLE_NAME : tableName.trim();
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid saga log table name " + value);
        }
        return value;
    }
}
