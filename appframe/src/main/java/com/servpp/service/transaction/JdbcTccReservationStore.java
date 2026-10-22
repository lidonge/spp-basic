package com.servpp.service.transaction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

public class JdbcTccReservationStore implements TccReservationStore {
    public static final String DEFAULT_TABLE_NAME = "tcc_reservation";

    private final DataSource dataSource;
    private final String tableName;
    private final String createTableSql;
    private final String deleteSql;
    private final String insertSql;
    private final String selectOneSql;
    private final String selectAllSql;
    private final String selectExpiredTryingSql;
    private final String deleteAllSql;
    private final String ensureBranchIdColumnSql;
    private final String ensureExpiresAtColumnSql;
    private final String ensureObjectIdColumnSql;

    public JdbcTccReservationStore(DataSource dataSource) {
        this(dataSource, DEFAULT_TABLE_NAME);
    }

    public JdbcTccReservationStore(DataSource dataSource, String tableName) {
        this.dataSource = dataSource;
        this.tableName = normalizeTableName(tableName);
        this.createTableSql = """
                create table if not exists %s (
                    reservation_id varchar(256) primary key,
                    branch_id varchar(256),
                    global_serial_number varchar(256) not null,
                    transaction_id varchar(128) not null,
                    resource_id varchar(256) not null,
                    action_name varchar(256) not null,
                    source_attribute_id varchar(512) not null,
                    reservation_attribute_id varchar(512) not null,
                    quantity decimal(38, 12) not null,
                    status varchar(32) not null,
                    message varchar(1024) not null,
                    updated_at varchar(64) not null,
                    expires_at varchar(64),
                    object_id varchar(1024)
                )
                """.formatted(this.tableName);
        this.deleteSql = "delete from " + this.tableName + " where reservation_id = ?";
        this.insertSql = """
                insert into %s (
                    reservation_id, branch_id, global_serial_number, transaction_id, resource_id, action_name,
                    source_attribute_id, reservation_attribute_id, quantity, status, message, updated_at, expires_at,
                    object_id
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(this.tableName);
        this.selectOneSql = """
                select reservation_id, branch_id, global_serial_number, transaction_id, resource_id, action_name,
                       source_attribute_id, reservation_attribute_id, quantity, status, message, updated_at, expires_at,
                       object_id
                  from %s
                 where reservation_id = ?
                """.formatted(this.tableName);
        this.selectAllSql = """
                select reservation_id, branch_id, global_serial_number, transaction_id, resource_id, action_name,
                       source_attribute_id, reservation_attribute_id, quantity, status, message, updated_at, expires_at,
                       object_id
                  from %s
                 order by updated_at, reservation_id
                """.formatted(this.tableName);
        this.selectExpiredTryingSql = """
                select reservation_id, branch_id, global_serial_number, transaction_id, resource_id, action_name,
                       source_attribute_id, reservation_attribute_id, quantity, status, message, updated_at, expires_at,
                       object_id
                  from %s
                 where status = ?
                   and expires_at is not null
                   and expires_at <= ?
                 order by expires_at, reservation_id
                """.formatted(this.tableName);
        this.deleteAllSql = "delete from " + this.tableName;
        this.ensureBranchIdColumnSql = "alter table " + this.tableName
                + " add column if not exists branch_id varchar(256)";
        this.ensureExpiresAtColumnSql = "alter table " + this.tableName
                + " add column if not exists expires_at varchar(64)";
        this.ensureObjectIdColumnSql = "alter table " + this.tableName
                + " add column if not exists object_id varchar(1024)";
        ensureTable();
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public String getTableName() {
        return tableName;
    }

    @Override
    public void save(TccReservationRecord record) {
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
            throw new IllegalStateException("Cannot save TCC reservation", e);
        }
    }

    @Override
    public Optional<TccReservationRecord> find(String reservationId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectOneSql)) {
            statement.setString(1, reservationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(record(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query TCC reservation", e);
        }
    }

    @Override
    public List<TccReservationRecord> findAll() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectAllSql);
             ResultSet resultSet = statement.executeQuery()) {
            List<TccReservationRecord> records = new ArrayList<>();
            while (resultSet.next()) {
                records.add(record(resultSet));
            }
            return records;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query TCC reservations", e);
        }
    }

    @Override
    public List<TccReservationRecord> findExpiredTrying(String cutoffIso) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectExpiredTryingSql)) {
            statement.setString(1, TccReservationStatus.TRYING.name());
            statement.setString(2, cutoffIso);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<TccReservationRecord> records = new ArrayList<>();
                while (resultSet.next()) {
                    records.add(record(resultSet));
                }
                return records;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query expired TCC reservations", e);
        }
    }

    @Override
    public void clear() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(deleteAllSql);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot clear TCC reservations", e);
        }
    }

    private void ensureTable() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(createTableSql);
            ensureColumn(connection, ensureBranchIdColumnSql);
            ensureColumn(connection, ensureExpiresAtColumnSql);
            ensureColumn(connection, ensureObjectIdColumnSql);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create TCC reservation table", e);
        }
    }

    private void upsert(Connection connection, TccReservationRecord record) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(deleteSql);
             PreparedStatement insert = connection.prepareStatement(insertSql)) {
            delete.setString(1, record.reservationId());
            delete.executeUpdate();

            insert.setString(1, record.reservationId());
            insert.setString(2, record.branchId());
            insert.setString(3, record.globalSerialNumber());
            insert.setString(4, record.transactionId());
            insert.setString(5, record.resourceId());
            insert.setString(6, record.actionName());
            insert.setString(7, record.sourceAttributeId());
            insert.setString(8, record.reservationAttributeId());
            insert.setBigDecimal(9, record.quantity());
            insert.setString(10, record.status().name());
            insert.setString(11, record.message());
            insert.setString(12, record.updatedAt());
            insert.setString(13, record.expiresAt());
            insert.setString(14, record.objectId());
            insert.executeUpdate();
        }
    }

    private TccReservationRecord record(ResultSet resultSet) throws SQLException {
        return new TccReservationRecord(
                resultSet.getString("reservation_id"),
                resultSet.getString("branch_id"),
                resultSet.getString("global_serial_number"),
                resultSet.getString("transaction_id"),
                resultSet.getString("resource_id"),
                resultSet.getString("action_name"),
                resultSet.getString("source_attribute_id"),
                resultSet.getString("reservation_attribute_id"),
                resultSet.getBigDecimal("quantity"),
                TccReservationStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("message"),
                resultSet.getString("updated_at"),
                resultSet.getString("expires_at"),
                resultSet.getString("object_id"));
    }

    public static String updatedNow() {
        return Instant.now().toString();
    }

    private void ensureColumn(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String normalizeTableName(String tableName) {
        String value = tableName == null || tableName.isBlank() ? DEFAULT_TABLE_NAME : tableName.trim();
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid TCC reservation table name " + value);
        }
        return value;
    }
}
