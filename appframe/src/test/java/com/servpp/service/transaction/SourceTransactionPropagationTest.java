package com.servpp.service.transaction;

import com.servpp.service.Service;
import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.scenario.IExecutor;
import com.servpp.service.scenario.ITransactionGroup;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** Database outcomes, rather than just invocation counts, define propagation. */
public class SourceTransactionPropagationTest {
    private JdbcDataSource dataSource;
    private QuantumAccessAdapter previous;
    @Before public void setup() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:propagation_" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("create table facts(id int primary key)");
        }
        var manager = DefaultTransactionManager.getInstance(); previous = manager.getQuantumAccessAdapter();
        manager.setQuantumAccessAdapter(new QuantumAccessAdapter() {
            public void executeInAccessScope(Runnable action) {
                try { JdbcTransactionScope.execute(dataSource, connection -> { action.run(); return null; }); }
                catch (SQLException failure) { throw new IllegalStateException(failure); }
            }
            public void commit(TransactionContext context, QuantumEntanglementBoundary boundary) { }
        });
    }
    @After public void cleanup() throws Exception {
        SagaTransactionGroupManager.resetCoordinator();
        com.servpp.service.server.DefaultServiceContainer.getInstance().clear();
        DefaultTransactionManager.getInstance().setQuantumAccessAdapter(previous);
        DefaultTransactionManager.clearLastContext();
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) { statement.execute("shutdown"); }
        assertNull(DeclaredTransactionContext.currentMode());
        assertTrue(JdbcTransactionScope.currentConnections().isEmpty());
    }
    @Test public void caughtRequiredFailurePreventsOuterCommit() throws Exception {
        var failure = assertThrows(IllegalStateException.class, () -> group(TransactionPropagation.REQUIRED,
            action(() -> {
                insert(1);
                try { group(TransactionPropagation.REQUIRED, action(() -> { insert(2); failTransaction(); })).execute(); }
                catch (IllegalStateException expected) { }
                insert(3);
            })).execute());
        assertEquals("TRANSACTION_ROLLBACK_ONLY", failure.getMessage());
        assertEquals(List.of(), facts());
    }
    @Test public void nestedFailureRollsBackOnlyInnerFacts() throws Exception {
        group(TransactionPropagation.REQUIRED, action(() -> {
            insert(1);
            try { group(TransactionPropagation.NESTED, action(() -> { insert(2); failTransaction(); })).execute(); }
            catch (IllegalStateException expected) { }
            insert(3);
        })).execute();
        assertEquals(List.of(1, 3), facts());
    }
    @Test public void successfulNestedWorkStillRollsBackWithOuter() throws Exception {
        assertThrows(IllegalStateException.class, () -> group(TransactionPropagation.REQUIRED, action(() -> {
            insert(1); group(TransactionPropagation.NESTED, action(() -> insert(2))).execute(); failTransaction();
        })).execute());
        assertEquals(List.of(), facts());
    }
    @Test public void independentCommitSurvivesOuterRollback() throws Exception {
        assertThrows(IllegalStateException.class, () -> group(TransactionPropagation.REQUIRED, action(() -> {
            insert(1); group(TransactionPropagation.REQUIRES_NEW, action(() -> insert(2))).execute(); failTransaction();
        })).execute());
        assertEquals(List.of(2), facts());
    }
    @Test public void pureSagaPlanCommitsPreparedLocalNodesTogether() throws Exception {
        var connections = new ArrayList<java.sql.Connection>();
        var store = new com.servpp.service.repository.JdbcObjectSnapshotStore(dataSource, value -> "{}");
        var repository = new com.servpp.service.repository.JdbcObjectRepository(store,
                new com.fasterxml.jackson.databind.ObjectMapper());
        DefaultTransactionManager.getInstance().setQuantumAccessAdapter(new QuantumAccessAdapter() {
            public void executeInAccessScope(Runnable action) { fail("Ordinary Saga writes must use their repository, not the quantum resource"); }
            public void commit(TransactionContext context, QuantumEntanglementBoundary boundary) { }
        });
        class Node implements Service, TransactionalService, com.servpp.service.repository.RepositoryWriteRuleProvider {
            public List<com.servpp.service.repository.RepositoryWriteRule> getRepositoryWriteRules() { return List.of(); }
            public void configureRepositoryWrites(java.util.function.Supplier<com.servpp.service.repository.ObjectRepository> supplier) { }
            public void executeInRepositoryScope(Runnable action) { repository.executeInTransaction(action); }
            public Object getRepositoryTransactionResource() { return repository.transactionResourceIdentity(); }
            public String getName() { return "node"; }
            public Object execute(Object... args) { return null; }
            public String getTransactionResourceId() { return "records"; }
            public TransactionMode getTransactionMode() { return TransactionMode.SAGA; }
        }
        var node = new Node();
        IExecutor[] calls = new IExecutor[2];
        for (int i = 0; i < calls.length; i++) {
            final int id = i + 1;
            calls[i] = new IExecutor() {
                public Service getService() { return node; }
                public boolean isPreparedOrdinaryUpdate() { return true; }
                public void execute() {
                    assertEquals(TransactionMode.SAGA, DeclaredTransactionContext.currentMode());
                    repository.verifyWriteTransaction();
                    var connection = JdbcTransactionScope.currentConnection(dataSource).orElseThrow();
                    connections.add(connection);
                    if (id == 2) {
                        try { assertEquals(List.of(), facts()); }
                        catch (Exception e) { throw new IllegalStateException(e); }
                    }
                    insert(id);
                }
            };
        }
        SourceTransactionPlan.execute(group(TransactionPropagation.REQUIRED, calls));
        assertEquals(List.of(1, 2), facts());
        assertEquals(2, connections.size());
        assertSame(connections.get(0), connections.get(1));
    }

    private void failTransaction() { throw new IllegalStateException("business rejected"); }

    @Test public void interleavedRemoteCallDoesNotSplitLocalCommit() throws Exception {
        var order = new ArrayList<String>();
        var first = new LocalNode(dataSource);
        // Different repository instances sharing the same datasource still join one transaction.
        var last = new LocalNode(dataSource);
        var remote = new RemoteNode(dataSource);
        SourceTransactionPlan.execute(group(TransactionPropagation.REQUIRED,
                prepared(first, () -> { order.add("A"); insert(1); }),
                prepared(remote, () -> {
                    order.add("C");
                    try { assertEquals(List.of(1, 2), facts()); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                }),
                prepared(last, () -> { order.add("B"); insert(2); })));
        assertEquals(List.of("A", "B", "C"), order);
    }

    @Test public void failedLocalGroupRollsBackEveryMemberInBothSchedulingModes() throws Exception {
        for (boolean parallel : new boolean[]{false, true}) {
            var node = new LocalNode(dataSource);
            var boundary = new ITransactionGroup() {
                public boolean parallelOrdinaryUpdates() { return parallel; }
                public IExecutor[] getServices() { return new IExecutor[]{
                        prepared(node, () -> insert(1)),
                        prepared(node, () -> { insert(2); failTransaction(); })}; }
            };
            assertThrows(IllegalStateException.class, () -> SourceTransactionPlan.execute(boundary));
            assertEquals(List.of(), facts());
        }
    }

    @Test public void callsToSameRemotePeerKeepSeparateTransactions() throws Exception {
        var connections = new ArrayList<java.sql.Connection>();
        var node = new RemoteNode(dataSource);
        SourceTransactionPlan.execute(group(TransactionPropagation.REQUIRED,
                prepared(node, () -> {
                    connections.add(JdbcTransactionScope.currentConnection(dataSource).orElseThrow()); insert(1);
                }),
                prepared(node, () -> {
                    connections.add(JdbcTransactionScope.currentConnection(dataSource).orElseThrow());
                    try { assertEquals(List.of(1), facts()); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                    insert(2);
                })));
        assertNotSame(connections.get(0), connections.get(1));
        assertEquals(List.of(1, 2), facts());
    }

    @Test public void preparationBarrierPreventsLocalReordering() throws Exception {
        var node = new LocalNode(dataSource);
        var order = new ArrayList<String>();
        SourceTransactionPlan.execute(group(TransactionPropagation.REQUIRED,
                prepared(node, () -> { insert(1); order.add("A"); }),
                action(() -> {
                    try { assertEquals(List.of(1), facts()); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                    order.add("prepare");
                }),
                prepared(node, () -> { insert(2); order.add("B"); })));
        assertEquals(List.of("A", "prepare", "B"), order);
    }

    @Test public void equalLogicalNamesDoNotMergeDistinctPhysicalResources() throws Exception {
        var alias = new JdbcDataSource(); alias.setURL(dataSource.getURL());
        var one = new LocalNode(dataSource);
        var two = new LocalNode(alias);
        SourceTransactionPlan.execute(group(TransactionPropagation.REQUIRED,
                prepared(one, () -> insert(1)),
                prepared(two, () -> {
                    assertTrue(JdbcTransactionScope.currentConnection(alias).isPresent());
                    assertFalse(JdbcTransactionScope.currentConnection(dataSource).isPresent());
                    try { assertEquals(List.of(1), facts()); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                })));
        assertEquals(List.of(1), facts());
    }

    private static class LocalNode implements Service, TransactionalService,
            com.servpp.service.repository.RepositoryWriteRuleProvider {
        private final com.servpp.service.repository.JdbcObjectRepository repository;
        LocalNode(javax.sql.DataSource source) {
            repository = new com.servpp.service.repository.JdbcObjectRepository(
                    new com.servpp.service.repository.JdbcObjectSnapshotStore(source, value -> "{}"),
                    new com.fasterxml.jackson.databind.ObjectMapper());
        }
        public String getName() { return "node"; }
        public Object execute(Object... arguments) { return null; }
        public String getTransactionResourceId() { return "same-logical-name"; }
        public TransactionMode getTransactionMode() { return TransactionMode.SAGA; }
        public List<com.servpp.service.repository.RepositoryWriteRule> getRepositoryWriteRules() { return List.of(); }
        public void configureRepositoryWrites(java.util.function.Supplier<com.servpp.service.repository.ObjectRepository> supplier) { }
        public Object getRepositoryTransactionResource() { return repository.transactionResourceIdentity(); }
        public void executeInRepositoryScope(Runnable action) { repository.executeInTransaction(action); }
    }
    @Test public void groupedLocalCompensationRollsBackAsOneTransaction() throws Exception {
        var services = com.servpp.service.server.DefaultServiceContainer.getInstance();
        var compensationConnections = new ArrayList<java.sql.Connection>();
        class Compensable extends LocalNode implements CompensationRuleProvider {
            Compensable() { super(dataSource); }
            public List<String> getCompensationServiceNames() { return List.of("undo"); }
        }
        services.registerService("undo", new LocalNode(dataSource) {
            public String getName() { return "undo"; }
            public Object execute(Object... arguments) {
                int id = (Integer)arguments[0];
                compensationConnections.add(JdbcTransactionScope.currentConnection(dataSource).orElseThrow());
                delete(id);
                if (id == 1) throw new IllegalStateException("undo temporarily unavailable");
                return null;
            }
        });
        var node = new Compensable();
        var failure = assertThrows(IllegalStateException.class, () -> SourceTransactionPlan.execute(
                group(TransactionPropagation.REQUIRED, prepared(node, 1), prepared(node, 2), action(this::failTransaction))));
        assertEquals(List.of(1, 2), facts());
        assertEquals(2, compensationConnections.size());
        assertSame(compensationConnections.get(0), compensationConnections.get(1));
        assertEquals(TransactionOutcomes.Outcome.UNKNOWN, TransactionOutcomes.outcome(failure));
    }
    @Test public void groupedRecoveryRejectsDifferentResourcesBeforeAnyCompensation() throws Exception {
        var services = com.servpp.service.server.DefaultServiceContainer.getInstance();
        var alias = new JdbcDataSource(); alias.setURL(dataSource.getURL());
        services.registerService("one", new LocalNode(dataSource) {
            public Object execute(Object... arguments) { fail("Must validate the entire compensation group first"); return null; }
        });
        services.registerService("two", new LocalNode(alias));
        insert(1); insert(2);
        var failure = assertThrows(IllegalStateException.class, () -> SourceSagaCompensation.compensate(true, List.of(
                new SourceSagaCompensation.Call("A", List.of("one"), List.of(), new Object[]{1}),
                new SourceSagaCompensation.Call("B", List.of("two"), List.of(), new Object[]{2}))));
        assertTrue(failure.getMessage().startsWith("SOURCE_SAGA_COMPENSATION_RESOURCE_MISMATCH"));
        assertEquals(List.of(1, 2), facts());
    }
    public static final class MutableId {
        private int id;
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
    }
    @Test public void localRecoveryUsesCommittedArgumentsBeforeLaterPreparation() throws Exception {
        var role = new MutableId(); role.setId(11);
        com.servpp.service.runtime.ParticipantIdentity.confirm(role, true);
        class Compensable extends LocalNode implements CompensationRuleProvider {
            Compensable() { super(dataSource); }
            public List<String> getCompensationServiceNames() { return List.of("undo"); }
        }
        com.servpp.service.server.DefaultServiceContainer.getInstance().registerService("undo", new LocalNode(dataSource) {
            public Object execute(Object... arguments) {
                assertTrue(com.servpp.service.runtime.ParticipantIdentity.isIdentified(arguments[0]));
                delete(((MutableId)arguments[0]).getId()); return null;
            }
        });
        var call = new IExecutor() {
            public Service getService() { return new Compensable(); }
            public Object[] getArguments() { return new Object[]{role}; }
            public boolean isPreparedOrdinaryUpdate() { return true; }
            public void execute() { insert(role.getId()); }
        };
        var failure = assertThrows(IllegalStateException.class, () -> SourceTransactionPlan.execute(
                group(TransactionPropagation.REQUIRED, call, action(() -> role.setId(999)), action(this::failTransaction))));
        assertEquals(List.of(), facts());
        assertEquals(TransactionOutcomes.Outcome.ROLLED_BACK, TransactionOutcomes.outcome(failure));
    }
    @Test public void configuredCoordinatorMustExplicitlySupportSourceStages() throws Exception {
        SagaTransactionGroupManager.useCoordinator((scope, calls) -> fail("Legacy execution must not silently flatten source stages"));
        var failure = assertThrows(IllegalStateException.class, () -> BusinessTraceContext.execute("test-unsupported",
                () -> SagaExecutionContext.execute("test", () -> {
                    SourceTransactionPlan.execute(group(TransactionPropagation.REQUIRED, prepared(new LocalNode(dataSource), 1)));
                    return null;
                })));
        assertEquals("SOURCE_SAGA_COORDINATOR_UNSUPPORTED", failure.getMessage());
        assertEquals(List.of(), facts());
    }
    private IExecutor prepared(Service service, int id) {
        return new IExecutor() {
            public Service getService() { return service; }
            public Object[] getArguments() { return new Object[]{id}; }
            public boolean isPreparedOrdinaryUpdate() { return true; }
            public void execute() { insert(id); }
        };
    }
    private void delete(int id) {
        try {
            JdbcTransactionScope.execute(dataSource, connection -> {
                try (var statement = connection.prepareStatement("delete from facts where id=?")) { statement.setInt(1, id); statement.executeUpdate(); }
                return null;
            });
        } catch (SQLException failure) { throw new IllegalStateException(failure); }
    }
    private static final class RemoteNode extends LocalNode implements com.servpp.service.runtime.RemoteGeneratedService {
        RemoteNode(javax.sql.DataSource source) { super(source); }
        @Override public Object getRepositoryTransactionResource() {
            throw new AssertionError("Remote resource metadata must never be used to group RPC calls");
        }
    }
    private IExecutor prepared(Service service, Runnable work) {
        return new IExecutor() {
            public Service getService() { return service; }
            public boolean isPreparedOrdinaryUpdate() { return true; }
            public void execute() { work.run(); }
        };
    }
    private void insert(int id) {
        try {
            JdbcTransactionScope.execute(dataSource, connection -> {
                try (var statement = connection.prepareStatement("insert into facts values(?)")) { statement.setInt(1, id); statement.executeUpdate(); }
                return null;
            });
        } catch (SQLException failure) { throw new IllegalStateException(failure); }
    }
    private List<Integer> facts() throws Exception {
        List<Integer> result = new ArrayList<>();
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery("select id from facts order by id")) {
            while (rows.next()) result.add(rows.getInt(1));
        }
        return result;
    }
    private IExecutor action(Runnable work) {
        return new IExecutor() {
            public Service getService() { return null; }
            public void execute() { work.run(); }
        };
    }
    private ITransactionGroup group(TransactionPropagation propagation, IExecutor... actions) {
        return new ITransactionGroup() {
            public TransactionMode getDeclaredTransactionMode() { return TransactionMode.AT; }
            public TransactionPropagation getPropagation() { return propagation; }
            public IExecutor[] getServices() { return actions; }
        };
    }
}
