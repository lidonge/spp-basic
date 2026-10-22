package com.servpp.mustache.model;

/**
 * @author lidong@date 2024-07-27@version 1.0
 */
public class Variable implements IMustacheBlock{
    private String varName;

    public String getVarName() {
        return varName;
    }

    public void setVarName(String varName) {
        this.varName = varName;
    }
}
