package com.servpp.service.serial;

import com.servpp.service.technical.TechnicalInvocation;
import com.servpp.service.technical.TechnicalService;

/**
 * Contract of the technical service named by an APP {@code serial rule using engine}. The engine supplies the raw
 * next number of one counter, starting at 1. S++ keeps the rule semantics: range, wrap and format are applied
 * to the number the engine returns. Implementations validate their own PHY configuration in
 * {@link #configure} and must be thread-safe.
 */
public interface SerialEngine extends TechnicalService {
    /** The next raw number of {@code request.counter()}: positive and increasing per counter. */
    long next(SerialRequest request);

    /** A serial engine takes no part in service-call interception. */
    @Override default void handle(TechnicalInvocation invocation) { }
}
