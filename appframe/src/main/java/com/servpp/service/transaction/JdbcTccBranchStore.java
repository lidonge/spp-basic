package com.servpp.service.transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

public class JdbcTccBranchStore implements TccBranchStore {
    public static final String DEFAULT_TABLE_NAME = "tcc_branch";

    private final DataSource dataSource;
    private final String tableName;
    private final String createTableSql;
    private final String deleteSql;
    private final String insertSql;
    private final String selectOneSql;
    private final String selectAllSql;
    private final String deleteAllSql;
    private final String ensureRecoveryPayloadColumnSql;

    public JdbcTccBranchStore(DataSource dataSource) {
        this(dataSource, DEFAULT_TABLE_NAME);
    }

    public JdbcTccBranchStore(DataSource dataSource, String tableName) {
        this.dataSource = dataSource;
        this.tableName = normalizeTableName(tableName);
        this.createTableSql = """
                create table if not exists %s (
                    branch_id varchar(128) primary key,
                    global_serial_number varchar(256) not null,
                    resource_id varchar(256) not null,
                    action_name varchar(256) not null,
                    status varchar(32) not null,
                    message varchar(1024) not null,
                    updated_at varchar(64) not null,
                    recovery_payload clob
                )
                """.formatted(this.tableName);
        this.deleteSql = "delete from " + this.tableName + " where branch_id = ?";
        this.insertSql = """
                insert into %s (
                    branch_id, global_serial_number, resource_id, action_name,
                    status, message, updated_at, recovery_payload
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(this.tableName);
        this.selectOneSql = """
                select branch_id, global_serial_number, resource_id, action_name,
                       status, message, updated_at, recovery_payload
                  from %s
                 where branch_id = ?
                """.formatted(this.tableName);
        this.selectAllSql = """
                select branch_id, global_serial_number, resource_id, action_name,
                       status, message, updated_at, recovery_payload
                  from %s
                 order by updated_at, branch_id
                """.formatted(this.tableName);
        this.deleteAllSql = "delete from " + this.tableName;
        this.ensureRecoveryPayloadColumnSql = "alter table " + this.tableName
                + " add column if not exists recovery_payload clob";
        ensureTable();
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public String getTableName() {
        return tableName;
    }

    @Override
    public void save(TccBranchRecord record) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                upsert(connection, record);
                connection.commit();
            } catch (SQLException | RuntimeException | Error e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot save TCC branch", e);
        }
    }

    @Override
    public Optional<TccBranchRecord> find(String branchId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectOneSql)) {
            statement.setString(1, branchId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(record(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query TCC branch", e);
        }
    }

    @Override
    public List<TccBranchRecord> findAll() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectAllSql);
             ResultSet resultSet = statement.executeQuery()) {
            List<TccBranchRecord> records = new ArrayList<>();
            while (resultSet.next()) {
                records.add(record(resultSet));
            }
            return records;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query TCC branches", e);
        }
    }

    @Override
    public void clear() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(deleteAllSql);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot clear TCC branches", e);
        }
    }

    private void ensureTable() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(createTableSql);
            ensureRecoveryPayloadColumn(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create TCC branch table", e);
        }
    }

    private void upsert(Connection connection, TccBranchRecord record) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(deleteSql);
             PreparedStatement insert = connection.prepareStatement(insertSql)) {
            delete.setString(1, record.branchId());
            delete.executeUpdate();

            insert.setString(1, record.branchId());
            insert.setString(2, record.globalSerialNumber());
            insert.setString(3, record.resourceId());
            insert.setString(4, record.actionName());
            insert.setString(5, record.status().name());
            insert.setString(6, record.message());
            insert.setString(7, record.updatedAt());
            insert.setString(8, record.recoveryPayload());
            insert.executeUpdate();
        }
    }

    private TccBranchRecord record(ResultSet resultSet) throws SQLException {
        return new TccBranchRecord(
                resultSet.getString("branch_id"),
                resultSet.getString("global_serial_number"),
                resultSet.getString("resource_id"),
                resultSet.getString("action_name"),
                TccBranchStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("message"),
                resultSet.getString("updated_at"),
                resultSet.getString("recovery_payload"));
    }

    private void ensureRecoveryPayloadColumn(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(ensureRecoveryPayloadColumnSql);
        }
    }

    private static String normalizeTableName(String tableName) {
        String value = tableName == null || tableName.isBlank() ? DEFAULT_TABLE_NAME : tableName.trim();
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid TCC branch table name " + value);
        }
        return value;
    }
}
