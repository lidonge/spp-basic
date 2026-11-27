package com.servpp.service.runtime;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Call-site views of role participants. A service called with a role where it expects the role's player
 * works on a projected copy; the transaction still tracks the role the caller passed. The alias lets
 * runtime checks (quantum endpoints) resolve the copy back to that participant.
 */
public final class RoleArgumentAliases {
    private static final ThreadLocal<Map<Object, Object>> CURRENT = new ThreadLocal<>();
    private RoleArgumentAliases() { }

    /** The participant a projected view was made from, or the object itself. */
    public static Object original(Object view) {
        var aliases = CURRENT.get();
        Object result = view;
        while (aliases != null && aliases.containsKey(result)) result = aliases.get(result);
        return result;
    }

    static void run(Map<Object, Object> views, Runnable action) {
        var previous = CURRENT.get();
        var merged = new IdentityHashMap<Object, Object>();
        if (previous != null) merged.putAll(previous);
        merged.putAll(views);
        CURRENT.set(merged);
        try { action.run(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
}
