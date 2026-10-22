package com.servpp.lang.models.app.expr;

/**
 * @author lidong@date 2023-11-23@version 1.0
 */
public abstract class GeneralOperation implements IOperation{
    String operator;
    Opers_Type type;
    @Override
    public String getOperator() {
        return operator;
    }

    @Override
    public Opers_Type getType() {
        return type;
    }

    @Override
    public void setType(Opers_Type type) {
        this.type = type;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }
}
