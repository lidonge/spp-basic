package com.servpp.service.runtime;

import java.util.function.Supplier;

/** Identifies the authenticated internal protocol scope accepting foreign read revisions. */
public final class DomainRpcContext {
    private static final ThreadLocal<String> DOMAIN = new ThreadLocal<>();
    private DomainRpcContext() { }
    public static String currentDomain() { return DOMAIN.get(); }
    public static <T> T execute(String domain, Supplier<T> action) {
        String previous = DOMAIN.get(); DOMAIN.set(domain);
        try { return action.get(); }
        finally { if (previous == null) DOMAIN.remove(); else DOMAIN.set(previous); }
    }
}
