package com.servpp.lang.models;

import com.servpp.lang.models.app.expr.IExpression;

/** A caller's post-trade interval; endpoints are captured before invocation. */
public final class QuantumCallBound {
    public final String path;
    public final IExpression minimum, maximum;
    public SppLocalVar participant;
    public String participantPath, property;
    public QuantumCallBound(String path, IExpression minimum, IExpression maximum) {
        this.path = path; this.minimum = minimum; this.maximum = maximum;
    }
}
