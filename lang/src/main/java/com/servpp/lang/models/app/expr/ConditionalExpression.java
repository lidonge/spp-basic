package com.servpp.lang.models.app.expr;

import com.servpp.lang.models.SppDomain;

public final class ConditionalExpression extends Expression {
    private final IExpression condition, whenTrue, whenFalse;
    public ConditionalExpression(IExpression condition, IExpression whenTrue, IExpression whenFalse) {
        this.condition = condition; this.whenTrue = whenTrue; this.whenFalse = whenFalse;
    }
    public IExpression getCondition() { return condition; }
    public IExpression getWhenTrue() { return whenTrue; }
    public IExpression getWhenFalse() { return whenFalse; }
    @Override public String getExpr() { throw new IllegalStateException("Conditional calculations require PHY lowering."); }
    @Override public void check(SppDomain domain) { }
}
