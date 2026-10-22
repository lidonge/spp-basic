package com.servpp.service.repository;

import com.servpp.service.transaction.DeclaredTransactionContext;
import java.lang.reflect.Array;
import java.util.IdentityHashMap;
import java.util.Map;

/** Generated participant read intent. Locking remains scoped to an existing source transaction. */
public final class TransactionQueryReads {
    private static final ThreadLocal<Map<Object, Boolean>> CURRENT = ThreadLocal.withInitial(IdentityHashMap::new);
    private TransactionQueryReads() { }
    public static boolean protects(Object participant, boolean ordinaryParticipant) {
        return DeclaredTransactionContext.currentMode() != null
                && CURRENT.get().getOrDefault(participant, ordinaryParticipant);
    }
    public static void execute(Object[] participants, boolean[] ordinaryUpdates, Runnable action) {
        if (participants.length != ordinaryUpdates.length) throw new IllegalArgumentException("QUERY_READ_POLICY_ARITY");
        Map<Object, Boolean> previous = CURRENT.get();
        Map<Object, Boolean> scope = new IdentityHashMap<>(previous);
        for (int i = 0; i < participants.length; i++) mark(scope, participants[i], ordinaryUpdates[i]);
        CURRENT.set(scope);
        try { action.run(); } finally { CURRENT.set(previous); }
    }
    private static void mark(Map<Object, Boolean> scope, Object participant, boolean ordinaryUpdates) {
        if (participant == null) return;
        scope.put(participant, ordinaryUpdates);
        if (participant instanceof Iterable<?> values) for (Object member : values) mark(scope, member, ordinaryUpdates);
        else if (participant.getClass().isArray())
            for (int i = 0; i < Array.getLength(participant); i++) mark(scope, Array.get(participant, i), ordinaryUpdates);
    }
    public static void project(Object[] participants, Object[] projected, Runnable action) {
        Map<Object, Boolean> previous = CURRENT.get();
        Map<Object, Boolean> scope = new IdentityHashMap<>(previous);
        for (int i = 0; i < participants.length; i++)
            if (previous.containsKey(participants[i])) mark(scope, projected[i], previous.get(participants[i]));
        CURRENT.set(scope);
        try { action.run(); } finally { CURRENT.set(previous); }
    }
}
