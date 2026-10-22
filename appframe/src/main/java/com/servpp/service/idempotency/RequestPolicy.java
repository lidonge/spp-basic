package com.servpp.service.idempotency;

import java.time.Duration;
import java.util.Map;

/** Application defaults, supplied by APP-generated deployment configuration. */
public final class RequestPolicy {
    private static volatile Duration retention = Duration.ofDays(3);
    private static volatile Duration duplicateWindow = Duration.ofSeconds(5);
    private RequestPolicy() {}
    public static Duration retention() { return retention; }
    public static Duration duplicateWindow() { return duplicateWindow; }
    public static void configure(Map<String,String> configuration) {
        Duration nextRetention = parse(configuration.getOrDefault("requestIdempotencyRetention", "P3D"));
        Duration nextWindow = parse(configuration.getOrDefault("requestDuplicateWindow", "PT5S"));
        retention = nextRetention; duplicateWindow = nextWindow;
    }
    public static Duration parse(String value) {
        Duration result = Duration.parse(value);
        if (result.isNegative() || result.isZero() || result.toMillis() < 1)
            throw new IllegalArgumentException("REQUEST_DURATION_MUST_BE_POSITIVE");
        return result;
    }
}
