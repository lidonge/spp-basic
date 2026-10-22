package com.servpp.generator.db;

import java.util.List;

/**
 * @author lidong@date 2023-12-15@version 1.0
 */
public interface IMybatisClass {
    default void addField(MybatisField field){
        if(getFields() == null)
            createFields();

        getFields().add(field);
        field.setParent(this);

    }

    String getQualifiedName();

    void createFields();

    List<MybatisField> getFields();
}
