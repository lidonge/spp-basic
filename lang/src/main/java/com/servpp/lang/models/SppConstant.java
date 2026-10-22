package com.servpp.lang.models;

/** A named, immutable business fact; String translations are APP/PHY choices. */
public final class SppConstant extends SppLocalVar {
    private final String domain;
    private final String literal;
    public SppConstant(SppCompilationUnit type, String name, String domain, String literal) {
        super(type, name);
        this.domain = domain;
        this.literal = literal;
    }
    public String getLiteral() { return literal; }
    public String getQualifiedName() { return domain + "." + getName(); }
    public String getJavaValue() {
        String value = literal;
        if ("java.math.BigDecimal".equals(getJavaType())) value = "new java.math.BigDecimal(\"" + literal + "\")";
        if ("java.math.BigInteger".equals(getJavaType())) value = "new java.math.BigInteger(\"" + literal + "\")";
        if ("String".equals(getType().getName()))
            return "com.servpp.service.runtime.InternationalizedConstants.stringValue(\"" + getQualifiedName() + "\", " + value + ")";
        return value;
    }
}
