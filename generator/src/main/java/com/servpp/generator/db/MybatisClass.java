package com.servpp.generator.db;

import com.servpp.lang.general.NameUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lidong@date 2023-12-15@version 1.0
 */
public class MybatisClass implements IMybatisClass{
    String table;
    String type;
    List<MybatisField> fields;

    List<MybatisField> primaryKeys;

    public MybatisClass(String table, String type) {
        this.table = table;
        this.type = type;
    }

    public void addPrimaryKey(MybatisField pk){
        if(primaryKeys == null)
            primaryKeys = new ArrayList<>();
        primaryKeys.add(pk);
    }
    @Override
    public String getQualifiedName() {
        return NameUtil.firstToLowerCase(type,true);
    }

    @Override
    public void createFields() {
        fields = new ArrayList<>();
    }

    @Override
    public List<MybatisField> getFields() {
        return fields;
    }
}
