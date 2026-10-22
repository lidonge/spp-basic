package com.servpp.service.runtime;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

public final class RoleIdentityContext {
    private static final ReferenceQueue<Object> STALE_ROLES = new ReferenceQueue<>();
    private static final Map<WeakIdentityKey, Map<String, List<Object>>> ROLE_PERFORMERS = new HashMap<>();

    private RoleIdentityContext() {
    }

    public static void attachPerformers(Object roleValue, String roleType, Object... performers) {
        if (roleValue == null || roleType == null || roleType.isBlank()) {
            return;
        }
        List<Object> performerList = performers == null ? List.of() : Arrays.stream(performers)
                .filter(java.util.Objects::nonNull)
                .toList();
        synchronized (ROLE_PERFORMERS) {
            pruneStaleRoles();
            ROLE_PERFORMERS.computeIfAbsent(new WeakIdentityKey(roleValue, STALE_ROLES),
                            ignored -> new ConcurrentHashMap<>())
                    .put(roleType, performerList);
        }
    }

    public static Optional<List<Object>> performers(Object roleValue, String roleType) {
        if (roleValue == null || roleType == null || roleType.isBlank()) {
            return Optional.empty();
        }
        synchronized (ROLE_PERFORMERS) {
            pruneStaleRoles();
            Map<String, List<Object>> roleBindings = ROLE_PERFORMERS.get(new WeakIdentityKey(roleValue, null));
            if (roleBindings == null) {
                return Optional.empty();
            }
            List<Object> performers = roleBindings.get(roleType);
            return performers == null || performers.isEmpty()
                    ? Optional.empty()
                    : Optional.of(List.copyOf(performers));
        }
    }

    public static void clear(Object roleValue) {
        if (roleValue == null) {
            return;
        }
        synchronized (ROLE_PERFORMERS) {
            pruneStaleRoles();
            ROLE_PERFORMERS.remove(new WeakIdentityKey(roleValue, null));
        }
    }

    private static void pruneStaleRoles() {
        WeakIdentityKey key;
        while ((key = (WeakIdentityKey) STALE_ROLES.poll()) != null) {
            ROLE_PERFORMERS.remove(key);
        }
    }

    private static final class WeakIdentityKey extends WeakReference<Object> {
        private final int identityHash;

        private WeakIdentityKey(Object referent, ReferenceQueue<Object> queue) {
            super(referent, queue);
            this.identityHash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof WeakIdentityKey other)) {
                return false;
            }
            Object value = get();
            return value != null && value == other.get();
        }
    }
}
