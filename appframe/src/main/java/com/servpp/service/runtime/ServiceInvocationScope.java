package com.servpp.service.runtime;

import com.servpp.service.transaction.BusinessTraceContext;
import java.util.UUID;
import java.util.Optional;
import java.util.function.Supplier;

/** Invocation context for entries which do not use the system request coordinator. */
public final class ServiceInvocationScope {
    private ServiceInvocationScope() { }

    public static <T> T execute(Supplier<T> action) {
        return execute(null, action);
    }

    public static <T> T execute(Object request, Supplier<T> action) {
        String trace = InvocationContext.current().mode() == InvocationContext.Mode.REMOTE
                ? "invocation:" + UUID.randomUUID()
                : businessTraceId(request).orElse(BusinessTraceContext.currentGlobalSerialNumber().orElse(""));
        return BusinessTraceContext.execute(trace, () -> InvocationContext.local(action));
    }

    public static Optional<String> businessTraceId(Object request) {
        Object header = read(request, "getAppHeader");
        for (String getter : new String[]{"getGlobalSerialNumber", "getMessageId"}) {
            Object value = read(header, getter);
            if (value != null && !value.toString().isBlank()) return Optional.of(value.toString().trim());
        }
        return Optional.empty();
    }

    private static Object read(Object target, String getter) {
        if (target == null) return null;
        try { return target.getClass().getMethod(getter).invoke(target); }
        catch (NoSuchMethodException missing) { return null; }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot read invocation trace " + getter, failure); }
    }
}
