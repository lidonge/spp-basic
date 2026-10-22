package com.servpp.lang.models;

import com.servpp.lang.general.NameUtil;

/**
 * @author lidong@date 2023-11-03@version 1.0
 */
public class SppRoleField extends SppField{
    String entityName;

    protected SppRoleField(SppCompilationUnit type, String name, int arrayDimension, int index, boolean isQuantum, String entityName) {
        super(type, name, arrayDimension, index, isQuantum);
        this.entityName = entityName;
    }

    public SppRoleField(SppCompilationUnit type, String name, int index, boolean isQuantum, String entityName) {
        super(type, name, index, isQuantum);
        this.entityName = entityName;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return new SppRoleField(type,name,getArrayDimension(),index,isQuantum,entityName);
    }

    public SppRoleField(SppCompilationUnit cls, String name, String entityName) {
        super(cls, name);
        this.entityName = entityName;
    }

    public SppRoleField(SppCompilationUnit cls, String name) {
        super(cls, name);
    }

    public String getEntityName() {
        return entityName;
    }

    public String getUpperEntityName(){
        return NameUtil.firstToLowerCase(entityName,false);
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    @Override
    public String toString() {
        return "SppRoleField{" +
                "entityName='" + entityName + '\'' +
                ", type=" + type.getName() +
                ", name='" + name + '\'' +
                '}';
    }
}
