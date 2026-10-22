package com.servpp.generator.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.generator.util.NamedArray;
import com.servpp.lang.general.IConstance;
import com.servpp.lang.general.ISppErrorLogger;
import com.servpp.lang.general.NameUtil;
import com.servpp.lang.models.*;
import com.servpp.lang.models.app.*;

import java.io.File;
import java.sql.JDBCType;
import java.util.*;

import static com.servpp.generator.db.DbTableConfig.*;

/**
 * @author lidong@date 2023-12-07@version 1.0
 */
public class DDLGenerator implements ISppErrorLogger {
    private SppDomain sppDomain;
    private NamedArray<DbTable> tableList = new NamedArray<>();
    private Map<String,DbTable> clsToTable = new HashMap<>();
    private List<String> mapperValidationErrors = new ArrayList<>();
    private Set<String> expandedPrimaryKeyTables = new HashSet<>();
    private File rdbFile;
    private final Map<AppTable,DbTable> externalKeyTables = new IdentityHashMap<>();

    private record TableGenerationPlan(AppTable appTable, DbTableConfig rootConfig) {
    }

    public SppDomain getSppDomain() { return sppDomain; }

    public DDLGenerator(SppDomain sppDomain, File rdbFile) {
        this.sppDomain = sppDomain;
        this.rdbFile = rdbFile;
        instanceEntity();
        createTables();
        mapperValidationErrors = validateMapperDeclarationsInternal(sppDomain);
        if (mapperValidationErrors.isEmpty()) {
            mapping(sppDomain);
        }
        removeDefaultServiceWithoutTable();
    }

