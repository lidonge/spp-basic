package com.servpp.service.repository;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.sql.DataSource;

import com.servpp.service.runtime.AppDdlCatalog;

public class JdbcAppDdlObjectTableReader {
    private final DataSource dataSource;
    private final AppDdlCatalog catalog;

    public JdbcAppDdlObjectTableReader(DataSource dataSource, AppDdlCatalog catalog) {
        this.dataSource = dataSource;
        this.catalog = catalog == null ? AppDdlCatalog.empty() : catalog;
    }

    public <T> boolean hydrate(String objectType, String objectId, T target) {
        if (objectType == null || objectId == null || target == null || catalog.tables().isEmpty()) {
            return false;
        }
        Optional<AppDdlCatalog.Table> table = catalog.tableForBusinessType(objectType);
        if (table.isEmpty()) {
            return false;
        }
        return hydrate(table.orElseThrow(), objectId, target);
    }

    public <T> Optional<T> find(String objectType, String objectId, Class<T> type) {
        if (objectType == null || objectId == null || type == null || catalog.tables().isEmpty()) {
            return Optional.empty();
        }
        T target = newInstance(type);
        return hydrate(objectType, objectId, target) ? Optional.of(target) : Optional.empty();
    }

    public <T> List<T> findAll(String objectType, Class<T> type) {
        if (objectType == null || type == null || catalog.tables().isEmpty()) {
            return List.of();
        }
        Optional<AppDdlCatalog.Table> table = catalog.tableForBusinessType(objectType);
        if (table.isEmpty()) {
            return List.of();
        }
        return findAll(table.orElseThrow(), type);
    }

    public List<String> findAllObjectIds(String objectType) {
        if (objectType == null || catalog.tables().isEmpty()) {
            return List.of();
        }
        Optional<AppDdlCatalog.Table> table = catalog.tableForBusinessType(objectType);
        if (table.isEmpty()) {
            return List.of();
        }
        return queryObjectIds(table.orElseThrow(), selectAllSql(table.orElseThrow()), List.of());
    }

    public <T> List<T> findBy(String objectType, String keyPath, Object keyValue, Class<T> type) {
        if (objectType == null || keyPath == null || type == null || catalog.tables().isEmpty()) {
            return List.of();
        }
        Optional<AppDdlCatalog.Table> table = catalog.tableForBusinessType(objectType);
        if (table.isEmpty()) {
            return List.of();
        }
        Optional<String> column = columnForPath(table.orElseThrow(), keyPath);
        if (column.isEmpty()) {
            return List.of();
        }
        return findBy(table.orElseThrow(), column.orElseThrow(), keyValue, type);
    }

    public List<String> findObjectIdsBy(String objectType, String keyPath, Object keyValue) {
        if (objectType == null || keyPath == null || catalog.tables().isEmpty()) {
            return List.of();
        }
        Optional<AppDdlCatalog.Table> table = catalog.tableForBusinessType(objectType);
        if (table.isEmpty()) {
            return List.of();
        }
        Optional<String> column = columnForPath(table.orElseThrow(), keyPath);
        if (column.isEmpty()) {
            return List.of();
        }
        String sql = selectColumnsSql(table.orElseThrow())
                + " where " + safeIdentifier(column.orElseThrow(), "APP DDL column") + " = ?"
                + orderBySql(table.orElseThrow());
        return queryObjectIds(table.orElseThrow(), sql, List.of(queryValue(keyValue)));
    }

    public <T> List<T> findBy(String objectType, List<String> keyPaths, List<?> keyValues, Class<T> type) {
        if (objectType == null || keyPaths == null || keyValues == null || keyPaths.size() != keyValues.size()
                || type == null || catalog.tables().isEmpty()) {
            return List.of();
        }
        if (keyPaths.isEmpty()) {
            return findAll(objectType, type);
        }
        Optional<AppDdlCatalog.Table> table = catalog.tableForBusinessType(objectType);
        if (table.isEmpty()) {
            return List.of();
        }
        List<String> columns = new ArrayList<>();
        for (String keyPath : keyPaths) {
            Optional<String> column = columnForPath(table.orElseThrow(), keyPath);
            if (column.isEmpty()) {
                return List.of();
            }
            columns.add(column.orElseThrow());
        }
        return findBy(table.orElseThrow(), columns, keyValues, type);
    }

