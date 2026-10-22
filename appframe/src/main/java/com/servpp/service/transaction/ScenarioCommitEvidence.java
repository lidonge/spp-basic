package com.servpp.service.transaction;

/** Request coordination observes independent source commits without creating a transaction. */
public final class ScenarioCommitEvidence {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private ScenarioCommitEvidence() { }
    public static Scope begin() { Scope scope = new Scope(CURRENT.get()); CURRENT.set(scope); return scope; }
    public static void committed() { for (Scope scope = CURRENT.get(); scope != null; scope = scope.previous) scope.committed.set(true); }
    public static Runnable bind(Runnable action) {
        Scope shared = CURRENT.get();
        return () -> {
            Scope previous = CURRENT.get();
            if (shared == null) CURRENT.remove(); else CURRENT.set(shared);
            try { action.run(); } finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
        };
    }
    public static final class Scope implements AutoCloseable {
        private final Scope previous;
        private final java.util.concurrent.atomic.AtomicBoolean committed = new java.util.concurrent.atomic.AtomicBoolean();
        private Scope(Scope previous) { this.previous = previous; }
        public boolean hasCommittedStage() { return committed.get(); }
        @Override public void close() { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
}
