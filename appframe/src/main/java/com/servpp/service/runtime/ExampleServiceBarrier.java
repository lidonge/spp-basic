package com.servpp.service.runtime;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** One bounded cross-thread rendezvous, enabled only for a declared example. */
public final class ExampleServiceBarrier {
    private static final AtomicReference<Plan> ACTIVE = new AtomicReference<>();
    private ExampleServiceBarrier() {}
    public static Scope activate(String service, int parties) { return activate(service, parties, 30_000); }
    static Scope activate(String service, int parties, long timeoutMillis) {
        if (service == null) {
            if (parties != 0) throw new IllegalArgumentException("Example barrier parties require a service");
            return new Scope(null);
        }
        if (service.isBlank() || parties < 2 || parties > 64 || timeoutMillis <= 0) {
            throw new IllegalArgumentException("Invalid example service barrier");
        }
        Plan plan = new Plan(service, parties, timeoutMillis);
        if (!ACTIVE.compareAndSet(null, plan)) throw new IllegalStateException("EXAMPLE_BARRIER_ALREADY_ACTIVE");
        return new Scope(plan);
    }
    static void before(String service) {
        Plan plan = ACTIVE.get();
        if (plan == null || !plan.service.equals(service) || plan.claims.getAndIncrement() >= plan.parties) return;
        try { plan.barrier.await(plan.timeoutMillis, TimeUnit.MILLISECONDS); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("EXAMPLE_BARRIER_INTERRUPTED", interrupted);
        } catch (TimeoutException | BrokenBarrierException failure) {
            throw new IllegalStateException("EXAMPLE_BARRIER_NOT_REACHED", failure);
        }
    }
    private static final class Plan {
        final String service;
        final int parties;
        final long timeoutMillis;
        final AtomicInteger claims = new AtomicInteger();
        final CyclicBarrier barrier;
        volatile boolean reached;
        Plan(String service, int parties, long timeoutMillis) {
            this.service = service; this.parties = parties; this.timeoutMillis = timeoutMillis;
            barrier = new CyclicBarrier(parties, () -> reached = true);
        }
    }
    public static final class Scope implements AutoCloseable {
        private final Plan plan;
        private Scope(Plan plan) { this.plan = plan; }
        public void verifyReached() {
            if (plan != null && !plan.reached) throw new IllegalStateException("EXAMPLE_BARRIER_NOT_REACHED: " + plan.service);
        }
        @Override public void close() { if (plan != null) ACTIVE.compareAndSet(plan, null); }
    }
}
