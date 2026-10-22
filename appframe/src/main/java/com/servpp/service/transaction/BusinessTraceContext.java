package com.servpp.service.transaction;

import java.util.Optional;
import java.util.function.Supplier;

public final class BusinessTraceContext {
    private static final ThreadLocal<String> CURRENT_GLOBAL_SERIAL_NUMBER = new ThreadLocal<>();

    private BusinessTraceContext() {
    }

    public static Optional<String> currentGlobalSerialNumber() {
        String value = CURRENT_GLOBAL_SERIAL_NUMBER.get();
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    /** Copy the business request identity to a worker without copying database transaction state. */
    public static Runnable bind(Runnable action) {
        String serial=CURRENT_GLOBAL_SERIAL_NUMBER.get();
        return () -> execute(serial, () -> { action.run(); return null; });
    }

    public static <T> T execute(String globalSerialNumber, Supplier<T> action) {
        String previous = CURRENT_GLOBAL_SERIAL_NUMBER.get();
        if (globalSerialNumber == null || globalSerialNumber.isBlank()) {
            CURRENT_GLOBAL_SERIAL_NUMBER.remove();
        } else {
            CURRENT_GLOBAL_SERIAL_NUMBER.set(globalSerialNumber);
        }
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT_GLOBAL_SERIAL_NUMBER.remove();
            } else {
                CURRENT_GLOBAL_SERIAL_NUMBER.set(previous);
            }
        }
    }
}
