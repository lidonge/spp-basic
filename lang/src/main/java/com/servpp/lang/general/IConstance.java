package com.servpp.lang.general;

import java.sql.JDBCType;

/**
 * @author lidong@date 2023-11-02@version 1.0
 */
public interface IConstance {
    public static String SUFFIX = ".java";

    public static String compilationUnitPackage = "com.servpp.client";

    public static enum CompilationUnitType {
        entity, reference, role, contract, atomicservice, scenario, rolemapper,Enum
    }

    static final String[] primaryTypes = new String[]{
            "boolean", "char", "String"
            , "short", "int", "long"
            , "float", "double","decimal","BigDecimal","BigInteger","DateTime"};

    static final JDBCType[] jdbcTypes = new JDBCType[]{
            JDBCType.BOOLEAN, JDBCType.CHAR, JDBCType.VARCHAR
            , JDBCType.SMALLINT, JDBCType.INTEGER, JDBCType.BIGINT
            , JDBCType.FLOAT, JDBCType.DOUBLE,JDBCType.DECIMAL,JDBCType.DECIMAL,JDBCType.NUMERIC,JDBCType.TIMESTAMP};

    public static enum ServiceType {
        query, check, calculate, update, unknown
    }


    public enum ScenarioType {
        Auto, serial, parallel
    }

    public enum TransactionType {
        at, xa, tcc, saga, quantum, db
    }
}
