package com.servpp.service.serial;

import com.servpp.service.runtime.InvocationContext;
import com.servpp.service.technical.FieldValueContext;
import com.servpp.service.technical.FieldValueInterceptor;
import java.util.Map;

/**
 * The built-in technical service behind {@code auto Entity.field context name}: the value comes from the trusted
 * invocation context established by the ingress, never from the business request.
 */
public final class ContextValueService implements FieldValueInterceptor {
    private String name;
    @Override public void configure(Map<String, String> configuration) {
        name = configuration.get("name");
        if (name == null || !InvocationContext.IDENTIFIER_NAMES.contains(name))
            throw new IllegalArgumentException("CONTEXT_IDENTIFIER_UNKNOWN: " + name);
    }
    @Override public Object produce(FieldValueContext context) {
        String value = context.invocation().identifiers().get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("CONTEXT_IDENTIFIER_UNAVAILABLE: " + name);
        return value;
    }
}
