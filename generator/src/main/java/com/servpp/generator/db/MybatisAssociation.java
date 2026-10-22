package com.servpp.generator.db;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lidong@date 2023-12-15@version 1.0
 */
public class MybatisAssociation extends MybatisField implements IMybatisClass{
    List<MybatisField> fields;
    String entityName;

    public MybatisAssociation(String qualifiedName,String property, String column) {
        super(qualifiedName,property, column);
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
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
