package com.servpp.service.transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import com.servpp.service.jdbc.JdbcTransactionScope;

public class JdbcQuantumJournalStore implements QuantumJournalStore {
    private static final String DEFAULT_JOURNAL_TABLE = "quantum_journal";
    private static final String DEFAULT_VALUE_TABLE = "quantum_attribute_value";
    public static final String DEFAULT_OBJECT_ID = "GLOBAL";
    private static final String CREATE_TABLE_TEMPLATE = """
            create table if not exists %s (
                transaction_id varchar(128) not null,
                boundary_service_id varchar(512) not null,
                status varchar(32) not null,
                sequence_no integer not null,
                group_id varchar(256) not null,
                attribute_id varchar(512) not null,
                object_id varchar(512) not null,
                before_value decimal(38, 12) not null,
                delta_value decimal(38, 12) not null,
                after_value decimal(38, 12) not null,
                primary key (transaction_id, sequence_no)
            )
            """;
    private static final String CREATE_VALUE_TABLE_TEMPLATE = """
            create table if not exists %s (
                attribute_id varchar(512) not null,
                object_id varchar(512) not null,
                group_id varchar(256) not null,
                boundary_service_id varchar(512) not null,
                transaction_id varchar(128) not null,
                current_value decimal(38, 12) not null,
                primary key (attribute_id, object_id)
            )
            """;
    private static final String INSERT_TEMPLATE = """
            insert into %s (
                transaction_id, boundary_service_id, status, sequence_no,
                group_id, attribute_id, object_id, before_value, delta_value, after_value
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_BY_TRANSACTION_TEMPLATE = """
            select transaction_id, boundary_service_id, status, sequence_no,
                   group_id, attribute_id, object_id, before_value, delta_value, after_value
              from %s
             where transaction_id = ?
             order by sequence_no
            """;
    private static final String SELECT_ALL_TEMPLATE = """
            select transaction_id, boundary_service_id, status, sequence_no,
                   group_id, attribute_id, object_id, before_value, delta_value, after_value
              from %s
             order by transaction_id, sequence_no
            """;
    private static final String DELETE_VALUE_TEMPLATE = "delete from %s where attribute_id = ? and object_id = ?";
    private static final String INSERT_VALUE_TEMPLATE = """
            insert into %s (
                attribute_id, object_id, group_id, boundary_service_id, transaction_id, current_value
            ) values (?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_VALUE_TEMPLATE = """
            select current_value
              from %s
             where attribute_id = ?
               and object_id = ?
            """;
    private static final String SELECT_ALL_VALUES_TEMPLATE = """
            select attribute_id, object_id, group_id, boundary_service_id, transaction_id, current_value
              from %s
             order by attribute_id, object_id
            """;

    private final DataSource dataSource;
    private final String journalTable;
    private final String valueTable;
    private final boolean textDecimals;
    private final String createTable;
    private final String createValueTable;
    private final String insert;
    private final String selectByTransaction;
    private final String selectAll;
    private final String deleteValue;
    private final String insertValue;
    private final String selectValue;
    private final String selectAllValues;
    private final String selectValueForUpdate;
    private final String deleteAll;
    private final String deleteAllValues;

    public JdbcQuantumJournalStore(DataSource dataSource) {
        this(dataSource, DEFAULT_JOURNAL_TABLE, DEFAULT_VALUE_TABLE);
    }

    public JdbcQuantumJournalStore(DataSource dataSource, String journalTable, String valueTable) {
        this(dataSource, journalTable, valueTable, "decimal");
    }

    /** Physical representation choice; business arithmetic and rounding remain outside storage. */
    public JdbcQuantumJournalStore(DataSource dataSource, String journalTable, String valueTable, String decimalStorage) {
        if (!java.util.Set.of("decimal", "text").contains(decimalStorage == null ? "" : decimalStorage)) {
            throw new IllegalArgumentException("Unsupported quantum decimalStorage: " + decimalStorage);
        }
        textDecimals = "text".equals(decimalStorage);
        this.dataSource = dataSource;
        this.journalTable = normalizeTableName(journalTable, DEFAULT_JOURNAL_TABLE);
        this.valueTable = normalizeTableName(valueTable, DEFAULT_VALUE_TABLE);
        createTable = decimalSchema(CREATE_TABLE_TEMPLATE).formatted(this.journalTable);
        createValueTable = decimalSchema(CREATE_VALUE_TABLE_TEMPLATE).formatted(this.valueTable);
        insert = INSERT_TEMPLATE.formatted(this.journalTable);
        selectByTransaction = SELECT_BY_TRANSACTION_TEMPLATE.formatted(this.journalTable);
        selectAll = SELECT_ALL_TEMPLATE.formatted(this.journalTable);
        deleteValue = DELETE_VALUE_TEMPLATE.formatted(this.valueTable);
        insertValue = INSERT_VALUE_TEMPLATE.formatted(this.valueTable);
        selectValue = SELECT_VALUE_TEMPLATE.formatted(this.valueTable);
        selectAllValues = SELECT_ALL_VALUES_TEMPLATE.formatted(this.valueTable);
        selectValueForUpdate = selectValue + " for update";
        deleteAll = "delete from " + this.journalTable;
        deleteAllValues = "delete from " + this.valueTable;
        ensureTable();
    }

    @Override
    public boolean supportsInvocationBounds() { return true; }

    @Override
    public void append(TransactionContext context, QuantumEntanglementBoundary boundary) {
        try {
            JdbcTransactionScope.execute(dataSource, connection -> {
                appendInTransaction(connection, context, boundary);
                return null;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot append quantum journal", e);
        }
    }

    @Override
    public List<QuantumJournalEntry> findByTransactionId(String transactionId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectByTransaction)) {
            statement.setString(1, transactionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return readEntries(resultSet);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query quantum journal", e);
        }
    }

    public List<QuantumJournalEntry> findAll() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectAll);
             ResultSet resultSet = statement.executeQuery()) {
            return readEntries(resultSet);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query quantum journal", e);
        }
    }

    /** Reverse committed deltas against current values, preserving all later settlements. */
    public void reverseDeltas(TransactionContext context, QuantumEntanglementBoundary original) {
        executeInTransaction(() -> {
            List<QuantumJournalEntry> prior = findByTransactionId(context.getId());
            if (!prior.isEmpty()) {
                if (prior.size() != original.getRecords().size()) throw new IllegalStateException("QUANTUM_REVERSAL_RETRY_MISMATCH");
                for (int index = 0; index < prior.size(); index++) {
                    var done = prior.get(index); var source = original.getRecords().get(index);
                    if (!done.getBoundaryServiceId().equals(original.getServiceId()) || !done.getAttributeId().equals(source.getAttributeId()) || !done.getObjectId().equals(source.getObjectId())
                            || !done.getGroupId().equals(source.getGroupId()) || done.getDeltaValue().compareTo(source.getDeltaValue().negate()) != 0
                            || done.getStatus() != QuantumEntanglementBoundary.Status.ROLLED_BACK)
                        throw new IllegalStateException("QUANTUM_REVERSAL_RETRY_MISMATCH");
                }
                return;
            }
            var reversal = new QuantumEntanglementBoundary(original.getServiceId(), original.getResourceIds());
            var pending = new java.util.HashMap<String,BigDecimal>();
            for (var source : original.getRecords()) {
                String key = source.getAttributeId() + ":" + source.getObjectId();
                BigDecimal before = pending.containsKey(key) ? pending.get(key)
                        : findCurrentValueForUpdate(source.getAttributeId(),source.getObjectId()).orElseThrow(
                            () -> new IllegalStateException("QUANTUM_REVERSAL_VALUE_MISSING"));
                BigDecimal delta = source.getDeltaValue().negate();
                BigDecimal after = before.add(delta); pending.put(key,after);
                // Forward bounds are not the business constraints of a reversal. Default reversal is unbounded.
                reversal.addRecord(new QuantumConservationRecord(source.getGroupId(),source.getAttributeId(),source.getObjectId(),before,delta,after));
            }
            reversal.validateConservation(); reversal.markRolledBack(); append(context,reversal);
        });
    }

    public Optional<BigDecimal> findCurrentValue(String attributeId) {
        return findCurrentValue(attributeId, DEFAULT_OBJECT_ID);
    }

    public Optional<BigDecimal> findCurrentValue(String attributeId, String objectId) {
        try {
            Optional<Connection> current = JdbcTransactionScope.currentConnection(dataSource);
            if (current.isPresent()) {
                return findCurrentValue(current.get(), attributeId, objectId, false);
            }
            try (Connection connection = dataSource.getConnection()) {
                return findCurrentValue(connection, attributeId, objectId, false);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query quantum attribute value", e);
        }
    }

    public Optional<BigDecimal> findCurrentValueForUpdate(String attributeId) {
        return findCurrentValueForUpdate(attributeId, DEFAULT_OBJECT_ID);
    }

    public Optional<BigDecimal> findCurrentValueForUpdate(String attributeId, String objectId) {
        try {
            Optional<Connection> current = JdbcTransactionScope.currentConnection(dataSource);
            if (current.isPresent()) {
                return findCurrentValue(current.get(), attributeId, objectId, true);
            }
            return JdbcTransactionScope.execute(dataSource,
                    connection -> findCurrentValue(connection, attributeId, objectId, true));
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query quantum attribute value for update", e);
        }
    }

    public void executeInTransaction(Runnable action) {
        try {
            JdbcTransactionScope.execute(dataSource, connection -> {
                action.run();
                return null;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot execute quantum transaction", e);
        }
    }

    private Optional<BigDecimal> findCurrentValue(Connection connection, String attributeId, String objectId,
                                                  boolean forUpdate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(forUpdate ? selectValueForUpdate : selectValue)) {
            statement.setString(1, attributeId);
            statement.setString(2, normalizeObjectId(objectId));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(resultSet.getBigDecimal("current_value"));
            }
        }
    }

    private List<QuantumJournalEntry> readEntries(ResultSet resultSet) throws SQLException {
        List<QuantumJournalEntry> entries = new ArrayList<>();
        while (resultSet.next()) {
            QuantumConservationRecord record = new QuantumConservationRecord(
                    resultSet.getString("group_id"),
                    resultSet.getString("attribute_id"),
                    resultSet.getString("object_id"),
                    resultSet.getBigDecimal("before_value"),
                    resultSet.getBigDecimal("delta_value"),
                    resultSet.getBigDecimal("after_value"));
            entries.add(new QuantumJournalEntry(
                    resultSet.getString("transaction_id"),
                    resultSet.getString("boundary_service_id"),
                    QuantumEntanglementBoundary.Status.valueOf(resultSet.getString("status")),
                    resultSet.getInt("sequence_no"),
                    record));
        }
        return entries;
    }

    public List<QuantumAttributeValue> findCurrentValues() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectAllValues);
             ResultSet resultSet = statement.executeQuery()) {
            List<QuantumAttributeValue> values = new ArrayList<>();
            while (resultSet.next()) {
                values.add(new QuantumAttributeValue(
                        resultSet.getString("attribute_id"),
                        resultSet.getString("object_id"),
                        resultSet.getString("group_id"),
                        resultSet.getString("boundary_service_id"),
                        resultSet.getString("transaction_id"),
                        resultSet.getBigDecimal("current_value")));
            }
            return values;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query quantum attribute values", e);
        }
    }

    /** Provision a new participant's facts in the same declared resource transaction. */
    public void insertInitialValue(String attributeId, String objectId, BigDecimal currentValue) {
        Connection connection = JdbcTransactionScope.currentConnection(dataSource)
                .orElseThrow(() -> new IllegalStateException("QUANTUM_PROVISION_RESOURCE_NOT_ENLISTED"));
        BigDecimal stored = currentValue;
        if (!textDecimals) {
            stored = currentValue.setScale(12, java.math.RoundingMode.UNNECESSARY);
            if (stored.precision() > 38) throw new IllegalStateException("QUANTUM_VALUE_STORAGE_OVERFLOW");
        }
        try (PreparedStatement statement = connection.prepareStatement(insertValue)) {
            statement.setString(1, attributeId); statement.setString(2, normalizeObjectId(objectId));
            statement.setString(3, "provisioning"); statement.setString(4, ""); statement.setString(5, "");
            if (textDecimals) statement.setString(6, stored.toPlainString()); else statement.setBigDecimal(6, stored);
            statement.executeUpdate();
        } catch (SQLException failure) { throw new IllegalStateException("QUANTUM_PROVISION_FAILED", failure); }
    }

    /** Missing-only technical provisioning; never overwrite previously traded values. */
    public void initializeValueIfAbsent(String attributeId, String objectId, BigDecimal currentValue) {
        if (!com.servpp.service.runtime.DatabaseInitializationContext.isActive())
            throw new IllegalStateException("DATABASE_INITIALIZATION_CONTEXT_REQUIRED");
        provisionMissingValue(attributeId, objectId, currentValue);
    }

    /** A zero fact establishes an address; it transfers no quantity between participants. */
    public void initializeZeroValueIfAbsent(String attributeId, String objectId) {
        try { provisionMissingValue(attributeId, objectId, BigDecimal.ZERO); }
        catch (IllegalStateException failure) {
            // Another technical replay may have inserted the same address concurrently.
            Throwable cause = failure.getCause();
            if (cause instanceof SQLException sql && sql.getSQLState() != null
                    && sql.getSQLState().startsWith("23")
                    && findCurrentValue(attributeId, objectId).isPresent()) return;
            throw failure;
        }
    }

    private void provisionMissingValue(String attributeId, String objectId, BigDecimal currentValue) {
        try {
            JdbcTransactionScope.execute(dataSource, connection -> {
                if (findCurrentValue(connection, attributeId, objectId, false).isEmpty())
                    insertInitialValue(attributeId, objectId, currentValue);
                return null;
            });
        } catch (SQLException failure) {
            throw new IllegalStateException("QUANTUM_INITIALIZATION_FAILED", failure);
        }
    }

    public void seedCurrentValue(String groupId, String attributeId, BigDecimal currentValue) {
        seedCurrentValue(groupId, attributeId, DEFAULT_OBJECT_ID, currentValue);
    }

    public void seedCurrentValue(String groupId, String attributeId, String objectId, BigDecimal currentValue) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                upsertValue(connection, null, null, groupId, attributeId, objectId, currentValue);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot seed quantum attribute value", e);
        }
    }

    @Override
    public void clear() {
        try (Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement()) {
            statement.executeUpdate(deleteAll);
            statement.executeUpdate(deleteAllValues);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot clear quantum journal", e);
        }
    }

    private String decimalSchema(String template) {
        return textDecimals ? template.replace("decimal(38, 12)", "varchar(4096)") : template;
    }

    private void verifyTextColumns(Statement statement, String table, String columns) throws SQLException {
        try (ResultSet result = statement.executeQuery("select " + columns + " from " + table + " where 1 = 0")) {
            var metadata = result.getMetaData();
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                int type = metadata.getColumnType(index);
                boolean text = java.util.Set.of(java.sql.Types.CHAR, java.sql.Types.VARCHAR, java.sql.Types.LONGVARCHAR,
                        java.sql.Types.NCHAR, java.sql.Types.NVARCHAR, java.sql.Types.LONGNVARCHAR,
                        java.sql.Types.CLOB, java.sql.Types.NCLOB).contains(type);
                if (!text || metadata.getPrecision(index) < 4096) {
                    throw new IllegalStateException("QUANTUM_DECIMAL_STORAGE_MISMATCH: " + table + "." + metadata.getColumnName(index));
                }
            }
        }
    }

    private void ensureTable() {
        try (Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement()) {
            statement.execute(createTable);
            statement.execute(createValueTable);
            if (textDecimals) {
                verifyTextColumns(statement, journalTable, "before_value, delta_value, after_value");
                verifyTextColumns(statement, valueTable, "current_value");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create quantum journal table", e);
        }
    }

    private List<QuantumJournalEntry> findByTransactionId(Connection connection,
                                                          String transactionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(selectByTransaction)) {
            statement.setString(1, transactionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return readEntries(resultSet);
            }
        }
    }

    private void appendInTransaction(Connection connection, TransactionContext context,
                                     QuantumEntanglementBoundary boundary) throws SQLException {
        List<QuantumJournalEntry> existingEntries = findByTransactionId(connection, context.getId());
        if (!existingEntries.isEmpty()) {
            QuantumJournalIdempotency.requireMatchingRetry(context, boundary, existingEntries);
            return;
        }
        List<QuantumConservationRecord> records = boundary.getRecords();
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            for (int i = 0; i < records.size(); i++) {
                QuantumConservationRecord record = records.get(i);
                statement.setString(1, context.getId());
                statement.setString(2, boundary.getServiceId());
                statement.setString(3, boundary.getStatus().name());
                statement.setInt(4, i);
                statement.setString(5, record.getGroupId());
                statement.setString(6, record.getAttributeId());
                statement.setString(7, normalizeObjectId(record.getObjectId()));
                statement.setBigDecimal(8, record.getBeforeValue());
                statement.setBigDecimal(9, record.getDeltaValue());
                statement.setBigDecimal(10, record.getAfterValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        upsertValues(connection, context, boundary, records);
    }

    private void upsertValues(Connection connection, TransactionContext context, QuantumEntanglementBoundary boundary,
                              List<QuantumConservationRecord> records) throws SQLException {
        for (QuantumConservationRecord record : records) {
            boolean exists = verifyBeforeValue(connection, record);
            settleValue(connection, context, boundary, record, exists);
        }
    }

    /** The locked before-value supplies the calculation basis; bounds are predicates of the write. */
    private boolean verifyBeforeValue(Connection connection, QuantumConservationRecord record) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(selectValueForUpdate)) {
            statement.setString(1, record.getAttributeId());
            statement.setString(2, normalizeObjectId(record.getObjectId()));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return false;
                BigDecimal currentValue = resultSet.getBigDecimal("current_value");
                if (currentValue.compareTo(record.getBeforeValue()) != 0) {
                    throw new IllegalStateException("quantum attribute " + record.getAttributeId()
                            + " object " + record.getObjectId()
                            + " current value " + currentValue + " does not match before value "
                            + record.getBeforeValue());
                }
                return true;
            }
        }
    }

    private void settleValue(Connection connection, TransactionContext context, QuantumEntanglementBoundary boundary,
                             QuantumConservationRecord record, boolean exists) throws SQLException {
        if (!textDecimals) {
            // A storage codec must not silently change the value whose interval is checked.
            BigDecimal stored = record.getAfterValue().setScale(12, java.math.RoundingMode.UNNECESSARY);
            if (stored.precision() > 38)
                throw new IllegalStateException("QUANTUM_DECIMAL_STORAGE_CAPACITY_EXCEEDED");
        }
        String sql = exists
                ? "update " + valueTable + " set group_id = ?, boundary_service_id = ?, transaction_id = ?, current_value = ?"
                    + " where attribute_id = ? and object_id = ?"
                : "insert into " + valueTable
                    + " (group_id, boundary_service_id, transaction_id, current_value, attribute_id, object_id)"
                    + " select ?, ?, ?, ?, ?, ? where 1 = 1";
        // Compare typed numeric parameters. Never cast text storage to a fixed precision decimal.
        if (record.getMinimum() != null) sql += " and ? >= ?";
        if (record.getMaximum() != null) sql += " and ? <= ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getGroupId());
            statement.setString(2, boundary.getServiceId());
            statement.setString(3, context.getId());
            statement.setBigDecimal(4, record.getAfterValue());
            statement.setString(5, record.getAttributeId());
            statement.setString(6, normalizeObjectId(record.getObjectId()));
            int index = 7;
            if (record.getMinimum() != null) {
                statement.setBigDecimal(index++, record.getAfterValue());
                statement.setBigDecimal(index++, record.getMinimum());
            }
            if (record.getMaximum() != null) {
                statement.setBigDecimal(index++, record.getAfterValue());
                statement.setBigDecimal(index++, record.getMaximum());
            }
            if (statement.executeUpdate() != 1)
                throw new QuantumBoundRejection(record);
        }
    }

    private void upsertValue(Connection connection, String transactionId, String boundaryServiceId,
                             String groupId, String attributeId, String objectId,
                             BigDecimal currentValue) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(deleteValue);
             PreparedStatement insert = connection.prepareStatement(insertValue)) {
            delete.setString(1, attributeId);
            delete.setString(2, normalizeObjectId(objectId));
            delete.executeUpdate();

            insert.setString(1, attributeId);
            insert.setString(2, normalizeObjectId(objectId));
            insert.setString(3, groupId);
            insert.setString(4, boundaryServiceId == null ? "" : boundaryServiceId);
            insert.setString(5, transactionId == null ? "" : transactionId);
            insert.setBigDecimal(6, currentValue);
            insert.executeUpdate();
        }
    }

    public String getJournalTable() {
        return journalTable;
    }

    public String getValueTable() {
        return valueTable;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    private static String normalizeObjectId(String objectId) {
        return objectId == null || objectId.isBlank() ? DEFAULT_OBJECT_ID : objectId.trim();
    }

    private static String normalizeTableName(String tableName, String fallback) {
        String value = tableName == null || tableName.isBlank() ? fallback : tableName.trim();
        String[] parts = value.split("\\.");
        for (String part : parts) {
            if (!part.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("Invalid quantum table name " + value);
            }
        }
        return value;
    }
}
