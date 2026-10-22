package com.servpp.service.transaction;

import com.servpp.service.Service;
import com.servpp.service.scenario.IExecutor;
import com.servpp.service.scenario.ITransactionGroup;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeclaredTransactionGroupTest {
    @After public void reset() { SagaTransactionGroupManager.resetCoordinator(); }

    @Test public void mismatchFailsBeforeAnyActionOrParticipantBegins() {
        List<String> events = new ArrayList<>();
        var error = assertThrows(IllegalStateException.class, () -> group(TransactionMode.QUANTUM,
                executor(new Action("write", TransactionMode.AT, events), false)).execute());
        assertTrue(error.getMessage(), error.getMessage().contains("QUANTUM") && error.getMessage().contains("AT"));
        assertTrue(events.toString(), events.isEmpty());
    }

    @Test public void declaredAtBoundaryDoesNotUseAnInstalledSagaCoordinator() {
        List<String> events = new ArrayList<>();
        SagaTransactionGroupManager.useCoordinator((scope, calls) -> { throw new AssertionError("unexpected Saga coordinator"); });
        group(TransactionMode.AT, executor(new Action("write", TransactionMode.AT, events), false)).execute();
        assertEquals(List.of("begin:write", "execute:write", "commit:write"), events);
    }

    @Test public void failureRunsParticipantRecoveryInsideTheDeclaredBoundary() {
        List<String> events = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> group(TransactionMode.AT,
                executor(new Action("first", TransactionMode.AT, events), false),
                executor(new Action("second", TransactionMode.AT, events), true)).execute());
        assertEquals(List.of("begin:first", "begin:second", "execute:first", "execute:second",
                "rollback:second", "rollback:first"), events);
    }

    @Test public void explicitSagaUsesTheConfiguredCoordinator() {
        List<String> events = new ArrayList<>();
        SagaTransactionGroupManager.useCoordinator((scope, calls) -> {
            events.add("coordinate"); calls.forEach(IExecutor::execute);
        });
        group(TransactionMode.SAGA, executor(new Action("write", TransactionMode.SAGA, events), false)).execute();
        assertEquals(List.of("coordinate", "execute:write"), events);
    }

    @Test public void sagaDoesNotWrapLocalNodesInOneQuantumAccessScope() {
        DefaultTransactionManager manager = DefaultTransactionManager.getInstance();
        QuantumAccessAdapter previous = manager.getQuantumAccessAdapter();
        List<TransactionMode> scopes = new ArrayList<>();
        manager.setQuantumAccessAdapter(new QuantumAccessAdapter() {
            public void executeInAccessScope(Runnable action) {
                scopes.add(DeclaredTransactionContext.currentMode()); action.run();
            }
            public void commit(TransactionContext context, QuantumEntanglementBoundary boundary) { }
        });
        try {
            DeclaredTransactionContext.execute(TransactionMode.SAGA, () -> manager.execute(List.of(new IExecutor() {
                public Service getService() { return null; }
                public void execute() {
                    assertEquals(TransactionMode.SAGA, DeclaredTransactionContext.currentMode());
                    DeclaredTransactionContext.execute(TransactionMode.AT, () -> manager.execute(List.of()));
                    assertEquals(TransactionMode.SAGA, DeclaredTransactionContext.currentMode());
                }
            })));
            assertEquals(List.of(TransactionMode.AT), scopes);
            assertNull(DeclaredTransactionContext.currentMode());
        } finally { manager.setQuantumAccessAdapter(previous); DefaultTransactionManager.clearLastContext(); }
    }

    @Test public void sourceSelectedUpdatesAreRecoveredWhenLaterOuterActionFails() {
        List<String> events = new ArrayList<>();
        IExecutor child = executor(new Action("child", TransactionMode.AT, events), false);
        IExecutor parent = new IExecutor() {
            public Service getService() { return null; }
            public void execute() {
                SourceTransactionUpdates.execute(child);
                throw new IllegalStateException("later source check");
            }
        };
        assertThrows(IllegalStateException.class, () -> group(TransactionMode.AT, parent).execute());
        assertEquals(List.of("begin:child", "execute:child", "rollback:child"), events);
        assertNull(DeclaredTransactionContext.currentMode());
    }

    @Test public void sagaLocalCommitIsNotTechnicallyRolledBackByAnOuterFailure() {
        List<String> events = new ArrayList<>();
        DefaultTransactionManager manager = DefaultTransactionManager.getInstance();
        IExecutor parent = new IExecutor() {
            public Service getService() { return null; }
            public void execute() {
                DeclaredTransactionContext.execute(TransactionMode.AT,
                        () -> SourceTransactionUpdates.execute(executor(new Action("local", TransactionMode.AT, events), false)));
                throw new IllegalStateException("later Saga node");
            }
        };
        assertThrows(IllegalStateException.class, () -> DeclaredTransactionContext.execute(TransactionMode.SAGA,
                () -> manager.execute(List.of(parent))));
        assertEquals(List.of("begin:local", "execute:local", "commit:local"), events);
    }

    @Test public void sourceSelectedUpdateCannotBypassItsDeclaredMode() {
        List<String> events = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> DeclaredTransactionContext.execute(TransactionMode.QUANTUM,
                () -> SourceTransactionUpdates.execute(executor(new Action("wrong", TransactionMode.AT, events), false))));
        assertTrue(events.isEmpty());
    }

    private static final class GeneratedScenario extends com.servpp.service.SourceScenarioService
            implements com.servpp.service.runtime.GeneratedServiceKindProvider, com.servpp.service.runtime.ScopeMetadataProvider {
        private final TransactionMode mode;
        GeneratedScenario(TransactionMode mode) { this.mode = mode; }
        public TransactionMode getTransactionMode() { return mode; }
        public String getName() { return "sourceScenario"; }
        public String startTransaction() { return null; }
        public void endTransaction(String id) {}
        public Object execute(Object... args) { return null; }
        public com.servpp.service.runtime.GeneratedServiceKind getGeneratedServiceKind() { return com.servpp.service.runtime.GeneratedServiceKind.SCENARIO; }
        public boolean isLocalScope() { return true; }
        public String getInputHeaderName() { return null; }
        public String getOutputHeaderName() { return null; }
    }
    @Test public void transactionCannotContainBusinessScenarios() {
        IExecutor inherited = new IExecutor() {
            public Service getService() { return new GeneratedScenario(TransactionMode.AT); }
            public void execute() { assertEquals(TransactionMode.QUANTUM, DeclaredTransactionContext.currentMode()); }
        };
        assertThrows(IllegalStateException.class, () -> group(TransactionMode.QUANTUM, inherited).execute());
        IExecutor bound = new IExecutor() {
            public Service getService() { return new GeneratedScenario(TransactionMode.SAGA); }
            public void execute() { fail("explicit Saga must not inherit Quantum"); }
        };
        assertThrows(IllegalStateException.class, () -> group(TransactionMode.QUANTUM, bound).execute());
        assertThrows(IllegalStateException.class, () -> group(TransactionMode.SAGA, inherited).execute());
        assertThrows(IllegalStateException.class, () -> group(TransactionMode.TCC, inherited).execute());
    }

    @Test public void emptyDeclaredBoundaryCannotReportSuccess() {
        assertThrows(IllegalStateException.class, () -> group(TransactionMode.QUANTUM).execute());
    }

    private ITransactionGroup group(TransactionMode mode, IExecutor... calls) {
        return new ITransactionGroup() {
            public TransactionMode getDeclaredTransactionMode() { return mode; }
            public IExecutor[] getServices() { return calls; }
        };
    }
    private IExecutor executor(Action service, boolean fail) {
        return new IExecutor() {
            public Service getService() { return service; }
            public void execute() {
                service.events.add("execute:" + service.name);
                if (fail) throw new IllegalStateException("injected");
            }
        };
    }
    private record Action(String name, TransactionMode mode, List<String> events) implements Service, TransactionalService {
        public String getName() { return name; }
        public Object execute(Object... roles) { return null; }
        public String getTransactionResourceId() { return name; }
        public TransactionMode getTransactionMode() { return mode; }
        public TransactionParticipant getTransactionParticipant() {
            return new TransactionParticipant() {
                public String getResourceId() { return name; }
                public void begin(TransactionContext c) { events.add("begin:" + name); }
                public void commit(TransactionContext c) { events.add("commit:" + name); }
                public void rollback(TransactionContext c, Throwable cause) { events.add("rollback:" + name); }
            };
        }
    }
}
