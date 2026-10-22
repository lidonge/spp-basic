package com.servpp.lang.models.app.expr;

import com.servpp.lang.models.IContainer;
import java.util.List;

/** A local decision inside an atomic calculation, not a scenario check scope. */
public final class CalculationRule implements IJavaStatement {
    private final IExpression condition;
    private final List<IJavaStatement> whenTrue, whenFalse;
    private IContainer parent;
    public CalculationRule(IExpression condition, List<IJavaStatement> whenTrue, List<IJavaStatement> whenFalse) {
        this.condition = condition; this.whenTrue = List.copyOf(whenTrue); this.whenFalse = List.copyOf(whenFalse);
    }
    public IExpression getCondition() { return condition; }
    public List<IJavaStatement> getWhenTrue() { return whenTrue; }
    public List<IJavaStatement> getWhenFalse() { return whenFalse; }
    @Override public String getJavaStatement() { throw new IllegalStateException("Calculation rules require PHY lowering."); }
    @Override public IContainer getParent() { return parent; }
    @Override public void setParent(IContainer parent) { this.parent = parent; }
}
