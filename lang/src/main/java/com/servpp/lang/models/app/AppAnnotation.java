package com.servpp.lang.models.app;

import com.servpp.lang.models.IComponent;
import com.servpp.lang.models.IContainer;
import com.servpp.lang.models.SppCompilationUnit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author lidong@date 2023-11-28@version 1.0
 */
public class AppAnnotation implements IContainer {
    private IContainer parent;
    private List<IComponent> children = new ArrayList<>();

    private List<SppCompilationUnit> units = new ArrayList<>();

    private Map<String, AnnotationDefine> annotationDefineHashMap = new HashMap<>();
    private List<AnnotationDefine> annotationDefineList = new ArrayList<>();

    public AnnotationDefine addAnnotationDefine(AnnotationDefine annotationDefine){
        if(annotationDefineHashMap.get(annotationDefine.getName()) != null){
            return null;
        }
        annotationDefineHashMap.put(annotationDefine.getName(),annotationDefine);
        annotationDefineList.add(annotationDefine);
        return annotationDefine;
    }

    public Map<String, AnnotationDefine> getAnnotationDefineHashMap() {
        return annotationDefineHashMap;
    }

    public List<AnnotationDefine> getAnnotationDefineList() {
        return annotationDefineList;
    }

//    public String getQualifiedName(){
//        String ret = "";
//        if(parent != null && parent instanceof  AppAnnotation){
//            ret = ((AppAnnotation) parent).getQualifiedName() +".";
//        }
//        return ret+ getName();
//    }
    public List<SppCompilationUnit> getUnits() {
        return units;
    }

    public List<IComponent> getChildren() {
        return children;
    }

    @Override
    public void _add(IComponent component) {
        children.add(component);
    }

    @Override
    public boolean isContains(IComponent component) {
        return children.indexOf(component) != -1;
    }

    @Override
    public IContainer getParent() {
        return parent;
    }

    @Override
    public void setParent(IContainer parent) {
        this.parent = parent;
    }
}
