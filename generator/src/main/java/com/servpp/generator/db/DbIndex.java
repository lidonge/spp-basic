package com.servpp.generator.db;

import com.servpp.generator.util.NamedArray;

public class DbIndex {
    private String name;
    private boolean unique;
    public boolean isUnique() { return unique; }
    public void setUnique(boolean value) { unique = value; }
    private NamedArray<DbColumn> columns = new NamedArray<>();

    public DbIndex(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public NamedArray<DbColumn> getColumns() {
        return columns;
    }

    public void addColumn(DbColumn column) {
        if (!columns.containsValue(column)) {
            columns.add(column);
        }
    }
}
