package com.servpp.generator.db;

import com.servpp.generator.util.NamedArray;
import com.servpp.lang.models.IComponent;
import com.servpp.lang.models.IContainer;
import com.servpp.lang.models.app.AppTable;
import com.servpp.lang.models.app.AppTables;
import com.servpp.lang.models.app.INamedObject;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lidong@date 2023-12-07@version 1.0
 */
public class DbTable implements IContainer, INamedObject {
    private AppTable appTable;

    private MybatisClass mybatisClass;

    public DbTable(AppTable appTable) {
        this.appTable = appTable;
        mybatisClass = new MybatisClass(appTable.getName(), appTable.getEntity().getName());
    }

    private NamedArray<DbColumn> columns = new NamedArray<>();

    private NamedArray<DbColumn> primaryKeys;

    private List<DbForeign> dbForeigns;

    private List<DbIndex> indexes;

    public MybatisClass getMybatisClass() {
        return mybatisClass;
    }

    public NamedArray<DbColumn> getColumns() {
        return columns;
    }

    public NamedArray<DbColumn> getObjectBackedColumns() {
        NamedArray<DbColumn> objectBackedColumns = new NamedArray<>();
        for (DbColumn column : columns.getArrayList()) {
            if (column.isObjectBacked()) {
                objectBackedColumns.add(column);
            }
        }
        return objectBackedColumns;
    }

    public NamedArray<DbColumn> getPrimaryKeys() {
        return primaryKeys;
    }

    public List<DbForeign> getDbForeigns() {
        return dbForeigns;
    }

    public List<DbIndex> getIndexes() {
        return indexes;
    }

    public AppTable getAppTable() {
        return appTable;
    }

    public AppTables.Category getCategory() {
        return appTable.getCategory();
    }

    public String getCategoryName() {
        return appTable.getCategory().name();
    }

    @Override
    public IContainer getParent() {
        return null;
    }

    @Override
    public void setParent(IContainer parent) {

    }

    public boolean addPrimaryKey(DbColumn key){
        if(primaryKeys == null)
            primaryKeys = new NamedArray<>();
        key.setNullable(false);

        return addPKey(primaryKeys,key);
    }

    public void addForeign(DbForeign dbForeign){
        if(dbForeigns == null)
            dbForeigns = new ArrayList<>();
        dbForeigns.add(dbForeign);
    }

    public void addIndex(DbIndex index) {
        if (indexes == null) {
            indexes = new ArrayList<>();
        }
        indexes.add(index);
    }
    public boolean addForeignKey(DbForeign dbForeign, DbColumn key){
        if(dbForeign.getForeignKeys() == null)
            dbForeign.setForeignKeys( new NamedArray<>());

        return addFKey(dbForeign.getForeignKeys(), key);
    }
    private boolean addPKey(NamedArray<DbColumn> keys, DbColumn key){
//        if(!columns.containsValue(key))
//            return false;
        return addFKey(keys, key);
    }

    private boolean addFKey(NamedArray<DbColumn> keys, DbColumn key) {
        if(!keys.containsValue(key))
            keys.add(key);
        return true;
    }

    @Override
    public void _add(IComponent component) {
        columns.add((DbColumn) component);
    }

    @Override
    public boolean isContains(IComponent component) {
        return columns.containsValue((DbColumn) component);
    }

    public void remove(DbColumn dbColumn){
        columns.remove(dbColumn);
    }

    @Override
    public String getName() {
        return appTable.getName();
    }

    @Override
    public String toString() {
        return "DbTable{" +
                "columns=" + columns +
                ", primaryKeys=" + primaryKeys +
                ", dbForeigns=" + dbForeigns +
                '}';
    }
}