    public List<String> findObjectIdsBy(String objectType, List<String> keyPaths, List<?> keyValues) {
        if (objectType == null || keyPaths == null || keyValues == null || keyPaths.size() != keyValues.size()
                || catalog.tables().isEmpty()) {
            return List.of();
        }
        if (keyPaths.isEmpty()) {
            return findAllObjectIds(objectType);
        }
        if (keyPaths.size() == 1) {
            return findObjectIdsBy(objectType, keyPaths.get(0), keyValues.get(0));
        }
        Optional<AppDdlCatalog.Table> table = catalog.tableForBusinessType(objectType);
        if (table.isEmpty()) {
            return List.of();
        }
        List<String> columns = new ArrayList<>();
        for (String keyPath : keyPaths) {
            Optional<String> column = columnForPath(table.orElseThrow(), keyPath);
            if (column.isEmpty()) {
                return List.of();
            }
            columns.add(column.orElseThrow());
        }
        List<Object> queryValues = new ArrayList<>();
        for (Object value : keyValues) {
            queryValues.add(queryValue(value));
        }
        String sql = selectColumnsSql(table.orElseThrow())
                + " where " + whereSql(columns, "APP DDL column")
                + orderBySql(table.orElseThrow());
        return queryObjectIds(table.orElseThrow(), sql, queryValues);
    }

    public Optional<Object> readPlatformColumn(String objectType, Object value, String columnName) {
        if (objectType == null || value == null || columnName == null || catalog.tables().isEmpty()) {
            return Optional.empty();
        }
        Optional<AppDdlCatalog.Table> table = catalog.tableForBusinessType(objectType);
        if (table.isEmpty()) {
            return Optional.empty();
        }
        KeyPredicate keyPredicate = keyPredicateFromValue(table.orElseThrow(), value);
        return readPlatformColumn(table.orElseThrow(), keyPredicate, columnName);
    }

    public Optional<Object> readPlatformColumnByObjectId(String objectType, String objectId, String columnName) {
        if (objectType == null || objectId == null || columnName == null || catalog.tables().isEmpty()) {
            return Optional.empty();
        }
        Optional<AppDdlCatalog.Table> table = catalog.tableForBusinessType(objectType);
        if (table.isEmpty()) {
            return Optional.empty();
        }
        return readPlatformColumn(table.orElseThrow(), keyPredicate(table.orElseThrow(), objectId, null), columnName);
    }

