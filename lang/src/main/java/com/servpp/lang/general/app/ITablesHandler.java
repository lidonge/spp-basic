package com.servpp.lang.general.app;

import com.servpp.lang.general.IConstance;
import com.servpp.lang.general.NameUtil;
import com.servpp.lang.models.IAnnotation;
import com.servpp.lang.models.SppClass;
import com.servpp.lang.models.SppCompilationUnit;
import com.servpp.lang.models.SppField;
import com.servpp.lang.models.SppLocalVar;
import com.servpp.lang.antlr.AppParser;
import com.servpp.lang.models.app.*;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author lidong@date 2023-12-05@version 1.0
 */
public interface ITablesHandler extends IApplicationHandler {
    @Override
    default void enterTables(AppParser.TablesContext ctx) {
        String name = ctx.getChild(1).getText();
        AppTables appTables = new AppTables(name);
        if (!appTables.isKnownCategory()) {
            logSppError(ctx, "Unknown APP table category " + name
                    + ". Expected entities, roles or contracts.");
        }
        getCurrentRuleBlock().setCurrentAnnotatable(appTables);
        appTables.addToList(getCurrentRuleBlock().getAppTables(), appTables);
    }

    @Override
    default void exitTables(AppParser.TablesContext ctx) {

    }

    @Override
    default void enterAnnotateTableDefine(AppParser.AnnotateTableDefineContext ctx) {
        AppTable appTable = new AppTable();
        RuleBlock currentRuleBlock = getCurrentRuleBlock();
        push(currentRuleBlock.getCurrentLineAnns());
        push(currentRuleBlock.getCurrentAnnotatable());
        currentRuleBlock.setCurrentAnnotatable(appTable);
        currentRuleBlock.setCurrentLineAnns(null);
    }

    @Override
    default void exitAnnotateTableDefine(AppParser.AnnotateTableDefineContext ctx) {
        RuleBlock currentRuleBlock = getCurrentRuleBlock();
        List<AnnotationDefine> currentLineAnns = currentRuleBlock.getCurrentLineAnns();
        currentRuleBlock.getCurrentAnnotatable().setAnnotations(currentLineAnns);
        currentRuleBlock.setCurrentAnnotatable((IAnnotation) pop());
        currentRuleBlock.setCurrentLineAnns((List<AnnotationDefine>) pop());
    }

    @Override
    default void enterTableDefine(AppParser.TableDefineContext ctx) {
        String name = ctx.getChild(1).getText();
        RuleBlock currentRuleBlock = getCurrentRuleBlock();
        AppTable appTable = (AppTable) currentRuleBlock.getCurrentAnnotatable();
        appTable.setName(name);
        AppTables appTables = getLastElement(currentRuleBlock.getAppTables());
        appTable.setCategory(appTables.getCategory());
        if(currentRuleBlock.getAllTables().get(name) != null){
            currentRuleBlock.addDuplicateTableName(name);
            logSppError(ctx, "Duplicate table " + name);
            appTables.getAppTableList().add(appTable);
        }else {
            appTables.getAppTableList().add(appTable);
            currentRuleBlock.getAllTables().put(name,appTable);
        }
    }

    @Override
    default void exitTableDefine(AppParser.TableDefineContext ctx) {
        AppTable table = getLastAppTable();
        if (table.getCategory() == AppTables.Category.roles && table.getPrimaryKeys().isEmpty()
                && table.getAppColumns().stream().noneMatch(AppColumn::isPrimaryKey)) {
            AppColumn key = new AppColumn(); key.setName("roleId"); key.setObjectBacked(false);
            key.setType((SppClass)getSppDomian().getSppClass(IConstance.CompilationUnitType.entity, "String"));
            key.addPrimaryKeyModifier(); key.addNotNullModifier(); key.setPrecision(64);
            table.getAppColumns().add(key); table.setIndependentIdentity(true);
        }
        if (table.isIndependentIdentity() && ((table.getCategory() != AppTables.Category.roles && table.getCategory() != AppTables.Category.entities)
                || !table.getPrimaryKeys().isEmpty()
                || table.getAppColumns().stream().filter(AppColumn::isPrimaryKey).count() != 1))
            logSppError(ctx, "Independent identity requires one APP-only primary key on an entity or role table.");
    }

