package com.servpp.service.transaction;

import com.servpp.service.SourceScenarioService;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceScenarioParticipantTest {
    public static class ExampleScenario extends SourceScenarioService {
        @Override public String getName() { return "exampleScenario"; }
        @Override public Object execute(Object... arguments) { return arguments[0]; }
        @Override public String startTransaction() { return null; }
        @Override public void endTransaction(String id) { }
        public int scenarioMethod(int value) { return value + 1; }
    }
    @Test public void technicalBindingPreservesConcreteScenarioMethodsAndExecutesParticipantHooks() {
        var scenario = new ExampleScenario(); var events = new ArrayList<String>();
        var bound = TransactionParticipantProxy.decorate(scenario, original ->
                new ChainedTransactionParticipant(original.getResourceId(), original, new TransactionParticipant() {
                    @Override public String getResourceId() { return original.getResourceId(); }
                    @Override public void begin(TransactionContext context) { events.add("begin"); }
                    @Override public void commit(TransactionContext context) { events.add("commit"); }
                    @Override public void rollback(TransactionContext context, Throwable failure) { events.add(failure.getMessage()); }
                }));
        assertSame(scenario, bound);
        assertEquals(3, ((ExampleScenario)bound).scenarioMethod(2));
        assertEquals("fact", bound.execute("fact"));
        var context = new TransactionContext(java.util.Map.of("exampleScenario", TransactionMode.SAGA));
        var participant = scenario.getTransactionParticipant();
        participant.begin(context); participant.commit(context);
        participant.rollback(context, new IllegalStateException("primary"));
        assertEquals(List.of("begin", "commit", "primary"), events);
    }
    @Test public void multipleTechnicalBindingsComposeWithoutLosingPriorParticipants() {
        var scenario = new ExampleScenario(); var events = new ArrayList<String>();
        for (String name : List.of("first", "second")) {
            assertSame(scenario, TransactionParticipantProxy.decorate(scenario, original ->
                    new ChainedTransactionParticipant(original.getResourceId(), original, new TransactionParticipant() {
                        @Override public String getResourceId() { return name; }
                        @Override public void begin(TransactionContext context) { events.add(name); }
                    })));
        }
        scenario.getTransactionParticipant().begin(new TransactionContext(java.util.Map.of()));
        assertEquals(List.of("first", "second"), events);
    }
}
