package com.servpp.service.runtime;

import java.util.ArrayDeque;
import java.util.Deque;

/** Runtime check outcomes. SPP has no business return values or exception handlers. */
public final class BusinessChecks {
    public record Result(boolean passed, String code) { }
    private static final ThreadLocal<Deque<Result>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Result> LAST = ThreadLocal.withInitial(() -> new Result(true, ""));
    private static final ThreadLocal<Result> OUTCOME = ThreadLocal.withInitial(() -> new Result(true, ""));
    private BusinessChecks() { }
    public static void fail(String code) {
        var frames=FRAMES.get();
        if (frames.isEmpty()) throw new IllegalStateException("CHECK_REQUIRES_EXECUTION_SCOPE");
        frames.pop(); frames.push(new Result(false,code));
    }
    public static Result evaluate(Runnable check) {
        var frames=FRAMES.get(); frames.push(new Result(true,""));
        try { check.run(); return frames.peek(); }
        finally { LAST.set(frames.pop()); if(frames.isEmpty()) FRAMES.remove(); }
    }
    public static void executeCheck(Runnable action) {
        if (!evaluate(action).passed()) recordBlockFailure();
    }
    public static boolean identify(Object participant) {
        boolean confirmed=ParticipantIdentity.isIdentified(participant);
        LAST.set(new Result(confirmed,confirmed?"":"PARTICIPANT_NOT_IDENTIFIED"));
        return confirmed;
    }
    public static void recordRejection(String code) {
        Result rejected = new Result(false, code); LAST.set(rejected); OUTCOME.set(rejected);
    }
    public static void recordBlockFailure() { OUTCOME.set(LAST.get()); }
    public static Result scenarioResult() { return OUTCOME.get(); }
    public static void restoreScenarioResult(Result result) { OUTCOME.set(result == null ? new Result(true, "") : result); }
    public static Result lastResult() { return LAST.get(); }
    /** Check observers run before evaluate pops its active frame. */
    public static Result currentCheckResult() { return FRAMES.get().isEmpty() ? LAST.get() : FRAMES.get().peek(); }
    /** Restore a skipped check without leaving a previous check's outcome on this thread. */
    public static void restoreCheckResult(Result result) {
        Result restored = result == null ? new Result(true, "") : result;
        var frames = FRAMES.get();
        if (!frames.isEmpty()) { frames.pop(); frames.push(restored); }
        LAST.set(restored);
        if (!restored.passed()) OUTCOME.set(restored);
    }
    /** Place this outside physical/idempotency scopes so rollback finishes before conversion. */
    public static Object executeScenario(java.util.function.Supplier<Object> action, java.util.function.Supplier<Object> rejectedResponse) {
        final Object[] result = new Object[1];
        executeScenario(() -> result[0] = action.get());
        return scenarioResult().passed() ? result[0] : (result[0] == null ? rejectedResponse.get() : result[0]);
    }
    public static void executeScenario(Runnable action) {
        LAST.set(new Result(true,"")); OUTCOME.set(new Result(true,""));
        ParticipantIdentity.execute(() -> {
            try { action.run(); }
            catch (com.servpp.service.transaction.BusinessTransactionRejection rejected) {
                if (rejected.getSuppressed().length != 0) throw rejected;
                recordRejection(rejected.getMessage());
            }
        });
    }
}
