package com.servpp.service.runtime;

/** Projects authoritative transport context into APP-declared technical header fields. */
public final class TechnicalCallHeaders {
    private TechnicalCallHeaders() {}
    public static void apply(Object request, InvocationContext.Header invocation) {
        if (request == null) throw new IllegalArgumentException("REQUEST_REQUIRED");
        try {
            java.lang.reflect.Method getter = null, setter = null;
            for (var method : request.getClass().getMethods()) {
                if (method.getName().equalsIgnoreCase("getAppHeader") && method.getParameterCount() == 0) getter = method;
                if (method.getName().equalsIgnoreCase("setAppHeader") && method.getParameterCount() == 1) setter = method;
            }
            if (getter == null || setter == null) return;
            Object header = getter.invoke(request);
            if (header == null) header = setter.getParameterTypes()[0].getConstructor().newInstance();
            set(header, "callerId", invocation.callerId());
            set(header, "requestSerialNumber", invocation.requestSerialNumber());
            set(header, "callMode", invocation.mode().name());
            set(header, "callOrigin", invocation.origin().name());
            setter.invoke(request, header);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("TECHNICAL_HEADER_FAILED", failure); }
    }
    public static void set(Object header, String name, String value) throws ReflectiveOperationException {
        for (var method : header.getClass().getMethods())
            if (method.getName().equalsIgnoreCase("set" + name) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == String.class) { method.invoke(header, value); return; }
    }
}
