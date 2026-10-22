package com.servpp.mustache.model;

import java.util.List;

/**
 * @author lidong@date 2024-07-27@version 1.0
 */
public class Partial implements IStatement{
    private String partialName;
    private List<Parameter> parameterList;
    private List<Parameter> returnsList;

    public String getPartialName() {
        return partialName;
    }

    public void setPartialName(String partialName) {
        this.partialName = partialName;
    }

    public List<Parameter> getReturnsList() {
        return returnsList;
    }

    public void setReturnsList(List<Parameter> returnsList) {
        this.returnsList = returnsList;
    }

    public List<Parameter> getParameterList() {
        return parameterList;
    }

    public void setParameterList(List<Parameter> parameterList) {
        this.parameterList = parameterList;
    }
}
