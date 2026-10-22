package com.servpp.service.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import com.servpp.service.runtime.AppDdlCatalog;

public class JdbcAppDdlSchemaManager {
    private final DataSource dataSource;

    public JdbcAppDdlSchemaManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void ensureTables(AppDdlCatalog catalog) {
        if (catalog == null || catalog.tables().isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement()) {
            for (AppDdlCatalog.Table table : catalog.tables()) {
                statement.execute(createTableSql(table, catalog));
                for (String indexSql : createIndexSql(table)) {
                    statement.execute(indexSql);
                }
            }
            JdbcRoleRelationshipStore.ensureTables(connection,catalog);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create APP DDL repository tables", e);
        }
    }

    private String createTableSql(AppDdlCatalog.Table table, AppDdlCatalog catalog) {
        String tableName = safeIdentifier(table.name(), "APP DDL table");
        List<String> definitions = new ArrayList<>();
        for (AppDdlCatalog.Column column : table.columns()) {
            String columnName = safeIdentifier(column.name(), "APP DDL column");
            String nullable = column.nullable() || table.primaryKeys().contains(column.name()) ? "" : " not null";
            definitions.add(columnName + " " + sqlType(column) + nullable);
        }
        var role = catalog.roleStorage().get(table.businessType());
        if (role != null && role.performerTable().isBlank())
            for (var performer : role.performers())
                if (table.columns().stream().noneMatch(column -> column.name().equals(JdbcRoleRelationshipStore.column(performer.slot()))))
                    definitions.add(JdbcRoleRelationshipStore.column(performer.slot()) + " clob");
        if (!table.primaryKeys().isEmpty()) {
            List<String> primaryKeys = new ArrayList<>();
            for (String primaryKey : table.primaryKeys()) {
                primaryKeys.add(safeIdentifier(primaryKey, "APP DDL primary key"));
            }
            definitions.add("primary key (" + String.join(", ", primaryKeys) + ")");
        }
        return "create table if not exists " + tableName + " (\n    "
                + String.join(",\n    ", definitions)
                + "\n)";
    }

    private List<String> createIndexSql(AppDdlCatalog.Table table) {
        List<String> indexes = new ArrayList<>();
        for (String indexPath : table.indexes()) {
            if (indexPath == null || indexPath.isBlank()) {
                continue;
            }
            List<String> columns = columnNamesForPath(table, indexPath);
            String column = String.join("_", columns);
            indexes.add("create index if not exists "
                    + safeIdentifier(table.name() + "_idx_" + column, "APP DDL index")
                    + " on " + safeIdentifier(table.name(), "APP DDL table")
                    + " (" + String.join(", ", columns) + ")");
        }
        for (String indexName : table.indexNames()) {
            List<String> paths = table.indexPathsByName().get(indexName);
            if (paths == null || paths.isEmpty()) {
                continue;
            }
            List<String> columns = new ArrayList<>();
            for (String path : paths) {
                for (String column : columnNamesForPath(table, path)) {
                    String safeColumn = safeIdentifier(column, "APP DDL index column");
                    if (!columns.contains(safeColumn)) columns.add(safeColumn);
                }
            }
            indexes.add("create " + (table.uniqueIndexNames().contains(indexName) ? "unique " : "") + "index if not exists "
                    + safeIdentifier(table.name() + "_" + indexName, "APP DDL index")
                    + " on " + safeIdentifier(table.name(), "APP DDL table")
                    + " (" + String.join(", ", columns) + ")");
        }
        return List.copyOf(indexes);
    }

    private List<String> columnNamesForPath(AppDdlCatalog.Table table, String path) {
        String normalizedPath = normalized(stripBusinessTypePrefix(table, path));
        String normalizedColumnPath = normalized(stripBusinessTypePrefix(table, path).replace(".", "_"));
        for (AppDdlCatalog.Column column : table.columns()) {
            String normalizedColumn = normalized(column.name());
            if (normalizedColumn.equals(normalizedPath) || normalizedColumn.equals(normalizedColumnPath)) {
                return List.of(safeIdentifier(column.name(), "APP DDL index column"));
            }
        }
        // A participant association projects each identity component into a physical column.
        String prefix = stripBusinessTypePrefix(table, path).replace(".", "_").toLowerCase(Locale.ROOT) + "_";
        List<String> projected = new ArrayList<>();
        for (AppDdlCatalog.Column column : table.columns()) {
            if (column.objectBacked() && column.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                projected.add(safeIdentifier(column.name(), "APP DDL index column"));
            }
        }
        if (!projected.isEmpty()) return List.copyOf(projected);
        throw new IllegalStateException("APP DDL index path " + path
                + " does not map to a column in table " + table.name());
    }

    private String stripBusinessTypePrefix(AppDdlCatalog.Table table, String path) {
        String prefix = table.businessType() + ".";
        return path != null && path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }

    private String sqlType(AppDdlCatalog.Column column) {
        String type = column.type() == null ? "" : column.type().trim().toUpperCase(Locale.ROOT);
        int precision = column.precision();
        int scale = column.scale();
        return switch (type) {
            case "VARCHAR", "CHAR", "CHARACTER VARYING" -> "varchar(" + positiveOrDefault(precision, 255) + ")";
            case "DECIMAL", "NUMERIC" -> "decimal(" + positiveOrDefault(precision, 19)
                    + "," + Math.max(scale, 0) + ")";
            case "DOUBLE", "DOUBLE PRECISION" -> "double";
            case "FLOAT", "REAL" -> type.toLowerCase(Locale.ROOT);
            case "INTEGER", "INT" -> "integer";
            case "BIGINT" -> "bigint";
            case "SMALLINT" -> "smallint";
            case "BOOLEAN", "BIT" -> "boolean";
            case "DATE" -> "date";
            case "TIMESTAMP", "TIMESTAMP_WITH_TIMEZONE" -> "timestamp";
            default -> type.isBlank() ? "varchar(255)" : type.toLowerCase(Locale.ROOT);
        };
    }

    private int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private String safeIdentifier(String identifier, String label) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException(label + " is not a safe SQL identifier: " + identifier);
        }
        return identifier;
    }

    private String normalized(String value) {
        return value == null ? "" : value.replace("_", "").replace(".", "").toLowerCase(Locale.ROOT);
    }
}
