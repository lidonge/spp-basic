package com.servpp.lang.models.app.expr;

import com.servpp.lang.models.SppDomain;

/**
 * @author lidong@date 2023-11-23@version 1.0
 */
public class Literal extends Expression implements IPrimaryExpression{
    String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String getExpr() {
        return value;
    }

    @Override
    public void check(SppDomain domain) {
    }
}
