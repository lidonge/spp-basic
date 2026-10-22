package com.servpp.service.repository;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.sql.DataSource;

import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.runtime.AppDdlCatalog;

public class JdbcAppDdlObjectTableWriter {
    private final DataSource dataSource;
    private final AppDdlCatalog catalog;
    private final NativeUpdateExecutor updates;

    public JdbcAppDdlObjectTableWriter(DataSource dataSource, AppDdlCatalog catalog) {
        this(dataSource,catalog,NativeUpdateExecutor.jdbc());
    }
    public JdbcAppDdlObjectTableWriter(DataSource dataSource, AppDdlCatalog catalog, NativeUpdateExecutor updates) {
        this.dataSource = dataSource;
        this.catalog = catalog == null ? AppDdlCatalog.empty() : catalog;
        this.updates = java.util.Objects.requireNonNull(updates);
    }

    public void save(String objectType, Object value) {
        save(objectType, null, value);
    }

    public void save(String objectType, String objectId, Object value) {
        if (objectType == null || value == null || catalog.tables().isEmpty()) {
            return;
        }
        Optional<AppDdlCatalog.Table> table = catalog.tableForBusinessType(objectType);
        if (table.isEmpty()) {
            return;
        }
        save(table.orElseThrow(), objectId, value);
    }

    public void validateUpdateIdentity(String objectType,Object before,Object after) {
        catalog.tableForBusinessType(objectType).ifPresent(table -> {
            for (String key : table.objectBackedPrimaryKeys()) {
                Object previous = readColumnValue(before,key), next = readColumnValue(after,key);
                boolean same = previous instanceof Number && next instanceof Number
                        ? new BigDecimal(previous.toString()).compareTo(new BigDecimal(next.toString())) == 0
                        : java.util.Objects.deepEquals(previous,next);
                if (!same) throw new IllegalStateException("PERSISTENCE_QUERY_IDENTITY_CHANGED");
            }
        });
    }

    /** Called only after the snapshot CAS succeeds in this same JDBC transaction. */
    public void updateChanged(String objectType, String objectId, Object value, java.util.Set<String> paths) {
        var mapping = catalog.tableForBusinessType(objectType);
        if (mapping.isEmpty()) return;
        var table = mapping.orElseThrow();
        var columns = table.columns().stream().filter(column -> column.objectBacked()
                && !table.primaryKeys().contains(column.name()) && ParticipantUpdatePatch.affects(column.name(),paths)).toList();
        if (columns.isEmpty()) return;
        var assignments = new ArrayList<NativeUpdate.Value>();
        for (var column : columns) {
            Object fact = readColumnValue(value,column.name());
            assignments.add(new NativeUpdate.Value(column.name(),table.snapshotFacts().contains(column.name()) && "CLOB".equalsIgnoreCase(column.type())
                    ? ParticipantSnapshotJson.encode(fact) : normalizeJdbcValue(fact)));
        }
        var keys = primaryKeyValues(table,objectId,value);
        if (keys.isEmpty() || keys.size() != table.primaryKeys().size()) throw new IllegalStateException("PERSISTENCE_UPDATE_NATIVE_KEY_MISSING");
        var identities = new ArrayList<NativeUpdate.Value>();
        for (int index=0; index<keys.size(); index++) identities.add(new NativeUpdate.Value(table.primaryKeys().get(index),keys.get(index)));
        var update = new NativeUpdate(table.name(),assignments,identities,paths);
        Connection connection = JdbcTransactionScope.currentConnection(dataSource)
                .orElseThrow(() -> new IllegalStateException("PERSISTENCE_REPOSITORY_NOT_ENLISTED"));
        try {
            if (updates.execute(connection,update) != 1) throw new IllegalStateException("PERSISTENCE_UPDATE_NATIVE_NOT_FOUND");
        } catch (SQLException failure) { throw new IllegalStateException("PERSISTENCE_UPDATE_NATIVE_FAILED",failure); }
    }

