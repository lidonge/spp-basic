package com.servpp.generator.db;

/**
 * @author lidong@date 2023-12-15@version 1.0
 */
public class MybatisField {
    private String property;
    private String column;
    private String qualifiedName;

    private IMybatisClass parent;

    public MybatisField(String qualifiedName, String property, String column) {
        this.qualifiedName = qualifiedName;
        this.property = property;
        this.column = column;
    }

    public String getQualifiedName(){
        return qualifiedName;
    }
    public String getProperty() {
        return property;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public IMybatisClass getParent() {
        return parent;
    }

    public void setParent(IMybatisClass parent) {
        this.parent = parent;
    }
}
