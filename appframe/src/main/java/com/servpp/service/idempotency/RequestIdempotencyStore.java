package com.servpp.service.idempotency;

import java.time.Duration;
import java.util.function.Supplier;

/** Replaceable atomic coordination protocol shared by remote replay and UI duplicate guards.
 * Implementations must not wrap action in an implicit business transaction.
 * Completed results expire after retention; running/uncertain outcomes must remain protected.
 */
public interface RequestIdempotencyStore {
    default void configure(java.util.Map<String,String> configuration) {}
    Object executeRequest(String key, Duration retention, Supplier<Object> action);
    /** Deletes only expired, definitively completed records, including completed records whose responses were erased. */
    int purgeExpired();
}
