package com.servpp.generator.db;

import com.servpp.generator.util.NamedArray;

/**
 * @author lidong@date 2023-12-08@version 1.0
 */
public class DbForeign {
    private NamedArray<DbColumn> foreignKeys;
    private DbTable reference;

    public DbTable getReference() {
        return reference;
    }

    public DbForeign setReference(DbTable reference) {
        this.reference = reference;
        return this;
    }

    public NamedArray<DbColumn> getForeignKeys() {
        return foreignKeys;
    }

    public DbForeign setForeignKeys(NamedArray<DbColumn> foreignKeys) {
        this.foreignKeys = foreignKeys;
        return this;
    }
}
