package com.servpp.lang.models.app.expr;

import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppLocalVar;
import com.servpp.lang.models.app.BlankPolicy;

/** Keep the literal abstract until its operand and APP declarations are resolved. */
public final class BlankLiteral extends Literal {
    private final SppDomain domain;

    public BlankLiteral(SppDomain domain) {
        this.domain = domain;
        setValue("BLANK");
    }

    static IExpression unwrap(IExpression value) {
        while (value instanceof IOperationExpression operation && "(".equals(operation.getOperation().getOperator()))
            value = operation.getRight();
        return value;
    }

    String matches(String expression, SppLocalVar field, String receiver) {
        return BlankPolicy.readJava(domain, field, receiver, expression);
    }

    String orderingValue(String expression, SppLocalVar field, String receiver) {
        return BlankPolicy.orderingJava(domain, field, receiver, expression);
    }

    String write(SppLocalVar field, String receiver) {
        return BlankPolicy.writeJava(domain, field, receiver);
    }
}