    private Optional<Object> readPlatformColumn(AppDdlCatalog.Table table, KeyPredicate keyPredicate,
                                                String columnName) {
        AppDdlCatalog.Column column = table.columns().stream()
                .filter(item -> columnName.equals(item.name()))
                .findFirst()
                .orElse(null);
        if (column == null || column.objectBacked()
                || keyPredicate.values().size() != table.primaryKeys().size()) {
            return Optional.empty();
        }
        String sql = "select " + safeIdentifier(columnName, "APP DDL platform column")
                + " from " + safeIdentifier(table.name(), "APP DDL table")
                + " where " + whereSql(keyPredicate.columns(), "APP DDL primary key");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, keyPredicate.values());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                Object result = resultSet.getObject(1);
                if (resultSet.next()) {
                    throw new IllegalStateException("APP DDL table " + table.name()
                            + " has multiple rows for " + keyPredicate.columns() + ".");
                }
                return Optional.ofNullable(result);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read APP DDL platform column "
                    + table.name() + "." + columnName, e);
        }
    }

    private <T> List<T> findAll(AppDdlCatalog.Table table, Class<T> type) {
        return query(table, type, selectAllSql(table), List.of());
    }

    private <T> List<T> findBy(AppDdlCatalog.Table table, String column, Object value, Class<T> type) {
        String sql = selectColumnsSql(table)
                + " where " + safeIdentifier(column, "APP DDL column") + " = ?"
                + orderBySql(table);
        List<Object> values = new ArrayList<>();
        values.add(queryValue(value));
        return query(table, type, sql, values);
    }

    private <T> List<T> findBy(AppDdlCatalog.Table table, List<String> columns, List<?> values, Class<T> type) {
        String sql = selectColumnsSql(table)
                + " where " + whereSql(columns, "APP DDL column")
                + orderBySql(table);
        List<Object> queryValues = new ArrayList<>();
        for (Object value : values) {
            queryValues.add(queryValue(value));
        }
        return query(table, type, sql, queryValues);
    }

    private <T> List<T> query(AppDdlCatalog.Table table, Class<T> type, String sql, List<Object> values) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (resultSet.next()) {
                    T target = newInstance(type);
                    for (AppDdlCatalog.Column column : table.columns()) {
                        if (table.snapshotFacts().contains(column.name()) && "CLOB".equalsIgnoreCase(column.type())) {
                            ParticipantSnapshotJson.hydrate(target, column.name(), resultSet.getString(column.name()));
                        } else writeColumnValue(target, column.name(), resultSet.getObject(column.name()));
                    }
                    results.add(target);
                }
                return List.copyOf(results);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query APP DDL table " + table.name(), e);
        }
    }

    private List<String> queryObjectIds(AppDdlCatalog.Table table, String sql, List<Object> values) {
        if (table.primaryKeys().isEmpty()) {
            return List.of();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> results = new ArrayList<>();
                while (resultSet.next()) {
                    List<Object> keyValues = new ArrayList<>();
                    for (String primaryKey : table.primaryKeys()) {
                        Object value = resultSet.getObject(primaryKey);
                        if (value == null) {
                            keyValues.clear();
                            break;
                        }
                        keyValues.add(value);
                    }
                    if (keyValues.size() == table.primaryKeys().size()) {
                        results.add(objectId(keyValues));
                    }
                }
                return List.copyOf(results);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot query APP DDL table " + table.name(), e);
        }
    }

    private String objectId(List<Object> keyValues) {
        if (keyValues.size() == 1) {
            return String.valueOf(keyValues.get(0));
        }
        return CompositeObjectId.encode(keyValues);
    }

    private <T> boolean hydrate(AppDdlCatalog.Table table, String objectId, T target) {
        KeyPredicate keyPredicate = keyPredicate(table, objectId, target);
        if (keyPredicate.values().isEmpty()) {
            return false;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql(table, keyPredicate))) {
            bind(statement, keyPredicate.values());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return false;
                }
                for (AppDdlCatalog.Column column : table.columns()) {
                    if (table.snapshotFacts().contains(column.name()) && "CLOB".equalsIgnoreCase(column.type()))
                        ParticipantSnapshotJson.hydrate(target, column.name(), resultSet.getString(column.name()));
                    else writeColumnValue(target, column.name(), resultSet.getObject(column.name()));
                }
                if (resultSet.next()) {
                    throw new IllegalStateException("APP DDL table " + table.name()
                            + " has multiple rows for " + keyPredicate.columns() + ".");
                }
                return true;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot hydrate APP DDL table " + table.name(), e);
        }
    }

    private KeyPredicate keyPredicate(AppDdlCatalog.Table table, String objectId, Object target) {
        if (table.primaryKeys().isEmpty()) {
            return new KeyPredicate(List.of(), List.of());
        }
        if (table.primaryKeys().size() == 1) {
            return new KeyPredicate(List.of(table.primaryKeys().get(0)), List.of(objectId));
        }
        List<String> encodedValues = CompositeObjectId.decode(objectId, table.primaryKeys().size());
        if (!encodedValues.isEmpty()) {
            return new KeyPredicate(List.copyOf(table.primaryKeys()), List.copyOf(encodedValues));
        }
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (String primaryKey : table.primaryKeys()) {
            Object value = readColumnValue(target, primaryKey);
            if (value != null) {
                columns.add(primaryKey);
                values.add(value);
            }
        }
        return new KeyPredicate(List.copyOf(columns), List.copyOf(values));
    }

    private KeyPredicate keyPredicateFromValue(AppDdlCatalog.Table table, Object value) {
        if (table.primaryKeys().isEmpty()) {
            return new KeyPredicate(List.of(), List.of());
        }
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (String primaryKey : table.primaryKeys()) {
            Object primaryKeyValue = readColumnValue(value, primaryKey);
            if (primaryKeyValue != null) {
                columns.add(primaryKey);
                values.add(primaryKeyValue);
            }
        }
        return new KeyPredicate(List.copyOf(columns), List.copyOf(values));
    }

    private String selectSql(AppDdlCatalog.Table table, KeyPredicate keyPredicate) {
        List<String> columns = new ArrayList<>();
        for (AppDdlCatalog.Column column : table.columns()) {
            columns.add(safeIdentifier(column.name(), "APP DDL column"));
        }
        List<String> where = new ArrayList<>();
        where.add(whereSql(keyPredicate.columns(), "APP DDL primary key"));
        return "select " + String.join(", ", columns)
                + " from " + safeIdentifier(table.name(), "APP DDL table")
                + " where " + String.join(" and ", where);
    }

    private String whereSql(List<String> columns, String label) {
        List<String> where = new ArrayList<>();
        for (String column : columns) {
            where.add(safeIdentifier(column, label) + " = ?");
        }
        return String.join(" and ", where);
    }

    private String selectAllSql(AppDdlCatalog.Table table) {
        return selectColumnsSql(table) + orderBySql(table);
    }

    private String selectColumnsSql(AppDdlCatalog.Table table) {
        List<String> columns = new ArrayList<>();
        for (AppDdlCatalog.Column column : table.columns()) {
            columns.add(safeIdentifier(column.name(), "APP DDL column"));
        }
        return "select " + String.join(", ", columns)
                + " from " + safeIdentifier(table.name(), "APP DDL table");
    }

    private String orderBySql(AppDdlCatalog.Table table) {
        if (table.primaryKeys().isEmpty()) {
            return "";
        }
        List<String> orderBy = new ArrayList<>();
        for (String primaryKey : table.primaryKeys()) {
            orderBy.add(safeIdentifier(primaryKey, "APP DDL primary key"));
        }
        return " order by " + String.join(", ", orderBy);
    }

    private Optional<String> columnForPath(AppDdlCatalog.Table table, String keyPath) {
        // Query plans use logical private identities; APP columns contain the configured key name.
        if (keyPath.equals("@storageIdentity") || keyPath.endsWith(".@storageIdentity")) {
            String prefix = keyPath.substring(0, keyPath.length() - "@storageIdentity".length()).replace('.', '_');
            var matches = new java.util.LinkedHashSet<String>();
            for (var owner : catalog.roleStorage().values()) {
                if (!owner.independent()) continue;
                if (prefix.isEmpty() && !owner.type().equals(table.businessType())) continue;
                String candidate = normalized(prefix + owner.primaryKey());
                for (var column : table.columns())
                    if (normalized(column.name()).equals(candidate)) matches.add(column.name());
            }
            if (matches.size() == 1) return Optional.of(matches.iterator().next());
            if (matches.size() > 1) throw new IllegalStateException("PERSISTENCE_QUERY_IDENTITY_COLUMN_AMBIGUOUS");
        }
        String normalizedPath = normalized(keyPath);
        String normalizedColumnPath = normalized(keyPath.replace(".", "_"));
        for (AppDdlCatalog.Column column : table.columns()) {
            String normalizedColumn = normalized(column.name());
            if (normalizedColumn.equals(normalizedPath) || normalizedColumn.equals(normalizedColumnPath)) {
                return Optional.of(column.name());
            }
        }
        return Optional.empty();
    }

    private Object queryValue(Object value) {
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return value;
    }

    private void bind(PreparedStatement statement, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            statement.setObject(i + 1, values.get(i));
        }
    }

    private Object readColumnValue(Object value, String columnName) {
        Object directValue = readProperty(value, columnName);
        if (directValue != null || !columnName.contains("_")) {
            return directValue;
        }
        String[] segments = columnName.split("_");
        Object current = value;
        for (String segment : segments) {
            current = readProperty(current, segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private Object readProperty(Object value, String propertyName) {
        Property property = findReadableProperty(value.getClass(), propertyName);
        if (property == null) {
            return null;
        }
        return property.read(value);
    }

    private void writeColumnValue(Object target, String columnName, Object value) {
        if (target == null) {
            return;
        }
        if (writeProperty(target, columnName, value) || !columnName.contains("_")) {
            return;
        }
        String[] segments = columnName.split("_");
        Object current = target;
        for (int i = 0; i < segments.length - 1; i++) {
            if (ParticipantPlatformKeys.restore(current,String.join("_",java.util.Arrays.copyOfRange(segments,i,segments.length)),value,catalog)) return;
            current = readOrCreateNestedProperty(current, segments[i]);
            if (current == null) {
                return;
            }
        }
        writeProperty(current, segments[segments.length - 1], value);
    }

    private Object readOrCreateNestedProperty(Object value, String propertyName) {
        Property property = findReadableProperty(value.getClass(), propertyName);
        if (property == null) {
            return null;
        }
        Object nested = property.read(value);
        if (nested != null) {
            return nested;
        }
        Class<?> propertyType = property.type();
        if (propertyType == null) {
            return null;
        }
        try {
            Object created = propertyType.getDeclaredConstructor().newInstance();
            return writeProperty(value, propertyName, created) ? created : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private boolean writeProperty(Object value, String propertyName, Object propertyValue) {
        WritableProperty property = findWritableProperty(value.getClass(), propertyName);
        if (property == null) {
            return ParticipantPlatformKeys.restore(value,propertyName,propertyValue,catalog);
        }
        property.write(value, convert(propertyValue, property.type()));
        return true;
    }

    private Property findReadableProperty(Class<?> type, String propertyName) {
        for (Method method : type.getMethods()) {
            if (method.getParameterCount() == 0 && methodNameMatches(method.getName(), propertyName)) {
                return new MethodProperty(method);
            }
        }
        Field field = findField(type, propertyName);
        return field == null ? null : new FieldProperty(field);
    }

    private WritableProperty findWritableProperty(Class<?> type, String propertyName) {
        for (Method method : type.getMethods()) {
            if (method.getParameterCount() == 1 && setterNameMatches(method.getName(), propertyName)) {
                return new MethodWritableProperty(method);
            }
        }
        Field field = findField(type, propertyName);
        return field == null ? null : new FieldProperty(field);
    }

    private Field findField(Class<?> type, String propertyName) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (propertyMatches(field.getName(), propertyName)) {
                    field.setAccessible(true);
                    return field;
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

    private boolean setterNameMatches(String methodName, String propertyName) {
        return methodName.startsWith("set")
                && methodName.length() > 3
                && propertyMatches(methodName.substring(3), propertyName);
    }

    private boolean propertyMatches(String actual, String expected) {
        return normalized(actual).equals(normalized(expected));
    }

    private String normalized(String value) {
        return value == null ? "" : value.replace("_", "").replace(".", "").toLowerCase(Locale.ROOT);
    }

    private Object convert(Object value, Class<?> targetType) {
        if (value == null || targetType == null || targetType.isInstance(value)) {
            return value;
        }
        if (targetType == String.class) {
            return value.toString();
        }
        if (targetType == java.math.BigDecimal.class && (value instanceof Number || value instanceof String)) {
            return new java.math.BigDecimal(value.toString());
        }
        if (targetType == java.math.BigInteger.class && (value instanceof Number || value instanceof String)) {
            return new java.math.BigDecimal(value.toString()).toBigIntegerExact();
        }
        if ((targetType == int.class || targetType == Integer.class) && value instanceof Number number) {
            return number.intValue();
        }
        if ((targetType == long.class || targetType == Long.class) && value instanceof Number number) {
            return number.longValue();
        }
        if ((targetType == double.class || targetType == Double.class) && value instanceof Number number) {
            return number.doubleValue();
        }
        if ((targetType == float.class || targetType == Float.class) && value instanceof Number number) {
            return number.floatValue();
        }
        if ((targetType == boolean.class || targetType == Boolean.class) && value instanceof Boolean) {
            return value;
        }
        if (targetType.isEnum() && value instanceof String enumName) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object converted = Enum.valueOf((Class<? extends Enum>) targetType, enumName);
            return converted;
        }
        return value;
    }

    private <T> T newInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate APP DDL object type " + type.getName(), e);
        }
    }

    private String safeIdentifier(String identifier, String label) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException(label + " is not a safe SQL identifier: " + identifier);
        }
        return identifier;
    }

    private interface Property {
        Object read(Object target);

        Class<?> type();
    }

    private interface WritableProperty {
        void write(Object target, Object value);

        Class<?> type();
    }

    private record KeyPredicate(List<String> columns, List<Object> values) {
    }

    private record MethodProperty(Method method) implements Property {
        @Override
        public Object read(Object target) {
            try {
                return method.invoke(target);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read " + method.getName()
                        + " from " + target.getClass().getName(), e);
            }
        }

        @Override
        public Class<?> type() {
            return method.getReturnType();
        }
    }

    private record MethodWritableProperty(Method method) implements WritableProperty {
        @Override
        public void write(Object target, Object value) {
            try {
                method.invoke(target, value);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot write " + method.getName()
                        + " on " + target.getClass().getName(), e);
            }
        }

        @Override
        public Class<?> type() {
            return method.getParameterTypes()[0];
        }
    }

    private record FieldProperty(Field field) implements Property, WritableProperty {
        @Override
        public Object read(Object target) {
            try {
                return field.get(target);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read " + field.getName()
                        + " from " + target.getClass().getName(), e);
            }
        }

        @Override
        public void write(Object target, Object value) {
            try {
                field.set(target, value);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot write " + field.getName()
                        + " on " + target.getClass().getName(), e);
            }
        }

        @Override
        public Class<?> type() {
            return field.getType();
        }
    }
}