    @Override default void enterRoleIdentityDefine(AppParser.RoleIdentityDefineContext ctx) { }
    @Override default void enterPerformerStorageDefine(AppParser.PerformerStorageDefineContext ctx) { }
    @Override default void exitRoleIdentityDefine(AppParser.RoleIdentityDefineContext ctx) {
        AppTable table = getLastAppTable();
        if (table.isIndependentIdentity()) logSppError(ctx, "Duplicate independent identity declaration.");
        table.setIndependentIdentity(true);
    }
    @Override default void exitPerformerStorageDefine(AppParser.PerformerStorageDefineContext ctx) {
        AppTable table = getLastAppTable();
        if (table.getCategory() != AppTables.Category.roles)
            logSppError(ctx, "Performer storage is configured on a role table.");
        if (table.getPerformerTable() != null) logSppError(ctx, "Duplicate performer storage declaration.");
        table.setPerformerTable(ctx.Identifier() == null ? "" : ctx.Identifier().getText());
    }

    @Override default void enterRoleStorageDefine(AppParser.RoleStorageDefineContext ctx) { }
    @Override default void exitRoleStorageDefine(AppParser.RoleStorageDefineContext ctx) {
        AppTable table = getLastAppTable();
        SppClass type = table.getEntity();
        String mode = ctx.getChild(0).getText();
        for (var target : ctx.qualifiedName()) {
            String path = target.getText();
            String prefix = type == null ? "" : type.getName() + ".";
            String property = path.startsWith(prefix) ? path.substring(prefix.length()) : "";
            SppField field = type == null ? null : type.getField(property);
            boolean participantSnapshot = "snapshot".equals(mode) && field != null
                    && (field.getType().getType() == IConstance.CompilationUnitType.entity
                    || field.getType().getType() == IConstance.CompilationUnitType.role
                    || field.getType().getType() == IConstance.CompilationUnitType.contract);
            boolean scalarRoleFact = type != null && type.getType() == IConstance.CompilationUnitType.role
                    && field != null && field.getArrayDimension() == 0 && (field.getType().getType() == null
                    || field.getType().getType() == IConstance.CompilationUnitType.Enum);
            if (type == null || property.contains(".") || (!participantSnapshot && !scalarRoleFact)) {
                logSppError(ctx, "APP current/snapshot requires a scalar mapped Role attribute or snapshot participant association: " + path);
                continue;
            }
            if (table.getMappedStorageModes().putIfAbsent(property, mode) != null) {
                logSppError(ctx, "Duplicate or conflicting APP mapped storage mode: " + path);
                continue;
            }
            field.setSnapshotStorageFact("snapshot".equals(mode));
        }
    }

    @Override default void enterAutoNumberDefine(AppParser.AutoNumberDefineContext ctx) { }
    @Override default void exitAutoNumberDefine(AppParser.AutoNumberDefineContext ctx) {
        AppTable table = getLastAppTable();
        String path = ctx.qualifiedName().getText();
        String literal = ctx.StringLiteral().getText();
        String prefix = literal.substring(1, literal.length() - 1);
        SppClass type = table.getEntity();
        String expected = type == null ? "" : type.getName() + ".";
        String property = path.startsWith(expected) ? path.substring(expected.length()) : "";
        SppField field = type == null ? null : type.getField(property);
        if (field == null || property.contains(".") || field.getArrayDimension() != 0
                || !"String".equals(field.getType().getName()) || field.getBusinessMeaning() == null
                || !prefix.matches("[A-Za-z0-9_-]+")) {
            logSppError(ctx, "APP table auto numbering requires an explicitly meaningful business String field on this participant and an alphanumeric prefix: " + path);
            return;
        }
        if (table.getAutoNumbers().putIfAbsent(property, prefix) != null)
            logSppError(ctx, "Duplicate APP table auto numbering for " + path);
    }

