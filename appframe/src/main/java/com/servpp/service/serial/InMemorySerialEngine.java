package com.servpp.service.serial;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic engine for tests and difftests: counters live in memory and can be seeded per counter, for example
 * from the same fixture as a legacy counter. Bound like any engine, with
 * {@code implementation: "com.servpp.service.serial.InMemorySerialEngine"}. Counters are shared per JVM and per
 * {@code store} config value (default {@code default}); call {@link #reset} between tests.
 */
public final class InMemorySerialEngine implements SerialEngine {
    private static final Map<String, Map<String, AtomicLong>> STORES = new ConcurrentHashMap<>();
    private String store = "default";

    @Override public void configure(Map<String, String> configuration) { store = configuration.getOrDefault("store", "default"); }

    @Override public long next(SerialRequest request) {
        return counters(store).computeIfAbsent(request.counter(), key -> new AtomicLong()).incrementAndGet();
    }
    /** After seeding {@code last}, the next allocation returns {@code last + 1}. */
    public static void seed(String store, String counter, long last) {
        counters(store).computeIfAbsent(counter, key -> new AtomicLong()).set(last);
    }
    public static void reset() { STORES.clear(); }
    private static Map<String, AtomicLong> counters(String store) { return STORES.computeIfAbsent(store, key -> new ConcurrentHashMap<>()); }
}
