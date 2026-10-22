package com.servpp.generator.db;

import java.sql.JDBCType;

/**
 * @author lidong@date 2023-12-07@version 1.0
 */
public class DbTableConfig {
    public static final String NAMING_TYPE = "namingType";
    public static final String DUP_NAMING_TYPE = "dupNamingType";
    public static final String INSIDE_ENTITY = "insideEntity";
    public static final String COLUMN = "column";

    public enum NamingType{
        snake_case, camelCase, PascalCase, UPPERCASE, lowercase,pascallowercase,PASCALUPPERCASE
    }
    private int varchar_precision = 50;
    private int decimal_precision = 17;

    private int decimal_scale = 2;
    private int bigint_precision = 20;
    private boolean nullable = true;
    private JDBCType type_boolean = JDBCType.BOOLEAN;
    private JDBCType type_String = JDBCType.VARCHAR;
    public DbTableConfig() {
    }
    private NamingType namingType = NamingType.snake_case;
    private NamingType dupNamingType = null;
    public DbTableConfig clone(){
        return new DbTableConfig(varchar_precision,decimal_precision,decimal_scale,
                bigint_precision,nullable,type_boolean,type_String,namingType,dupNamingType);
    }

    public void copy(DbTableConfig config){
        copy(config.varchar_precision,config.decimal_precision,config.decimal_scale,config.
                bigint_precision,config.nullable,config.type_boolean,config.type_String,config.namingType,
                config.dupNamingType);
    }

    private DbTableConfig(int varchar_precision, int decimal_precision, int decimal_scale, int bigint_precision,
                          boolean nullable, JDBCType type_boolean, JDBCType type_String, NamingType namingType,
                          NamingType dupNamingType) {
        copy(varchar_precision, decimal_precision, decimal_scale, bigint_precision, nullable, type_boolean,
                type_String, namingType, dupNamingType);
    }

    private void copy(int varchar_precision, int decimal_precision, int decimal_scale, int bigint_precision,
                      boolean nullable, JDBCType type_boolean, JDBCType type_String, NamingType namingType,
                      NamingType dupNamingType) {
        this.varchar_precision = varchar_precision;
        this.decimal_precision = decimal_precision;
        this.decimal_scale = decimal_scale;
        this.bigint_precision = bigint_precision;
        this.nullable = nullable;
        this.type_boolean = type_boolean;
        this.type_String = type_String;
        this.namingType = namingType;
        this.dupNamingType = dupNamingType;
    }

    public NamingType getDupNamingType() {
        return dupNamingType == null ? namingType : dupNamingType;
    }

    public void setDupNamingType(NamingType dupNamingType) {
        this.dupNamingType = dupNamingType;
    }

    public NamingType getNamingType() {
        return namingType;
    }

    public DbTableConfig setNamingType(NamingType namingType) {
        this.namingType = namingType;
        return this;
    }

    public int getDecimal_scale() {
        return decimal_scale;
    }

    public DbTableConfig setDecimal_scale(int decimal_scale) {
        this.decimal_scale = decimal_scale;
        return this;
    }

    public int getBigint_precision() {
        return bigint_precision;
    }

    public DbTableConfig setBigint_precision(int bigint_precision) {
        this.bigint_precision = bigint_precision;
        return this;
    }

    public int getVarchar_precision() {
        return varchar_precision;
    }

    public void setVarchar_precision(int varchar_precision) {
        this.varchar_precision = varchar_precision;
    }

    public int getDecimal_precision() {
        return decimal_precision;
    }

    public void setDecimal_precision(int decimal_precision) {
        this.decimal_precision = decimal_precision;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public JDBCType getType_boolean() {
        return type_boolean;
    }

    public void setType_boolean(JDBCType type_boolean) {
        this.type_boolean = type_boolean;
    }

    public JDBCType getType_String() {
        return type_String;
    }

    public void setType_String(JDBCType type_String) {
        this.type_String = type_String;
    }
}
