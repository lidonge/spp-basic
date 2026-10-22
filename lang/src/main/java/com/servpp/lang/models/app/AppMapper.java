package com.servpp.lang.models.app;

import com.servpp.lang.models.Annotation;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lidong@date 2023-11-22@version 1.0
 */
public class AppMapper extends Annotation implements INamedObject{
    AppTable appTable;
    private String declaredTableName;

    List<String> mapParameters = new ArrayList<>();
    List<MapperItem> mapperItemList = new ArrayList<>();

    public AppMapper(AppTable appTable) {
        this.appTable = appTable;
        this.declaredTableName = appTable == null ? null : appTable.getName();
    }

    public AppMapper(String declaredTableName) {
        this.declaredTableName = declaredTableName;
    }

    public String getName() {
        return appTable == null ? declaredTableName : appTable.getName();
    }

    public AppTable getAppTable() {
        return appTable;
    }

    public void setAppTable(AppTable appTable) {
        this.appTable = appTable;
        this.declaredTableName = appTable == null ? declaredTableName : appTable.getName();
    }

    public String getDeclaredTableName() {
        return declaredTableName;
    }

    public String addParameter(String parameter){
        if(mapParameters.indexOf(parameter) != -1)
            return "Duplicate name "+ parameter;
        mapParameters.add(parameter);
        return null;
    }

    public List<String> getMapParameters() {
        return mapParameters;
    }

    public List<MapperItem> getMapperItemList() {
        return mapperItemList;
    }

    public String addMapperItem(MapperItem mapperItem){
        for (int i = 0; i < mapperItemList.size(); i++) {
            MapperItem existing = mapperItemList.get(i);
            if (existing.getName().equals(mapperItem.getName())) {
                mapperItemList.add(mapperItem);
                return "Duplicate name "+ mapperItem.getName() +" at position: " + i;
            }
        }
        mapperItemList.add(mapperItem);
        return null;
    }

    @Override
    public String toString() {
        return "AppMapper{" +
                "sppClass=" + getName() +
                ", mapParameters=" + mapParameters +
                ", mapperItemList=" + mapperItemList +
                '}';
    }
}
