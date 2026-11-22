package com.servpp.lang.models.app.expr;

import com.servpp.lang.models.IContainer;
import com.servpp.lang.models.IComponent;

public class RequireStatement implements IJavaStatement, IContainer {
    private boolean quantumConstraint;
    public boolean isQuantumConstraint() { return quantumConstraint; }
    public RequireStatement quantumConstraint(boolean value) { quantumConstraint = value; return this; }
    private IExpression condition;
    private String message;
    private EnumValue rejectionCode;
    private IContainer parent;

    public RequireStatement(String message) {
        this(null, message);
    }

    public RequireStatement(IExpression condition, String message) {
        this.condition = condition;
        this.message = message;
    }

    public RequireStatement(IExpression condition, EnumValue rejectionCode) {
        this.condition = condition;
        this.rejectionCode = rejectionCode;
    }

    public IExpression getCondition() {
        return condition;
    }

    public void setCondition(IExpression condition) {
        this.condition = condition;
    }

    public String getMessage() {
        return rejectionCode == null ? message : EnumValue.javaLiteral(rejectionCode.getValue());
    }

    public void setMessage(String message) {
        this.rejectionCode = null;
        this.message = message;
    }

    @Override
    public String getJavaStatement() {
        return "if (!(" + condition.getExpr() + ")) { throw new IllegalStateException(" + getMessage() + "); }";
    }

    @Override
    public void _add(IComponent component) {
        setCondition((IExpression) component);
    }

    @Override
    public boolean isContains(IComponent component) {
        return condition == component;
    }

    @Override
    public IContainer getParent() {
        return parent;
    }

    @Override
    public void setParent(IContainer parent) {
        this.parent = parent;
    }
}
