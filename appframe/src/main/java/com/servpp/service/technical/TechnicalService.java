package com.servpp.service.technical;

import java.util.Map;

/** PHY extension protocol. Implementations must be thread-safe and purely technical. */
public interface TechnicalService {
    /** Coordination failures must reach the transaction/entry owner; observers keep their existing isolation. */
    default boolean critical() { return false; }
    default void configure(Map<String, String> configuration) { }
    void handle(TechnicalInvocation invocation);
}