    @Override
    default void enterIdentifyList(AppParser.IdentifyListContext ctx) {
    }

    @Override
    default void exitIdentifyList(AppParser.IdentifyListContext ctx) {
    }

    @Override
    default void enterQualifiedItemList(AppParser.QualifiedItemListContext ctx) {
    }

    @Override
    default void exitQualifiedItemList(AppParser.QualifiedItemListContext ctx) {
    }

    @Override
    default void enterQualifiedItem(AppParser.QualifiedItemContext ctx) {
        String name = ctx.getText();

        ParserRuleContext parent = ctx.getParent().getParent();
        AppTable appTable = getLastAppTable();
        SppClass sppClass = appTable.getEntity();
        String platformColumnPath = declaredPlatformColumnPath(appTable, name);
        if (isInside(ctx, AppParser.IndexDefineContext.class) && platformColumnPath != null) {
            appTable.getIndexes().add(platformColumnPath);
            getLastElement(appTable.getAppIndexes()).getPaths().add(platformColumnPath);
            return;
        }
        SppFieldReference sppFieldReference = getQualifiedField(ctx, null, name);
        if (sppFieldReference == null) {
            logSppError(ctx, "Field " + name + " not found.");
        } else {
            if (parent instanceof AppParser.PrimaryKeyDefineContext) {
                appTable.getPrimaryKeys().add(sppFieldReference);
            } else if (parent instanceof AppParser.ForeignKeyDefineContext) {
                if(!appTable.containsEntity(sppFieldReference.getSppField().getParent().getName()))
                    logSppError(ctx, "Foreign key:" + sppFieldReference.getSppField().getName() +" not defined in table entities.");
                else
                    getLastElement(appTable.getForeignKeys()).getKeys().add(sppFieldReference);
            } else if (parent instanceof AppParser.IndexDefineContext) {
                appTable.getIndexes().add(name);
                getLastElement(appTable.getAppIndexes()).getPaths().add(name);
            }
        }
    }

    private String declaredPlatformColumnPath(AppTable appTable, String name) {
        if (appTable == null || name == null || name.isBlank()) {
            return null;
        }
        String columnName = name;
        if (name.contains(".")) {
            String prefix = name.substring(0, name.indexOf('.'));
            if (appTable.getEntity() == null || !prefix.equals(appTable.getEntity().getName())) {
                return null;
            }
            columnName = name.substring(name.indexOf('.') + 1);
        }
        for (AppColumn column : appTable.getAppColumns()) {
            if (columnName.equals(column.getName()) && !column.isObjectBacked()) {
                return columnName;
            }
        }
        if (generatedPlatformColumnExists(appTable, columnName)) {
            return columnName;
        }
        return null;
    }

    private boolean generatedPlatformColumnExists(AppTable appTable, String columnName) {
        if (appTable == null || appTable.getEntity() == null || columnName == null || columnName.isBlank()) {
            return false;
        }
        return generatedPlatformColumnExists(appTable, appTable.getEntity(), "", columnName, false, new HashSet<>());
    }

    private boolean generatedPlatformColumnExists(AppTable appTable, SppClass currentClass, String pathPrefix,
                                                  String columnName, boolean duplicateTypeField,
                                                  Set<String> visitingTypes) {
        if (currentClass == null || !visitingTypes.add(currentClass.getName())) {
            return false;
        }
        for (SppLocalVar field : currentClass.getSppFieldList()) {
            if (!(field instanceof SppField sppField) || field.getArrayDimension() != 0) {
                continue;
            }
            if (sppField.getType() instanceof SppClass nestedClass) {
                String generatedPrefix = generatedAppColumnName(appTable, pathPrefix, field.getName(),
                        duplicateTypeField);
                if (generatedPlatformColumnExists(appTable, nestedClass, generatedPrefix, columnName,
                        containsSameType(currentClass, field), visitingTypes)) {
                    visitingTypes.remove(currentClass.getName());
                    return true;
                }
                continue;
            }
            if (!pathPrefix.isBlank()
                    && generatedAppColumnName(appTable, pathPrefix, field.getName(), duplicateTypeField)
                    .equals(columnName)) {
                visitingTypes.remove(currentClass.getName());
                return true;
            }
        }
        visitingTypes.remove(currentClass.getName());
        return false;
    }

