package com.servpp.mustache.model;

/**
 * @author lidong@date 2024-07-27@version 1.0
 */
public class MultiExpr implements IStatement{
    private String multiExpr;
    private int beginLineInMainFile;

    public int getBeginLineInMainFile() {
        return beginLineInMainFile;
    }

    public String getMultiExpr() {
        return multiExpr;
    }

    public MultiExpr setMultiExpr(String multiExpr,int beginLineInMainFile) {
        this.multiExpr = multiExpr;
        this.beginLineInMainFile = beginLineInMainFile;
        return this;
    }
}
