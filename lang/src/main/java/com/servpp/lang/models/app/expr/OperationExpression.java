package com.servpp.lang.models.app.expr;

import com.servpp.lang.general.app.SemanticException;
import com.servpp.lang.models.IComponent;
import com.servpp.lang.models.SppCompilationUnit;
import com.servpp.lang.models.SppDomain;

/**
 * @author lidong@date 2023-11-23@version 1.0
 */
public class OperationExpression extends Expression implements IOperationExpression{

    IExpression left;
    IExpression right;
    IOperation operation;

    public String getExpr() {
        return  operation.getExpr(left, right);
    }

    @Override
    public SppCompilationUnit getReturnType() {
        return returnType;
    }

    @Override
    public void check(SppDomain domain) throws SemanticException {
        operation.check(left,right);
    }

    public void setReturnType(SppCompilationUnit returnType) {
        this.returnType = returnType;
    }

    @Override
    public IExpression getLeft() {
        return left;
    }

    public void setLeft(IExpression left) {
        this.left = left;
        if(left != null){
            left.setParent(this);
        }
    }

    @Override
    public IExpression getRight() {
        return right;
    }

    public void setRight(IExpression right) {
        this.right = right;
        right.setParent(this);
    }

    @Override
    public IOperation getOperation() {
        return operation;
    }

    public void setOperation(IOperation operation) {
        this.operation = operation;
    }

    @Override
    public boolean isContains(IComponent component) {
        return left == component || right == component;
    }
}
