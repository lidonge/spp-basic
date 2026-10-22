package com.servpp.service.repository;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import javax.sql.DataSource;

import com.servpp.service.jdbc.JdbcTransactionScope;

public class JdbcObjectSnapshotStore {
    public static final String DEFAULT_TABLE_NAME = "spp_object_snapshot";
    public static final String TRANSACTION_SNAPSHOT_TYPE = "TransactionSnapshot";

    private final DataSource dataSource;
    private final Function<Object, String> serializer;
    private final String tableName;
    private final String transactionHistoryTableName;
    private final String transactionArchiveTableName;
    private final String createTableSql;
    private final String createTransactionHistoryTableSql;
    private final String createTransactionArchiveTableSql;
    private final String createTransactionTraceIndexSql;
    private final String createTransactionResourcePhaseIndexSql;
    private final String createTransactionArchiveTraceIndexSql;
    private final String createTransactionArchiveResourcePhaseIndexSql;
    private final String deleteOneSql;
    private final String insertOneSql;
    private final String insertTransactionHistorySql;
    private final String archiveTransactionHistoryBeforeSql;
    private final String archiveTransactionHistoryByResourceBeforeSql;
    private final String selectAllSql;
    private final String selectByTypeSql;
    private final String selectTransactionHistorySql;
    private final String selectTransactionHistoryByIndexSql;
    private final String selectTransactionArchiveSql;
    private final String selectTransactionArchiveByIndexSql;
    private final String selectTransactionHistoryIdsForTraceSql;
    private final String selectOneSql;
    private final String deleteAllSql;
    private final String deleteTransactionHistorySql;
    private final String deleteTransactionArchiveSql;
    private final String deleteTransactionHistoryBeforeSql;
    private final String deleteTransactionHistoryByResourceBeforeSql;
    private final String deleteTransactionHistoryBySnapshotIdSql;
    private final String deleteTransactionArchiveBeforeSql;
    private final String deleteTransactionArchiveByResourceBeforeSql;

    public JdbcObjectSnapshotStore(DataSource dataSource, Function<Object, String> serializer) {
        this(dataSource, serializer, DEFAULT_TABLE_NAME);
    }

