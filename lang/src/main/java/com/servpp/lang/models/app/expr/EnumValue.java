package com.servpp.lang.models.app.expr;

import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppEnum;
import com.servpp.lang.models.SppEnumItem;

/** A declared enumeration member, distinct from a participant attribute reference. */
public final class EnumValue extends Expression {
    private final SppEnumItem item;

    public EnumValue(SppEnum enumeration, SppEnumItem item) {
        setReturnType(enumeration);
        this.item = item;
    }

    public String getValue() { return item.getWireValue(); }

    @Override public String getExpr() {
        // Use the wire value: generated Java member names need not match SPP member names.
        return javaType((SppEnum) getReturnType()) + ".fromValue(" + javaLiteral(getValue()) + ")";
    }

    static String javaType(SppEnum enumeration) {
        return (enumeration.getBasePackage() == null ? "" : enumeration.getPackageName() + ".")
                + enumeration.getName();
    }

    public static String javaLiteral(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    @Override public void check(SppDomain domain) { }
}
