package com.servpp.lang.models.app.expr;

import com.servpp.lang.models.IComponent;
import com.servpp.lang.models.IContainer;
import com.servpp.lang.models.SppCompilationUnit;

/**
 * @author lidong@date 2023-11-23@version 1.0
 */
public abstract class Expression implements IExpression, IComponent {
    SppCompilationUnit returnType;
    IContainer parent;
    @Override
    public SppCompilationUnit getReturnType() {
        return returnType;
    }

    public void setReturnType(SppCompilationUnit returnType) {
        this.returnType = returnType;
    }

    @Override
    public IContainer getParent() {
        return parent;
    }

    public void setParent(IContainer parent) {
        this.parent = parent;
    }
}
