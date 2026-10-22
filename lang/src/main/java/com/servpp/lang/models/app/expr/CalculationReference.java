package com.servpp.lang.models.app.expr;

import com.servpp.lang.models.SppLocalVar;

/** Resolved lexical symbol; local variables never become service API parameters. */
public final class CalculationReference extends Reference {
    public SppLocalVar field;
    public SppLocalVar root;
    public boolean local;
    public boolean collectionLength;
    @Override public String getExpr() {
        if (collectionLength) {
            String path = getValue().substring(0, getValue().length() - ".length".length());
            return "com.servpp.service.runtime.ParticipantCollections.length(" + com.servpp.lang.general.NameUtil.getNameCall(local ? "$sppLocal_" + path : path) + ")";
        }
        if (field instanceof com.servpp.lang.models.SppConstant constant) return constant.getJavaValue();
        if (!local) { return super.getExpr(); }
        String value = "$sppLocal_" + getValue();
        return com.servpp.lang.general.NameUtil.getNameCall(value);
    }
}
