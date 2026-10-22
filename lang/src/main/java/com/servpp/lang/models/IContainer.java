package com.servpp.lang.models;

/**
 * @author lidong@date 2023-11-28@version 1.0
 */
public interface IContainer<T extends IComponent> extends IComponent{
    default T addComponent(T component){
        if(isContains(component))
            return null;
        _add(component);
        component.setParent(this);
        return component;
    }

    void _add(T component);

    boolean isContains(T component);
}
