package com.servpp.lang.models.app.expr;

import com.servpp.lang.models.IContainer;

/** Generated view of a statement, leaving the original AST intact. */
public final class RenderedJavaStatement implements IJavaStatement {
    private final String source;
    private IContainer parent;

    public RenderedJavaStatement(String source, IContainer parent) {
        this.source = source;
        this.parent = parent;
    }

    public String getJavaStatement() { return source; }
    public IContainer getParent() { return parent; }
    public void setParent(IContainer value) { parent = value; }
}
