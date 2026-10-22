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

    public String getValue() { return item.getValue() == null ? item.getName() : item.getValue(); }

    @Override public String getExpr() {
        String type = (getReturnType().getBasePackage() == null ? "" : getReturnType().getPackageName() + ".")
                + getReturnType().getName();
        // Use the wire value: generated Java member names need not match SPP member names.
        String value = getValue().replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        return type + ".fromValue(\"" + value + "\")";
    }

    @Override public void check(SppDomain domain) { }
}
