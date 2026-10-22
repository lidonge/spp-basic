package com.servpp.lang.models.app.expr;

import com.servpp.lang.general.app.SemanticException;
import com.servpp.lang.models.IComponent;
import com.servpp.lang.models.SppCompilationUnit;
import com.servpp.lang.models.SppDomain;

/**
 * @author lidong@date 2023-11-23@version 1.0
 */
public interface IExpression extends IComponent {

    SppCompilationUnit getReturnType();
    String getExpr();
    void check(SppDomain domain) throws SemanticException;
}
