package com.servpp.service.runtime;

/** Technical APP startup seeds, completed before publishing service readiness. */
public final class DatabaseInitializationContext {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();
    private DatabaseInitializationContext() { }
    public static boolean isActive() { return Boolean.TRUE.equals(ACTIVE.get()); }
    public static void execute(Runnable initialization) {
        Boolean previous = ACTIVE.get();
        ACTIVE.set(true);
        try { initialization.run(); }
        finally { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
    }
}
