package com.servpp.service.runtime;

import java.util.function.Supplier;

/** Established by transport facilities, never inferred from a caller-controlled request body. */
public final class InvocationContext {
    public enum Mode { LOCAL, REMOTE }
    public enum Origin { USER, SERVICE }
    public record Header(Mode mode, Origin origin, String callerId, String requestSerialNumber) {
        public Header {
            if (mode == null || origin == null) throw new IllegalArgumentException("CALL_CONTEXT_REQUIRED");
        }
    }
    private static final Header LOCAL = new Header(Mode.LOCAL, Origin.SERVICE, "", "");
    private static final ThreadLocal<Header> CURRENT = new ThreadLocal<>();
    private InvocationContext() {}
    public static Header current() { Header value = CURRENT.get(); return value == null ? LOCAL : value; }
    public static <T> T remote(Origin origin, String callerId, String serial, Supplier<T> action) {
        if (callerId == null || callerId.isBlank()) throw new IllegalArgumentException("CALLER_ID_REQUIRED");
        if (serial == null || serial.isBlank()) throw new IllegalArgumentException("REQUEST_SERIAL_NUMBER_REQUIRED");
        return with(new Header(Mode.REMOTE, origin, callerId, serial), action);
    }
    public static <T> T local(Supplier<T> action) { return with(LOCAL, action); }
    public static <T> T with(Header header, Supplier<T> action) {
        Header previous = CURRENT.get(); CURRENT.set(header);
        try { return action.get(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
}
