package com.servpp.lang.models.app;

import com.servpp.lang.models.Annotation;
import com.servpp.lang.general.IConstance;
import com.servpp.lang.models.SppClass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author lidong@date 2023-12-05@version 1.0
 */
public class AppTable extends Annotation implements INamedObject{
    private String name;
    private SppClass entity;
    private AppTables.Category category = AppTables.Category.other;
    private List<AppColumn> appColumns = new ArrayList<>();
    private  List<SppFieldReference> primaryKeys = new ArrayList<>();
    private  List<AppForeign> foreignKeys = new ArrayList<>();
    private List<String> indexes = new ArrayList<>();
    private List<AppIndex> appIndexes = new ArrayList<>();
    private List<String> extraBusinessTypeNames = new ArrayList<>();
    private List<String> undefinedBusinessTypeNames = new ArrayList<>();
    private Map<String, IConstance.CompilationUnitType> nonBusinessTypeNames = new LinkedHashMap<>();
    private final Map<String, String> autoNumbers = new LinkedHashMap<>();
    public Map<String, String> getAutoNumbers() { return autoNumbers; }

    private final Map<String, String> mappedStorageModes = new LinkedHashMap<>();
    public Map<String, String> getMappedStorageModes() { return mappedStorageModes; }

    private boolean independentIdentity;
    private String performerTable;
    public boolean isIndependentIdentity() { return independentIdentity; }
    public void setIndependentIdentity(boolean value) { independentIdentity = value; }
    public String getPerformerTable() { return performerTable; }
    public void setPerformerTable(String value) { performerTable = value; }

    public AppTable() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SppClass getEntity() {
        return entity;
    }

    public void setEntity(SppClass entity) {
        this.entity = entity;
    }

    public void addExtraBusinessTypeName(String name) {
        extraBusinessTypeNames.add(name);
    }

    public List<String> getExtraBusinessTypeNames() {
        return extraBusinessTypeNames;
    }

    public void addUndefinedBusinessTypeName(String name) {
        undefinedBusinessTypeNames.add(name);
    }

    public List<String> getUndefinedBusinessTypeNames() {
        return undefinedBusinessTypeNames;
    }

    public void addNonBusinessTypeName(String name, IConstance.CompilationUnitType type) {
        nonBusinessTypeNames.put(name, type);
    }

    public Map<String, IConstance.CompilationUnitType> getNonBusinessTypeNames() {
        return nonBusinessTypeNames;
    }

    public AppTables.Category getCategory() {
        return category;
    }

    public void setCategory(AppTables.Category category) {
        this.category = category;
    }

    public List<AppColumn> getAppColumns() {
        return appColumns;
    }

    public boolean containsEntity(String name){
        return entity.getName().equals(name);
    }

    public List<SppFieldReference> getPrimaryKeys() {
        return primaryKeys;
    }

    public List<AppForeign> getForeignKeys() {
        return foreignKeys;
    }

    public List<String> getIndexes() {
        return indexes;
    }

    public List<AppIndex> getAppIndexes() {
        return appIndexes;
    }
}
