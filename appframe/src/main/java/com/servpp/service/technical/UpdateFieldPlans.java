package com.servpp.service.technical;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Per-invocation technical write parameters. Absence means full ordinary-fact persistence. */
public final class UpdateFieldPlans {
    private static final ThreadLocal<Map<Object,Set<String>>> CURRENT = new ThreadLocal<>();
    private UpdateFieldPlans() { }

    public static Optional<Set<String>> fields(Object participant) {
        var frame = CURRENT.get();
        return frame == null ? Optional.empty() : Optional.ofNullable(frame.get(participant));
    }

    static void select(Object participant, Set<String> paths) {
        var frame = CURRENT.get();
        if (frame == null) throw new IllegalStateException("UPDATE_FIELDS_INVOCATION_REQUIRED");
        var selected = Set.copyOf(paths);
        for (String path : selected) if (!path.matches("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*"))
            throw new IllegalArgumentException("UPDATE_FIELD_PATH_INVALID: " + path);
        Set<String> previous = frame.putIfAbsent(participant, selected);
        if (previous != null && !previous.equals(selected))
            throw new IllegalStateException("UPDATE_FIELDS_CONFLICT");
    }

    static <T> T invoke(Supplier<T> action) {
        var previous = CURRENT.get(); CURRENT.set(new IdentityHashMap<>());
        try { return action.get(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
}
