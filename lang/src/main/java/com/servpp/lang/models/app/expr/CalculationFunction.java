package com.servpp.lang.models.app.expr;

import com.servpp.lang.models.SppDomain;
import java.util.List;

/** A business operation, independent of the Java representation. */
public final class CalculationFunction extends Expression {
    private final String name;
    private final List<IExpression> arguments;

    public CalculationFunction(String name, List<IExpression> arguments) {
        this.name = name;
        this.arguments = List.copyOf(arguments);
    }
    public String getName() { return name; }
    public List<IExpression> getArguments() { return arguments; }
    @Override public String getExpr() { throw new IllegalStateException("Calculation functions require PHY lowering."); }
    @Override public void check(SppDomain domain) { }
}
