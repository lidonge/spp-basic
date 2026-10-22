package com.servpp.lang.models.app.expr;

import com.servpp.lang.general.NameUtil;
import com.servpp.lang.models.app.IQualifiedNameUtil;
import com.servpp.lang.general.app.SemanticException;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.app.SppFieldReference;

/**
 * @author lidong@date 2023-11-23@version 1.0
 */
public class Reference extends Expression implements IPrimaryExpression, IQualifiedNameUtil {
    String value;

    @Override
    public String getExpr() {
        return NameUtil.getNameCall(value);
    }

    @Override
    public void check(SppDomain domain) throws SemanticException {
        SppFieldReference sppFieldReference = getQualifiedField(domain,null, value);
        if(sppFieldReference != null)
            setReturnType(sppFieldReference.getSppField().getParent());
    }

    public void setValue(String value) {
        this.value = NameUtil.normalizeParticipantReference(value);
    }

    @Override
    public String getValue() {
        return value;
    }
}
