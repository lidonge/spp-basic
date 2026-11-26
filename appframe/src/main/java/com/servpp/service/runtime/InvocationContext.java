package com.servpp.service.runtime;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Established by transport facilities, never inferred from a caller-controlled request body. */
public final class InvocationContext {
    public enum Mode { LOCAL, REMOTE }
    public enum Origin { USER, SERVICE }
    /** Technical identifiers an ingress may establish; APP {@code auto ... context} fields read these names. */
    public static final Set<String> IDENTIFIER_NAMES =
            Set.of("transactionLogNumber", "auditLogNumber", "nonAccountingLogNumber", "triggerSequence");
    public record Header(Mode mode, Origin origin, String callerId, String requestSerialNumber, Map<String, String> identifiers) {
        public Header {
            if (mode == null || origin == null) throw new IllegalArgumentException("CALL_CONTEXT_REQUIRED");
            identifiers = identifiers == null ? Map.of() : Map.copyOf(identifiers);
            for (String name : identifiers.keySet())
                if (!IDENTIFIER_NAMES.contains(name)) throw new IllegalArgumentException("CONTEXT_IDENTIFIER_UNKNOWN: " + name);
        }
        public Header(Mode mode, Origin origin, String callerId, String requestSerialNumber) {
            this(mode, origin, callerId, requestSerialNumber, Map.of());
        }
    }
    private static final Header LOCAL = new Header(Mode.LOCAL, Origin.SERVICE, "", "");
    private static final ThreadLocal<Header> CURRENT = new ThreadLocal<>();
    private InvocationContext() {}
    public static Header current() { Header value = CURRENT.get(); return value == null ? LOCAL : value; }
    public static <T> T remote(Origin origin, String callerId, String serial, Supplier<T> action) {
        return remote(origin, callerId, serial, Map.of(), action);
    }
    /** The ingress supplies the technical identifiers; they never come from the business request body. */
    public static <T> T remote(Origin origin, String callerId, String serial, Map<String, String> identifiers, Supplier<T> action) {
        if (callerId == null || callerId.isBlank()) throw new IllegalArgumentException("CALLER_ID_REQUIRED");
        if (serial == null || serial.isBlank()) throw new IllegalArgumentException("REQUEST_SERIAL_NUMBER_REQUIRED");
        return with(new Header(Mode.REMOTE, origin, callerId, serial, identifiers), action);
    }
    /** Internal calls run as LOCAL; the trusted identifiers of the enclosing ingress travel with them. */
    public static <T> T local(Supplier<T> action) {
        var identifiers = current().identifiers();
        return with(identifiers.isEmpty() ? LOCAL : new Header(Mode.LOCAL, Origin.SERVICE, "", "", identifiers), action);
    }
    /** Test-only: a LOCAL context carrying technical identifiers, as an ingress would establish them. */
    public static <T> T withIdentifiers(Map<String, String> identifiers, Supplier<T> action) {
        return with(new Header(Mode.LOCAL, Origin.SERVICE, "", "", identifiers), action);
    }
    public static <T> T with(Header header, Supplier<T> action) {
        Header previous = CURRENT.get(); CURRENT.set(header);
        try { return action.get(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
}
