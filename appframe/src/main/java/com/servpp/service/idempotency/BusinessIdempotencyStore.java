package com.servpp.service.idempotency;

import java.util.function.Supplier;

public interface BusinessIdempotencyStore {
    Object execute(String key, Supplier<Object> action);

    /** Scenario coordination must preserve the source-declared commit boundaries. */
    default Object executeScenario(String key, Supplier<Object> action) { return execute(key, action); }

    boolean contains(String key);

    void clear();
}