    public String getRoleRelationshipDdl() {
        var names = new java.util.LinkedHashSet<String>();
        for (var group : sppDomain.getRuleBlock().getAppTables()) for (var table : group.getAppTableList())
            if (table.getPerformerTable() != null && !table.getPerformerTable().isBlank()) names.add(table.getPerformerTable());
        return names.stream().map(name -> "create table " + name
                + " (role_type varchar(255) not null, role_id varchar(2048) not null, performer_type varchar(255) not null,"
                + " performer_key clob not null, primary key(role_type, role_id, performer_type));")
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private void removeDefaultServiceWithoutTable() {
        List<SppDefaultService> list = sppDomain.getSppDefaultServices();
        for(int i = list.size() - 1;i>=0;i--){
            SppDefaultService sppDefaultService = list.get(i);
            SppClass realm = (SppClass) sppDefaultService.getRealm();
            if(clsToTable.get(realm.getName()) == null){
                for(SppService sppService: sppDefaultService.getServiceList()){
                    SppCompilationUnit sppCompilationUnit = sppDomain.getMapsOfClass().get(sppService.getName());
                    sppDomain.getMapsOfClass().remove(sppService.getName());
                }
                list.remove(i);
            }
        }
    }

    private void instanceEntity() {
        for (AppTables appTables : sppDomain.getRuleBlock().getAppTables()) {
            for (AppTable appTable : appTables.getAppTableList()) {
                if (!isGeneratable(appTable)) {
                    continue;
                }
                SppClass sppClass = appTable.getEntity();
                sppClass = sppClass.instance(new SppField(sppClass,NameUtil.firstToLowerCase(sppClass.getName(),true)),sppDomain);
                for (AppColumn appColumn : appTable.getAppColumns()) {
                    if (sppClass.getField(appColumn.getName()) == null) {
                        appColumn.setObjectBacked(false);
                        SppField field = new SppField(appColumn.getType(), appColumn.getName());
                        sppClass.addField(field);
                    }
                }
                appTable.setEntity(sppClass);
            }
        }
    }

    private boolean isGeneratable(AppTable appTable) {
        return isGeneratable(appTable, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private boolean isGeneratable(AppTable appTable, Set<AppTable> visitingTables) {
        if (appTable == null || !visitingTables.add(appTable)) {
            return false;
        }
        try {
            return isGeneratableInternal(appTable, visitingTables);
        } finally {
            visitingTables.remove(appTable);
        }
    }

    private boolean isGeneratableInternal(AppTable appTable, Set<AppTable> visitingTables) {
        return appTable != null
                && hasUniqueAppTableName(appTable)
                && appTable.getEntity() != null
                && appTable.getEntity().isReal()
                && hasSingleBusinessTypeTarget(appTable)
                && hasUniqueBusinessTypeMapping(appTable)
                && hasMatchingTableCategory(appTable)
                && hasPrimaryKey(appTable)
                && hasResolvableObjectKeyFields(appTable, visitingTables)
                && hasSingleTargetForeignKeyDeclarations(appTable)
                && hasStableForeignKeyReferences(appTable)
                && hasNoQuantumKeyFields(appTable)
                && hasUniqueExplicitColumnNames(appTable)
                && hasStableColumnModifiers(appTable)
                && hasSupportedColumnPrecisionScale(appTable)
                && hasStableExplicitColumnShadows(appTable)
                && hasStableImplicitObjectExpansion(appTable)
                && hasUniqueOutputColumnNames(appTable)
                && !hasArrayKeyFields(appTable);
    }

    private SppDomain tableOwner(AppTable table) {
        if (sppDomain.getProject() != null) for (var owner : sppDomain.getProject().getSppDomainMap().values())
            for (var group : owner.getRuleBlock().getAppTables())
                if (group.getAppTableList().contains(table)) return owner;
        return sppDomain;
    }

    private boolean hasUniqueAppTableName(AppTable appTable) {
        return appTable.getName() != null
                && !tableOwner(appTable).getRuleBlock().getDuplicateTableNames().contains(appTable.getName());
    }

    private boolean hasSingleBusinessTypeTarget(AppTable appTable) {
        return appTable.getExtraBusinessTypeNames().isEmpty();
    }

    private boolean hasUniqueBusinessTypeMapping(AppTable appTable) {
        if (appTable.getEntity() == null || !appTable.getEntity().isReal()) {
            return false;
        }
        int count = 0;
        String businessTypeName = appTable.getEntity().getName();
        for (AppTables appTables : tableOwner(appTable).getRuleBlock().getAppTables()) {
            for (AppTable candidate : appTables.getAppTableList()) {
                if (candidate.getEntity() != null
                        && candidate.getEntity().isReal()
                        && businessTypeName.equals(candidate.getEntity().getName())) {
                    count++;
                }
            }
        }
        return count == 1;
    }

    private boolean hasMatchingTableCategory(AppTable appTable) {
        IConstance.CompilationUnitType expectedType = expectedTableTargetType(appTable.getCategory());
        return expectedType != null && appTable.getEntity().getType() == expectedType;
    }

    private IConstance.CompilationUnitType expectedTableTargetType(AppTables.Category category) {
        return switch (category) {
            case entities -> IConstance.CompilationUnitType.entity;
            case roles -> IConstance.CompilationUnitType.role;
            case contracts -> IConstance.CompilationUnitType.contract;
            case other -> null;
        };
    }

    private boolean hasUniqueExplicitColumnNames(AppTable appTable) {
        Set<String> columnNames = new LinkedHashSet<>();
        for (AppColumn appColumn : appTable.getAppColumns()) {
            if (!columnNames.add(appColumn.getName())) {
                return false;
            }
        }
        return true;
    }

    private boolean hasStableColumnModifiers(AppTable appTable) {
        for (AppColumn appColumn : appTable.getAppColumns()) {
            if (appColumn.getPrimaryKeyModifierCount() > 1
                    || appColumn.getNullableModifierCount() > 1
                    || appColumn.getNotNullModifierCount() > 1
                    || appColumn.hasConflictingNullabilityModifiers()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasStableExplicitColumnShadows(AppTable appTable) {
        if (appTable.getEntity() == null) {
            return true;
        }
        for (AppColumn appColumn : appTable.getAppColumns()) {
            SppLocalVar businessField = appTable.getEntity().getField(appColumn.getName());
            if (businessField == null) {
                continue;
            }
            if (businessField.getArrayDimension() != 0 || !isPersistedScalarDbField(businessField)) {
                return false;
            }
            if (appColumn.getType() == null || businessField.getType() == null
                    || !appColumn.getType().getName().equals(businessField.getType().getName())) {
                return false;
            }
        }
        return true;
    }

    private boolean hasStableImplicitObjectExpansion(AppTable appTable) {
        if (appTable.getEntity() == null) {
            return true;
        }
        Set<String> explicitlyExpandedFields = explicitlyExpandedObjectFieldNames(appTable);
        for (SppLocalVar field : appTable.getEntity().getSppFieldList()) {
            if (((SppField)field).isPerformerRelation() || field.getArrayDimension() != 0 || isPersistedScalarDbField(field)
                    || explicitlyExpandedFields.contains(field.getName())
                    || !(field.getType() instanceof SppClass refClass)) {
                continue;
            }
            if (hasRecursiveImplicitObjectExpansion(refClass,
                    Collections.newSetFromMap(new IdentityHashMap<>()))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasRecursiveImplicitObjectExpansion(SppClass refClass, Set<SppClass> visiting) {
        if (refClass == null) {
            return false;
        }
        if (!visiting.add(refClass)) {
            return true;
        }
        for (SppLocalVar field : refClass.getSppFieldList()) {
            if (((SppField)field).isPerformerRelation() || field.getArrayDimension() != 0 || isPersistedScalarDbField(field)
                    || !(field.getType() instanceof SppClass nestedClass)) {
                continue;
            }
            if (hasRecursiveImplicitObjectExpansion(nestedClass, visiting)) {
                visiting.remove(refClass);
                return true;
            }
        }
        visiting.remove(refClass);
        return false;
    }

    private boolean hasUniqueOutputColumnNames(AppTable appTable) {
        Set<String> outputColumns = new LinkedHashSet<>();
        Set<String> explicitColumnNames = new LinkedHashSet<>();
        DbTableConfig configs = tableConfig(appTable);
        for (AppColumn appColumn : appTable.getAppColumns()) {
            explicitColumnNames.add(appColumn.getName());
            if (!addOutputColumn(outputColumns, appColumn.getName())) {
                return false;
            }
        }
        if (appTable.getEntity() != null) {
            for (SppLocalVar field : appTable.getEntity().getSppFieldList()) {
                if (((com.servpp.lang.models.SppField)field).isStoredHere() && field.getArrayDimension() == 0 && isPersistedScalarDbField(field)
                        && !explicitColumnNames.contains(field.getName())
                        && !addOutputColumn(outputColumns, field.getName())) {
                    return false;
                }
            }
        }
        for (SppFieldReference primaryKey : appTable.getPrimaryKeys()) {
            if (!addFieldOutputColumns(appTable, configs, outputColumns, explicitColumnNames,
                    primaryKey)) {
                return false;
            }
        }
        for (AppForeign appForeign : appTable.getForeignKeys()) {
            for (SppFieldReference keyReference : appForeign.getKeys()) {
                // PK and FK can constrain the same participant relationship.
                if (appTable.getPrimaryKeys().stream().anyMatch(primary ->
                        appDdlObjectPath(primary).equals(appDdlObjectPath(keyReference)))) continue;
                if (!addFieldOutputColumns(appTable, configs, outputColumns, explicitColumnNames,
                        keyReference.getSppField())) {
                    return false;
                }
            }
        }
        return addImplicitObjectOutputColumns(appTable, configs, outputColumns, explicitColumnNames);
    }

    private boolean addFieldOutputColumns(AppTable appTable, DbTableConfig configs, Set<String> outputColumns,
                                          Set<String> explicitColumnNames, SppFieldReference fieldReference) {
        SppField field = fieldReference == null ? null : fieldReference.getSppField();
        if (field == null || field.getArrayDimension() != 0) {
            return true;
        }
        String objectPath = appDdlObjectPath(fieldReference);
        if (isPersistedScalarDbField(field) && objectPath.contains(".")) {
            String columnName = appDdlPathColumnName(appTable, configs, objectPath);
            return explicitColumnNames.contains(columnName)
                    || outputColumns.contains(columnName)
                    || addOutputColumn(outputColumns, columnName);
        }
        return addFieldOutputColumns(appTable, configs, outputColumns, explicitColumnNames, field);
    }

    private boolean addFieldOutputColumns(AppTable appTable, DbTableConfig configs, Set<String> outputColumns,
                                          Set<String> explicitColumnNames, SppField field) {
        if (field == null || field.getArrayDimension() != 0) {
            return true;
        }
        if (isPersistedScalarDbField(field)) {
            return explicitColumnNames.contains(field.getName())
                    || outputColumns.contains(field.getName())
                    || addOutputColumn(outputColumns, field.getName());
        }
        AppTable targetTable = findAppTableByBusinessType(field.getType().getName());
        if (targetTable == null) {
            return addOutputColumn(outputColumns, field.getName());
        }
        boolean hasSame = containsSameType(appTable.getEntity(), field);
        DbTableConfig targetConfigs = tableConfig(targetTable);
        for (String targetPrimaryKeyName : getPrimaryKeyColumnNames(targetTable, targetConfigs,
                Collections.newSetFromMap(new IdentityHashMap<>()))) {
            String columnName = genColumnName(configs, field.getName(), targetPrimaryKeyName,
                    hasSame ? configs.getDupNamingType() : configs.getNamingType());
            if (!explicitColumnNames.contains(columnName) && !addOutputColumn(outputColumns, columnName)) {
                return false;
            }
        }
        return true;
    }

    private boolean addImplicitObjectOutputColumns(AppTable appTable, DbTableConfig configs,
                                                   Set<String> outputColumns,
                                                   Set<String> explicitColumnNames) {
        if (appTable.getEntity() == null) {
            return true;
        }
        Set<String> explicitlyExpandedFields = explicitlyExpandedObjectFieldNames(appTable);
        Map<String, AppTable> foreignTableByType = foreignTableByType(appTable);
        for (SppLocalVar field : appTable.getEntity().getSppFieldList()) {
            if (((SppField)field).isPerformerRelation() || field.getArrayDimension() != 0 || isPersistedScalarDbField(field)
                    || explicitlyExpandedFields.contains(field.getName())) {
                continue;
            }
            AppTable foreignTable = foreignTableByType.get(field.getType().getName());
            boolean hasSame = containsSameType(appTable.getEntity(), field);
            if (foreignTable != null) {
                DbTableConfig targetConfigs = tableConfig(foreignTable);
                for (String targetPrimaryKeyName : getPrimaryKeyColumnNames(foreignTable, targetConfigs,
                        Collections.newSetFromMap(new IdentityHashMap<>()))) {
                    String columnName = genColumnName(configs, field.getName(), targetPrimaryKeyName,
                            hasSame ? configs.getDupNamingType() : configs.getNamingType());
                    if (!explicitColumnNames.contains(columnName) && !addOutputColumn(outputColumns, columnName)) {
                        return false;
                    }
                }
                continue;
            }
            if (field.getType() instanceof SppClass refClass
                    && !addExpandedObjectOutputColumns(configs, outputColumns, explicitColumnNames,
                    field.getName(), refClass, hasSame, Collections.newSetFromMap(new IdentityHashMap<>()))) {
                return false;
            }
        }
        return true;
    }

    private boolean addExpandedObjectOutputColumns(DbTableConfig configs, Set<String> outputColumns,
                                                   Set<String> explicitColumnNames, String superName,
                                                   SppClass refClass, boolean hasSame,
                                                   Set<SppClass> visiting) {
        if (refClass == null || !visiting.add(refClass)) {
            return true;
        }
        for (SppLocalVar field : refClass.getSppFieldList()) {
            if (field.getArrayDimension() != 0) {
                continue;
            }
            String columnName = genColumnName(configs, superName, field.getName(),
                    hasSame ? configs.getDupNamingType() : configs.getNamingType());
            if (isPersistedScalarDbField(field)) {
                if (!explicitColumnNames.contains(columnName) && !addOutputColumn(outputColumns, columnName)) {
                    visiting.remove(refClass);
                    return false;
                }
            } else if (field.getType() instanceof SppClass nestedClass
                    && !addExpandedObjectOutputColumns(configs, outputColumns, explicitColumnNames,
                    columnName, nestedClass,
                    containsSameType(refClass, field), visiting)) {
                visiting.remove(refClass);
                return false;
            }
        }
        visiting.remove(refClass);
        return true;
    }

    private boolean addOutputColumn(Set<String> outputColumns, String columnName) {
        return columnName == null || outputColumns.add(columnName);
    }

    private String appDdlObjectPath(SppFieldReference fieldReference) {
        if (fieldReference == null) {
            return "";
        }
        String objectPath = fieldReference.getObjectPath();
        if (objectPath != null && !objectPath.isBlank()) {
            return objectPath;
        }
        return fieldReference.getSppField() == null ? "" : fieldReference.getSppField().getName();
    }

    private String appDdlQualifiedName(SppFieldReference fieldReference) {
        if (fieldReference == null) {
            return "";
        }
        String qualifiedName = fieldReference.getQualifiedName();
        if (qualifiedName != null && !qualifiedName.isBlank()) {
            return qualifiedName;
        }
        SppField field = fieldReference.getSppField();
        return field == null ? "" : field.getQualifiedInstName();
    }

    private String appDdlPathColumnName(AppTable appTable, DbTableConfig configs, String objectPath) {
        if (objectPath == null || objectPath.isBlank()) {
            return objectPath;
        }
        if (!objectPath.contains(".")) {
            return objectPath;
        }
        List<String> columnNames = indexColumnNames(appTable, configs, objectPath.split("\\."));
        if (columnNames.size() == 1) {
            return columnNames.get(0);
        }
        return objectPath.replace(".", "_").toLowerCase(Locale.ROOT);
    }

    private Set<String> explicitlyExpandedObjectFieldNames(AppTable appTable) {
        Set<String> fields = new LinkedHashSet<>();
        for (SppFieldReference primaryKey : appTable.getPrimaryKeys()) {
            SppField field = primaryKey.getSppField();
            if (field != null && !isPersistedScalarDbField(field)) {
                fields.add(field.getName());
                continue;
            }
            String objectPath = appDdlObjectPath(primaryKey);
            if (objectPath.contains(".")) {
                fields.add(objectPath.substring(0, objectPath.indexOf('.')));
            }
        }
        for (AppForeign appForeign : appTable.getForeignKeys()) {
            for (SppFieldReference keyReference : appForeign.getKeys()) {
                SppField field = keyReference.getSppField();
                if (field != null && !isPersistedScalarDbField(field)) {
                    fields.add(field.getName());
                }
            }
        }
        return fields;
    }

    private Map<String, AppTable> foreignTableByType(AppTable appTable) {
        Map<String, AppTable> foreignTableByType = new LinkedHashMap<>();
        for (AppForeign appForeign : appTable.getForeignKeys()) {
            for (SppFieldReference keyReference : appForeign.getKeys()) {
                SppField field = keyReference.getSppField();
                if (field == null || field.getType() == null || isPersistedScalarDbField(field)) {
                    continue;
                }
                AppTable targetTable = findAppTableByBusinessType(field.getType().getName());
                if (targetTable != null) {
                    foreignTableByType.putIfAbsent(field.getType().getName(), targetTable);
                }
            }
        }
        return foreignTableByType;
    }

    private boolean hasArrayKeyFields(AppTable appTable) {
        for (SppFieldReference primaryKey : appTable.getPrimaryKeys()) {
            if (primaryKey.getSppField() != null && primaryKey.getSppField().getArrayDimension() != 0) {
                return true;
            }
        }
        for (AppForeign appForeign : appTable.getForeignKeys()) {
            for (SppFieldReference keyReference : appForeign.getKeys()) {
                if (keyReference.getSppField() != null && keyReference.getSppField().getArrayDimension() != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasPrimaryKey(AppTable appTable) {
        if (!appTable.getPrimaryKeys().isEmpty()) {
            return true;
        }
        for (AppColumn appColumn : appTable.getAppColumns()) {
            if (appColumn.isPrimaryKey()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasResolvableObjectKeyFields(AppTable appTable, Set<AppTable> visitingTables) {
        for (SppFieldReference primaryKey : appTable.getPrimaryKeys()) {
            SppField field = primaryKey.getSppField();
            if (field != null && !isPersistedScalarDbField(field)
                    && !hasTargetTableWithExpandablePrimaryKey(field, visitingTables)) {
                return false;
            }
        }
        for (AppForeign appForeign : appTable.getForeignKeys()) {
            for (SppFieldReference keyReference : appForeign.getKeys()) {
                SppField field = keyReference.getSppField();
                if (field != null && !isPersistedScalarDbField(field)
                        && !hasTargetTableWithExpandablePrimaryKey(field, visitingTables)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasSingleTargetForeignKeyDeclarations(AppTable appTable) {
        for (AppForeign appForeign : appTable.getForeignKeys()) {
            Set<String> targetTypes = new LinkedHashSet<>();
            for (SppFieldReference keyReference : appForeign.getKeys()) {
                SppField field = keyReference.getSppField();
                if (field != null && field.getType() != null && !isPersistedScalarDbField(field)) {
                    targetTypes.add(field.getType().getName());
                }
            }
            if (targetTypes.size() > 1) {
                return false;
            }
        }
        return true;
    }

    private boolean hasStableForeignKeyReferences(AppTable appTable) {
        for (AppForeign appForeign : appTable.getForeignKeys()) {
            if (appForeign.getDeclaredForeignReferenceName() != null) {
                if (!(appForeign.getForeignReference() instanceof SppClass)) {
                    return false;
                }
            }
            for (SppFieldReference keyReference : appForeign.getKeys()) {
                SppField field = keyReference.getSppField();
                if (field == null || field.getType() == null || isPersistedScalarDbField(field)) {
                    continue;
                }
                if (appForeign.getForeignReference() != null
                        && !field.getType().getName().equals(appForeign.getForeignReference().getName())) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasNoQuantumKeyFields(AppTable appTable) {
        for (SppFieldReference primaryKey : appTable.getPrimaryKeys()) {
            SppField field = primaryKey.getSppField();
            if (field != null && field.isQuantum()) {
                return false;
            }
        }
        for (AppForeign appForeign : appTable.getForeignKeys()) {
            for (SppFieldReference keyReference : appForeign.getKeys()) {
                SppField field = keyReference.getSppField();
                if (field != null && field.isQuantum()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasSupportedColumnPrecisionScale(AppTable appTable) {
        for (AppColumn appColumn : appTable.getAppColumns()) {
            if (appColumn.getType() == null || appColumn.getPrecision() <= 0 && appColumn.getScale() <= 0) {
                continue;
            }
            String typeName = appColumn.getType().getName();
            if (appColumn.getPrecision() > 0 && !supportsExplicitColumnPrecision(typeName)) {
                return false;
            }
            if (appColumn.getScale() > 0 && !supportsExplicitColumnScale(typeName)) {
                return false;
            }
        }
        return true;
    }

    private boolean supportsExplicitColumnPrecision(String typeName) {
        return "String".equals(typeName) || supportsExplicitColumnScale(typeName);
    }

    private boolean supportsExplicitColumnScale(String typeName) {
        return "double".equals(typeName) || "decimal".equals(typeName);
    }

    private boolean hasTargetTableWithExpandablePrimaryKey(SppField field, Set<AppTable> visitingTables) {
        if (field == null || field.getType() == null) {
            return false;
        }
        AppTable targetTable = findAppTableByBusinessType(field.getType().getName());
        return targetTable != null
                && isGeneratable(targetTable, visitingTables)
                && hasExpandablePrimaryKeyColumns(targetTable, visitingTables);
    }

    private boolean hasExpandablePrimaryKeyColumns(AppTable appTable, Set<AppTable> visitingTables) {
        for (AppColumn appColumn : appTable.getAppColumns()) {
            if (appColumn.isPrimaryKey()) {
                return true;
            }
        }
        for (SppFieldReference primaryKey : appTable.getPrimaryKeys()) {
            SppField field = primaryKey.getSppField();
            if (field == null || field.getArrayDimension() != 0) {
                continue;
            }
            if (isPersistedScalarDbField(field)) {
                return true;
            }
            AppTable targetTable = findAppTableByBusinessType(field.getType().getName());
            if (targetTable != null
                    && isGeneratable(targetTable, visitingTables)
                    && hasExpandablePrimaryKeyColumns(targetTable, visitingTables)) {
                return true;
            }
        }
        return false;
    }

    private AppTable findAppTableByBusinessType(String businessTypeName) {
        return sppDomain.resolveAppTable(businessTypeName);
    }

    private DbTableConfig tableConfig(AppTable appTable) {
        DbTableConfig configs = new DbTableConfig();
        if (appTable == null) {
            return configs;
        }
        for (AppTables appTables : tableOwner(appTable).getRuleBlock().getAppTables()) {
            if (appTables.getAppTableList().contains(appTable)) {
                configs = readConfig(appTables.getAnnotations(), configs);
                break;
            }
        }
        return readConfig(appTable.getAnnotations(), configs.clone());
    }

    private Set<String> getPrimaryKeyColumnNames(AppTable appTable, DbTableConfig configs, Set<AppTable> visiting) {
        Set<String> primaryKeyNames = new LinkedHashSet<>();
        if (appTable == null || !visiting.add(appTable)) {
            return primaryKeyNames;
        }
        for (SppFieldReference targetPrimaryKey : appTable.getPrimaryKeys()) {
            SppField targetPrimaryKeyField = targetPrimaryKey.getSppField();
            if (targetPrimaryKeyField == null || targetPrimaryKeyField.getArrayDimension() != 0) {
                continue;
            }
            if (isPersistedScalarDbField(targetPrimaryKeyField)) {
                primaryKeyNames.add(appDdlPathColumnName(appTable, configs, appDdlObjectPath(targetPrimaryKey)));
                continue;
            }
            AppTable targetTable = findAppTableByBusinessType(targetPrimaryKeyField.getType().getName());
            DbTableConfig targetConfigs = tableConfig(targetTable);
            boolean hasSame = containsSameType(appTable.getEntity(), targetPrimaryKeyField);
            for (String targetPrimaryKeyName : getPrimaryKeyColumnNames(targetTable, targetConfigs, visiting)) {
                primaryKeyNames.add(genColumnName(configs, appDdlObjectPath(targetPrimaryKey), targetPrimaryKeyName,
                        hasSame ? configs.getDupNamingType() : configs.getNamingType()));
            }
        }
        for (AppColumn appColumn : appTable.getAppColumns()) {
            if (appColumn.isPrimaryKey()) {
                primaryKeyNames.add(appColumn.getName());
            }
        }
        visiting.remove(appTable);
        return primaryKeyNames;
    }

    public NamedArray<DbTable> getTableList() {
        return tableList;
    }

    public List<String> validateMapperDeclarations() {
        return new ArrayList<>(mapperValidationErrors);
    }

    private List<String> validateMapperDeclarationsInternal(SppDomain sppDomain) {
        List<String> errors = new ArrayList<>();
        for (String duplicateMapperName : sppDomain.getRuleBlock().getDuplicateMapperNames()) {
            errors.add("APP mapper " + duplicateMapperName
                    + " is declared multiple times.");
        }
        for (AppMapper appMapper : sppDomain.getRuleBlock().getAppMappers()) {
            DbTable dbTable = tableList.get(appMapper.getName());
            if (dbTable == null) {
                errors.add("Mapper table " + appMapper.getName() + " is not defined.");
                continue;
            }
            collectUnsupportedMapperParameters(appMapper, errors);
            collectDuplicateMapperSources(appMapper, errors);
            collectMapperColumnTypeMismatches(appMapper, dbTable, errors);
            collectUnsupportedMapperPrecisionScale(appMapper, errors);
            for (MapperItem mapperItem : appMapper.getMapperItemList()) {
                if (!hasGeneratedColumn(dbTable, mapperItem.getName())) {
                    errors.add("Mapper " + appMapper.getName() + " column " + mapperItem.getName()
                            + " is not defined in generated table " + dbTable.getName() + ".");
                }
            }
            collectOutputColumnConflicts(appMapper, dbTable, errors);
        }
        return errors;
    }

    private void collectUnsupportedMapperParameters(AppMapper appMapper, List<String> errors) {
        if (!appMapper.getMapParameters().isEmpty()) {
            errors.add("Mapper " + appMapper.getName()
                    + " declares unsupported parameters: "
                    + String.join(", ", appMapper.getMapParameters()) + ".");
        }
    }

    private void collectDuplicateMapperSources(AppMapper appMapper, List<String> errors) {
        Set<String> sourceColumns = new LinkedHashSet<>();
        Set<String> duplicateSourceColumns = new LinkedHashSet<>();
        for (MapperItem mapperItem : appMapper.getMapperItemList()) {
            if (!sourceColumns.add(mapperItem.getName())) {
                duplicateSourceColumns.add(mapperItem.getName());
            }
        }
        for (String duplicateSourceColumn : duplicateSourceColumns) {
            errors.add("Mapper " + appMapper.getName()
                    + " declares duplicate source column " + duplicateSourceColumn + ".");
        }
    }

    private void collectUnsupportedMapperPrecisionScale(AppMapper appMapper, List<String> errors) {
        for (MapperItem mapperItem : appMapper.getMapperItemList()) {
            SQlType sqlType = mapperItem.getSqlType();
            if (sqlType == null || sqlType.getType() == null
                    || (sqlType.getPrecision() == null && sqlType.getScale() == null)) {
                continue;
            }
            String typeName = sqlType.getType();
            if (sqlType.getPrecision() != null && !supportsExplicitColumnPrecision(typeName)) {
                errors.add("Mapper " + appMapper.getName() + " column " + mapperItem.getName()
                        + " with type " + typeName + " cannot declare precision.");
            }
            if (sqlType.getScale() != null && !supportsExplicitColumnScale(typeName)) {
                errors.add("Mapper " + appMapper.getName() + " column " + mapperItem.getName()
                        + " with type " + typeName + " cannot declare scale.");
            }
        }
    }

    private void collectMapperColumnTypeMismatches(AppMapper appMapper, DbTable dbTable, List<String> errors) {
        for (MapperItem mapperItem : appMapper.getMapperItemList()) {
            SQlType sqlType = mapperItem.getSqlType();
            if (sqlType == null || sqlType.getType() == null) {
                continue;
            }
            DbColumn dbColumn = findGeneratedColumn(dbTable, mapperItem.getName());
            String tableType = generatedColumnTypeName(dbColumn);
            if (tableType == null || sqlType.getType().equals(tableType)
                    || sppDomain.supportsExactNumericTextColumn(appMapper.getName(), mapperItem.getName(), sqlType.getType())) {
                continue;
            }
            errors.add("Mapper " + appMapper.getName() + " column " + mapperItem.getName()
                    + " declares type " + sqlType.getType() + " but generated table column "
                    + mapperItem.getName() + " has type " + tableType + ".");
        }
    }

    private boolean hasGeneratedColumn(DbTable dbTable, String columnName) {
        return findGeneratedColumn(dbTable, columnName) != null;
    }

    private DbColumn findGeneratedColumn(DbTable dbTable, String columnName) {
        if (dbTable.getColumns().get(columnName) != null) {
            return dbTable.getColumns().get(columnName);
        }
        if (dbTable.getPrimaryKeys() != null && dbTable.getPrimaryKeys().get(columnName) != null) {
            return dbTable.getPrimaryKeys().get(columnName);
        }
        if (dbTable.getDbForeigns() != null) {
            for (DbForeign dbForeign : dbTable.getDbForeigns()) {
                if (dbForeign.getForeignKeys() != null && dbForeign.getForeignKeys().get(columnName) != null) {
                    return dbForeign.getForeignKeys().get(columnName);
                }
            }
        }
        return null;
    }

    private String generatedColumnTypeName(DbColumn dbColumn) {
        if (dbColumn == null || dbColumn.getField() == null || dbColumn.getField().getType() == null) {
            return null;
        }
        return dbColumn.getField().getType().getName();
    }

    private void collectOutputColumnConflicts(AppMapper appMapper, DbTable dbTable, List<String> errors) {
        Map<String, MapperItem> mapperItems = new HashMap<>();
        for (MapperItem mapperItem : appMapper.getMapperItemList()) {
            mapperItems.put(mapperItem.getName(), mapperItem);
        }
        Set<String> sourceColumns = new HashSet<>();
        Map<String, String> outputColumns = new HashMap<>();
        addOutputColumns(appMapper, dbTable.getColumns(), mapperItems, sourceColumns, outputColumns, errors);
        addOutputColumns(appMapper, dbTable.getPrimaryKeys(), mapperItems, sourceColumns, outputColumns, errors);
        if (dbTable.getDbForeigns() != null) {
            for (DbForeign dbForeign : dbTable.getDbForeigns()) {
                addOutputColumns(appMapper, dbForeign.getForeignKeys(), mapperItems, sourceColumns, outputColumns,
                        errors);
            }
        }
    }

    private void addOutputColumns(AppMapper appMapper, NamedArray<DbColumn> columns, Map<String, MapperItem> mapperItems,
                                  Set<String> sourceColumns, Map<String, String> outputColumns, List<String> errors) {
        if (columns == null) {
            return;
        }
        for (DbColumn dbColumn : columns.getArrayList()) {
            if (!sourceColumns.add(dbColumn.getName())) {
                continue;
            }
            MapperItem mapperItem = mapperItems.get(dbColumn.getName());
            String outputName = mapperItem == null || mapperItem.getMapName() == null
                    ? dbColumn.getName()
                    : mapperItem.getMapName();
            String previousSource = outputColumns.putIfAbsent(outputName, dbColumn.getName());
            if (previousSource != null && !previousSource.equals(dbColumn.getName())) {
                errors.add("Mapper " + appMapper.getName() + " output column " + outputName
                        + " is produced by multiple generated columns.");
            }
        }
    }

    private void mapping(SppDomain sppDomain) {
        for (AppMapper appMapper : sppDomain.getRuleBlock().getAppMappers()) {
            DbTable dbTable = tableList.get(appMapper.getName());
            for (MapperItem mapperItem : appMapper.getMapperItemList()) {
                String name = mapperItem.getName();
                mapColumn(mapperItem, dbTable.getColumns(), dbTable, true);
                mapColumn(mapperItem, dbTable.getPrimaryKeys(), dbTable, false);
                for (MybatisField mybatisField : dbTable.getMybatisClass().getFields()) {
                    if (name.equals(mybatisField.getColumn())) {
                        mybatisField.setColumn(mappedColumnName(mapperItem));
                    }
                }
                if(dbTable.getDbForeigns() != null) {
                    for (DbForeign dbForeign : dbTable.getDbForeigns()) {
                        mapColumn(mapperItem, dbForeign.getForeignKeys(), dbTable, false);
                    }
                }
            }
            mappingMybatis(appMapper, dbTable.getMybatisClass().getFields());
        }
    }

    private void mappingMybatis(AppMapper appMapper, List<MybatisField> mybatisFields) {
        if (mybatisFields == null) {
            return;
        }
        for (MybatisField mybatisField : mybatisFields) {
            for (MapperItem mapperItem : appMapper.getMapperItemList()) {
                String name = mapperItem.getName();
                if (mybatisField instanceof IMybatisClass) {
                    mappingMybatis(appMapper, ((IMybatisClass) mybatisField).getFields());
                } else if (mybatisField instanceof MybatisRef) {
                    for (Map.Entry entry : ((MybatisRef) mybatisField).foreignKeys) {
                        if (name.equals(entry.getValue()))
                            entry.setValue(mappedColumnName(mapperItem));
                    }
                } else if (name.equals(mybatisField.getColumn())) {
                    mybatisField.setColumn(mappedColumnName(mapperItem));
                }
            }
        }
    }

    private String mappedColumnName(MapperItem mapperItem) {
        return mapperItem.getMapName() == null ? mapperItem.getName() : mapperItem.getMapName();
    }

    private void mapColumn(MapperItem mapperItem, NamedArray<DbColumn> columns, DbTable dbTable, boolean showError) {
        DbColumn dbColumn = columns == null ? null : columns.get(mapperItem.getName());
        if (dbColumn == null) {
            if (showError)
                logSppError(mapperItem.getCtx(), "Column " + mapperItem.getName() + " not defined in table " + dbTable.getName());
        } else {
            SQlType sqlType = mapperItem.getSqlType();
            dbColumn.setName(mapperItem.getMapName() == null ? dbColumn.getName() : mapperItem.getMapName())
                    .setNullable(sqlType.isNotnull() == null ? dbColumn.isNullable() : !sqlType.isNotnull())
                    .setScale(sqlType.getScale() == null ? dbColumn.getScale() : sqlType.getScale())
                    .setPrecision(sqlType.getPrecision() == null ? dbColumn.getPrecision() : sqlType.getPrecision())
                    .setJdbcType(sqlType.getType() == null ? dbColumn.getJdbcType() : mapToJDBCType(sqlType.getType()));
            columns.reindex(mapperItem.getName(), dbColumn);
        }
    }

    private void createTables() {
        List<TableGenerationPlan> plans = new ArrayList<>();
        for (AppTables appTables : sppDomain.getRuleBlock().getAppTables()) {
            DbTableConfig rootConfigs = readConfig(appTables.getAnnotations(), new DbTableConfig());
            for (AppTable appTable : appTables.getAppTableList()) {
                if (!isGeneratable(appTable)) {
                    continue;
                }
                plans.add(new TableGenerationPlan(appTable, rootConfigs.clone()));
            }
        }
        for (TableGenerationPlan plan : plans) {
            AppTable appTable = plan.appTable();
            DbTableConfig configs = readConfig(appTable.getAnnotations(), plan.rootConfig().clone());
            DbTable dbTable = new DbTable(appTable);
            tableList.add(dbTable);
            clsToTable.put(appTable.getEntity().getName(),dbTable);
            addAppColumns(appTable, configs, dbTable);
            addPrimaryKeys(appTable, configs, dbTable);
        }
        for (TableGenerationPlan plan : plans) {
            AppTable appTable = plan.appTable();
            DbTableConfig configs = readConfig(appTable.getAnnotations(), plan.rootConfig().clone());
            DbTable dbTable = tableList.get(appTable.getName());
            if (dbTable == null) {
                continue;
            }
            ensureObjectPrimaryKeysExpanded(appTable, dbTable, new HashSet<>());
            for (AppForeign appForeign : appTable.getForeignKeys()) {
                genForeignKeys(configs, appForeign, dbTable);
            }
            addEntityColumns(appTable, configs, dbTable);
            // Identity relationships remain physical columns even when performer facts are omitted.
            if (dbTable.getPrimaryKeys() != null)
                for (DbColumn column : dbTable.getPrimaryKeys().getArrayList())
                    if (dbTable.getColumns().get(column.getName()) == null) dbTable.addComponent(column.clone());
            if (dbTable.getDbForeigns() != null) for (DbForeign foreign : dbTable.getDbForeigns())
                if (foreign.getForeignKeys() != null) for (DbColumn column : foreign.getForeignKeys().getArrayList())
                    if (dbTable.getColumns().get(column.getName()) == null) dbTable.addComponent(column.clone());
            if ((appTable.isIndependentIdentity() || appTable.getPerformerTable() != null)
                    && (appTable.getPerformerTable() == null || appTable.getPerformerTable().isBlank())) {
                for (var performer : sppDomain.roleStoragePerformers(appTable.getEntity())) {
                    String columnName = "spp_performer_" + performer.slot();
                    if (dbTable.getColumns().get(columnName) == null)
                        dbTable.addComponent(new DbColumn<DbTable>().setName(columnName).setJdbcType(JDBCType.CLOB)
                                .setField(new SppField(new SppClass("String"),columnName)).setObjectBacked(false));
                }
            }
            addIndexes(appTable, configs, dbTable);
        }
    }

    private void addIndexes(AppTable appTable, DbTableConfig configs, DbTable dbTable) {
        int indexNumber = 0;
        for (AppIndex appIndex : appTable.getAppIndexes()) {
            List<DbColumn> columns = indexColumns(appTable, configs, dbTable, appIndex.getPaths());
            if (columns.isEmpty()) {
                continue;
            }
            String indexName = appIndex.getName() == null
                    ? "idx_" + appTable.getName() + "_" + indexNumber++
                    : "idx_" + appTable.getName() + "_" + appIndex.getName();
            DbIndex dbIndex = new DbIndex(indexName);
            dbIndex.setUnique(appIndex.isUnique());
            for (DbColumn column : columns) {
                dbIndex.addColumn(column);
            }
            dbTable.addIndex(dbIndex);
        }
    }

    private List<DbColumn> indexColumns(AppTable appTable, DbTableConfig configs, DbTable dbTable,
                                        List<String> indexPaths) {
        if (indexPaths == null || indexPaths.isEmpty() || appTable.getEntity() == null) {
            return List.of();
        }
        List<DbColumn> columns = new ArrayList<>();
        String prefix = appTable.getEntity().getName() + ".";
        for (String indexPath : indexPaths) {
            DbColumn platformColumn = platformIndexColumn(appTable, dbTable, indexPath);
            if (platformColumn != null) {
                columns.add(platformColumn);
                continue;
            }
            if (indexPath == null || !indexPath.startsWith(prefix)) {
                return List.of();
            }
            String[] segments = indexPath.substring(prefix.length()).split("\\.");
            if (segments.length == 0) {
                return List.of();
            }
            for (String columnName : indexColumnNames(appTable, configs, segments)) {
                DbColumn column = findGeneratedColumn(dbTable, columnName);
                if (column == null) {
                    return List.of();
                }
                columns.add(column);
            }
        }
        return columns;
    }

    private DbColumn platformIndexColumn(AppTable appTable, DbTable dbTable, String indexPath) {
        if (appTable == null || dbTable == null || indexPath == null || indexPath.contains(".")) {
            return null;
        }
        for (AppColumn column : appTable.getAppColumns()) {
            if (indexPath.equals(column.getName()) && !column.isObjectBacked()) {
                return findGeneratedColumn(dbTable, indexPath);
            }
        }
        return null;
    }

    private List<String> indexColumnNames(AppTable appTable, DbTableConfig configs, String[] segments) {
        SppClass currentClass = appTable.getEntity();
        SppLocalVar field = null;
        String columnName = null;
        for (int i = 0; i < segments.length; i++) {
            field = currentClass == null ? null : currentClass.getField(segments[i]);
            if (field == null || field.getArrayDimension() != 0 || field.isQuantum()) {
                return List.of();
            }
            if (i == 0) {
                columnName = field.getName();
            } else {
                columnName = genColumnName(configs, columnName, field.getName(),
                        containsSameType(currentClass, field) ? configs.getDupNamingType() : configs.getNamingType());
            }
            if (field.getType() instanceof SppClass sppClass) {
                currentClass = sppClass;
            } else if (i != segments.length - 1) {
                return List.of();
            }
        }
        if (field == null || field.getType() == null) {
            return List.of();
        }
        if (isPersistedScalarDbField(field)) {
            return List.of(columnName);
        }
        AppTable targetTable = findAppTableByBusinessType(field.getType().getName());
        if (targetTable == null) {
            return List.of(columnName);
        }
        DbTableConfig targetConfigs = tableConfig(targetTable);
        List<String> columns = new ArrayList<>();
        for (String targetPrimaryKeyName : getPrimaryKeyColumnNames(targetTable, targetConfigs,
                Collections.newSetFromMap(new IdentityHashMap<>()))) {
            columns.add(genColumnName(configs, columnName, targetPrimaryKeyName,
                    containsSameType(currentClass, field) ? configs.getDupNamingType() : configs.getNamingType()));
        }
        return columns;
    }

    /**
     * If pk is not a primary type, use the primary key of the entity as pk.
     *
     * @param appTable
     * @param configs
     * @param dbTable
     */
    private void genPrimaryKeyWhichIsTable(AppTable appTable, DbTableConfig configs, DbTable dbTable) {
        for (SppFieldReference pk : appTable.getPrimaryKeys()) {
            SppField sppField = pk.getSppField();
            if (sppField != null && !isPersistedScalarDbField(sppField)) {
                DbTable outerTable = getDbTableByRoleName(sppField.getType().getName());
                if (outerTable == null) {
                    continue;
                }
                //should not have same type pk
                boolean hasSame = containsSameType(appTable.getEntity(), sppField);
                for (DbColumn dbColumn : outerTable.getPrimaryKeys().getArrayList()) {
                    dbColumn = dbColumn.clone();
                    dbColumn.setName(genColumnName(configs, appDdlObjectPath(pk), dbColumn.getName(),
                            hasSame ? configs.getDupNamingType() : configs.getNamingType()));
                    dbColumn.setParentField(sppField);
                    dbColumn.setObjectBacked(true); // The association supplies its target's private identity.
                    dbTable.addPrimaryKey(dbColumn);
                }
            }
        }
    }

    private void ensureObjectPrimaryKeysExpanded(AppTable appTable, DbTable dbTable,
                                                 Set<String> visitingTables) {
        if (appTable == null || dbTable == null || expandedPrimaryKeyTables.contains(appTable.getName())) {
            return;
        }
        if (!visitingTables.add(appTable.getName())) {
            return;
        }
        for (SppFieldReference pk : appTable.getPrimaryKeys()) {
            SppField sppField = pk.getSppField();
            if (sppField == null || isPersistedScalarDbField(sppField)) {
                continue;
            }
            DbTable outerTable = getDbTableByRoleName(sppField.getType().getName());
            if (outerTable != null) {
                ensureObjectPrimaryKeysExpanded(outerTable.getAppTable(), outerTable, visitingTables);
            }
        }
        DbTableConfig configs = readEffectiveConfig(appTable);
        genPrimaryKeyWhichIsTable(appTable, configs, dbTable);
        orderDeclaredPrimaryKeys(appTable,configs,dbTable);
        expandedPrimaryKeyTables.add(appTable.getName());
        visitingTables.remove(appTable.getName());
    }

    /** Native composite IDs and repository key paths must follow the same APP declaration order. */
    private void orderDeclaredPrimaryKeys(AppTable appTable,DbTableConfig configs,DbTable dbTable) {
        var names=new ArrayList<String>();
        for(var key:appTable.getPrimaryKeys()) {
            var field=key.getSppField();
            if(field==null)continue;
            if(isPersistedScalarDbField(field))names.add(appDdlPathColumnName(appTable,configs,appDdlObjectPath(key)));
            else {
                var target=getDbTableByRoleName(field.getType().getName());
                if(target!=null)for(var column:target.getPrimaryKeys().getArrayList())
                    names.add(genColumnName(configs,appDdlObjectPath(key),column.getName(),
                            containsSameType(appTable.getEntity(),field)?configs.getDupNamingType():configs.getNamingType()));
            }
        }
        var keys=dbTable.getPrimaryKeys().getArrayList();var existing=new ArrayList<DbColumn>(keys);var ordered=new ArrayList<DbColumn>();
        for(var column:existing)if(!names.contains(column.getName()))ordered.add(column);
        for(String name:names)for(var column:existing)if(name.equals(column.getName()) && !ordered.contains(column))ordered.add(column);
        keys.clear();keys.addAll(ordered);
    }

    private DbTableConfig readEffectiveConfig(AppTable appTable) {
        for (AppTables appTables : sppDomain.getRuleBlock().getAppTables()) {
            if (appTables.getAppTableList().contains(appTable)) {
                DbTableConfig rootConfigs = readConfig(appTables.getAnnotations(), new DbTableConfig());
                return readConfig(appTable.getAnnotations(), rootConfigs.clone());
            }
        }
        return readConfig(appTable.getAnnotations(), new DbTableConfig());
    }

    private void addPrimaryKeys(AppTable appTable, DbTableConfig configs, DbTable dbTable) {
        for (SppFieldReference pk : appTable.getPrimaryKeys()) {
            SppField sppField = pk.getSppField();
            if (isPersistedScalarDbField(sppField)) {
                DbColumn dbColumn = createDbColumn(configs, sppField);
                String columnName = appDdlPathColumnName(appTable, configs, appDdlObjectPath(pk));
                dbColumn.setName(columnName);
                dbTable.addPrimaryKey(dbColumn);
                MybatisField mpk = new MybatisField(appDdlQualifiedName(pk), appDdlObjectPath(pk), dbColumn.getName());
                mpk.setParent(dbTable.getMybatisClass());
                dbTable.getMybatisClass().addPrimaryKey(mpk);

            }
        }
    }

    private void genForeignKeys(DbTableConfig configs, AppForeign appForeign, DbTable dbTable) {
        DbForeign dbForeign = new DbForeign();
        dbTable.addForeign(dbForeign);
        for (SppFieldReference sppFieldReference : appForeign.getKeys()) {
            createForeignDbColumns(configs, dbTable, dbForeign, sppFieldReference.getSppField());
        }
    }

    private void addAppColumns(AppTable appTable, DbTableConfig configs, DbTable dbTable) {
        SppClass sppClass = appTable.getEntity();
        for (AppColumn appColumn : appTable.getAppColumns()) {
            SppField field = sppClass.getField(appColumn.getName());
            DbColumn dbColumn = new DbColumn();
            dbColumn.setName(appColumn.getName()).setJdbcType(changeType(
                            new DbColumn().setJdbcType(mapToJDBCType(appColumn.getType())), configs))
                    .setNullable(appColumn.isNullable())
                    .setField(field)
                    .setObjectBacked(appColumn.isObjectBacked());
            setPrecisionScale(dbColumn, configs);
            if (appColumn.getPrecision() > 0) {
                dbColumn.setPrecision(appColumn.getPrecision());
            }
            if (appColumn.getScale() > 0) {
                dbColumn.setScale(appColumn.getScale());
            }
            dbTable.remove(dbColumn);
            dbTable.addComponent(dbColumn);
            if (appColumn.isPrimaryKey()) {
                dbTable.addPrimaryKey(dbColumn);
            }
            if (!appColumn.isObjectBacked()) {
                continue;
            }
            MybatisField pk = new MybatisField(field.getQualifiedInstName(), field.getName(), dbColumn.getName());
            if (appColumn.isPrimaryKey()) {
                pk.setParent(dbTable.getMybatisClass());
                dbTable.getMybatisClass().addPrimaryKey(pk);
            } else {
                dbTable.getMybatisClass().addField(pk);
            }
        }
    }

    /**
     * Test if there are several field with same type, {Address ship; Address bill} e.g.
     *
     * @param sppClass
     * @param localVar
     * @return
     */
    private boolean containsSameType(SppClass sppClass, SppLocalVar localVar) {
        int count = 0;
        for (SppLocalVar sppLocalVar : sppClass.getSppFieldList()) {
            if (sppLocalVar.getType().getName().equals(localVar.getType().getName()) &&
                    sppLocalVar.getArrayDimension() == localVar.getArrayDimension()) {
                count++;
                if (count > 1)
                    return true;
            }
        }

        return false;
    }

    private void addEntityColumns(AppTable appTable, DbTableConfig configs, DbTable dbTable) {
        SppClass sppClass = appTable.getEntity();
        Set<String> nonObjectBackedColumns = nonObjectBackedAppColumnNames(appTable);
        ArrayList<SppLocalVar> refs = new ArrayList<>();
        for (SppLocalVar sppLocalVar : sppClass.getSppFieldList()) {
            if (((SppField)sppLocalVar).isSnapshotStorageFact() && !isPersistedScalarDbField(sppLocalVar)) {
                dbTable.addComponent(new DbColumn().setName(sppLocalVar.getName())
                        .setJdbcType(java.sql.JDBCType.CLOB).setField(sppLocalVar));
                continue;
            }
            if (!((com.servpp.lang.models.SppField)sppLocalVar).isStoredHere() || ((SppField)sppLocalVar).isPerformerRelation() || sppLocalVar.getArrayDimension() != 0)
                continue;
            if (nonObjectBackedColumns.contains(sppLocalVar.getName())) {
                continue;
            }
            if (isPersistedScalarDbField(sppLocalVar)) {
                DbColumn dbColumn = createDbColumn(configs, sppLocalVar);
                dbTable.addComponent(dbColumn);
                boolean inPrimaryKeys = false;
                inPrimaryKeys = isInPrimaryKeys(dbTable, sppLocalVar, inPrimaryKeys);
                if (!inPrimaryKeys)
                    dbTable.getMybatisClass().addField(new MybatisField(((SppField) sppLocalVar).getQualifiedInstName(), sppLocalVar.getName(),dbColumn.getName()));
            } else {//Entity type field
                DbTable ref = getColumnReferredForeignTable(dbTable, sppLocalVar);
                boolean hasSame = containsSameType(appTable.getEntity(), sppLocalVar);
                if (ref == null) {//foreign key not exists, expand the entity
                    SppClass refClass = (SppClass) sppLocalVar.getType();
                    MybatisAssociation mybatisAssociation = getMybatisAssociation(dbTable.getMybatisClass(), (SppField) sppLocalVar, refClass);
                    expandRefEntity(configs, dbTable, mybatisAssociation, sppLocalVar.getName(), refClass, hasSame,
                            Collections.newSetFromMap(new IdentityHashMap<>()));
                } else {
                    genRefEntityWithItsPrimaryKeys(configs, dbTable, sppLocalVar, ref, hasSame);
                }
            }
        }
        //move ref to the end of children, for recursion of mustache
        List<MybatisField> fields = dbTable.getMybatisClass().getFields();
        if (fields == null) {
            return;
        }
        for (Object obj : fields.toArray()) {
            MybatisField field = (MybatisField) obj;
            if (field instanceof MybatisRef
                    || field instanceof MybatisAssociation) {
                fields.remove(field);
                fields.add(field);
            }
        }
    }

    private Set<String> nonObjectBackedAppColumnNames(AppTable appTable) {
        Set<String> names = new HashSet<>();
        for (AppColumn appColumn : appTable.getAppColumns()) {
            if (!appColumn.isObjectBacked()) {
                names.add(appColumn.getName());
            }
        }
        return names;
    }

    private static boolean isInPrimaryKeys(DbTable dbTable, SppLocalVar sppLocalVar, boolean inPrimaryKeys) {
        for (DbColumn pk : dbTable.getPrimaryKeys().getArrayList()) {
            if (pk.getField() != null && pk.getField().getName().equals(sppLocalVar.getName())) {
                inPrimaryKeys = true;
                break;
            }
        }
        return inPrimaryKeys;
    }

    private static DbTable getColumnReferredForeignTable(DbTable dbTable, SppLocalVar sppLocalVar) {
        DbTable ref = null;
        if (dbTable.getDbForeigns() != null) {
            for (DbForeign dbForeign : dbTable.getDbForeigns()) {
                if (dbForeign.getReference().getAppTable().containsEntity(sppLocalVar.getType().getName())) {
                    ref = dbForeign.getReference();
                    break;
                }
            }
        }
        return ref;
    }

    private void genRefEntityWithItsPrimaryKeys(DbTableConfig configs, DbTable dbTable, SppLocalVar sppLocalVar, DbTable ref, boolean hasSame) {
        String qualifiedInstName = ((SppField) sppLocalVar).getQualifiedInstName();
        MybatisRef mybatisRef = new MybatisRef(qualifiedInstName, sppLocalVar.getName(), null);
        mybatisRef.setRefName(sppLocalVar.getType().getName());
        dbTable.getMybatisClass().addField(mybatisRef);
//        if(dbTable.getAppTable().getName().equals("PRODUCT"))
//            dbTable = dbTable;
        for (DbColumn dbColumn : ref.getPrimaryKeys().getArrayList()) {
            DbColumn col = dbColumn.clone();
            col.setNullable(configs.isNullable());
            col.setName(genColumnName(configs, sppLocalVar.getName(), col.getName(),
                    hasSame ? configs.getDupNamingType() : configs.getNamingType()));
            col.setObjectBacked(true);
            dbTable.addComponent(col);
            if(col.getField()!=null)mybatisRef.addKeyPair(qualifiedInstName+"." +col.getField().getName(), col.getName());
        }
    }

    private void expandRefEntity(DbTableConfig configs, DbTable dbTable, IMybatisClass mybatisClass, String superName,
                                 SppClass refClass, boolean hasSame, Set<SppClass> visiting) {
        if (refClass == null || !visiting.add(refClass)) {
            return;
        }
        for (SppLocalVar field : refClass.getSppFieldList()) {
            String colName = genColumnName(configs, superName, field.getName(),
                    hasSame ? configs.getDupNamingType() : configs.getNamingType());
            if (isPersistedScalarDbField(field)) {
                DbColumn dbColumn = createDbColumn(configs, field);
                dbColumn.setName(colName);
                dbTable.addComponent(dbColumn);
                mybatisClass.addField(new MybatisField(((SppField) field).getQualifiedInstName(), field.getName(), dbColumn.getName()));
            } else {
                SppClass sppClass = (SppClass) field.getType();
                MybatisAssociation mybatisAssociation = getMybatisAssociation(mybatisClass, (SppField) field, sppClass);
                expandRefEntity(configs, dbTable, mybatisAssociation, colName, sppClass,
                        containsSameType(refClass, field), visiting);
            }
        }
        visiting.remove(refClass);
    }

    private MybatisAssociation getMybatisAssociation(IMybatisClass mybatisClass, SppField sppField, SppClass sppClass) {
        MybatisAssociation mybatisAssociation = new MybatisAssociation(sppField.getQualifiedInstName(), sppField.getName(), null);
        mybatisAssociation.setEntityName(sppClass.getName());
        mybatisClass.addField(mybatisAssociation);
        return mybatisAssociation;
    }

    private DbColumn createDbColumn(DbTableConfig configs, SppLocalVar sppLocalVar) {
        DbColumn dbColumn = new DbColumn()
                .setName(sppLocalVar.getName())
                .setJdbcType(mapToJDBCType(sppLocalVar.getType()))
                .setField(sppLocalVar);
        dbColumn.setJdbcType(changeType(dbColumn, configs)).setNullable(configs.isNullable());
        setPrecisionScale(dbColumn, configs);
        String physical = sppLocalVar.getPhysicalNumericSpecification();
        if (physical != null) {
            String javaType = sppLocalVar.getJavaType();
            if ("java.math.BigDecimal".equals(javaType)) dbColumn.setJdbcType(java.sql.JDBCType.DECIMAL);
            if ("java.math.BigInteger".equals(javaType)) dbColumn.setJdbcType(java.sql.JDBCType.NUMERIC);
            switch (javaType) {
                case "short" -> dbColumn.setJdbcType(java.sql.JDBCType.SMALLINT);
                case "int" -> dbColumn.setJdbcType(java.sql.JDBCType.INTEGER);
                case "long" -> dbColumn.setJdbcType(java.sql.JDBCType.BIGINT);
                case "float" -> dbColumn.setJdbcType(java.sql.JDBCType.REAL);
                case "double" -> dbColumn.setJdbcType(java.sql.JDBCType.DOUBLE);
            }
            int[] dimensions = com.servpp.lang.models.app.NumericBinding.dimensions(physical);
            if (dimensions != null) { dbColumn.setPrecision(dimensions[0]); dbColumn.setScale(dimensions[1]); }
            else if ("java.math.BigInteger".equals(javaType)) { dbColumn.setPrecision(1000); dbColumn.setScale(0); }
        }
        return dbColumn;
    }

    private boolean isScalarDbField(SppLocalVar sppLocalVar) {
        IConstance.CompilationUnitType type = sppLocalVar.getType().getType();
        return type == null || type == IConstance.CompilationUnitType.Enum;
    }

    private boolean isPersistedScalarDbField(SppLocalVar sppLocalVar) {
        return sppLocalVar != null && isScalarDbField(sppLocalVar) && !sppLocalVar.isQuantum();
    }

    private DbTableConfig readConfig(List<AnnotationDefine> annotationDefines, DbTableConfig config) {
        if (annotationDefines == null)
            return config;
        for (AnnotationDefine annotationDefine : annotationDefines) {
            String name = annotationDefine.getName();
            if (COLUMN.equals(name)) {
                ObjectMapper objectMapper = new ObjectMapper();
                DbTableConfig config1 = objectMapper.convertValue(annotationDefine.getParameters(), DbTableConfig.class);
                config.copy(config1);
            } else if (INSIDE_ENTITY.equals(name)) {
                NamingType namingType = NamingType.valueOf(annotationDefine.getParameters().get(NAMING_TYPE));
                config.setNamingType(namingType);
                String dup = annotationDefine.getParameters().get(DUP_NAMING_TYPE);
                if (dup != null) {
                    NamingType dupNamingType = NamingType.valueOf(dup);
                    config.setDupNamingType(dupNamingType);
                }
            }
        }
        return config;
    }

    private void setPrecisionScale(DbColumn dbColumn, DbTableConfig config) {
        switch (dbColumn.getJdbcType()) {
            case VARCHAR:
                dbColumn.setPrecision(config.getVarchar_precision());
                break;
            case BIGINT:
                dbColumn.setPrecision(config.getBigint_precision());
                break;
            case DOUBLE:
            case NUMERIC:
            case DECIMAL:
                dbColumn.setPrecision(config.getDecimal_precision());
                dbColumn.setScale(config.getDecimal_scale());
                break;
        }
    }

    private JDBCType changeType(DbColumn dbColumn, DbTableConfig config) {
        //because spptype is converted by default, so use jdbctype as spptype
        JDBCType jdbcType = dbColumn.getJdbcType();
        switch (jdbcType) {
            case BOOLEAN:
                jdbcType = config.getType_boolean();
                break;
            case VARCHAR:
                jdbcType = config.getType_String();
                break;
//            default://TODO
//                throw new RuntimeException("type " + jdbcType + " not supported.");
        }
        return jdbcType;
    }
//    private int precision(SppLocalVar sppLocalVar)

    private JDBCType mapToJDBCType(SppCompilationUnit sppClass) {
        return mapToJDBCType(sppClass.getName());
    }

    private JDBCType mapToJDBCType(String sppType) {
        int index = Arrays.stream(IConstance.primaryTypes).toList().indexOf(sppType);
        if (index < 0) {
            SppCompilationUnit unit = sppDomain.getSppClass(sppType);
            if (unit != null && unit.getType() == IConstance.CompilationUnitType.Enum) {
                return JDBCType.VARCHAR;
            }
            throw new IllegalArgumentException("Unsupported S++ type for DDL column: " + sppType);
        }
        return IConstance.jdbcTypes[index];
    }

    private String genColumnName(DbTableConfig configs, String superName, String colName, NamingType namingType) {
        String columnName = NameUtil.firstToLowerCase(superName, false) +
                NameUtil.firstToLowerCase(colName, false);//default PascalCase
        switch (namingType) {
            case snake_case:
                columnName = superName + "_" + colName;
                columnName = columnName.toLowerCase(Locale.ROOT);
                break;
            case lowercase:
                columnName = colName.toLowerCase(Locale.ROOT);
                break;
            case UPPERCASE:
                columnName = colName.toUpperCase(Locale.ROOT);
                break;
            case camelCase:
                columnName = NameUtil.firstToLowerCase(columnName, true);
                break;
            case PascalCase:
                break;
            case pascallowercase:
                columnName = columnName.toLowerCase(Locale.ROOT);
                break;
            case PASCALUPPERCASE:
                columnName = columnName.toUpperCase(Locale.ROOT);
                break;
        }
        return columnName;
    }

    private void createForeignDbColumns(DbTableConfig configs, DbTable dbTable, DbForeign dbForeign, SppLocalVar sppLocalVar) {
        String foreignRoleName = sppLocalVar.getType().getName();
        DbTable foreignTable = getDbTableByRoleName(foreignRoleName);
        boolean hasSame = containsSameType(dbTable.getAppTable().getEntity(), sppLocalVar);
        for (DbColumn dbColumn : foreignTable.getPrimaryKeys().getArrayList()) {
            DbRefColumn dbRefColumn = new DbRefColumn(dbColumn);
            // The referencing association owns nullability, not the target primary key.
            dbRefColumn.setNullable(configs.isNullable());
            dbRefColumn.setRefName(dbColumn.getName());
            dbRefColumn.setOrigName(sppLocalVar.getName());
            dbRefColumn.setName(genColumnName(configs, sppLocalVar.getName(), dbRefColumn.getName(),
                    hasSame ? configs.getDupNamingType() : configs.getNamingType()));
            dbForeign.setReference(foreignTable);
            dbTable.addForeignKey(dbForeign, dbRefColumn);
        }
    }

    private DbTable getDbTableByRoleName(String foreignRoleName) {
        DbTable ret = null;
        for (DbTable theTable : tableList.getArrayList()) {
            if (theTable.getAppTable().containsEntity(foreignRoleName)) {
                ret = theTable;
                break;
            }
        }
        if (ret == null) {
            AppTable target = findAppTableByBusinessType(foreignRoleName);
            if (target != null && tableOwner(target) != sppDomain) {
                ret = externalKeyTables.get(target);
                if (ret == null) {
                    ret = new DbTable(target); externalKeyTables.put(target,ret);
                    DbTableConfig configs=tableConfig(target);
                    addAppColumns(target,configs,ret); addPrimaryKeys(target,configs,ret);
                    ensureObjectPrimaryKeysExpanded(target,ret,new HashSet<>());
                }
            }
        }
        return ret;
    }

    @Override
    public String toString() {
        return "DDLGenerator{" +
                "tableList=" + tableList +
                '}';
    }

    @Override
    public File getAntlrFile() {
        return rdbFile;
    }
}
