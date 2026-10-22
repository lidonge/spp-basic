package com.servpp.service.runtime;

import java.util.Optional;
import java.util.function.Supplier;

/** Transport-level retry identity; reuse the same identity and input for one logical outbound call. */
public final class OutboundRequestContext {
    public record Identity(String callerId, String requestSerialNumber) {
        public Identity {
            if (callerId == null || callerId.isBlank() || requestSerialNumber == null || requestSerialNumber.isBlank())
                throw new IllegalArgumentException("OUTBOUND_REQUEST_IDENTITY_REQUIRED");
        }
    }
    private static final ThreadLocal<Identity> CURRENT = new ThreadLocal<>();
    private OutboundRequestContext() {}
    public static Optional<Identity> current() { return Optional.ofNullable(CURRENT.get()); }
    public static <T> T with(String callerId, String serial, Supplier<T> action) {
        Identity previous = CURRENT.get(); CURRENT.set(new Identity(callerId, serial));
        try { return action.get(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
}
