package com.servpp.mustache.model;

/**
 * @author lidong@date 2024-07-30@version 1.0
 */
public class Recursive implements IStatement{
    private String recursiveName;

    public String getRecursiveName() {
        return recursiveName;
    }

    public Recursive setRecursiveName(String recursiveName) {
        this.recursiveName = recursiveName;
        return this;
    }
}
