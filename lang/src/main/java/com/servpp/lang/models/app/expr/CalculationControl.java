package com.servpp.lang.models.app.expr;

import com.servpp.lang.models.IContainer;
import com.servpp.lang.models.SppLocalVar;
import java.util.*;

/** Java control structure whose expressions retain business numeric semantics. */
public final class CalculationControl implements IJavaStatement {
    public enum Kind { BLOCK, WHILE, FOR, EACH, SWITCH, CASE, LOCAL, BREAK, CONTINUE, EMPTY }
    public final Kind kind;
    public IExpression condition;
    public SppLocalVar local;
    public final List<IJavaStatement> body = new ArrayList<>();
    public final List<IJavaStatement> init = new ArrayList<>();
    public final List<IJavaStatement> update = new ArrayList<>();
    private IContainer parent;
    public CalculationControl(Kind kind) { this.kind = kind; }
    public List<IJavaStatement> children() {
        List<IJavaStatement> all = new ArrayList<>(init); all.addAll(body); all.addAll(update); return all;
    }
    @Override public String getJavaStatement() { throw new IllegalStateException("Business control requires PHY lowering."); }
    @Override public IContainer getParent() { return parent; }
    @Override public void setParent(IContainer parent) { this.parent = parent; }
}