    public void delete(String objectType, String objectId, Object original) {
        Optional<AppDdlCatalog.Table> table = catalog.tableForBusinessType(objectType);
        if (table.isEmpty()) return;
        AppDdlCatalog.Table mapping = table.orElseThrow();
        List<Object> keys = primaryKeyValues(mapping, objectId, original);
        if (keys.size() != mapping.primaryKeys().size() || keys.isEmpty()) {
            throw new IllegalStateException("PERSISTENCE_DELETE_NATIVE_KEY_MISSING");
        }
        Connection connection = JdbcTransactionScope.currentConnection(dataSource)
                .orElseThrow(() -> new IllegalStateException("PERSISTENCE_REPOSITORY_NOT_ENLISTED"));
        try (PreparedStatement statement = connection.prepareStatement(deleteSql(mapping))) {
            bind(statement, keys);
            if (statement.executeUpdate() != 1) throw new IllegalStateException("PERSISTENCE_DELETE_NATIVE_NOT_FOUND");
        } catch (SQLException failure) {
            throw new IllegalStateException("PERSISTENCE_DELETE_NATIVE_FAILED", failure);
        }
    }

    private void save(AppDdlCatalog.Table table, String objectId, Object value) {
        try {
            JdbcTransactionScope.execute(dataSource, connection -> {
                List<Object> primaryKeyValues = new ArrayList<>();
                List<String> decodedObjectId = decodeObjectId(objectId, table.primaryKeys().size());
                for (int i = 0; i < table.primaryKeys().size(); i++) {
                    String primaryKey = table.primaryKeys().get(i);
                    Object primaryKeyValue = readPrimaryKeyValue(table, primaryKey, i, decodedObjectId, objectId, value);
                    if (primaryKeyValue == null) {
                        return null;
                    }
                    primaryKeyValues.add(normalizeJdbcValue(primaryKeyValue));
                }
                List<String> columnNames = table.columns().stream()
                        .map(AppDdlCatalog.Column::name)
                        .toList();
                List<Object> existingValues = existingValues(connection, table, primaryKeyValues, columnNames);
                List<Object> columnValues = new ArrayList<>();
                for (int i = 0; i < table.columns().size(); i++) {
                    AppDdlCatalog.Column column = table.columns().get(i);
                    if (table.snapshotFacts().contains(column.name()) && "CLOB".equalsIgnoreCase(column.type())) {
                        // A snapshot replaces the whole value, including an explicit null.
                        columnValues.add(ParticipantSnapshotJson.encode(readColumnValue(value, column.name())));
                        continue;
                    }
                    int primaryKeyIndex = table.primaryKeys().indexOf(column.name());
                    Object columnValue = primaryKeyIndex >= 0 && primaryKeyIndex < primaryKeyValues.size()
                            ? primaryKeyValues.get(primaryKeyIndex)
                            : column.objectBacked()
                            ? objectBackedColumnValue(value, column.name(), existingValues.get(i))
                            : platformColumnValue(table, primaryKeyValues, column, existingValues.get(i), value);
                    columnValues.add(normalizeJdbcValue(columnValue));
                }
                try (PreparedStatement delete = connection.prepareStatement(deleteSql(table));
                     PreparedStatement insert = connection.prepareStatement(insertSql(table, columnNames))) {
                    bind(delete, primaryKeyValues);
                    delete.executeUpdate();
                    bind(insert, columnValues);
                    insert.executeUpdate();
                }
                return null;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot save APP DDL table " + table.name(), e);
        }
    }

    public void adjustPlatformColumn(String objectType, Object value, String columnName, BigDecimal delta) {
        adjustPlatformColumn(objectType, null, value, columnName, delta);
    }

    public void adjustPlatformColumn(String objectType, String objectId, Object value,
                                     String columnName, BigDecimal delta) {
        if (objectType == null || !hasObjectIdentity(objectId, value) || columnName == null || delta == null
                || BigDecimal.ZERO.compareTo(delta) == 0 || catalog.tables().isEmpty()) {
            return;
        }
        Optional<AppDdlCatalog.Table> table = catalog.tableForBusinessType(objectType);
        if (table.isEmpty()) {
            return;
        }
        AppDdlCatalog.Column column = table.orElseThrow().columns().stream()
                .filter(item -> columnName.equals(item.name()))
                .findFirst()
                .orElse(null);
        if (column == null || column.objectBacked()) {
            return;
        }
        if (value != null) {
            save(table.orElseThrow(), objectId, value);
        }
        try {
            JdbcTransactionScope.execute(dataSource, connection -> {
                List<Object> primaryKeyValues = primaryKeyValues(table.orElseThrow(), objectId, value);
                if (primaryKeyValues.isEmpty()) {
                    return null;
                }
                String sql = "update " + safeIdentifier(table.orElseThrow().name(), "APP DDL table")
                        + " set " + safeIdentifier(columnName, "APP DDL column")
                        + " = coalesce(" + safeIdentifier(columnName, "APP DDL column") + ", 0) + ?"
                        + " where " + wherePrimaryKeys(table.orElseThrow());
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setBigDecimal(1, delta);
                    bind(statement, primaryKeyValues, 2);
                    statement.executeUpdate();
                }
                return null;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot adjust APP DDL platform column "
                    + table.orElseThrow().name() + "." + columnName, e);
        }
    }

    public void writePlatformColumn(String objectType, Object value, String columnName, Object columnValue) {
        writePlatformColumn(objectType, null, value, columnName, columnValue);
    }

    public void writePlatformColumn(String objectType, String objectId, Object value,
                                    String columnName, Object columnValue) {
        if (objectType == null || !hasObjectIdentity(objectId, value) || columnName == null
                || catalog.tables().isEmpty()) {
            return;
        }
        Optional<AppDdlCatalog.Table> table = catalog.tableForBusinessType(objectType);
        if (table.isEmpty()) {
            return;
        }
        AppDdlCatalog.Column column = column(table.orElseThrow(), columnName);
        if (column == null || column.objectBacked()) {
            return;
        }
        if (value != null) {
            save(table.orElseThrow(), objectId, value);
        }
        try {
            JdbcTransactionScope.execute(dataSource, connection -> {
                List<Object> primaryKeyValues = primaryKeyValues(table.orElseThrow(), objectId, value);
                if (primaryKeyValues.isEmpty()) {
                    return null;
                }
                String sql = "update " + safeIdentifier(table.orElseThrow().name(), "APP DDL table")
                        + " set " + safeIdentifier(columnName, "APP DDL column") + " = ?"
                        + " where " + wherePrimaryKeys(table.orElseThrow());
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setObject(1, normalizeJdbcValue(columnValue));
                    bind(statement, primaryKeyValues, 2);
                    statement.executeUpdate();
                }
                return null;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot write APP DDL platform column "
                    + table.orElseThrow().name() + "." + columnName, e);
        }
    }

    public void compareVersion(String objectType,String objectId,Object value,String columnName,Object expectedVersion) {
        var table = catalog.tableForBusinessType(objectType).orElseThrow(() -> new IllegalStateException("PLATFORM_VERSION_TABLE_REQUIRED"));
        var column = table.columns().stream().filter(c -> c.name().equals(columnName) && !c.objectBacked()).findFirst()
                .orElseThrow(() -> new IllegalStateException("PLATFORM_VERSION_COLUMN_REQUIRED"));
        if (!(expectedVersion instanceof Number)) throw new IllegalStateException("PERSISTENCE_VERSION_REQUIRED");
        long expected = new BigDecimal(expectedVersion.toString()).longValueExact();
        long next = Math.addExact(expected,1L);
        try {
        JdbcTransactionScope.execute(dataSource, connection -> {
            var keys = primaryKeyValues(table,objectId,value);
            String sql="update "+safeIdentifier(table.name(),"APP table")+" set "+safeIdentifier(columnName,"APP column")
                    +" = ? where "+wherePrimaryKeys(table)+" and "+safeIdentifier(columnName,"APP column")+" = ?";
            try(var statement=connection.prepareStatement(sql)) {
                statement.setLong(1,next); bind(statement,keys,2); statement.setLong(keys.size()+2,expected);
                if(statement.executeUpdate()!=1) throw new IllegalStateException("PERSISTENCE_VERSION_CONFLICT");
            }
            return null;
        });
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot compare APP platform version " + table.name() + "." + columnName, e);
        }
    }

    private List<Object> primaryKeyValues(AppDdlCatalog.Table table, Object value) {
        return primaryKeyValues(table, null, value);
    }

    private List<Object> primaryKeyValues(AppDdlCatalog.Table table, String objectId, Object value) {
        List<Object> primaryKeyValues = new ArrayList<>();
        List<String> decodedObjectId = decodeObjectId(objectId, table.primaryKeys().size());
        for (String primaryKey : table.primaryKeys()) {
            int index = table.primaryKeys().indexOf(primaryKey);
            Object primaryKeyValue = readPrimaryKeyValue(table, primaryKey, index, decodedObjectId, objectId, value);
            if (primaryKeyValue == null) {
                return List.of();
            }
            primaryKeyValues.add(normalizeJdbcValue(primaryKeyValue));
        }
        return primaryKeyValues;
    }

    private Object readPrimaryKeyValue(AppDdlCatalog.Table table, String primaryKey, int index,
                                       List<String> decodedObjectId, String objectId, Object value) {
        Object valueFromObject = readColumnValue(value, primaryKey);
        if (valueFromObject != null) {
            return valueFromObject;
        }
        if (!decodedObjectId.isEmpty() && index < decodedObjectId.size()) {
            return decodedObjectId.get(index);
        }
        if (table.primaryKeys().size() == 1) {
            return objectId;
        }
        AppDdlCatalog.Column column = column(table, primaryKey);
        if (column == null || column.objectBacked()) {
            return null;
        }
        return null;
    }

    private Object platformColumnValue(AppDdlCatalog.Table table, List<Object> primaryKeyValues,
                                       AppDdlCatalog.Column column, Object existingValue, Object value) {
        int primaryKeyIndex = table.primaryKeys().indexOf(column.name());
        if (primaryKeyIndex >= 0 && primaryKeyIndex < primaryKeyValues.size()) {
            return primaryKeyValues.get(primaryKeyIndex);
        }
        Object valueFromObject = readColumnValue(value, column.name());
        if (valueFromObject != null) {
            return valueFromObject;
        }
        if (existingValue == null && "version".equals(column.name()) && !column.objectBacked()) return 0L;
        return existingValue;
    }

    private Object objectBackedColumnValue(Object value, String columnName, Object existingValue) {
        Object valueFromObject = readColumnValue(value, columnName);
        return valueFromObject != null ? valueFromObject : existingValue;
    }

    private List<String> decodeObjectId(String objectId, int keyCount) {
        if (objectId == null || objectId.isBlank() || keyCount <= 1) {
            return List.of();
        }
        return CompositeObjectId.decode(objectId, keyCount);
    }

    private AppDdlCatalog.Column column(AppDdlCatalog.Table table, String columnName) {
        for (AppDdlCatalog.Column column : table.columns()) {
            if (column.name().equals(columnName)) {
                return column;
            }
        }
        return null;
    }

    private boolean hasObjectIdentity(String objectId, Object value) {
        return value != null || (objectId != null && !objectId.isBlank());
    }

    private List<Object> existingValues(Connection connection, AppDdlCatalog.Table table,
                                        List<Object> primaryKeyValues, List<String> columnNames)
            throws SQLException {
        List<Object> defaults = new ArrayList<>();
        for (int i = 0; i < columnNames.size(); i++) {
            defaults.add(null);
        }
        if (primaryKeyValues.isEmpty()) {
            return defaults;
        }
        String sql = "select " + selectColumns(columnNames) + " from "
                + safeIdentifier(table.name(), "APP DDL table") + " where " + wherePrimaryKeys(table);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, primaryKeyValues);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return defaults;
                }
                List<Object> values = new ArrayList<>();
                for (int i = 0; i < columnNames.size(); i++) {
                    values.add(resultSet.getObject(i + 1));
                }
                return values;
            }
        }
    }

    private String deleteSql(AppDdlCatalog.Table table) {
        return "delete from " + safeIdentifier(table.name(), "APP DDL table")
                + " where " + wherePrimaryKeys(table);
    }

    private String insertSql(AppDdlCatalog.Table table, List<String> columnNames) {
        List<String> columns = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        for (String columnName : columnNames) {
            columns.add(safeIdentifier(columnName, "APP DDL column"));
            placeholders.add("?");
        }
        return "insert into " + safeIdentifier(table.name(), "APP DDL table")
                + " (" + String.join(", ", columns) + ") values ("
                + String.join(", ", placeholders) + ")";
    }

    private void bind(PreparedStatement statement, List<Object> values) throws SQLException {
        bind(statement, values, 1);
    }

    private void bind(PreparedStatement statement, List<Object> values, int offset) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            statement.setObject(i + offset, values.get(i));
        }
    }

    private String selectColumns(List<String> columnNames) {
        List<String> columns = new ArrayList<>();
        for (String columnName : columnNames) {
            columns.add(safeIdentifier(columnName, "APP DDL column"));
        }
        return String.join(", ", columns);
    }

    private String wherePrimaryKeys(AppDdlCatalog.Table table) {
        List<String> where = new ArrayList<>();
        for (String primaryKey : table.primaryKeys()) {
            where.add(safeIdentifier(primaryKey, "APP DDL primary key") + " = ?");
        }
        if (where.isEmpty()) {
            throw new IllegalStateException("APP DDL table " + table.name() + " has no primary key.");
        }
        return String.join(" and ", where);
    }

    private Object readColumnValue(Object value, String columnName) {
        Object directValue = readProperty(value, columnName);
        if (directValue != null || !columnName.contains("_")) {
            return directValue;
        }
        String[] segments = columnName.split("_");
        Object current = value;
        for (int i = 0; i < segments.length; i++) {
            var platformKey = ParticipantPlatformKeys.read(current,String.join("_",java.util.Arrays.copyOfRange(segments,i,segments.length)),catalog);
            if (platformKey.isPresent()) return platformKey.get();
            current = readProperty(current, segments[i]);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private Object readProperty(Object value, String propertyName) {
        if (value == null || propertyName == null || propertyName.isBlank()) {
            return null;
        }
        var platformKey = ParticipantPlatformKeys.read(value,propertyName,catalog);
        if (platformKey.isPresent()) return platformKey.get();
        if (value instanceof java.util.Map<?, ?> properties) {
            for (var entry : properties.entrySet()) {
                if (entry.getKey() instanceof String key && propertyMatches(key, propertyName)) return entry.getValue();
            }
            return null;
        }
        for (Method method : value.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && methodNameMatches(method.getName(), propertyName)) {
                try {
                    return method.invoke(value);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot read property " + propertyName
                            + " from " + value.getClass().getName(), e);
                }
            }
        }
        Class<?> current = value.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (propertyMatches(field.getName(), propertyName)) {
                    try {
                        field.setAccessible(true);
                        return field.get(value);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Cannot read field " + propertyName
                                + " from " + value.getClass().getName(), e);
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private boolean methodNameMatches(String methodName, String propertyName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return propertyMatches(methodName.substring(3), propertyName);
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return propertyMatches(methodName.substring(2), propertyName);
        }
        return propertyMatches(methodName, propertyName);
    }

    private boolean propertyMatches(String actual, String expected) {
        return normalized(actual).equals(normalized(expected));
    }

    private String normalized(String value) {
        return value == null ? "" : value.replace("_", "").toLowerCase(Locale.ROOT);
    }

    private Object normalizeJdbcValue(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof BigDecimal
                || value instanceof java.sql.Date
                || value instanceof java.sql.Timestamp) {
            return value;
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof LocalDate localDate) {
            return java.sql.Date.valueOf(localDate);
        }
        if (value instanceof Instant instant) {
            return Timestamp.from(instant);
        }
        return value.toString();
    }

    private String safeIdentifier(String identifier, String label) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException(label + " is not a safe SQL identifier: " + identifier);
        }
        return identifier;
    }
}
