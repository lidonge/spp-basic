package com.servpp.generator.db;

import com.servpp.lang.models.IComponent;
import com.servpp.lang.models.IContainer;
import com.servpp.lang.models.SppLocalVar;
import com.servpp.lang.models.app.INamedObject;

import java.sql.JDBCType;

/**
 * @author lidong@date 2023-12-07@version 1.0
 */
public class DbColumn<T extends IContainer> implements IComponent, INamedObject {
    private String name;
    private JDBCType jdbcType;
    private int precision;
    private int scale;

    private boolean nullable = true;

    DbTable parent;

    private SppLocalVar parentField;
    private SppLocalVar field;
    private boolean objectBacked = true;

    public DbColumn() {
    }

    public DbColumn clone(){
        return new DbColumn(name,jdbcType,precision,scale,nullable,field,parentField).setObjectBacked(objectBacked);
    }
    protected DbColumn(String name, JDBCType jdbcType, int precision, int scale, boolean nullable, SppLocalVar field, SppLocalVar parentField) {
        this.name = name;
        this.jdbcType = jdbcType;
        this.precision = precision;
        this.scale = scale;
        this.nullable = nullable;
        this.field = field;
        this.parentField = parentField;
    }

    public SppLocalVar getParentField() {
        return parentField;
    }

    public void setParentField(SppLocalVar parentField) {
        this.parentField = parentField;
    }

    public DbTable getTable(){
        return parent;
    }
    public SppLocalVar getField() {
        return field;
    }

    public DbColumn<T> setField(SppLocalVar field) {
        this.field = field;
        return this;
    }

    public boolean isObjectBacked() {
        return objectBacked;
    }

    public DbColumn<T> setObjectBacked(boolean objectBacked) {
        this.objectBacked = objectBacked;
        return this;
    }

    //for mustache
    public String getNullString(){
        return isNullable()? "null": "not null";
    }

    private String getTypeString(){
        String ret = jdbcType.getName();
        switch (jdbcType){
            case DOUBLE:
                ret = "BigDecimal";
                break;
        }
        return ret;
    }
    public String getPrecisionScale(){
        String ret = null;
        switch (jdbcType){
            case DOUBLE:
            case NUMERIC:
            case DECIMAL:
                ret = "(" +precision+","+scale+")";
                break;
            case VARCHAR:
                ret = "(" +precision+")";
                break;
        }
        return ret;
    }
    public boolean isNullable() {
        return nullable;
    }

    public DbColumn setNullable(boolean nullable) {
        this.nullable = nullable;
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    public DbColumn setName(String name) {
        this.name = name;
        return this;
    }

    public JDBCType getJdbcType() {
        return jdbcType;
    }

    public DbColumn setJdbcType(JDBCType jdbcType) {
        this.jdbcType = jdbcType;
        return this;
    }

    public int getPrecision() {
        return precision;
    }

    public DbColumn setPrecision(int precision) {
        this.precision = precision;
        return this;
    }

    public int getScale() {
        return scale;
    }

    public DbColumn setScale(int scale) {
        this.scale = scale;
        return this;
    }

    @Override
    public DbTable getParent() {
        return parent;
    }

    @Override
    public void setParent(IContainer parent) {
        this.parent = (DbTable) parent;
    }
}
