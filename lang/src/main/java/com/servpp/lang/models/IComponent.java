package com.servpp.lang.models;

/**
 * @author lidong@date 2023-11-28@version 1.0
 */
public interface IComponent<T extends IContainer> {
    T getParent();

    void setParent(T parent);
}
