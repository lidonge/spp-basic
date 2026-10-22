package com.servpp.generator.db;

/**
 * @author lidong@date 2023-12-12@version 1.0
 */
public class DbRefColumn extends DbColumn{
    private String refName;
    private String origName;

    public DbRefColumn(DbColumn dbColumn) {
        super(dbColumn.getName(), dbColumn.getJdbcType(), dbColumn.getPrecision(), dbColumn.getScale(), dbColumn.isNullable(),dbColumn.getField(),dbColumn.getParentField());
    }

    public String getRefName() {
        return refName;
    }

    public DbRefColumn setRefName(String refName) {
        this.refName = refName;
        return this;
    }

    public void setOrigName(String name) {
        this.origName = name;
    }
}
