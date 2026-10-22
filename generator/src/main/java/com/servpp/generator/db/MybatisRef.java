package com.servpp.generator.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author lidong@date 2023-12-15@version 1.0
 */
public class MybatisRef extends MybatisField{
    String refName;
    List<Map.Entry> foreignKeys = new ArrayList<>();
    public MybatisRef(String qualifiedName,String property, String column) {
        super(qualifiedName,property, column);
    }

    public void setRefName(String refName) {
        this.refName = refName;
    }

    public String getRefName() {
        return refName;
    }

    public List<Map.Entry> getForeignKeys() {
        return foreignKeys;
    }

    public void addKeyPair(String mapperId, String colName){
        Map.Entry record = new Map.Entry() {
            Object value = colName;
            @Override
            public Object getKey() {
                return mapperId;
            }

            @Override
            public Object getValue() {
                return value;
            }

            @Override
            public Object setValue(Object value) {
                this.value = value;
                return value;
            }

        };
        foreignKeys.add(record);
    }
}
