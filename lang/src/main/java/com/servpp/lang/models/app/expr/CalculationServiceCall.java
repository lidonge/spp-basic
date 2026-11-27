package com.servpp.lang.models.app.expr;

import com.servpp.lang.models.*;
import java.util.*;

/** A pure calculation composition, resolved after all service declarations are known. */
public final class CalculationServiceCall implements IJavaStatement {
    public final String sourceLocation;
    public final String name;
    public final SppDomain domain;
    public final boolean implicit;
    public final List<String> arguments = new ArrayList<>();
    public final List<CalculationReference> resolvedArguments = new ArrayList<>();
    public final Map<Integer, SharedPlayerBinding> roleBindings = new LinkedHashMap<>();
    public SppService callee;
    private IContainer parent;
    public CalculationServiceCall(String sourceLocation, String name, SppDomain domain, boolean implicit, List<String> arguments) {
        this.sourceLocation = sourceLocation; this.name=name; this.domain=domain; this.implicit=implicit; this.arguments.addAll(arguments);
    }
    @Override public String getJavaStatement() { throw new IllegalStateException("Calculation call requires participant lowering"); }
    @Override public IContainer getParent() { return parent; }
    @Override public void setParent(IContainer parent) { this.parent=parent; }
}
