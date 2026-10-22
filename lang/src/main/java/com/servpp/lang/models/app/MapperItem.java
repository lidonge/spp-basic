package com.servpp.lang.models.app;

import org.antlr.v4.runtime.ParserRuleContext;

/**
 * @author lidong@date 2023-11-22@version 1.0
 */
public class MapperItem implements INamedObject{

    private ParserRuleContext ctx;
    String name;


    SQlType sqlType = new SQlType();
    String mapName;

    public MapperItem(String name, ParserRuleContext ctx) {
        this.name = name;
        this.ctx = ctx;
    }

    public ParserRuleContext getCtx() {
        return ctx;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SQlType getSqlType() {
        return sqlType;
    }

    public void setSqlType(SQlType sqlType) {
        this.sqlType = sqlType;
    }

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "MapperItem{" +
                "qualifiedName='" + name + '\'' +
                ", sqlType=" + sqlType +
                ", mapName='" + mapName + '\'' +
                '}';
    }
}
