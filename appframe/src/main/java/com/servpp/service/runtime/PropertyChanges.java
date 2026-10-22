package com.servpp.service.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collection;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/** Open tracking protocol. No tracking algorithm is included in the basic runtime. */
public final class PropertyChanges {
    private static final PropertyChangeProvider PROVIDER = load();
    private PropertyChanges() { }

    private static PropertyChangeProvider load() {
        var providers = ServiceLoader.load(PropertyChangeProvider.class).iterator();
        if (!providers.hasNext()) return null;
        var provider = providers.next();
        if (providers.hasNext()) throw new IllegalStateException("PROPERTY_TRACKING_PROVIDER_AMBIGUOUS");
        return provider;
    }
    public static boolean isAvailable() { return PROVIDER != null; }
    private static PropertyChangeProvider required() {
        if (PROVIDER == null) throw new IllegalStateException("PROPERTY_TRACKING_PROVIDER_REQUIRED");
        return PROVIDER;
    }

    /** Opaque extension-owned state, kept outside business bean/JSON properties. */
    public static final class State {
        private final Object extensionState = required().createState();
        public Object extensionState() { return extensionState; }
    }
    public record Checkpoint(Map<String,Long> writes, Map<String,JsonNode> cleanValues,
                             Map<String,Long> cleanVersions) { }

    public static void modified(Object participant, String property) {
        if (PROVIDER != null) PROVIDER.modified(participant, property);
    }
    public static void loading(Runnable action) {
        if (PROVIDER == null) action.run(); else PROVIDER.loading(action);
    }
    public static Map<State,Checkpoint> checkpoint(Collection<?> participants) {
        return PROVIDER == null ? Map.of() : PROVIDER.checkpoint(participants);
    }
    public static void restoreCheckpoint(Map<State,Checkpoint> states) {
        if (!states.isEmpty()) required().restoreCheckpoint(states);
    }
    public static Runnable acknowledgement(Object participant) {
        return PROVIDER == null ? () -> { } : PROVIDER.acknowledgement(participant);
    }
    public static void clean(Object participant) {
        if (PROVIDER != null) PROVIDER.clean(participant);
    }
    public static void copy(Object source, Object target) {
        if (PROVIDER != null) PROVIDER.copy(source, target);
    }
    public static void project(Object source, Object target) {
        if (PROVIDER != null) PROVIDER.project(source, target);
    }
    public static void restore(Object target, Set<String> paths) {
        if (PROVIDER != null) PROVIDER.restore(target, paths);
        else if (paths != null && !paths.isEmpty())
            throw new IllegalStateException("PROPERTY_TRACKING_PROVIDER_REQUIRED");
    }
    public static Set<String> paths(Object value, Object baseline) {
        return PROVIDER == null ? Set.of() : PROVIDER.paths(value, baseline);
    }
    public static Set<String> paths(Object value) {
        return PROVIDER == null ? Set.of() : PROVIDER.paths(value);
    }
    public static void mapped(Object source, String from, Object target, String to, Runnable assignment) {
        if (PROVIDER == null) assignment.run(); else PROVIDER.mapped(source, from, target, to, assignment);
    }
    public static boolean overlaps(String left, String right) {
        return left.equals(right) || left.startsWith(right + ".") || right.startsWith(left + ".");
    }
}
