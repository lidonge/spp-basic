package com.servpp.lang.models.app.expr;

import com.servpp.lang.general.app.SemanticException;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppEnum;

import java.util.function.Function;

/** Classifies a String expression as a member of a declared enumeration. */
public final class EnumConstruction extends Expression {
    private final SppEnum enumeration;
    private final IExpression code;

    public EnumConstruction(SppEnum enumeration, IExpression code) {
        this.enumeration = enumeration;
        this.code = code;
        setReturnType(enumeration);
    }

    public IExpression getCode() {
        return code;
    }

    String render(Function<IExpression, String> renderExpression) {
        String factory = enumeration.preservesUnknownWireValues() ? ".fromValue(" : ".fromCode(";
        return EnumValue.javaType(enumeration) + factory + renderExpression.apply(code) + ")";
    }

    @Override
    public String getExpr() {
        return render(IExpression::getExpr);
    }

    @Override
    public void check(SppDomain domain) throws SemanticException {
        code.check(domain);
    }
}
