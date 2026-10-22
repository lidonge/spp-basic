package com.servpp.service.runtime;

import com.servpp.service.AtomicService;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExampleServiceBarrierTest {
    public interface Step extends AtomicService { void step(); }
    public static class Action implements Step {
        final AtomicInteger calls = new AtomicInteger();
        public String getName() { return "step"; }
        public void step() { calls.incrementAndGet(); }
        public Object execute(Object... values) { step(); return null; }
    }
    @Test public void proxyRendezvousRequiresTwoThreadsAndOnlySynchronizesFirstWave() throws Exception {
        Action action = new Action(); Step proxy = (Step) ExampleServiceFaults.decorate(action);
        var pool = Executors.newFixedThreadPool(2);
        try (var scope = ExampleServiceBarrier.activate("step", 2, 2000)) {
            var first = pool.submit(proxy::step);
            var second = pool.submit(proxy::step);
            first.get(3, TimeUnit.SECONDS); second.get(3, TimeUnit.SECONDS);
            scope.verifyReached(); assertEquals(2, action.calls.get());
            proxy.step(); assertEquals(3, action.calls.get());
        } finally { pool.shutdownNow(); }
        proxy.step(); assertEquals(4, action.calls.get());
    }
    @Test public void missingArrivalTimesOutWithoutExecutingBodyAndDoesNotLeak() {
        Action action = new Action(); Step proxy = (Step) ExampleServiceFaults.decorate(action);
        try (var scope = ExampleServiceBarrier.activate("step", 2, 50)) {
            assertEquals("EXAMPLE_BARRIER_NOT_REACHED", assertThrows(IllegalStateException.class, proxy::step).getMessage());
            assertThrows(IllegalStateException.class, scope::verifyReached);
            assertEquals(0, action.calls.get());
        }
        proxy.step(); assertEquals(1, action.calls.get());
    }
    @Test public void metadataAndOtherTargetsCannotSatisfyBarrierAndScopesCannotOverlap() {
        Step proxy = (Step) ExampleServiceFaults.decorate(new Action());
        try (var scope = ExampleServiceBarrier.activate("other", 2, 50)) {
            proxy.getName(); proxy.step();
            assertThrows(IllegalStateException.class, scope::verifyReached);
            assertThrows(IllegalStateException.class, () -> ExampleServiceBarrier.activate("step", 2));
        }
        assertThrows(IllegalArgumentException.class, () -> ExampleServiceBarrier.activate("step", 1));
        assertThrows(IllegalArgumentException.class, () -> ExampleServiceBarrier.activate(null, 2));
    }
}
