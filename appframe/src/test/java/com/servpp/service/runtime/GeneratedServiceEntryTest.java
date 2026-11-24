package com.servpp.service.runtime;

import com.servpp.service.Scenario;
import com.servpp.service.Service;
import org.junit.Test;
import static org.junit.Assert.*;

public class GeneratedServiceEntryTest {
    private final Scenario scenario = new Scenario() {
        public String getName() { return "example.preview"; }
        public String startTransaction() { return null; }
        public void endTransaction(String id) { }
        public Object execute(Object... args) {
            GeneratedServiceEntry.enterScenario(this);
            GeneratedServiceEntry.requireAtomicCaller();
            return "done";
        }
    };

    @Test public void frontEndMayEnterOnceAndNestedDispatchCannotEnter() {
        assertThrows(IllegalStateException.class, scenario::execute);
        assertThrows(IllegalStateException.class, GeneratedServiceEntry::requireAtomicCaller);
        assertThrows(IllegalStateException.class, () -> GeneratedServiceEntry.dispatch(scenario, () -> {
            scenario.execute();
            return GeneratedServiceEntry.dispatch(scenario, scenario::execute);
        }));
        assertEquals("done", GeneratedServiceEntry.dispatch(scenario, scenario::execute));
    }

    @Test public void atomicCannotBeAFrontEndEntry() {
        Service atomic = new Service() {
            public Object execute(Object... args) { throw new AssertionError(); }
            public String getName() { return "example.read"; }
        };
        assertTrue(assertThrows(IllegalStateException.class,
                () -> GeneratedServiceEntry.dispatch(atomic, atomic::execute)).getMessage().contains("P0-b"));
    }
}
