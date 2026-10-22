package com.servpp.lang.models.app.expr;

import com.servpp.lang.general.app.SemanticException;

/**
 * @author lidong@date 2023-11-23@version 1.0
 */
public class ArithmeticOperation extends GeneralOperation implements IArithmeticOperation{
    @Override
    public void check(IExpression left, IExpression right) throws SemanticException {

    }
}