    public JdbcObjectSnapshotStore(DataSource dataSource, Function<Object, String> serializer, String tableName) {
        this.dataSource = dataSource;
        this.serializer = serializer;
        this.tableName = validateTableName(tableName);
        this.transactionHistoryTableName = validateTableName(this.tableName + "_tx_history");
        this.transactionArchiveTableName = validateTableName(this.tableName + "_tx_archive");
        this.createTableSql = """
                create table if not exists %s (
                    object_type varchar(128) not null,
                    object_id varchar(256) not null,
                    payload_json clob not null,
                    updated_at timestamp not null,
                    revision_token varchar(64) not null,
                    primary key (object_type, object_id)
                )
                """.formatted(this.tableName);
        this.createTransactionHistoryTableSql = """
                create table if not exists %s (
                    snapshot_id varchar(96) not null,
                    object_type varchar(128) not null,
                    object_id varchar(256) not null,
                    trace_id varchar(256),
                    transaction_id varchar(128),
                    resource_id varchar(256),
                    phase varchar(64),
                    payload_json clob not null,
                    updated_at timestamp not null,
                    primary key (snapshot_id)
                )
                """.formatted(this.transactionHistoryTableName);
        this.createTransactionArchiveTableSql = """
                create table if not exists %s (
                    snapshot_id varchar(96) not null,
                    object_type varchar(128) not null,
                    object_id varchar(256) not null,
                    trace_id varchar(256),
                    transaction_id varchar(128),
                    resource_id varchar(256),
                    phase varchar(64),
                    payload_json clob not null,
                    updated_at timestamp not null,
                    archived_at timestamp not null,
                    primary key (snapshot_id)
                )
                """.formatted(this.transactionArchiveTableName);
        this.createTransactionTraceIndexSql = "create index if not exists "
                + validateTableName(this.transactionHistoryTableName + "_trace_idx")
                + " on " + this.transactionHistoryTableName + " (trace_id, updated_at, snapshot_id)";
        this.createTransactionResourcePhaseIndexSql = "create index if not exists "
                + validateTableName(this.transactionHistoryTableName + "_res_phase_idx")
                + " on " + this.transactionHistoryTableName + " (resource_id, phase, updated_at, snapshot_id)";
        this.createTransactionArchiveTraceIndexSql = "create index if not exists "
                + validateTableName(this.transactionArchiveTableName + "_trace_idx")
                + " on " + this.transactionArchiveTableName + " (trace_id, updated_at, snapshot_id)";
        this.createTransactionArchiveResourcePhaseIndexSql = "create index if not exists "
                + validateTableName(this.transactionArchiveTableName + "_res_phase_idx")
                + " on " + this.transactionArchiveTableName + " (resource_id, phase, updated_at, snapshot_id)";
        this.deleteOneSql = """
                delete from %s
                 where object_type = ? and object_id = ?
                """.formatted(this.tableName);
        this.insertOneSql = """
                insert into %s (
                    object_type, object_id, payload_json, updated_at, revision_token
                ) values (?, ?, ?, ?, ?)
                """.formatted(this.tableName);
        this.insertTransactionHistorySql = """
                insert into %s (
                    snapshot_id, object_type, object_id, trace_id, transaction_id, resource_id, phase,
                    payload_json, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(this.transactionHistoryTableName);
        this.archiveTransactionHistoryBeforeSql = """
                insert into %s (
                    snapshot_id, object_type, object_id, trace_id, transaction_id, resource_id, phase,
                    payload_json, updated_at, archived_at
                )
                select snapshot_id, object_type, object_id, trace_id, transaction_id, resource_id, phase,
                       payload_json, updated_at, ?
                  from %s
                 where updated_at < ?
                """.formatted(this.transactionArchiveTableName, this.transactionHistoryTableName);
        this.archiveTransactionHistoryByResourceBeforeSql = """
                insert into %s (
                    snapshot_id, object_type, object_id, trace_id, transaction_id, resource_id, phase,
                    payload_json, updated_at, archived_at
                )
                select snapshot_id, object_type, object_id, trace_id, transaction_id, resource_id, phase,
                       payload_json, updated_at, ?
                  from %s
                 where resource_id = ? and updated_at < ?
                """.formatted(this.transactionArchiveTableName, this.transactionHistoryTableName);
        this.selectAllSql = """
                select object_type, object_id, payload_json, updated_at, revision_token
                  from %s
                 order by object_type, object_id
                """.formatted(this.tableName);
        this.selectByTypeSql = """
                select object_type, object_id, payload_json, updated_at, revision_token
                  from %s
                 where object_type = ?
                 order by object_id
                """.formatted(this.tableName);
        this.selectTransactionHistorySql = """
                select object_type, object_id, payload_json, updated_at
                  from %s
                 order by updated_at, snapshot_id
                """.formatted(this.transactionHistoryTableName);
        this.selectTransactionHistoryByIndexSql = """
                select object_type, object_id, payload_json, updated_at
                  from %s
                 where (? is null or trace_id = ?)
                   and (? is null or resource_id = ?)
                   and (? is null or phase = ?)
                 order by updated_at, snapshot_id
                """.formatted(this.transactionHistoryTableName);
        this.selectTransactionArchiveSql = """
                select object_type, object_id, payload_json, updated_at
                  from %s
                 order by updated_at, snapshot_id
                """.formatted(this.transactionArchiveTableName);
        this.selectTransactionArchiveByIndexSql = """
                select object_type, object_id, payload_json, updated_at
                  from %s
                 where (? is null or trace_id = ?)
                   and (? is null or resource_id = ?)
                   and (? is null or phase = ?)
                 order by updated_at, snapshot_id
                """.formatted(this.transactionArchiveTableName);
        this.selectTransactionHistoryIdsForTraceSql = """
                select snapshot_id
                  from %s
                 where trace_id = ?
                 order by updated_at desc, snapshot_id desc
                """.formatted(this.transactionHistoryTableName);
        this.selectOneSql = """
                select object_type, object_id, payload_json, updated_at, revision_token
                  from %s
                 where object_type = ? and object_id = ?
                """.formatted(this.tableName);
        this.deleteAllSql = "delete from " + this.tableName;
        this.deleteTransactionHistorySql = "delete from " + this.transactionHistoryTableName;
        this.deleteTransactionArchiveSql = "delete from " + this.transactionArchiveTableName;
        this.deleteTransactionHistoryBeforeSql = """
                delete from %s
                 where updated_at < ?
                """.formatted(this.transactionHistoryTableName);
        this.deleteTransactionHistoryByResourceBeforeSql = """
                delete from %s
                 where resource_id = ? and updated_at < ?
                """.formatted(this.transactionHistoryTableName);
        this.deleteTransactionHistoryBySnapshotIdSql = """
                delete from %s
                 where snapshot_id = ?
                """.formatted(this.transactionHistoryTableName);
        this.deleteTransactionArchiveBeforeSql = """
                delete from %s
                 where archived_at < ?
                """.formatted(this.transactionArchiveTableName);
        this.deleteTransactionArchiveByResourceBeforeSql = """
                delete from %s
                 where resource_id = ? and archived_at < ?
                """.formatted(this.transactionArchiveTableName);
        ensureTable();
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public String getTableName() {
        return tableName;
    }

    public String getTransactionHistoryTableName() {
        return transactionHistoryTableName;
    }

    public String getTransactionArchiveTableName() {
        return transactionArchiveTableName;
    }

    public void save(String objectType, String objectId, Object value) {
        try {
            JdbcTransactionScope.execute(dataSource, connection -> {
                save(connection, objectType, objectId, value);
                return null;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot save object snapshot " + objectType + ":" + objectId, e);
        }
    }

    public void verifyWriteTransaction() {
        if (com.servpp.service.transaction.DeclaredTransactionContext.currentMode() == null) {
            throw new IllegalStateException("PERSISTENCE_REQUIRES_DECLARED_TRANSACTION");
        }
        if (JdbcTransactionScope.currentConnection(dataSource).isEmpty()) {
            throw new IllegalStateException("PERSISTENCE_REPOSITORY_NOT_ENLISTED");
        }
    }

    public void insert(String objectType, String objectId, Object value) {
        verifyWriteTransaction();
        Connection connection = JdbcTransactionScope.currentConnection(dataSource).orElseThrow();
        try (PreparedStatement statement = connection.prepareStatement(insertOneSql)) {
            statement.setString(1, objectType);
            statement.setString(2, objectId);
            statement.setString(3, serializer.apply(value));
            statement.setTimestamp(4, Timestamp.from(Instant.now()));
            statement.setString(5, UUID.randomUUID().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            String code = e.getSQLState() != null && e.getSQLState().startsWith("23")
                    ? "PERSISTENCE_ALREADY_EXISTS" : "PERSISTENCE_INSERT_FAILED";
            throw new IllegalStateException(code, e);
        }
    }

    /** The surrounding declared transaction owns commit; a duplicate only restores this statement. */
    public boolean insertIfAbsent(String objectType, String objectId, Object value) {
        verifyWriteTransaction();
        Connection connection=JdbcTransactionScope.currentConnection(dataSource).orElseThrow();
        try {
            java.sql.Savepoint point=connection.setSavepoint();
            try {
                insert(objectType,objectId,value);
                connection.releaseSavepoint(point);
                return true;
            } catch (IllegalStateException failure) {
                if (!(failure.getCause() instanceof SQLException sql) || !"23505".equals(sql.getSQLState())) { throw failure; }
                connection.rollback(point);
                connection.releaseSavepoint(point);
                return false;
            }
        } catch (SQLException failure) { throw new IllegalStateException("PERSISTENCE_INSERT_IF_ABSENT_FAILED",failure); }
    }

    /** Strict deletion joins the declared connection and returns the locked original for physical mirrors. */
    public ObjectSnapshot delete(String objectType, String objectId) {
        verifyWriteTransaction();
        Connection connection = JdbcTransactionScope.currentConnection(dataSource).orElseThrow();
        try {
            ObjectSnapshot original;
            try (PreparedStatement lock = connection.prepareStatement(selectOneSql + " FOR UPDATE")) {
                lock.setString(1, objectType); lock.setString(2, objectId);
                try (ResultSet result = lock.executeQuery()) {
                    if (!result.next()) throw new IllegalStateException("PERSISTENCE_NOT_FOUND");
                    original = readSnapshot(result);
                }
            }
            try (PreparedStatement delete = connection.prepareStatement(deleteOneSql)) {
                delete.setString(1, objectType); delete.setString(2, objectId);
                if (delete.executeUpdate() != 1) throw new IllegalStateException("PERSISTENCE_NOT_FOUND");
            }
            return original;
        } catch (SQLException failure) {
            throw new IllegalStateException("PERSISTENCE_DELETE_FAILED", failure);
        }
    }

    public void update(String objectType, String objectId, Object value,
                       String versionPath, Object expectedVersion) {
        verifyWriteTransaction();
        Connection connection = JdbcTransactionScope.currentConnection(dataSource).orElseThrow();
        try {
            try (PreparedStatement lock = connection.prepareStatement(selectOneSql + " FOR UPDATE")) {
                lock.setString(1, objectType); lock.setString(2, objectId);
                try (ResultSet result = lock.executeQuery()) {
                    if (!result.next()) { throw new IllegalStateException("PERSISTENCE_NOT_FOUND"); }
                    if (versionPath != null) {
                        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getString("payload_json"));
                        for (String part : versionPath.split("\\.")) { json = json == null ? null : json.get(part); }
                        java.math.BigDecimal expected = versionNumber(expectedVersion);
                        if (json == null || json.isNull() || expected.compareTo(versionNumber(json.asText())) != 0) {
                            throw new IllegalStateException("PERSISTENCE_VERSION_CONFLICT");
                        }
                        if (versionNumber(ObjectPropertyAccess.read(value, versionPath)).compareTo(expected) <= 0) {
                            throw new IllegalStateException("PERSISTENCE_VERSION_MUST_ADVANCE");
                        }
                    }
                }
            }
            try (PreparedStatement update = connection.prepareStatement("UPDATE " + tableName
                    + " SET payload_json = ?, updated_at = ?, revision_token = ? WHERE object_type = ? AND object_id = ?")) {
                update.setString(1, serializer.apply(value));
                update.setTimestamp(2, Timestamp.from(Instant.now()));
                update.setString(3, UUID.randomUUID().toString());
                update.setString(4, objectType); update.setString(5, objectId);
                if (update.executeUpdate() != 1) { throw new IllegalStateException("PERSISTENCE_NOT_FOUND"); }
            }
        } catch (java.io.IOException | SQLException e) {
            throw new IllegalStateException("PERSISTENCE_UPDATE_FAILED", e);
        }
    }

    /** Atomic optimistic update: the condition belongs to the write, not a separate read. */
    public String updateRevision(String objectType, String objectId, Object value, String expectedRevision) {
        verifyWriteTransaction();
        if (expectedRevision == null) throw new IllegalStateException("PERSISTENCE_READ_REVISION_REQUIRED");
        String next = UUID.randomUUID().toString();
        Connection connection = JdbcTransactionScope.currentConnection(dataSource).orElseThrow();
        try (PreparedStatement update = connection.prepareStatement("update " + tableName
                + " set payload_json=?, updated_at=?, revision_token=?"
                + " where object_type=? and object_id=? and revision_token=?")) {
            update.setString(1, serializer.apply(value)); update.setTimestamp(2, Timestamp.from(Instant.now()));
            update.setString(3, next); update.setString(4, objectType); update.setString(5, objectId);
            update.setString(6, expectedRevision);
            if (update.executeUpdate() != 1) throw new IllegalStateException("PERSISTENCE_VERSION_CONFLICT");
            return next;
        } catch (SQLException failure) { throw new IllegalStateException("PERSISTENCE_UPDATE_FAILED", failure); }
    }

    private java.math.BigDecimal versionNumber(Object value) {
        try {
            java.math.BigDecimal number = new java.math.BigDecimal(java.util.Objects.requireNonNull(value).toString());
            if (number.stripTrailingZeros().scale() > 0) { throw new NumberFormatException("fractional version"); }
            return number;
        } catch (NumberFormatException | NullPointerException e) {
            throw new IllegalStateException("PERSISTENCE_INVALID_VERSION", e);
        }
    }

    public List<ObjectSnapshot> findAll() {
        try {
            return JdbcTransactionScope.execute(dataSource, connection -> {
                try (PreparedStatement statement = connection.prepareStatement(selectAllSql);
                     ResultSet resultSet = statement.executeQuery()) {
                    List<ObjectSnapshot> snapshots = new ArrayList<>();
                    while (resultSet.next()) {
                        snapshots.add(readSnapshot(resultSet));
                    }
                    return snapshots;
                }
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query object snapshots", e);
        }
    }

    public List<ObjectSnapshot> findByType(String objectType) {
        if (TRANSACTION_SNAPSHOT_TYPE.equals(objectType)) {
            return findTransactionHistory();
        }
        try {
            return JdbcTransactionScope.execute(dataSource, connection -> {
                try (PreparedStatement statement = connection.prepareStatement(selectByTypeSql)) {
                    statement.setString(1, objectType);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        List<ObjectSnapshot> snapshots = new ArrayList<>();
                        while (resultSet.next()) {
                            snapshots.add(readSnapshot(resultSet));
                        }
                        return snapshots;
                    }
                }
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query object snapshots for " + objectType, e);
        }
    }

    public List<ObjectSnapshot> findTransactionHistory() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectTransactionHistorySql);
             ResultSet resultSet = statement.executeQuery()) {
            List<ObjectSnapshot> snapshots = new ArrayList<>();
            while (resultSet.next()) {
                snapshots.add(readSnapshot(resultSet));
            }
            return snapshots;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query transaction snapshot history", e);
        }
    }

    public List<ObjectSnapshot> findTransactionHistory(String traceId, String resourceId, String phase) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectTransactionHistoryByIndexSql)) {
            String normalizedTraceId = normalizeFilter(traceId);
            String normalizedResourceId = normalizeFilter(resourceId);
            String normalizedPhase = normalizeFilter(phase);
            statement.setString(1, normalizedTraceId);
            statement.setString(2, normalizedTraceId);
            statement.setString(3, normalizedResourceId);
            statement.setString(4, normalizedResourceId);
            statement.setString(5, normalizedPhase);
            statement.setString(6, normalizedPhase);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ObjectSnapshot> snapshots = new ArrayList<>();
                while (resultSet.next()) {
                    snapshots.add(readSnapshot(resultSet));
                }
                return snapshots;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query indexed transaction snapshot history", e);
        }
    }

    public List<ObjectSnapshot> findArchivedTransactionHistory() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectTransactionArchiveSql);
             ResultSet resultSet = statement.executeQuery()) {
            List<ObjectSnapshot> snapshots = new ArrayList<>();
            while (resultSet.next()) {
                snapshots.add(readSnapshot(resultSet));
            }
            return snapshots;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query archived transaction snapshot history", e);
        }
    }

    public List<ObjectSnapshot> findArchivedTransactionHistory(String traceId, String resourceId, String phase) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectTransactionArchiveByIndexSql)) {
            String normalizedTraceId = normalizeFilter(traceId);
            String normalizedResourceId = normalizeFilter(resourceId);
            String normalizedPhase = normalizeFilter(phase);
            statement.setString(1, normalizedTraceId);
            statement.setString(2, normalizedTraceId);
            statement.setString(3, normalizedResourceId);
            statement.setString(4, normalizedResourceId);
            statement.setString(5, normalizedPhase);
            statement.setString(6, normalizedPhase);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ObjectSnapshot> snapshots = new ArrayList<>();
                while (resultSet.next()) {
                    snapshots.add(readSnapshot(resultSet));
                }
                return snapshots;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query indexed archived transaction snapshot history", e);
        }
    }

    public Optional<ObjectSnapshot> find(String objectType, String objectId) {
        try {
            return JdbcTransactionScope.execute(dataSource, connection -> {
                try (PreparedStatement statement = connection.prepareStatement(selectOneSql)) {
                    statement.setString(1, objectType);
                    statement.setString(2, objectId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            return Optional.empty();
                        }
                        return Optional.of(readSnapshot(resultSet));
                    }
                }
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query object snapshot " + objectType + ":" + objectId, e);
        }
    }

    public Optional<ObjectSnapshot> findForUpdate(String objectType, String objectId) {
        verifyWriteTransaction();
        Connection connection = JdbcTransactionScope.currentConnection(dataSource).orElseThrow();
        try (PreparedStatement statement = connection.prepareStatement(selectOneSql + " FOR UPDATE")) {
            statement.setString(1, objectType);
            statement.setString(2, objectId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readSnapshot(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot lock object snapshot " + objectType + ":" + objectId, e);
        }
    }

    public int pruneTransactionHistoryBefore(Instant cutoff) {
        if (cutoff == null) {
            throw new IllegalArgumentException("Transaction snapshot history cutoff is required.");
        }
        try {
            return JdbcTransactionScope.execute(dataSource, connection -> {
                try (PreparedStatement statement = connection.prepareStatement(deleteTransactionHistoryBeforeSql)) {
                    statement.setTimestamp(1, Timestamp.from(cutoff));
                    return statement.executeUpdate();
                }
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot prune transaction snapshot history before " + cutoff, e);
        }
    }

    public int archiveTransactionHistoryBefore(Instant cutoff) {
        if (cutoff == null) {
            throw new IllegalArgumentException("Transaction snapshot history archive cutoff is required.");
        }
        try {
            return JdbcTransactionScope.execute(dataSource, connection -> {
                Timestamp cutoffTimestamp = Timestamp.from(cutoff);
                Timestamp archivedAt = Timestamp.from(Instant.now());
                int archived;
                try (PreparedStatement archive = connection.prepareStatement(archiveTransactionHistoryBeforeSql)) {
                    archive.setTimestamp(1, archivedAt);
                    archive.setTimestamp(2, cutoffTimestamp);
                    archived = archive.executeUpdate();
                }
                try (PreparedStatement delete = connection.prepareStatement(deleteTransactionHistoryBeforeSql)) {
                    delete.setTimestamp(1, cutoffTimestamp);
                    delete.executeUpdate();
                }
                return archived;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot archive transaction snapshot history before " + cutoff, e);
        }
    }

    public int archiveTransactionHistoryBefore(String resourceId, Instant cutoff) {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("Transaction snapshot history archive resource id is required.");
        }
        if (cutoff == null) {
            throw new IllegalArgumentException("Transaction snapshot history archive cutoff is required.");
        }
        try {
            return JdbcTransactionScope.execute(dataSource, connection -> {
                Timestamp cutoffTimestamp = Timestamp.from(cutoff);
                Timestamp archivedAt = Timestamp.from(Instant.now());
                int archived;
                try (PreparedStatement archive =
                             connection.prepareStatement(archiveTransactionHistoryByResourceBeforeSql)) {
                    archive.setTimestamp(1, archivedAt);
                    archive.setString(2, resourceId);
                    archive.setTimestamp(3, cutoffTimestamp);
                    archived = archive.executeUpdate();
                }
                try (PreparedStatement delete =
                             connection.prepareStatement(deleteTransactionHistoryByResourceBeforeSql)) {
                    delete.setString(1, resourceId);
                    delete.setTimestamp(2, cutoffTimestamp);
                    delete.executeUpdate();
                }
                return archived;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot archive transaction snapshot history for resource "
                    + resourceId + " before " + cutoff, e);
        }
    }

    public int retainLatestTransactionHistory(String traceId, int keepLatest) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("Transaction snapshot history trace id is required.");
        }
        if (keepLatest < 0) {
            throw new IllegalArgumentException("Transaction snapshot history retention count must not be negative.");
        }
        try {
            return JdbcTransactionScope.execute(dataSource, connection -> {
                List<String> staleSnapshotIds = transactionHistoryIdsToDelete(connection, traceId, keepLatest);
                int deleted = 0;
                try (PreparedStatement delete = connection.prepareStatement(deleteTransactionHistoryBySnapshotIdSql)) {
                    for (String snapshotId : staleSnapshotIds) {
                        delete.setString(1, snapshotId);
                        deleted += delete.executeUpdate();
                    }
                }
                return deleted;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot retain transaction snapshot history for trace " + traceId, e);
        }
    }

    public int pruneArchivedTransactionHistoryBefore(Instant cutoff) {
        if (cutoff == null) {
            throw new IllegalArgumentException("Archived transaction snapshot cutoff is required.");
        }
        try {
            return JdbcTransactionScope.execute(dataSource, connection -> {
                try (PreparedStatement delete = connection.prepareStatement(deleteTransactionArchiveBeforeSql)) {
                    delete.setTimestamp(1, Timestamp.from(cutoff));
                    return delete.executeUpdate();
                }
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot prune archived transaction snapshot history before " + cutoff, e);
        }
    }

    public int pruneArchivedTransactionHistoryBefore(String resourceId, Instant cutoff) {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("Archived transaction snapshot resource id is required.");
        }
        if (cutoff == null) {
            throw new IllegalArgumentException("Archived transaction snapshot cutoff is required.");
        }
        try {
            return JdbcTransactionScope.execute(dataSource, connection -> {
                try (PreparedStatement delete =
                             connection.prepareStatement(deleteTransactionArchiveByResourceBeforeSql)) {
                    delete.setString(1, resourceId);
                    delete.setTimestamp(2, Timestamp.from(cutoff));
                    return delete.executeUpdate();
                }
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot prune archived transaction snapshot history for resource "
                    + resourceId + " before " + cutoff, e);
        }
    }

    public void clear() {
        try {
            JdbcTransactionScope.execute(dataSource, connection -> {
                clear(connection);
                return null;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot clear object snapshots", e);
        }
    }

    private List<String> transactionHistoryIdsToDelete(Connection connection, String traceId,
                                                       int keepLatest) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(selectTransactionHistoryIdsForTraceSql)) {
            statement.setString(1, traceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> staleSnapshotIds = new ArrayList<>();
                int index = 0;
                while (resultSet.next()) {
                    if (index >= keepLatest) {
                        staleSnapshotIds.add(resultSet.getString("snapshot_id"));
                    }
                    index++;
                }
                return staleSnapshotIds;
            }
        }
    }

    private void ensureTable() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(createTableSql);
            statement.execute(createTransactionHistoryTableSql);
            statement.execute(createTransactionArchiveTableSql);
            ensureTransactionHistoryColumn(connection, "trace_id", "varchar(256)");
            ensureTransactionHistoryColumn(connection, "transaction_id", "varchar(128)");
            ensureTransactionHistoryColumn(connection, "resource_id", "varchar(256)");
            ensureTransactionHistoryColumn(connection, "phase", "varchar(64)");
            statement.execute(createTransactionTraceIndexSql);
            statement.execute(createTransactionResourcePhaseIndexSql);
            statement.execute(createTransactionArchiveTraceIndexSql);
            statement.execute(createTransactionArchiveResourcePhaseIndexSql);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create object snapshot table", e);
        }
    }

    private void clear(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(deleteAllSql);
            statement.executeUpdate(deleteTransactionHistorySql);
            statement.executeUpdate(deleteTransactionArchiveSql);
        }
    }

    private void save(Connection connection, String objectType, String objectId, Object value) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(deleteOneSql);
             PreparedStatement insert = connection.prepareStatement(insertOneSql)) {
            String payloadJson = serializer.apply(value);
            Timestamp updatedAt = Timestamp.from(Instant.now());
            delete.setString(1, objectType);
            delete.setString(2, objectId);
            delete.executeUpdate();

            insert.setString(1, objectType);
            insert.setString(2, objectId);
            insert.setString(3, payloadJson);
            insert.setTimestamp(4, updatedAt);
            insert.setString(5, UUID.randomUUID().toString());
            insert.executeUpdate();

            if (TRANSACTION_SNAPSHOT_TYPE.equals(objectType)) {
                saveTransactionHistory(connection, objectType, objectId, value, payloadJson, updatedAt);
            }
        }
    }

    private void saveTransactionHistory(Connection connection, String objectType, String objectId,
                                        Object value, String payloadJson, Timestamp updatedAt) throws SQLException {
        TransactionHistoryIndex index = TransactionHistoryIndex.from(objectId, value);
        try (PreparedStatement insert = connection.prepareStatement(insertTransactionHistorySql)) {
            insert.setString(1, "%020d-%s".formatted(System.nanoTime(), UUID.randomUUID()));
            insert.setString(2, objectType);
            insert.setString(3, objectId);
            insert.setString(4, index.traceId());
            insert.setString(5, index.transactionId());
            insert.setString(6, index.resourceId());
            insert.setString(7, index.phase());
            insert.setString(8, payloadJson);
            insert.setTimestamp(9, updatedAt);
            insert.executeUpdate();
        }
    }

    private void ensureTransactionHistoryColumn(Connection connection, String columnName,
                                                String columnDefinition) throws SQLException {
        if (hasTransactionHistoryColumn(connection, columnName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("alter table " + transactionHistoryTableName
                    + " add column " + columnName + " " + columnDefinition);
        }
    }

    private boolean hasTransactionHistoryColumn(Connection connection, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        for (String tablePattern : List.of(transactionHistoryTableName,
                transactionHistoryTableName.toUpperCase(Locale.ROOT),
                transactionHistoryTableName.toLowerCase(Locale.ROOT))) {
            for (String columnPattern : List.of(columnName, columnName.toUpperCase(Locale.ROOT),
                    columnName.toLowerCase(Locale.ROOT))) {
                try (ResultSet resultSet = metaData.getColumns(null, null, tablePattern, columnPattern)) {
                    if (resultSet.next()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static ObjectSnapshot readSnapshot(ResultSet resultSet) throws SQLException {
        String revision = null;
        for (int index = 1; index <= resultSet.getMetaData().getColumnCount(); index++)
            if ("revision_token".equalsIgnoreCase(resultSet.getMetaData().getColumnLabel(index)))
                revision = resultSet.getString(index);
        return new ObjectSnapshot(
                resultSet.getString("object_type"),
                resultSet.getString("object_id"),
                resultSet.getString("payload_json"),
                resultSet.getTimestamp("updated_at").toInstant().toString(), revision);
    }

    private static String validateTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return DEFAULT_TABLE_NAME;
        }
        String normalized = tableName.trim();
        if (!normalized.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Object snapshot table name is not a safe SQL identifier: "
                    + tableName);
        }
        return normalized;
    }

    private static String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record TransactionHistoryIndex(String traceId, String transactionId, String resourceId, String phase) {
        private static TransactionHistoryIndex from(String objectId, Object value) {
            return new TransactionHistoryIndex(
                    firstNonBlank(readString(value, "globalSerialNumber"), readString(value, "traceId"), objectId),
                    readString(value, "transactionId"),
                    readString(value, "resourceId"),
                    readString(value, "phase"));
        }

        private static String readString(Object value, String name) {
            Object raw = readValue(value, name);
            return raw == null ? null : raw.toString();
        }

        private static Object readValue(Object value, String name) {
            if (value == null) {
                return null;
            }
            if (value instanceof Map<?, ?> map) {
                return map.get(name);
            }
            try {
                Method method = value.getClass().getMethod(name);
                if (method.getParameterCount() == 0) {
                    return method.invoke(value);
                }
            } catch (ReflectiveOperationException ignored) {
                // Try JavaBean style below.
            }
            String accessorSuffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            try {
                Method method = value.getClass().getMethod("get" + accessorSuffix);
                if (method.getParameterCount() == 0) {
                    return method.invoke(value);
                }
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
            return null;
        }

        private static String firstNonBlank(String... values) {
            if (values == null) {
                return null;
            }
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        }
    }
}
