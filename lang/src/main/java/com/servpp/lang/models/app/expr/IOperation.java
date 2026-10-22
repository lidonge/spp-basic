package com.servpp.lang.models.app.expr;

import com.servpp.lang.general.app.SemanticException;

/**
 * @author lidong@date 2023-11-23@version 1.0
 */
public interface IOperation {
    public enum Opers_Type {
        Left, Right, Double
    }
    String getOperator();

    void setOperator(String operator);

    default String getExpr(IExpression left, IExpression right){
        String ret = "";
        ret += left == null ? "" : left.getExpr();
        ret += getOperator();
        ret += right == null ? "" : right.getExpr();

        return ret;
    }

    Opers_Type getType();
    void setType(Opers_Type type);

    void check(IExpression left, IExpression right) throws SemanticException;
}
