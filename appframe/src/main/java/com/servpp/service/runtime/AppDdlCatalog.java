package com.servpp.service.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public record AppDdlCatalog(List<Table> tables, Map<String, RoleStorage> roleStorage, boolean propertyTracking,
                            boolean optimisticConcurrency) {
    public AppDdlCatalog(List<Table> tables, Map<String, RoleStorage> roles, boolean propertyTracking) {
        this(tables, roles, propertyTracking, true);
    }
    public AppDdlCatalog(List<Table> tables, Map<String, RoleStorage> roles) { this(tables, roles, true); }
    public record Performer(String type, String slot, String javaType, String member, List<String> keys) { }
    public record RoleStorage(String table, String type, String primaryKey, boolean independent,
                              String performerTable, List<Performer> performers, List<List<String>> variants) {
        public RoleStorage(String table, String type, String primaryKey, boolean independent,
                           String performerTable, List<Performer> performers) {
            this(table,type,primaryKey,independent,performerTable,performers,List.of());
        }
    }
    public AppDdlCatalog(List<Table> tables) { this(tables, Map.of()); }
    public AppDdlCatalog { tables = List.copyOf(tables); roleStorage = Map.copyOf(roleStorage); }

    private static boolean policy(Properties properties, String name) {
        String value = properties.getProperty(name, "true").trim();
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid APP " + name + " policy");
        return Boolean.parseBoolean(value);
    }
    private static final Set<String> SUPPORTED_TABLE_CATEGORIES =
            Set.of("entities", "roles", "contracts", "references");

    public static AppDdlCatalog empty() {
        return new AppDdlCatalog(List.of());
    }

    public static AppDdlCatalog fromResource(String resourceName) {
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalStateException("Cannot find generated APP DDL catalog: resource name is required");
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream stream = classLoader == null
                ? AppDdlCatalog.class.getResourceAsStream(resourceName)
                : classLoader.getResourceAsStream(stripLeadingSlash(resourceName));
        if (stream == null) {
            throw new IllegalStateException("Cannot find generated APP DDL catalog " + resourceName);
        }
        return fromProperties(stream);
    }

    public static AppDdlCatalog fromProperties(InputStream stream) {
        if (stream == null) {
            throw new IllegalStateException("Cannot load generated APP DDL catalog: input stream is null");
        }
        Properties properties = new Properties();
        try (InputStream input = stream) {
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load generated APP DDL catalog", e);
        }
        return fromProperties(properties);
    }

    public static AppDdlCatalog fromProperties(Properties properties) {
        if (properties == null) {
            throw new IllegalStateException("Cannot load generated APP DDL catalog: properties are null");
        }
        String tableNames = properties.getProperty("tables", "");
        if (tableNames.isBlank()) {
            return new AppDdlCatalog(List.of(), Map.of(), policy(properties, "propertyTracking"), policy(properties, "optimisticConcurrency"));
        }
        List<Table> tables = new ArrayList<>();
        Set<String> seenTableNames = new LinkedHashSet<>();
        Map<String, String> tableNameByBusinessType = new LinkedHashMap<>();
        for (String normalizedName : parseList(tableNames, "APP DDL catalog table")) {
            if (!seenTableNames.add(normalizedName)) {
                throw new IllegalStateException("APP DDL catalog declares duplicate table " + normalizedName);
            }
            String category = properties.getProperty("table." + normalizedName + ".category", "").trim();
            if (!category.isBlank() && !SUPPORTED_TABLE_CATEGORIES.contains(category)) {
                throw new IllegalStateException("APP DDL table " + normalizedName
                        + " category must be one of entities, roles, contracts, references, but was " + category);
            }
            String businessType = properties.getProperty("table." + normalizedName + ".businessType", "").trim();
            if (!businessType.isBlank()) {
                String existingTableName = tableNameByBusinessType.putIfAbsent(businessType, normalizedName);
                if (existingTableName != null) {
                    throw new IllegalStateException("APP DDL catalog maps business type " + businessType
                            + " to multiple tables: " + existingTableName + ", " + normalizedName);
                }
            }
            List<String> primaryKeys = parseList(properties.getProperty(
                    "table." + normalizedName + ".primaryKeys", ""),
                    "APP DDL table " + normalizedName + " primary key");
            requireUnique("APP DDL table " + normalizedName + " primary key", primaryKeys);
            List<String> indexes = parseList(properties.getProperty("table." + normalizedName + ".indexes", ""),
                    "APP DDL table " + normalizedName + " index path");
            requireUnique("APP DDL table " + normalizedName + " index path", indexes);
            List<String> indexNames = parseList(properties.getProperty("table." + normalizedName + ".indexNames", ""),
                    "APP DDL table " + normalizedName + " named index");
            requireUnique("APP DDL table " + normalizedName + " named index", indexNames);
            List<String> businessCredentialColumns = parseList(properties.getProperty(
                    "table." + normalizedName + ".businessCredentialColumns", ""),
                    "APP DDL table " + normalizedName + " business credential column");
            requireUnique("APP DDL table " + normalizedName + " business credential column",
                    businessCredentialColumns);
            Map<String, List<String>> indexPathsByName = parseNamedIndexPaths(properties, normalizedName, indexNames);
            List<Column> columns = parseColumns(properties, normalizedName);
            tables.add(new Table(normalizedName,
                    category,
                    businessType,
                    primaryKeys,
                    indexes,
                    indexNames,
                    businessCredentialColumns,
                    indexPathsByName,
                    columns,
                    keyPathsByColumnBacking(businessType, primaryKeys, columns, true),
                    keyPathsByColumnBacking(businessType, primaryKeys, columns, false),
                    indexPathsByColumnBacking(businessType, indexes, indexPathsByName, columns, true),
                    indexPathsByColumnBacking(businessType, indexes, indexPathsByName, columns, false),
                    platformColumns(columns),
                    parseList(properties.getProperty("table." + normalizedName + ".excludedFacts", ""), "excluded facts"),
                    properties.getProperty("table." + normalizedName + ".queryService", ""),
                    parseList(properties.getProperty("table." + normalizedName + ".snapshotFacts", ""), "snapshot facts"),
                    indexNames.stream().filter(index -> parseBoolean(properties,
                            "table." + normalizedName + ".index." + index + ".unique", false)).toList()));
        }
        Map<String,RoleStorage> roles = new LinkedHashMap<>();
        for (Table table : tables) {
            String prefix = "table." + table.name() + ".roleStorage.";
            if (!parseBoolean(properties,prefix + "enabled",false)) continue;
            boolean entity = "entities".equals(table.category());
            if ((!entity && !"roles".equals(table.category())) || table.primaryKeys().size() != 1)
                throw new IllegalStateException("Independent identity or performer storage requires one entity or role primary key: " + table.name());
            // Keep the existing catalog namespace, but entities have no performer relationships.
            if (entity && (!parseBoolean(properties,prefix + "independent",false)
                    || !properties.getProperty(prefix + "performerTable","").isBlank()
                    || !properties.getProperty(prefix + "performers","").isBlank()
                    || !properties.getProperty(prefix + "variants","").isBlank()
                    || table.platformPrimaryKeys().size() != 1))
                throw new IllegalStateException("Entity independent identity requires an APP-only key and no performers: " + table.name());
            String relation = properties.getProperty(prefix + "performerTable", "");
            safeRoleStorageIdentifier(table.name()); safeRoleStorageIdentifier(table.primaryKeys().get(0));
            if (!relation.isBlank()) {
                safeRoleStorageIdentifier(relation);
                if (tables.stream().anyMatch(candidate -> candidate.name().equalsIgnoreCase(relation)))
                    throw new IllegalStateException("Role relationship table conflicts with business table " + relation);
            }
            List<Performer> performers = new ArrayList<>();
            for (String name : parseList(properties.getProperty(prefix + "performers", ""),"role performer")) {
                safeRoleStorageIdentifier(name);
                String actor = prefix + "performer." + name + ".";
                performers.add(new Performer(properties.getProperty(actor + "type",name), name, properties.getProperty(actor + "javaType", ""),
                        properties.getProperty(actor + "member", ""), parseList(properties.getProperty(actor + "keys", ""), "performer keys")));
            }
            roles.put(table.businessType(), new RoleStorage(table.name(),table.businessType(),table.primaryKeys().get(0),
                    parseBoolean(properties,prefix + "independent",false),relation,List.copyOf(performers),
                    java.util.Arrays.stream(properties.getProperty(prefix + "variants", "").split(";"))
                        .filter(value -> !value.isBlank()).map(value -> parseList(value,"role variant")).toList()));
        }
        return new AppDdlCatalog(tables, roles, policy(properties, "propertyTracking"), policy(properties, "optimisticConcurrency"));
    }

    private static void safeRoleStorageIdentifier(String value) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new IllegalStateException("Invalid role storage identifier: " + value);
    }

    public Optional<Table> table(String tableName) {
        for (Table table : tables) {
            if (table.name().equals(tableName)) {
                return Optional.of(table);
            }
        }
        return Optional.empty();
    }

    public Optional<Table> tableForBusinessType(String businessType) {
        for (Table table : tables) {
            if (table.businessType().equals(businessType)) {
                return Optional.of(table);
            }
        }
        return Optional.empty();
    }

    private static List<Column> parseColumns(Properties properties, String tableName) {
        List<Column> columns = new ArrayList<>();
        List<String> columnNames = parseList(properties.getProperty("table." + tableName + ".columns", ""),
                "APP DDL table " + tableName + " column");
        requireUnique("APP DDL table " + tableName + " column", columnNames);
        for (String columnName : columnNames) {
            String prefix = "table." + tableName + ".column." + columnName + ".";
            columns.add(new Column(columnName,
                    properties.getProperty(prefix + "type", ""),
                    parseInt(properties, prefix + "precision", 0),
                    parseInt(properties, prefix + "scale", 0),
                    parseBoolean(properties, prefix + "nullable", true),
                    parseBoolean(properties, prefix + "objectBacked", true)));
        }
        return List.copyOf(columns);
    }

    private static Map<String, List<String>> parseNamedIndexPaths(Properties properties, String tableName,
                                                                  List<String> indexNames) {
        Map<String, List<String>> indexes = new LinkedHashMap<>();
        for (String indexName : indexNames) {
            String label = "APP DDL table " + tableName + " named index " + indexName + " path";
            List<String> paths = parseList(properties.getProperty(
                    "table." + tableName + ".index." + indexName + ".paths", ""),
                    label);
            requireUnique(label, paths);
            indexes.put(indexName, paths);
        }
        return Map.copyOf(indexes);
    }

    private static List<String> parseList(String value, String label) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String item : value.split(",", -1)) {
            String normalized = item.trim();
            if (normalized.isEmpty()) {
                throw new IllegalStateException(label + " contains blank value");
            }
            values.add(normalized);
        }
        return List.copyOf(values);
    }

    private static void requireUnique(String label, List<String> values) {
        Set<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            if (!seen.add(value)) {
                throw new IllegalStateException(label + " declares duplicate value " + value);
            }
        }
    }

    private static int parseInt(Properties properties, String propertyName, int defaultValue) {
        String value = properties.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("APP DDL catalog property " + propertyName
                    + " must be an integer, but was " + value, e);
        }
    }

    private static boolean parseBoolean(Properties properties, String propertyName, boolean defaultValue) {
        String value = properties.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalStateException("APP DDL catalog property " + propertyName
                + " must be true or false, but was " + value);
    }

    private static String stripLeadingSlash(String resourceName) {
        return resourceName.startsWith("/") ? resourceName.substring(1) : resourceName;
    }

    public Map<String, Table> byName() {
        Map<String, Table> values = new LinkedHashMap<>();
        for (Table table : tables) {
            values.put(table.name(), table);
        }
        return Map.copyOf(values);
    }

    public record Table(String name, String category, String businessType,
                        List<String> primaryKeys, List<String> indexes, List<String> indexNames,
                        List<String> businessCredentialColumns, Map<String, List<String>> indexPathsByName,
                        List<Column> columns,
                        List<String> objectBackedPrimaryKeys, List<String> platformPrimaryKeys,
                        List<String> objectBackedIndexPaths, List<String> platformIndexPaths,
                        List<String> platformColumns, List<String> excludedFacts, String queryService, List<String> snapshotFacts,
                        List<String> uniqueIndexNames) {
        public Table(String name, String category, String businessType,
                     List<String> primaryKeys, List<String> indexes, List<String> indexNames,
                     List<String> businessCredentialColumns, Map<String,List<String>> indexPathsByName,
                     List<Column> columns, List<String> objectBackedPrimaryKeys, List<String> platformPrimaryKeys,
                     List<String> objectBackedIndexPaths, List<String> platformIndexPaths, List<String> platformColumns,
                     List<String> excludedFacts, String queryService, List<String> snapshotFacts) {
            this(name,category,businessType,primaryKeys,indexes,indexNames,businessCredentialColumns,indexPathsByName,
                    columns,objectBackedPrimaryKeys,platformPrimaryKeys,objectBackedIndexPaths,platformIndexPaths,
                    platformColumns,excludedFacts,queryService,snapshotFacts,List.of());
        }
        public Table(String name, String category, String businessType,
                     List<String> primaryKeys, List<String> indexes, List<String> indexNames,
                     List<String> businessCredentialColumns, Map<String,List<String>> indexPathsByName,
                     List<Column> columns, List<String> objectBackedPrimaryKeys, List<String> platformPrimaryKeys,
                     List<String> objectBackedIndexPaths, List<String> platformIndexPaths, List<String> platformColumns,
                     List<String> excludedFacts, String queryService) {
            this(name,category,businessType,primaryKeys,indexes,indexNames,businessCredentialColumns,indexPathsByName,
                    columns,objectBackedPrimaryKeys,platformPrimaryKeys,objectBackedIndexPaths,platformIndexPaths,
                    platformColumns,excludedFacts,queryService,List.of());
        }
        public Table(String name, String category, String businessType,
                     List<String> primaryKeys, List<String> indexes, List<String> indexNames,
                     List<String> businessCredentialColumns, Map<String,List<String>> indexPathsByName,
                     List<Column> columns, List<String> objectBackedPrimaryKeys, List<String> platformPrimaryKeys,
                     List<String> objectBackedIndexPaths, List<String> platformIndexPaths, List<String> platformColumns) {
            this(name,category,businessType,primaryKeys,indexes,indexNames,businessCredentialColumns,indexPathsByName,
                 columns,objectBackedPrimaryKeys,platformPrimaryKeys,objectBackedIndexPaths,platformIndexPaths,platformColumns,List.of(), "");
        }
        public Table(String name, String category, String businessType,
                     List<String> primaryKeys, List<String> indexes, List<String> indexNames,
                     List<String> businessCredentialColumns, Map<String,List<String>> indexPathsByName,
                     List<Column> columns, List<String> objectBackedPrimaryKeys, List<String> platformPrimaryKeys,
                     List<String> objectBackedIndexPaths, List<String> platformIndexPaths, List<String> platformColumns, List<String> excludedFacts) {
            this(name,category,businessType,primaryKeys,indexes,indexNames,businessCredentialColumns,indexPathsByName,
                 columns,objectBackedPrimaryKeys,platformPrimaryKeys,objectBackedIndexPaths,platformIndexPaths,platformColumns,excludedFacts, "");
        }
        public Table {
            uniqueIndexNames = List.copyOf(uniqueIndexNames);
            if (!indexNames.containsAll(uniqueIndexNames)) throw new IllegalArgumentException("Unique indexes must be declared named indexes");
            snapshotFacts = List.copyOf(snapshotFacts); excludedFacts = List.copyOf(excludedFacts); queryService = queryService == null ? "" : queryService; }
    }

    public record Column(String name, String type, int precision, int scale, boolean nullable, boolean objectBacked) {
    }

    private static List<String> keyPathsByColumnBacking(String businessType, List<String> keyPaths,
                                                        List<Column> columns, boolean objectBacked) {
        List<String> values = new ArrayList<>();
        for (String keyPath : keyPaths) {
            if (isObjectBackedPath(businessType, keyPath, columns) == objectBacked) {
                values.add(keyPath);
            }
        }
        return List.copyOf(values);
    }

    private static List<String> indexPathsByColumnBacking(String businessType, List<String> indexes,
                                                          Map<String, List<String>> indexPathsByName,
                                                          List<Column> columns, boolean objectBacked) {
        Set<String> values = new LinkedHashSet<>();
        for (String indexPath : indexes) {
            if (isObjectBackedIndexPath(businessType, indexPath, columns) == objectBacked) {
                values.add(indexPath);
            }
        }
        for (List<String> indexPaths : indexPathsByName.values()) {
            for (String indexPath : indexPaths) {
                if (isObjectBackedIndexPath(businessType, indexPath, columns) == objectBacked) {
                    values.add(indexPath);
                }
            }
        }
        return List.copyOf(values);
    }

    private static List<String> platformColumns(List<Column> columns) {
        List<String> values = new ArrayList<>();
        for (Column column : columns) {
            if (!column.objectBacked()) {
                values.add(column.name());
            }
        }
        return List.copyOf(values);
    }

    private static boolean isObjectBackedPath(String businessType, String path, List<Column> columns) {
        Set<String> candidateColumnNames = columnNameCandidates(businessType, path);
        for (Column column : columns) {
            if (candidateColumnNames.contains(column.name())) {
                return column.objectBacked();
            }
        }
        return true;
    }

    private static boolean isObjectBackedIndexPath(String businessType, String path, List<Column> columns) {
        Set<String> candidateColumnNames = columnNameCandidates(businessType, path);
        for (Column column : columns) {
            if (candidateColumnNames.contains(column.name())) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> columnNameCandidates(String businessType, String path) {
        Set<String> values = new LinkedHashSet<>();
        values.add(path);
        values.add(path.replace(".", "_"));
        String normalizedPath = stripBusinessTypePrefix(businessType, path);
        values.add(normalizedPath);
        values.add(normalizedPath.replace(".", "_"));
        Set<String> lowerCaseValues = new LinkedHashSet<>();
        for (String value : values) {
            lowerCaseValues.add(value.toLowerCase(Locale.ROOT));
        }
        values.addAll(lowerCaseValues);
        return values;
    }

    private static String stripBusinessTypePrefix(String businessType, String path) {
        String prefix = businessType + ".";
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }
}
