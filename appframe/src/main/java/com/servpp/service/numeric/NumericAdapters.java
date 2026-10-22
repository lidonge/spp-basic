package com.servpp.service.numeric;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

public final class NumericAdapters {
    private static final ConcurrentHashMap<Class<?>, NumericAdapter<?>> ADAPTERS = new ConcurrentHashMap<>();
    private NumericAdapters() { }

    /** The Java compiler verifies the adapter type; construction errors are PHY failures. */
    @SuppressWarnings("unchecked")
    public static <T, A extends NumericAdapter<T>> A get(Class<A> type) {
        return (A) ADAPTERS.computeIfAbsent(type, key -> {
            try { return type.getConstructor().newInstance(); }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot construct PHY numeric adapter " + type.getName(), e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    public static BigDecimal toDecimal(Object value, NumericAdapter<?> adapter) {
        return ((NumericAdapter<Object>) adapter).toDecimal(value);
    }
}