    private String generatedAppColumnName(AppTable appTable, String superName, String columnName,
                                          boolean duplicateTypeField) {
        if (superName == null || superName.isBlank()) {
            return columnName;
        }
        String namingType = appTableNamingType(appTable, duplicateTypeField);
        String pascalName = NameUtil.firstToLowerCase(superName, false)
                + NameUtil.firstToLowerCase(columnName, false);
        return switch (namingType) {
            case "lowercase" -> columnName.toLowerCase(Locale.ROOT);
            case "UPPERCASE" -> columnName.toUpperCase(Locale.ROOT);
            case "camelCase" -> NameUtil.firstToLowerCase(pascalName, true);
            case "PascalCase" -> pascalName;
            case "pascallowercase" -> pascalName.toLowerCase(Locale.ROOT);
            case "PASCALUPPERCASE" -> pascalName.toUpperCase(Locale.ROOT);
            case "snake_case" -> (superName + "_" + columnName).toLowerCase(Locale.ROOT);
            default -> (superName + "_" + columnName).toLowerCase(Locale.ROOT);
        };
    }

    private String appTableNamingType(AppTable appTable, boolean duplicateTypeField) {
        AppTableNamingConfig namingConfig = new AppTableNamingConfig();
        for (AppTables appTables : getCurrentRuleBlock().getAppTables()) {
            if (appTables.getAppTableList().contains(appTable)) {
                applyInsideEntityNamingConfig(namingConfig, appTables.getAnnotations());
                break;
            }
        }
        applyInsideEntityNamingConfig(namingConfig, appTable.getAnnotations());
        return duplicateTypeField ? namingConfig.dupNamingType() : namingConfig.namingType();
    }

    private void applyInsideEntityNamingConfig(AppTableNamingConfig namingConfig,
                                               List<AnnotationDefine> annotations) {
        if (annotations == null) {
            return;
        }
        for (AnnotationDefine annotation : annotations) {
            if (!"insideEntity".equals(annotation.getName())) {
                continue;
            }
            String namingType = annotation.getParameters().get("namingType");
            if (namingType != null && !namingType.isBlank()) {
                namingConfig.namingType = namingType;
            }
            String dupNamingType = annotation.getParameters().get("dupNamingType");
            if (dupNamingType != null && !dupNamingType.isBlank()) {
                namingConfig.dupNamingType = dupNamingType;
            }
        }
    }

