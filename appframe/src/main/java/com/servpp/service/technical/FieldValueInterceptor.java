package com.servpp.service.technical;

/**
 * Value contract of a technical service attached with {@code intercept Entity { insert: service -> field; }}.
 * The runtime calls it before the record is written; the returned value is stored into the target field when
 * the field is still empty. Implementations must be thread-safe and purely technical.
 */
public interface FieldValueInterceptor extends TechnicalService {
    /** The value for {@link FieldValueContext#fieldName()}; it is converted to the field's declared type. */
    Object produce(FieldValueContext context);

    /** A value interceptor takes no part in service-call interception. */
    @Override default void handle(TechnicalInvocation invocation) { }
}
