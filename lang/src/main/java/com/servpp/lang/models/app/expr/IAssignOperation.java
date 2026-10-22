package com.servpp.lang.models.app.expr;

/**
 * @author lidong@date 2023-11-23@version 1.0
 */
public interface IAssignOperation extends IOperation{
    static final String ASSIGN = "=";

    String getAssocOperation();
}
