package com.servpp.service.repository;

import java.util.Set;

/** A deliberately bound blank String key is distinct from a missing query input. */
public final class BlankReadKeys {
    public record Intent(int argument, Set<String> paths) {}
    private record Binding(Object participant, Set<String> paths) {}
    private static final ThreadLocal<Binding> CURRENT = new ThreadLocal<>();
    private BlankReadKeys() {}

    public static void execute(Object participant, Set<String> paths, Runnable query) {
        var previous = CURRENT.get();
        CURRENT.set(new Binding(participant, paths));
        try { query.run(); }
        finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static Intent capture(Object[] participants) {
        var current = CURRENT.get();
        if (current != null) {
            for (int i = 0; i < participants.length; i++)
                if (current.participant() == participants[i]) return new Intent(i, current.paths());
        }
        return null;
    }

    public static void project(Intent intent, Object[] participants, Runnable query) {
        if (intent == null) query.run();
        else execute(participants[intent.argument()], intent.paths(), query);
    }

    public static boolean contains(Object participant, String path) {
        var binding = CURRENT.get();
        return binding != null && participant == binding.participant() && binding.paths().contains(path);
    }
}
