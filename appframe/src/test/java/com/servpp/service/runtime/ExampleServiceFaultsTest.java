package com.servpp.service.runtime;

import com.servpp.service.AtomicService;
import com.servpp.service.Service;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExampleServiceFaultsTest {
    public interface Step extends AtomicService { void step(); }
    public static class Action implements Step {
        int calls;
        RuntimeException failure;
        public String getName() { return "step"; }
        public void step() { calls++; if (failure != null) throw failure; }
        public Object execute(Object... values) { step(); return null; }
    }
    public interface Arm extends AtomicService { void arm(); }
    public static class Anchor implements Arm {
        RuntimeException failure;
        public String getName() { return "arm"; }
        public void arm() { if (failure != null) throw failure; }
        public Object execute(Object... values) { arm(); return null; }
    }
    @Test public void repeatedFaultRequiresEveryDeclaredOccurrenceAndStopsAfterwards() {
        Action action = new Action(); Step proxy = (Step) ExampleServiceFaults.decorate(action);
        try (var scope = ExampleServiceFaults.activate("step", "before", 2, null)) {
            assertThrows(IllegalStateException.class, proxy::step);
            assertThrows(IllegalStateException.class, scope::verifyTriggered);
            assertThrows(IllegalStateException.class, proxy::step);
            scope.verifyTriggered(); assertEquals(0, action.calls);
            proxy.step(); assertEquals(1, action.calls);
        }
    }
    @Test public void qualifiedServiceNamesStillInterceptTheirUnqualifiedJavaMethod() {
        Action action = new Action() { @Override public String getName() { return "domain.step"; } };
        Step proxy = (Step) ExampleServiceFaults.decorate(action);
        try (var scope = ExampleServiceFaults.activate("domain.step", "after")) {
            assertEquals("EXAMPLE_SERVICE_FAULT", assertThrows(IllegalStateException.class, proxy::step).getMessage());
            assertEquals(1, action.calls); scope.verifyTriggered();
            proxy.step(); assertEquals(2, action.calls);
        }
    }
    @Test public void onlySuccessfulAnchorArmsLaterCallsAndRepeatedAnchorsDoNotResetCount() {
        Action action = new Action(); Step proxy = (Step) ExampleServiceFaults.decorate(action);
        Anchor anchor = new Anchor(); Arm arm = (Arm) ExampleServiceFaults.decorate(anchor);
        try (var scope = ExampleServiceFaults.activate("step", "before", 2, "arm")) {
            proxy.step(); assertEquals(1, action.calls);
            anchor.failure = new IllegalArgumentException("anchor failed");
            assertSame(anchor.failure, assertThrows(IllegalArgumentException.class, arm::arm));
            proxy.step(); assertEquals(2, action.calls);
            assertThrows(IllegalStateException.class, scope::verifyTriggered);
            anchor.failure = null; arm.arm();
            assertThrows(IllegalStateException.class, proxy::step);
            arm.arm(); assertThrows(IllegalStateException.class, proxy::step);
            arm.arm(); proxy.step(); scope.verifyTriggered(); assertEquals(3, action.calls);
        }
    }
    @Test public void invalidRepeatedFaultConfigurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ExampleServiceFaults.activate("step", "before", 0, null));
        assertThrows(IllegalArgumentException.class, () -> ExampleServiceFaults.activate("step", "before", 2, ""));
        assertThrows(IllegalArgumentException.class, () -> ExampleServiceFaults.activate(null, null, 2, null));
    }
    @Test public void beforeFaultSkipsBodyAndIsOneShot() {
        Action action = new Action(); Step proxy = (Step) ExampleServiceFaults.decorate(action);
        try (var scope = ExampleServiceFaults.activate("step", "before")) {
            assertEquals("EXAMPLE_SERVICE_FAULT", assertThrows(IllegalStateException.class, proxy::step).getMessage());
            assertEquals(0, action.calls); scope.verifyTriggered();
            proxy.step(); assertEquals(1, action.calls);
        }
        proxy.step(); assertEquals(2, action.calls);
    }
    @Test public void afterFaultRunsBodyAndRawInvocationCountsOnce() {
        Action action = new Action(); Service proxy = ExampleServiceFaults.decorate(action);
        try (var scope = ExampleServiceFaults.activate("step", "after")) {
            assertThrows(IllegalStateException.class, proxy::execute);
            assertEquals(1, action.calls); scope.verifyTriggered();
            proxy.execute(); assertEquals(2, action.calls);
        }
    }
    @Test public void metadataAndMissingTargetCannotSatisfyFault() {
        Step proxy = (Step) ExampleServiceFaults.decorate(new Action());
        try (var scope = ExampleServiceFaults.activate("step", "after")) {
            assertEquals("step", proxy.getName()); proxy.getTransactionResourceId();
            assertTrue(assertThrows(IllegalStateException.class, scope::verifyTriggered).getMessage().contains("NOT_TRIGGERED"));
        }
    }
    @Test public void afterFaultDoesNotReplaceAnEarlierBodyFailure() {
        Action action = new Action(); action.failure = new IllegalArgumentException("body failed");
        Step proxy = (Step) ExampleServiceFaults.decorate(action);
        try (var scope = ExampleServiceFaults.activate("step", "after")) {
            assertSame(action.failure, assertThrows(IllegalArgumentException.class, proxy::step));
            assertThrows(IllegalStateException.class, scope::verifyTriggered);
        }
    }
    @Test public void nestedScopeRestoresParentAndDoesNotLeakToAnotherThread() throws Exception {
        Step proxy = (Step) ExampleServiceFaults.decorate(new Action());
        try (var parent = ExampleServiceFaults.activate("step", "before")) {
            Thread other = new Thread(proxy::step); other.start(); other.join();
            assertThrows(IllegalStateException.class, parent::verifyTriggered);
            try (var child = ExampleServiceFaults.activate(null, null)) { proxy.step(); child.verifyTriggered(); }
            assertThrows(IllegalStateException.class, proxy::step); parent.verifyTriggered();
        }
        proxy.step();
    }
}