    private boolean containsSameType(SppClass sppClass, SppLocalVar localVar) {
        if (sppClass == null || localVar == null || localVar.getType() == null) {
            return false;
        }
        int count = 0;
        for (SppLocalVar sppLocalVar : sppClass.getSppFieldList()) {
            if (sppLocalVar.getType() != null
                    && sppLocalVar.getType().getName().equals(localVar.getType().getName())
                    && sppLocalVar.getArrayDimension() == localVar.getArrayDimension()) {
                count++;
                if (count > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    class AppTableNamingConfig {
        private String namingType = "snake_case";
        private String dupNamingType;

        private String namingType() {
            return namingType;
        }

        private String dupNamingType() {
            return dupNamingType == null ? namingType : dupNamingType;
        }
    }

    private boolean isInside(ParserRuleContext ctx, Class<? extends ParserRuleContext> type) {
        ParserRuleContext current = ctx;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }


    @Override
    default void exitQualifiedItem(AppParser.QualifiedItemContext ctx) {
    }

    @Override
    default void enterIdentifyItem(AppParser.IdentifyItemContext ctx) {
        String name = ctx.getChild(0).getText();

//        ParserRuleContext parent = ctx.getParent().getParent();
//        if(parent instanceof AppParser.TableDefineContext){
        AppTable appTable = getLastAppTable();
        SppCompilationUnit unit = getSppDomian().getSppClass(name);
        if (unit == null) {
            appTable.addUndefinedBusinessTypeName(name);
            logSppError(ctx, "Entity " + name + " not found.");
            return;
        }
        if (!(unit instanceof SppClass sppClass)) {
            appTable.addNonBusinessTypeName(name, unit.getType());
            logSppError(ctx, "Table " + appTable.getName()
                    + " must map to a business type, but " + name + " is "
                    + unit.getType().name().toLowerCase(Locale.ROOT) + ".");
            return;
        }
        if (appTable.getEntity() != null) {
            appTable.addExtraBusinessTypeName(name);
            logSppError(ctx, "Table " + appTable.getName()
                    + " must map to exactly one business type; already mapped to "
                    + appTable.getEntity().getName() + ", cannot also map " + name + ".");
            return;
        }
        validateTableTargetCategory(ctx, appTable, sppClass);
        appTable.setEntity(sppClass);
//        }
    }

    @Override
    default void exitIdentifyItem(AppParser.IdentifyItemContext ctx) {
    }

    @Override
    default void enterColumnDefine(AppParser.ColumnDefineContext ctx) {
        AppTable appTable = getLastAppTable();
        appTable.getAppColumns().add(new AppColumn());
    }

    @Override
    default void exitColumnDefine(AppParser.ColumnDefineContext ctx) {
    }

    @Override
    default void enterColumnNullalbe(AppParser.ColumnNullalbeContext ctx) {
        AppColumn appColumn = getLastAppColumn();
        appColumn.addNullableModifier();
    }

    @Override
    default void exitColumnNullalbe(AppParser.ColumnNullalbeContext ctx) {
    }

    @Override
    default void enterColumnNotnull(AppParser.ColumnNotnullContext ctx) {
        getLastAppColumn().addNotNullModifier();
    }

    @Override
    default void exitColumnNotnull(AppParser.ColumnNotnullContext ctx) {
    }

    @Override
    default void enterColumnPrimaryKey(AppParser.ColumnPrimaryKeyContext ctx) {
        getLastAppColumn().addPrimaryKeyModifier();
    }

    @Override
    default void exitColumnPrimaryKey(AppParser.ColumnPrimaryKeyContext ctx) {
    }

    @Override
    default void enterColumnTypePrimitiveType(AppParser.ColumnTypePrimitiveTypeContext ctx) {
        String type = ctx.getChild(0).getText();
        SppClass sppClass = (SppClass) getSppDomian().getSppClass(type);
        getLastAppColumn().setType(sppClass);
    }

    @Override
    default void exitColumnTypePrimitiveType(AppParser.ColumnTypePrimitiveTypeContext ctx) {
    }

    @Override
    default void enterColumnTypePrecision(AppParser.ColumnTypePrecisionContext ctx) {
        int precesion = Integer.parseInt(ctx.getChild(0).getText());
        getLastAppColumn().setPrecision(precesion);
    }

    @Override
    default void exitColumnTypePrecision(AppParser.ColumnTypePrecisionContext ctx) {
    }

    @Override
    default void enterColumnTypeScale(AppParser.ColumnTypeScaleContext ctx) {
        int scale = Integer.parseInt(ctx.getChild(0).getText());
        getLastAppColumn().setScale(scale);
    }

    @Override
    default void exitColumnTypeScale(AppParser.ColumnTypeScaleContext ctx) {
    }

    @Override
    default void enterColumnTypePrecisionScale(AppParser.ColumnTypePrecisionScaleContext ctx) {
    }

    @Override
    default void exitColumnTypePrecisionScale(AppParser.ColumnTypePrecisionScaleContext ctx) {
    }

    @Override
    default void enterColumnName(AppParser.ColumnNameContext ctx) {
        AppTable appTable = getLastAppTable();
        AppColumn appColumn = getLastAppColumn();
        String columnName = ctx.getChild(0).getText();
        appColumn.setName(columnName);
        appColumn.setObjectBacked(false);
    }

    @Override
    default void exitColumnName(AppParser.ColumnNameContext ctx) {
    }

    @Override
    default void enterColumnType(AppParser.ColumnTypeContext ctx) {
    }

    @Override
    default void exitColumnType(AppParser.ColumnTypeContext ctx) {
    }

    @Override
    default void enterKeyDefine(AppParser.KeyDefineContext ctx) {
    }

    @Override
    default void exitKeyDefine(AppParser.KeyDefineContext ctx) {
    }

    @Override
    default void enterPrimaryKeyDefine(AppParser.PrimaryKeyDefineContext ctx) {
    }

    @Override
    default void exitPrimaryKeyDefine(AppParser.PrimaryKeyDefineContext ctx) {
    }

    @Override
    default void enterForeignKeyDefine(AppParser.ForeignKeyDefineContext ctx) {
        AppForeign appForeign = new AppForeign();
        getLastAppTable().getForeignKeys().add(appForeign);
    }

    @Override
    default void exitForeignKeyDefine(AppParser.ForeignKeyDefineContext ctx) {
    }

    @Override
    default void enterForeignKeyReferences(AppParser.ForeignKeyReferencesContext ctx) {
        String id = ctx.getChild(1).getText();
        AppTable appTable = getLastAppTable();
        AppForeign appForeign = getLastElement(appTable.getForeignKeys());
        appForeign.setDeclaredForeignReferenceName(id);
        SppCompilationUnit sppClass = getSppDomian().getSppClass(id);
        if (sppClass == null) {
            logSppError(ctx, "Entity " + id + " not found.");
        } else
            appForeign.setForeignReference(sppClass);
    }

    @Override
    default void exitForeignKeyReferences(AppParser.ForeignKeyReferencesContext ctx) {
    }

    @Override
    default void enterIndexDefine(AppParser.IndexDefineContext ctx) {
        AppTable appTable = getLastAppTable();
        String name = ctx.Identifier() == null ? null : ctx.Identifier().getText();
        AppIndex index = new AppIndex(name);
        index.setUnique(ctx.unique != null);
        if (index.isUnique() && name == null) logSppError(ctx, "A unique index requires an explicit name.");
        appTable.getAppIndexes().add(index);
    }

    @Override
    default void exitIndexDefine(AppParser.IndexDefineContext ctx) {
    }

    private AppColumn getLastAppColumn() {
        AppTable appTable = getLastAppTable();
        AppColumn appColumn = getLastElement(appTable.getAppColumns());
        return appColumn;
    }

    private AppTable getLastAppTable() {
        return getLastElement(getLastElement(getCurrentRuleBlock().getAppTables()).getAppTableList());
    }

    private void validateTableTargetCategory(ParserRuleContext ctx, AppTable appTable, SppClass sppClass) {
        if (appTable.getCategory() == AppTables.Category.other) {
            return;
        }
        IConstance.CompilationUnitType expected = switch (appTable.getCategory()) {
            case entities -> IConstance.CompilationUnitType.entity;
            case roles -> IConstance.CompilationUnitType.role;
            case contracts -> IConstance.CompilationUnitType.contract;
            case other -> null;
        };
        if (expected != null && sppClass.getType() != expected) {
            logSppError(ctx, "Table " + appTable.getName() + " in tables "
                    + appTable.getCategory().name() + " must map to a "
                    + expected.name() + ", but " + sppClass.getName()
                    + " is " + sppClass.getType().name() + ".");
        }
    }
}
