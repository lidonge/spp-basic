package com.servpp.lang.models.app.expr;

import com.servpp.lang.models.SppCompilationUnit;
import com.servpp.lang.models.SppDomain;

/** Construction is limited to declared data models, never arbitrary Java classes. */
public final class ParticipantConstruction extends Expression {
    private final boolean collection;
    private final java.util.List<IExpression> elements;
    public ParticipantConstruction(SppCompilationUnit type) { this(type, false, java.util.List.of()); }
    public ParticipantConstruction(SppCompilationUnit type, boolean collection, java.util.List<IExpression> elements) {
        setReturnType(type); this.collection=collection; this.elements=java.util.List.copyOf(elements);
    }
    public boolean isCollection() { return collection; }
    public java.util.List<IExpression> getElements() { return elements; }
    public String getJavaType() {
        return (getReturnType().getBasePackage() == null ? "" : getReturnType().getPackageName() + ".") + getReturnType().getName();
    }
    @Override public String getExpr() {
        return "new " + getJavaType() + (collection ? "[] {" + elements.stream().map(IExpression::getExpr).collect(java.util.stream.Collectors.joining(", ")) + "}" : "()");
    }
    @Override public void check(SppDomain domain) { }
}
