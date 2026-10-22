package com.servpp.service.transaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;

import com.servpp.service.AtomicService;
import com.servpp.service.DelegatingService;
import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.repository.CompositeObjectId;
import com.servpp.service.repository.RepositoryHydratingServiceProxy;
import com.servpp.service.scenario.IExecutor;
import com.servpp.service.server.DefaultServiceContainer;

public class DefaultTransactionManagerTest {
    @Test
    public void failedParticipantRollbackPreservesCauseAndUnknownOutcome() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        RecordingParticipant participant = new RecordingParticipant("sample") {
            @Override public void rollback(TransactionContext context, Throwable cause) {
                throw new IllegalStateException("RESTORE_FAILED");
            }
        };
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> manager.execute(List.of(
                executor(new TestService("changeSample", "sample", TransactionMode.AT, true, participant)))));
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("RESTORE_FAILED", failure.getSuppressed()[0].getMessage());
        // A later successful database rollback must not erase a failed participant restore.
        TransactionOutcomes.record(failure, TransactionOutcomes.Outcome.ROLLED_BACK);
        assertEquals(TransactionOutcomes.Outcome.UNKNOWN, TransactionOutcomes.outcome(failure));
    }

    @Test
    public void rejectsMultipleModesForOneResource() {
        DefaultTransactionManager manager = new DefaultTransactionManager();

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> manager.execute(List.of(
                executor(new TestService("debit", "account", TransactionMode.AT, false, new RecordingParticipant("account"))),
                executor(new TestService("reserve", "account", TransactionMode.TCC, false, new RecordingParticipant("account")))
        )));

        assertEquals("Resource account uses multiple transaction modes: AT and TCC", error.getMessage());
    }

    @Test
    public void rollsBackExecutedSagaParticipantsInReverseOrder() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        RecordingParticipant first = new RecordingParticipant("order");
        RecordingParticipant second = new RecordingParticipant("credit");

        assertThrows(IllegalStateException.class, () -> manager.execute(List.of(
                executor(new TestService("createOrder", "order", TransactionMode.SAGA, false, first)),
                executor(new TestService("reserveCredit", "credit", TransactionMode.SAGA, true, second))
        )));

        assertEquals(List.of("begin", "before", "after", "rollback"), first.events);
        assertEquals(List.of("begin", "before", "rollback"), second.events);
    }

    @Test
    public void commitsTccParticipantAfterTryExecute() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        RecordingParticipant participant = new RecordingParticipant("inventory");

        manager.execute(List.of(executor(new TestService("lockInventory", "inventory", TransactionMode.TCC, false, participant))));

        assertEquals(List.of("begin", "before", "after", "commit"), participant.events);
    }

    @Test
    public void tccParticipantUsesExplicitTryConfirmLifecycle() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        RecordingTccParticipant participant = new RecordingTccParticipant("inventory");

        manager.execute(List.of(executor(new TestService("lockInventory", "inventory", TransactionMode.TCC,
                false, participant))));

        assertEquals(List.of("begin", "beforeTry", "afterTry", "confirm"), participant.events);
    }

    @Test
    public void tccParticipantCancelsExecutedTryWhenLaterServiceFails() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        RecordingTccParticipant participant = new RecordingTccParticipant("inventory");
        RecordingParticipant order = new RecordingParticipant("salesOrder");

        assertThrows(IllegalStateException.class, () -> manager.execute(List.of(
                executor(new TestService("lockInventory", "inventory", TransactionMode.TCC, false, participant)),
                executor(new TestService("openOrder", "salesOrder", TransactionMode.AT, true, order))
        )));

        assertEquals(List.of("begin", "beforeTry", "afterTry", "cancel"), participant.events);
        assertEquals(List.of("begin", "before", "rollback"), order.events);
    }

    @Test
    public void tccParticipantCancelsCurrentTryWhenTryExecuteFails() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        RecordingTccParticipant participant = new RecordingTccParticipant("inventory");

        assertThrows(IllegalStateException.class, () -> manager.execute(List.of(
                executor(new TestService("lockInventory", "inventory", TransactionMode.TCC, true, participant))
        )));

        assertEquals(List.of("begin", "beforeTry", "cancel"), participant.events);
    }

    @Test
    public void compensationRuleProviderInvokesCompensationServiceOnRollback() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        MutableBalance balance = new MutableBalance(100);
        List<String> events = new ArrayList<>();
        InMemorySagaLogStore sagaLogStore = new InMemorySagaLogStore();
        SagaLogManager.setStore(sagaLogStore);
        DefaultServiceContainer.getInstance().registerService("releaseCreditForTest",
                new ReleaseCreditForTestService(events));

        assertThrows(IllegalStateException.class, () -> BusinessTraceContext.execute("REQ-COMP-1", () -> {
            manager.execute(List.of(
                executor(new ReserveCreditForTestService(events), balance),
                executor(new TestService("openOrder", "salesOrder", TransactionMode.AT, true,
                        new RecordingParticipant("salesOrder")))
            ));
            return null;
        }));

        assertEquals(100, balance.amount);
        assertEquals(List.of("reserve", "release"), events);
        assertEquals(1, sagaLogStore.findAll().size());
        SagaLogEntry entry = sagaLogStore.findAll().get(0);
        assertEquals("REQ-COMP-1", entry.globalSerialNumber());
        assertEquals("releaseCreditForTest", entry.serviceName());
        assertEquals("COMPENSATION_DONE", entry.status());
        assertEquals("compensated credit", entry.message());
    }

    @Test
    public void compensationFailureIsLoggedSuppressedAndStillRestoresSnapshot() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        MutableBalance balance = new MutableBalance(100);
        List<String> events = new ArrayList<>();
        InMemorySagaLogStore sagaLogStore = new InMemorySagaLogStore();
        SagaLogManager.setStore(sagaLogStore);
        DefaultServiceContainer.getInstance().registerService("releaseCreditForTest",
                new FailingReleaseCreditForTestService(events));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BusinessTraceContext.execute("REQ-COMP-FAIL", () -> {
                    manager.execute(List.of(
                            executor(new ReserveCreditForTestService(events), balance),
                            executor(new TestService("openOrder", "salesOrder", TransactionMode.AT, true,
                                    new RecordingParticipant("salesOrder")))
                    ));
                    return null;
                }));

        assertEquals("openOrder failed", error.getMessage());
        assertEquals(1, error.getSuppressed().length);
        assertEquals("release failed", error.getSuppressed()[0].getMessage());
        assertEquals(100, balance.amount);
        assertEquals(List.of("reserve", "release-failed"), events);
        assertEquals(1, sagaLogStore.findAll().size());
        SagaLogEntry entry = sagaLogStore.findAll().get(0);
        assertEquals("REQ-COMP-FAIL", entry.globalSerialNumber());
        assertEquals("releaseCreditForTest", entry.serviceName());
        assertEquals("COMPENSATION_FAILED", entry.status());
        assertEquals("compensation failed for credit: release failed", entry.message());
    }

    @Test
    public void compensationParticipantAttemptsAllTargetsWhenOneFails() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        MutableBalance balance = new MutableBalance(100);
        List<String> events = new ArrayList<>();
        InMemorySagaLogStore sagaLogStore = new InMemorySagaLogStore();
        SagaLogManager.setStore(sagaLogStore);
        DefaultServiceContainer.getInstance().registerService("releaseCreditForTest",
                new FailingReleaseCreditForTestService(events));
        DefaultServiceContainer.getInstance().registerService("auditCompensationForTest",
                new AuditCompensationForTestService(events));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BusinessTraceContext.execute("REQ-COMP-MULTI", () -> {
                    manager.execute(List.of(
                            executor(new MultiCompensatedReserveCreditForTestService(events), balance),
                            executor(new TestService("openOrder", "salesOrder", TransactionMode.AT, true,
                                    new RecordingParticipant("salesOrder")))
                    ));
                    return null;
                }));

        assertEquals("openOrder failed", error.getMessage());
        assertEquals(1, error.getSuppressed().length);
        assertEquals("release failed", error.getSuppressed()[0].getMessage());
        assertEquals(List.of("reserve", "release-failed", "audit-compensation"), events);
        assertEquals(2, sagaLogStore.findAll().size());
        assertEquals("COMPENSATION_FAILED", sagaLogStore.findAll().get(0).status());
        assertEquals("COMPENSATION_DONE", sagaLogStore.findAll().get(1).status());
        assertEquals("auditCompensationForTest", sagaLogStore.findAll().get(1).serviceName());
    }

    @Test
    public void defaultTccParticipantRecordsBranchStatus() {
        InMemoryTccBranchStore store = new InMemoryTccBranchStore();
        DefaultTccTransactionParticipant.setBranchStore(store);
        DefaultTransactionManager manager = new DefaultTransactionManager();

        manager.execute(List.of(executor(new TestService("tryLockInventory", "inventory",
                TransactionMode.TCC, false,
                new DefaultTccTransactionParticipant("inventory", "tryLockInventory")))));

        assertEquals(1, store.findAll().size());
        TccBranchRecord record = store.findAll().get(0);
        assertEquals("inventory", record.resourceId());
        assertEquals("tryLockInventory", record.actionName());
        assertEquals(TccBranchStatus.CONFIRMED, record.status());
    }

    @Test
    public void defaultTccParticipantRecordsCancelStatus() {
        InMemoryTccBranchStore store = new InMemoryTccBranchStore();
        DefaultTccTransactionParticipant.setBranchStore(store);
        DefaultTransactionManager manager = new DefaultTransactionManager();

        assertThrows(IllegalStateException.class, () -> manager.execute(List.of(
                executor(new TestService("tryLockInventory", "inventory", TransactionMode.TCC, false,
                        new DefaultTccTransactionParticipant("inventory", "tryLockInventory"))),
                executor(new TestService("openOrder", "salesOrder", TransactionMode.AT, true,
                        new RecordingParticipant("salesOrder")))
        )));

        assertEquals(1, store.findAll().size());
        TccBranchRecord record = store.findAll().get(0);
        assertEquals("inventory", record.resourceId());
        assertEquals("tryLockInventory", record.actionName());
        assertEquals(TccBranchStatus.CANCELLED, record.status());
        assertEquals("openOrder failed", record.message());
    }

    @Test
    public void defaultTccParticipantRestoresTryArgumentsWhenCancelled() {
        InMemoryTccBranchStore store = new InMemoryTccBranchStore();
        DefaultTccTransactionParticipant.setBranchStore(store);
        DefaultTransactionManager manager = new DefaultTransactionManager();
        MutableBalance inventory = new MutableBalance(20);

        assertThrows(IllegalStateException.class, () -> manager.execute(List.of(
                executor(new TccSnapshotMutatingService("tryLockInventory", "inventoryTcc", 2,
                        new DefaultTccTransactionParticipant("inventoryTcc", "tryLockInventory")), inventory),
                executor(new TestService("openOrder", "salesOrder", TransactionMode.AT, true,
                        new RecordingParticipant("salesOrder")))
        )));

        assertEquals(20, inventory.amount);
        assertEquals(TccBranchStatus.CANCELLED, store.findAll().get(0).status());
    }

    @Test
    public void defaultTccParticipantRunsConfirmAndCancelActions() {
        InMemoryTccBranchStore store = new InMemoryTccBranchStore();
        DefaultTccTransactionParticipant.setBranchStore(store);
        DefaultTransactionManager manager = new DefaultTransactionManager();
        MutableBalance inventory = new MutableBalance(20);
        MutableBalance confirmed = new MutableBalance(0);
        MutableBalance cancelled = new MutableBalance(0);

        manager.execute(List.of(executor(new TccSnapshotMutatingService("tryLockInventory", "inventoryTcc", 2,
                new DefaultTccTransactionParticipant("inventoryTcc", "tryLockInventory")
                        .onConfirm(() -> confirmed.amount++)
                        .onCancel(() -> cancelled.amount++)), inventory)));

        assertEquals(18, inventory.amount);
        assertEquals(1, confirmed.amount);
        assertEquals(0, cancelled.amount);
        assertEquals(TccBranchStatus.CONFIRMED, store.findAll().get(0).status());

        assertThrows(IllegalStateException.class, () -> manager.execute(List.of(
                executor(new TccSnapshotMutatingService("tryLockInventory", "inventoryTcc", 2,
                        new DefaultTccTransactionParticipant("inventoryTcc", "tryLockInventory")
                                .onConfirm(() -> confirmed.amount++)
                                .onCancel(() -> cancelled.amount++)), inventory),
                executor(new TestService("openOrder", "salesOrder", TransactionMode.AT, true,
                        new RecordingParticipant("salesOrder")))
        )));

        assertEquals(18, inventory.amount);
        assertEquals(1, confirmed.amount);
        assertEquals(1, cancelled.amount);
        assertEquals(2, store.findAll().size());
        assertEquals(TccBranchStatus.CANCELLED, store.findAll().get(1).status());
    }

    @Test
    public void defaultTccParticipantIgnoresRepeatedConfirmAfterConfirmed() {
        InMemoryTccBranchStore store = new InMemoryTccBranchStore();
        DefaultTccTransactionParticipant.setBranchStore(store);
        MutableBalance confirmed = new MutableBalance(0);
        DefaultTccTransactionParticipant participant =
                new DefaultTccTransactionParticipant("inventoryTcc", "tryLockInventory")
                        .onConfirm(() -> confirmed.amount++);
        TransactionContext context = new TransactionContext(Map.of("inventoryTcc", TransactionMode.TCC));

        participant.beforeTry(context);
        participant.afterTry(context);
        participant.confirm(context);
        participant.confirm(context);

        assertEquals(1, confirmed.amount);
        assertEquals(TccBranchStatus.CONFIRMED, store.findAll().get(0).status());
    }

    @Test
    public void defaultTccParticipantIgnoresRepeatedCancelAfterCancelled() {
        InMemoryTccBranchStore store = new InMemoryTccBranchStore();
        DefaultTccTransactionParticipant.setBranchStore(store);
        MutableBalance cancelled = new MutableBalance(0);
        DefaultTccTransactionParticipant participant =
                new DefaultTccTransactionParticipant("inventoryTcc", "tryLockInventory")
                        .onCancel(() -> cancelled.amount++);
        TransactionContext context = new TransactionContext(Map.of("inventoryTcc", TransactionMode.TCC));

        participant.beforeTry(context);
        participant.cancel(context, new IllegalStateException("order failed"));
        participant.cancel(context, new IllegalStateException("retry failure"));

        assertEquals(1, cancelled.amount);
        assertEquals(TccBranchStatus.CANCELLED, store.findAll().get(0).status());
        assertEquals("order failed", store.findAll().get(0).message());
    }

    @Test
    public void defaultTccParticipantRecordsEmptyRollbackWhenCancelArrivesBeforeTry() {
        InMemoryTccBranchStore store = new InMemoryTccBranchStore();
        DefaultTccTransactionParticipant.setBranchStore(store);
        MutableBalance cancelled = new MutableBalance(0);
        DefaultTccTransactionParticipant participant =
                new DefaultTccTransactionParticipant("inventoryTcc", "tryLockInventory")
                        .onCancel(() -> cancelled.amount++);
        TransactionContext context = new TransactionContext(Map.of("inventoryTcc", TransactionMode.TCC));

        participant.cancel(context, new IllegalStateException("cancel before try"));
        participant.cancel(context, new IllegalStateException("retry cancel before try"));

        assertEquals(0, cancelled.amount);
        assertEquals(1, store.findAll().size());
        assertEquals(TccBranchStatus.CANCELLED, store.findAll().get(0).status());
        assertEquals("empty rollback", store.findAll().get(0).message());
    }

    @Test
    public void defaultTccParticipantRejectsTryAfterEmptyRollback() {
        InMemoryTccBranchStore store = new InMemoryTccBranchStore();
        DefaultTccTransactionParticipant.setBranchStore(store);
        DefaultTccTransactionParticipant participant =
                new DefaultTccTransactionParticipant("inventoryTcc", "tryLockInventory");
        TransactionContext context = new TransactionContext(Map.of("inventoryTcc", TransactionMode.TCC));

        participant.cancel(context, new IllegalStateException("cancel before try"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> participant.beforeTry(context));

        assertTrue(error.getMessage().contains("is already CANCELLED"));
        assertEquals(TccBranchStatus.CANCELLED, store.findAll().get(0).status());
    }

    @Test
    public void defaultTccParticipantRejectsOppositeTerminalTccState() {
        InMemoryTccBranchStore store = new InMemoryTccBranchStore();
        DefaultTccTransactionParticipant.setBranchStore(store);
        DefaultTccTransactionParticipant participant =
                new DefaultTccTransactionParticipant("inventoryTcc", "tryLockInventory");
        TransactionContext context = new TransactionContext(Map.of("inventoryTcc", TransactionMode.TCC));

        participant.beforeTry(context);
        participant.afterTry(context);
        participant.confirm(context);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> participant.cancel(context, new IllegalStateException("late cancel")));

        assertTrue(error.getMessage().contains("is already CONFIRMED"));
        assertEquals(TccBranchStatus.CONFIRMED, store.findAll().get(0).status());
    }

    @Test
    public void defaultTccParticipantRejectsConfirmAfterCancelled() {
        InMemoryTccBranchStore store = new InMemoryTccBranchStore();
        DefaultTccTransactionParticipant.setBranchStore(store);
        DefaultTccTransactionParticipant participant =
                new DefaultTccTransactionParticipant("inventoryTcc", "tryLockInventory");
        TransactionContext context = new TransactionContext(Map.of("inventoryTcc", TransactionMode.TCC));

        participant.beforeTry(context);
        participant.cancel(context, new IllegalStateException("order failed"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> participant.confirm(context));

        assertTrue(error.getMessage().contains("is already CANCELLED"));
        assertEquals(TccBranchStatus.CANCELLED, store.findAll().get(0).status());
    }

    @Test
    public void defaultTccParticipantInvokesConfiguredLifecycleServicesWithTryArguments() {
        InMemoryTccBranchStore store = new InMemoryTccBranchStore();
        DefaultTccTransactionParticipant.setBranchStore(store);
        DefaultServiceContainer services = DefaultServiceContainer.getInstance();
        services.registerService("confirmDeductInventory", new ConfirmInventoryService());
        services.registerService("cancelLockInventory", new CancelInventoryService());
        DefaultTransactionManager manager = new DefaultTransactionManager();

        MutableInventory confirmedInventory = new MutableInventory(20, 0);
        manager.execute(List.of(executor(new TccInventoryTryService(
                new DefaultTccTransactionParticipant("inventoryTcc", "tryLockInventory")
                        .confirmWithService("confirmDeductInventory")
                        .cancelWithService("cancelLockInventory")), confirmedInventory, 2)));

        assertEquals(18, confirmedInventory.availableQty);
        assertEquals(0, confirmedInventory.lockedQty);
        assertEquals(TccBranchStatus.CONFIRMED, store.findAll().get(0).status());

        MutableInventory cancelledInventory = new MutableInventory(20, 0);
        assertThrows(IllegalStateException.class, () -> manager.execute(List.of(
                executor(new TccInventoryTryService(
                        new DefaultTccTransactionParticipant("inventoryTcc", "tryLockInventory")
                                .confirmWithService("confirmDeductInventory")
                                .cancelWithService("cancelLockInventory")), cancelledInventory, 2),
                executor(new TestService("openOrder", "salesOrder", TransactionMode.AT, true,
                        new RecordingParticipant("salesOrder")))
        )));

        assertEquals(20, cancelledInventory.availableQty);
        assertEquals(0, cancelledInventory.lockedQty);
        assertEquals(2, store.findAll().size());
        assertEquals(TccBranchStatus.CANCELLED, store.findAll().get(1).status());
    }

    @Test
    public void rollsBackExecutedQuantumParticipantsTogether() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        RecordingParticipant debit = new RecordingParticipant("account.debit");
        RecordingParticipant credit = new RecordingParticipant("account.credit");

        assertThrows(IllegalStateException.class, () -> manager.execute(List.of(
                executor(new TestService("debitBalance", "account.debit", TransactionMode.QUANTUM, false, debit)),
                executor(new TestService("creditBalance", "account.credit", TransactionMode.QUANTUM, true, credit))
        )));

        assertEquals(List.of("begin", "before", "after", "rollback"), debit.events);
        assertEquals(List.of("begin", "before", "rollback"), credit.events);
    }

    @Test
    public void defaultAtomicParticipantRestoresExecutorArguments() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        MutableBalance balance = new MutableBalance(100);

        assertThrows(IllegalStateException.class, () -> manager.execute(List.of(
                executor(new SnapshotMutatingService("reserve", false, 60), balance),
                executor(new SnapshotMutatingService("pay", true, 10), balance)
        )));

        assertEquals(100, balance.amount);
    }

    @Test
    public void chainedParticipantRunsAfterExecuteHooksAndRollsBackInReverseOrder() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        MutableBalance balance = new MutableBalance(100);
        List<String> events = new ArrayList<>();

        assertThrows(IllegalStateException.class, () -> manager.execute(List.of(
                executor(new SnapshotMutatingService("reserve", false, 60), balance),
                executor(new TestService("persistedReserve", "account", TransactionMode.AT, false,
                        new ChainedTransactionParticipant("account",
                                new RecordingParticipant("snapshot") {
                                    @Override
                                    public void rollback(TransactionContext context, Throwable cause) {
                                        events.add("rollbackSnapshot");
                                    }
                                },
                                new AfterExecuteTransactionParticipant("account", args -> {
                                    events.add("after:" + ((MutableBalance) args[0]).amount);
                                }) {
                                    @Override
                                    public void rollback(TransactionContext context, Throwable cause) {
                                        events.add("rollbackAfterHook");
                                    }
                                })), balance),
                executor(new TestService("pay", "payment", TransactionMode.AT, true,
                        new RecordingParticipant("payment")))
        )));

        assertEquals(List.of("after:40", "rollbackAfterHook", "rollbackSnapshot"), events);
    }

    @Test
    public void transactionParticipantProxyKeepsServiceInterfaceAndDecoratesParticipant() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        MutableBalance balance = new MutableBalance(100);
        List<String> events = new ArrayList<>();
        BalanceService service = new SimpleBalanceService(events);
        com.servpp.service.Service proxy = TransactionParticipantProxy.decorate(service,
                participant -> new ChainedTransactionParticipant(participant.getResourceId(), participant,
                        new AfterExecuteTransactionParticipant(participant.getResourceId(),
                                args -> events.add("persist:" + ((MutableBalance) args[0]).amount))));

        assertTrue(proxy instanceof BalanceService);
        assertTrue(proxy instanceof DelegatingService);
        assertSame(service, ((DelegatingService) proxy).getDelegateService());
        assertEquals("manual:100", ((BalanceService) proxy).describe(balance));

        manager.execute(List.of(executor((AtomicService) proxy, balance)));

        assertEquals(90, balance.amount);
        assertEquals(List.of("execute", "after", "persist:90"), events);
    }

    @Test
    public void chainedParticipantKeepsTccLifecycleSemantics() {
        RecordingTccParticipant tcc = new RecordingTccParticipant("inventoryTcc");
        RecordingParticipant snapshot = new RecordingParticipant("inventoryTcc");
        ChainedTransactionParticipant participant =
                new ChainedTransactionParticipant("inventoryTcc", tcc, snapshot);
        TransactionContext context = new TransactionContext(Map.of("inventoryTcc", TransactionMode.TCC),
                new InMemoryQuantumJournalStore());
        TccTransactionStrategy strategy = new TccTransactionStrategy();

        strategy.beforeExecute(context, participant);
        strategy.afterExecute(context, participant);
        strategy.commit(context, participant);

        assertEquals(List.of("beforeTry", "afterTry", "confirm"), tcc.events);
        assertEquals(List.of("before", "after", "commit"), snapshot.events);
    }

    @Test
    public void sagaLogRecorderCapturesCurrentTransactionBoundary() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        MutableBalance source = new MutableBalance(100);
        MutableBalance target = new MutableBalance(20);
        InMemorySagaLogStore store = new InMemorySagaLogStore();

        manager.execute(List.of(
                executor(new QuantumMutatingService("debit", "source", source, -30,
                        new QuantumConservationParticipant("source")
                                .conserve("transfer", "source.amount", () -> source.amount))),
                executor(new QuantumMutatingService("credit", "target", target, 30,
                        new QuantumConservationParticipant("target")
                                .conserve("transfer", "target.amount", () -> target.amount)))
        ));

        SagaLogEntry entry = new SagaLogRecorder(store)
                .append("REQ-SAGA-1", "transferScenario", "DONE", "transfer completed");

        assertEquals(1, store.findAll().size());
        assertEquals(entry, store.findAll().get(0));
        assertEquals("REQ-SAGA-1", entry.globalSerialNumber());
        assertEquals("transferScenario", entry.serviceName());
        assertEquals("DONE", entry.status());
        assertEquals(DefaultTransactionManager.getLastContext().getId(), entry.transactionId());
        assertEquals("source+target", entry.quantumBoundaryServiceId());
    }

    @Test
    public void quantumConservationParticipantCommitsBalancedGroup() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        MutableBalance source = new MutableBalance(100);
        MutableBalance target = new MutableBalance(20);

        manager.execute(List.of(
                executor(new QuantumMutatingService("debit", "source", source, -30,
                        new QuantumConservationParticipant("source")
                                .conserve("transfer", "source.amount", () -> source.amount))),
                executor(new QuantumMutatingService("credit", "target", target, 30,
                        new QuantumConservationParticipant("target")
                                .conserve("transfer", "target.amount", () -> target.amount)))
        ));

        assertEquals(70, source.amount);
        assertEquals(50, target.amount);
    }

    @Test
    public void quantumConservationParticipantRecordsCommittedDeltas() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        MutableBalance source = new MutableBalance(100);
        MutableBalance target = new MutableBalance(20);

        manager.execute(List.of(
                executor(new QuantumMutatingService("debit", "source", source, -30,
                        new QuantumConservationParticipant("source")
                                .conserve("transfer", "source.amount", () -> source.amount))),
                executor(new QuantumMutatingService("credit", "target", target, 30,
                        new QuantumConservationParticipant("target")
                                .conserve("transfer", "target.amount", () -> target.amount)))
        ));

        List<QuantumConservationRecord> records =
                QuantumConservationParticipant.getRecords(DefaultTransactionManager.getLastContext());
        assertEquals(2, records.size());
        assertEquals("transfer", records.get(0).getGroupId());
        assertEquals("source.amount", records.get(0).getAttributeId());
        assertEquals(100, records.get(0).getBeforeValue().intValue());
        assertEquals(-30, records.get(0).getDeltaValue().intValue());
        assertEquals(70, records.get(0).getAfterValue().intValue());
        assertEquals("target.amount", records.get(1).getAttributeId());
        assertEquals(30, records.get(1).getDeltaValue().intValue());
    }

    @Test
    public void transactionContextBuildsSyntheticQuantumEntanglementBoundary() {
        DefaultTransactionManager manager = new DefaultTransactionManager();

        manager.execute(List.of(
                executor(new TestService("reserveCredit", "creditAccounting", TransactionMode.QUANTUM, false,
                        new RecordingParticipant("creditAccounting"))),
                executor(new TestService("deductInventory", "inventoryAccounting", TransactionMode.QUANTUM, false,
                        new RecordingParticipant("inventoryAccounting"))),
                executor(new TestService("markOrderPaid", "salesOrder", TransactionMode.AT, false,
                        new RecordingParticipant("salesOrder")))
        ));

        QuantumEntanglementBoundary boundary =
                DefaultTransactionManager.getLastContext().getQuantumEntanglementBoundary();
        assertEquals("creditAccounting+inventoryAccounting", boundary.getServiceId());
        assertEquals(List.of("creditAccounting", "inventoryAccounting"), boundary.getResourceIds());
    }

    @Test
    public void syntheticQuantumBoundaryOwnsCommittedConservationRecords() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        MutableBalance source = new MutableBalance(100);
        MutableBalance target = new MutableBalance(20);

        manager.execute(List.of(
                executor(new QuantumMutatingService("debit", "source", source, -30,
                        new QuantumConservationParticipant("source")
                                .conserve("transfer", "source.amount", () -> source.amount))),
                executor(new QuantumMutatingService("credit", "target", target, 30,
                        new QuantumConservationParticipant("target")
                                .conserve("transfer", "target.amount", () -> target.amount)))
        ));

        QuantumEntanglementBoundary boundary =
                DefaultTransactionManager.getLastContext().getQuantumEntanglementBoundary();
        assertEquals(2, boundary.getRecords().size());
        assertEquals(QuantumEntanglementBoundary.Status.COMMITTED, boundary.getStatus());
        assertTrue(boundary.isCommitted());
        assertEquals("source.amount", boundary.getRecords().get(0).getAttributeId());
        assertEquals(-30, boundary.getRecords().get(0).getDeltaValue().intValue());
        assertEquals("target.amount", boundary.getRecords().get(1).getAttributeId());
        assertEquals(30, boundary.getRecords().get(1).getDeltaValue().intValue());
    }

    @Test
    public void committedQuantumBoundaryPersistsFrameworkJournalEntries() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        MutableBalance source = new MutableBalance(100);
        MutableBalance target = new MutableBalance(20);

        manager.execute(List.of(
                executor(new QuantumMutatingService("debit", "source", source, -30,
                        new QuantumConservationParticipant("source")
                                .conserve("transfer", "source.amount", () -> source.amount))),
                executor(new QuantumMutatingService("credit", "target", target, 30,
                        new QuantumConservationParticipant("target")
                                .conserve("transfer", "target.amount", () -> target.amount)))
        ));

        TransactionContext context = DefaultTransactionManager.getLastContext();
        List<QuantumJournalEntry> journals = manager.getQuantumJournalStore().findByTransactionId(context.getId());
        assertEquals(2, journals.size());
        assertEquals("source+target", journals.get(0).getBoundaryServiceId());
        assertEquals(QuantumEntanglementBoundary.Status.COMMITTED, journals.get(0).getStatus());
        assertEquals("transfer", journals.get(0).getGroupId());
        assertEquals("source.amount", journals.get(0).getAttributeId());
        assertEquals(-30, journals.get(0).getDeltaValue().intValue());
        assertEquals(1, journals.get(1).getSequence());
        assertEquals("target.amount", journals.get(1).getAttributeId());
        assertEquals(30, journals.get(1).getDeltaValue().intValue());
    }

    @Test
    public void committedQuantumBoundaryPayloadReversesExactlyOnce() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        JdbcQuantumJournalStore store = new JdbcQuantumJournalStore(h2DataSource());
        manager.setQuantumJournalStore(store);
        store.seedCurrentValue("transfer", "source.amount", java.math.BigDecimal.valueOf(100));
        store.seedCurrentValue("transfer", "target.amount", java.math.BigDecimal.ZERO);
        MutableBalance source = new MutableBalance(100);
        MutableBalance target = new MutableBalance(0);

        BusinessTraceContext.execute("GSN-QUANTUM-REVERSAL-1", () -> {
            manager.execute(List.of(
                    executor(new QuantumMutatingService("debit", "source", source, -30,
                            new QuantumConservationParticipant("source")
                                    .conserve("transfer", "source.amount", () -> source.amount))),
                    executor(new QuantumMutatingService("credit", "target", target, 30,
                            new QuantumConservationParticipant("target")
                                    .conserve("transfer", "target.amount", () -> target.amount)))));
            return null;
        });
        String payload = manager.committedQuantumBoundaryPayload().orElseThrow();
        String originalTransactionId = DefaultTransactionManager.getLastContext().getId();

        manager.reverseCommittedQuantumBoundary(payload);
        manager.reverseCommittedQuantumBoundary(payload);

        assertEquals(java.math.BigDecimal.valueOf(100).setScale(12),
                store.findCurrentValue("source.amount").orElseThrow());
        assertEquals(java.math.BigDecimal.ZERO.setScale(12),
                store.findCurrentValue("target.amount").orElseThrow());
        assertEquals(2, store.findByTransactionId(originalTransactionId).size());
        List<QuantumJournalEntry> reversalEntries =
                store.findByTransactionId(originalTransactionId + ":reversal");
        assertEquals(2, reversalEntries.size());
        assertEquals(QuantumEntanglementBoundary.Status.ROLLED_BACK,
                reversalEntries.get(0).getStatus());
        assertEquals(30, reversalEntries.get(0).getDeltaValue().intValue());
        assertEquals(-30, reversalEntries.get(1).getDeltaValue().intValue());
    }

    @Test
    public void inMemoryQuantumJournalStoreIgnoresDuplicateTransactionAppend() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        MutableBalance source = new MutableBalance(100);
        MutableBalance target = new MutableBalance(20);

        manager.execute(List.of(
                executor(new QuantumMutatingService("debit", "source", source, -30,
                        new QuantumConservationParticipant("source")
                                .conserve("transfer", "source.amount", () -> source.amount))),
                executor(new QuantumMutatingService("credit", "target", target, 30,
                        new QuantumConservationParticipant("target")
                                .conserve("transfer", "target.amount", () -> target.amount)))
        ));

        TransactionContext context = DefaultTransactionManager.getLastContext();
        QuantumEntanglementBoundary boundary = context.getQuantumEntanglementBoundary();
        manager.getQuantumJournalStore().append(context, boundary);

        List<QuantumJournalEntry> journals = manager.getQuantumJournalStore().findByTransactionId(context.getId());
        assertEquals(2, journals.size());
        assertEquals("source.amount", journals.get(0).getAttributeId());
        assertEquals("target.amount", journals.get(1).getAttributeId());
    }

    @Test
    public void inMemoryQuantumJournalStoreRejectsIdempotencyKeyReuseWithDifferentBoundary() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        MutableBalance source = new MutableBalance(100);
        MutableBalance target = new MutableBalance(20);

        manager.execute(List.of(
                executor(new QuantumMutatingService("debit", "source", source, -30,
                        new QuantumConservationParticipant("source")
                                .conserve("transfer", "source.amount", () -> source.amount))),
                executor(new QuantumMutatingService("credit", "target", target, 30,
                        new QuantumConservationParticipant("target")
                                .conserve("transfer", "target.amount", () -> target.amount)))
        ));

        TransactionContext context = DefaultTransactionManager.getLastContext();
        QuantumEntanglementBoundary conflicting = new QuantumEntanglementBoundary(
                "different-boundary", context.getQuantumEntanglementBoundary().getResourceIds());
        for (QuantumConservationRecord record : context.getQuantumEntanglementBoundary().getRecords()) {
            conflicting.addRecord(record);
        }
        conflicting.markCommitted();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> manager.getQuantumJournalStore().append(context, conflicting));

        assertTrue(error.getMessage().contains(
                "reused an idempotency key with different boundary or conservation records"));
        assertEquals(2, manager.getQuantumJournalStore().findByTransactionId(context.getId()).size());
    }

    @Test
    public void jdbcQuantumJournalStorePersistsJournalAndCurrentValuesInLocalDatabase() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        JdbcQuantumJournalStore store = new JdbcQuantumJournalStore(h2DataSource());
        manager.setQuantumJournalStore(store);
        MutableBalance source = new MutableBalance(100);
        MutableBalance target = new MutableBalance(20);

        manager.execute(List.of(
                executor(new QuantumMutatingService("debit", "source", source, -30,
                        new QuantumConservationParticipant("source")
                                .conserve("transfer", "source.amount", () -> source.amount))),
                executor(new QuantumMutatingService("credit", "target", target, 30,
                        new QuantumConservationParticipant("target")
                                .conserve("transfer", "target.amount", () -> target.amount)))
        ));

        TransactionContext context = DefaultTransactionManager.getLastContext();
        List<QuantumJournalEntry> journals = store.findByTransactionId(context.getId());
        assertEquals(2, journals.size());
        assertEquals("source+target", journals.get(0).getBoundaryServiceId());
        assertEquals(QuantumEntanglementBoundary.Status.COMMITTED, journals.get(0).getStatus());
        List<QuantumJournalEntry> allJournals = store.findAll();
        assertEquals(2, allJournals.size());
        assertEquals(context.getId(), allJournals.get(0).getTransactionId());
        assertEquals(0, allJournals.get(0).getSequence());
        assertEquals("source.amount", allJournals.get(0).getAttributeId());
        assertEquals(1, allJournals.get(1).getSequence());
        assertEquals("target.amount", allJournals.get(1).getAttributeId());
        assertEquals(70, store.findCurrentValue("source.amount").orElseThrow().intValue());
        assertEquals(50, store.findCurrentValue("target.amount").orElseThrow().intValue());

        List<QuantumAttributeValue> currentValues = store.findCurrentValues();
        assertEquals(2, currentValues.size());
        assertEquals("source.amount", currentValues.get(0).attributeId());
        assertEquals(JdbcQuantumJournalStore.DEFAULT_OBJECT_ID, currentValues.get(0).objectId());
        assertEquals("source+target", currentValues.get(0).boundaryServiceId());
        assertEquals(context.getId(), currentValues.get(0).transactionId());
        assertEquals(70, currentValues.get(0).currentValue().intValue());
        assertEquals("target.amount", currentValues.get(1).attributeId());
        assertEquals(JdbcQuantumJournalStore.DEFAULT_OBJECT_ID, currentValues.get(1).objectId());
        assertEquals(50, currentValues.get(1).currentValue().intValue());
    }

    @Test
    public void jdbcQuantumJournalStoreStoresCurrentValuesByAttributeAndObjectId() {
        JdbcQuantumJournalStore store = new JdbcQuantumJournalStore(h2DataSource());

        store.seedCurrentValue("inventoryAccounting", "Inventory.availableQty", "store-a|sku-1",
                java.math.BigDecimal.valueOf(20));
        store.seedCurrentValue("inventoryAccounting", "Inventory.availableQty", "store-b|sku-1",
                java.math.BigDecimal.valueOf(7));
        store.seedCurrentValue("inventoryAccounting", "Inventory.availableQty", java.math.BigDecimal.valueOf(99));

        assertEquals(20, store.findCurrentValue("Inventory.availableQty", "store-a|sku-1")
                .orElseThrow().intValue());
        assertEquals(7, store.findCurrentValue("Inventory.availableQty", "store-b|sku-1")
                .orElseThrow().intValue());
        assertEquals(99, store.findCurrentValue("Inventory.availableQty").orElseThrow().intValue());

        List<QuantumAttributeValue> currentValues = store.findCurrentValues();
        assertEquals(3, currentValues.size());
        assertEquals(JdbcQuantumJournalStore.DEFAULT_OBJECT_ID, currentValues.get(0).objectId());
        assertEquals("store-a|sku-1", currentValues.get(1).objectId());
        assertEquals("store-b|sku-1", currentValues.get(2).objectId());
    }

    @Test
    public void jdbcQuantumJournalStoreIgnoresDuplicateTransactionAppend() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        JdbcQuantumJournalStore store = new JdbcQuantumJournalStore(h2DataSource());
        manager.setQuantumJournalStore(store);
        MutableBalance source = new MutableBalance(100);
        MutableBalance target = new MutableBalance(20);

        manager.execute(List.of(
                executor(new QuantumMutatingService("debit", "source", source, -30,
                        new QuantumConservationParticipant("source")
                                .conserve("transfer", "source.amount", () -> source.amount))),
                executor(new QuantumMutatingService("credit", "target", target, 30,
                        new QuantumConservationParticipant("target")
                                .conserve("transfer", "target.amount", () -> target.amount)))
        ));

        TransactionContext context = DefaultTransactionManager.getLastContext();
        store.append(context, context.getQuantumEntanglementBoundary());

        List<QuantumJournalEntry> journals = store.findByTransactionId(context.getId());
        assertEquals(2, journals.size());
        assertEquals(70, store.findCurrentValue("source.amount").orElseThrow().intValue());
        assertEquals(50, store.findCurrentValue("target.amount").orElseThrow().intValue());
    }

    @Test
    public void jdbcQuantumJournalStoreRejectsIdempotencyKeyReuseWithDifferentConservationRecords() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        JdbcQuantumJournalStore store = new JdbcQuantumJournalStore(h2DataSource());
        manager.setQuantumJournalStore(store);
        MutableBalance source = new MutableBalance(100);
        MutableBalance target = new MutableBalance(20);

        manager.execute(List.of(
                executor(new QuantumMutatingService("debit", "source", source, -30,
                        new QuantumConservationParticipant("source")
                                .conserve("transfer", "source.amount", () -> source.amount))),
                executor(new QuantumMutatingService("credit", "target", target, 30,
                        new QuantumConservationParticipant("target")
                                .conserve("transfer", "target.amount", () -> target.amount)))
        ));

        TransactionContext context = DefaultTransactionManager.getLastContext();
        QuantumEntanglementBoundary conflicting = new QuantumEntanglementBoundary(
                context.getQuantumEntanglementBoundary().getServiceId(),
                context.getQuantumEntanglementBoundary().getResourceIds());
        conflicting.addRecord(new QuantumConservationRecord(
                "transfer", "source.amount", JdbcQuantumJournalStore.DEFAULT_OBJECT_ID,
                java.math.BigDecimal.valueOf(100), java.math.BigDecimal.valueOf(-40),
                java.math.BigDecimal.valueOf(60)));
        conflicting.addRecord(new QuantumConservationRecord(
                "transfer", "target.amount", JdbcQuantumJournalStore.DEFAULT_OBJECT_ID,
                java.math.BigDecimal.valueOf(20), java.math.BigDecimal.valueOf(40),
                java.math.BigDecimal.valueOf(60)));
        conflicting.markCommitted();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> store.append(context, conflicting));

        assertTrue(error.getMessage().contains(
                "reused an idempotency key with different boundary or conservation records"));
        assertEquals(2, store.findByTransactionId(context.getId()).size());
        assertEquals(70, store.findCurrentValue("source.amount").orElseThrow().intValue());
        assertEquals(50, store.findCurrentValue("target.amount").orElseThrow().intValue());
    }

    @Test
    public void jdbcQuantumJournalStoreRejectsStaleBeforeValueAndRollsBackObjects() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        JdbcQuantumJournalStore store = new JdbcQuantumJournalStore(h2DataSource());
        store.seedCurrentValue("transfer", "source.amount", java.math.BigDecimal.valueOf(99));
        store.seedCurrentValue("transfer", "target.amount", java.math.BigDecimal.valueOf(20));
        manager.setQuantumJournalStore(store);
        MutableBalance source = new MutableBalance(100);
        MutableBalance target = new MutableBalance(20);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> manager.execute(List.of(
                executor(new QuantumMutatingService("debit", "source", source, -30,
                        new QuantumConservationParticipant("source")
                                .conserve("transfer", "source.amount", () -> source.amount)), source),
                executor(new QuantumMutatingService("credit", "target", target, 30,
                        new QuantumConservationParticipant("target")
                                .conserve("transfer", "target.amount", () -> target.amount)), target)
        )));

        assertEquals("quantum attribute source.amount object GLOBAL current value 99.000000000000"
                + " does not match before value 100", error.getMessage());
        assertEquals(100, source.amount);
        assertEquals(20, target.amount);
        assertEquals(0, store.findByTransactionId(DefaultTransactionManager.getLastContext().getId()).size());
        assertEquals(99, store.findCurrentValue("source.amount").orElseThrow().intValue());
        assertEquals(20, store.findCurrentValue("target.amount").orElseThrow().intValue());
    }

    @Test
    public void jdbcQuantumParticipantLoadsCurrentValuesBeforeGeneratedServiceMutatesObjects() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        JdbcQuantumJournalStore store = new JdbcQuantumJournalStore(h2DataSource());
        store.seedCurrentValue("transfer", "source.amount", java.math.BigDecimal.valueOf(100));
        store.seedCurrentValue("transfer", "target.amount", java.math.BigDecimal.valueOf(20));
        manager.setQuantumJournalStore(store);
        MutableBalance source = new MutableBalance(1);
        MutableBalance target = new MutableBalance(2);

        manager.execute(List.of(
                executor(new QuantumMutatingService("debit", "source", source, -30,
                        new QuantumConservationParticipant("source")
                                .conserveArgument(0, "transfer", "source.amount", "amount")), source),
                executor(new QuantumMutatingService("credit", "target", target, 30,
                        new QuantumConservationParticipant("target")
                                .conserveArgument(0, "transfer", "target.amount", "amount")), target)
        ));

        assertEquals(70, source.amount);
        assertEquals(50, target.amount);
        assertEquals(70, store.findCurrentValue("source.amount").orElseThrow().intValue());
        assertEquals(50, store.findCurrentValue("target.amount").orElseThrow().intValue());
    }

    @Test
    public void jdbcQuantumParticipantUsesRoleIdentityObjectIdForCurrentValues() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        JdbcQuantumJournalStore store = new JdbcQuantumJournalStore(h2DataSource());
        String mainBookObjectId = CompositeObjectId.encode(List.of("MAIN", "SKU-BOOK-001"));
        String outletBookObjectId = CompositeObjectId.encode(List.of("OUTLET", "SKU-BOOK-001"));
        store.seedCurrentValue("inventoryAccounting", "Inventory.availableQty", mainBookObjectId,
                java.math.BigDecimal.valueOf(20));
        store.seedCurrentValue("inventoryAccounting", "Inventory.availableQty", outletBookObjectId,
                java.math.BigDecimal.valueOf(7));
        store.seedCurrentValue("inventoryAccounting", "SalesItem.orderQty", java.math.BigDecimal.ZERO);
        manager.setQuantumJournalStore(store);
        MutableInventory inventory = new MutableInventory(1, 0);
        MutableBalance salesItem = new MutableBalance(0);
        RepositoryHydratingServiceProxy.RoleIdentityResolver resolver = (roleType, arguments) -> {
            if (!"Inventory".equals(roleType)) {
                return Optional.empty();
            }
            return Optional.of(new RepositoryHydratingServiceProxy.RoleIdentity(
                    "Inventory",
                    List.of("Store.name", "Goods.skuId"),
                    List.of("MAIN", "SKU-BOOK-001"),
                    List.of("store_name", "goods_skuid"),
                    "PERFORMER",
                    true));
        };

        manager.execute(List.of(
                executor(new QuantumInventoryMutatingService("deductInventory", "inventoryAccounting",
                        inventory, -2,
                        new QuantumConservationParticipant("inventoryAccounting")
                                .resolveRoleIdentities(resolver)
                                .conserveArgument(0, "inventoryAccounting", "Inventory.availableQty",
                                        "availableQty")), inventory),
                executor(new QuantumMutatingService("addSalesItem", "salesItemAccounting", salesItem, 2,
                        new QuantumConservationParticipant("salesItemAccounting")
                                .conserveArgument(0, "inventoryAccounting", "SalesItem.orderQty",
                                        "amount")), salesItem)
        ));

        assertEquals(18, inventory.availableQty);
        assertEquals(2, salesItem.amount);
        assertEquals(18, store.findCurrentValue("Inventory.availableQty", mainBookObjectId)
                .orElseThrow().intValue());
        assertEquals(7, store.findCurrentValue("Inventory.availableQty", outletBookObjectId)
                .orElseThrow().intValue());
        assertEquals(2, store.findCurrentValue("SalesItem.orderQty").orElseThrow().intValue());
        List<QuantumJournalEntry> entries = store.findByTransactionId(DefaultTransactionManager.getLastContext()
                .getId());
        assertEquals(2, entries.size());
        assertEquals("Inventory.availableQty", entries.get(0).getAttributeId());
        assertEquals(mainBookObjectId, entries.get(0).getObjectId());
        assertEquals("SalesItem.orderQty", entries.get(1).getAttributeId());
        assertEquals(JdbcQuantumJournalStore.DEFAULT_OBJECT_ID, entries.get(1).getObjectId());
    }

    @Test
    public void jdbcQuantumTransactionKeepsServiceExecutionInsideJdbcScope() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        JdbcDataSource dataSource = h2DataSource();
        JdbcQuantumJournalStore store = new JdbcQuantumJournalStore(dataSource);
        store.seedCurrentValue("transfer", "source.amount", java.math.BigDecimal.valueOf(100));
        store.seedCurrentValue("transfer", "target.amount", java.math.BigDecimal.valueOf(20));
        manager.setQuantumJournalStore(store);
        MutableBalance source = new MutableBalance(1);
        MutableBalance target = new MutableBalance(2);

        manager.execute(List.of(
                executor(new ScopedQuantumMutatingService("debit", "source", source, -30, dataSource,
                        new QuantumConservationParticipant("source")
                                .conserveArgument(0, "transfer", "source.amount", "amount")), source),
                executor(new ScopedQuantumMutatingService("credit", "target", target, 30, dataSource,
                        new QuantumConservationParticipant("target")
                                .conserveArgument(0, "transfer", "target.amount", "amount")), target)
        ));

        assertEquals(70, source.amount);
        assertEquals(50, target.amount);
        assertEquals(70, store.findCurrentValue("source.amount").orElseThrow().intValue());
        assertEquals(50, store.findCurrentValue("target.amount").orElseThrow().intValue());
    }

    @Test
    public void transactionContextDoesNotBuildQuantumBoundaryWithoutQuantumResources() {
        DefaultTransactionManager manager = new DefaultTransactionManager();

        manager.execute(List.of(executor(new TestService("markOrderPaid", "salesOrder",
                TransactionMode.AT, false, new RecordingParticipant("salesOrder")))));

        assertEquals(null, DefaultTransactionManager.getLastContext().getQuantumEntanglementBoundary());
    }

    @Test
    public void quantumConservationParticipantRejectsUnbalancedGroupAndRestoresArguments() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        MutableBalance source = new MutableBalance(100);
        MutableBalance target = new MutableBalance(20);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> manager.execute(List.of(
                executor(new QuantumMutatingService("debit", "source", source, -30,
                        new QuantumConservationParticipant("source")
                                .conserve("transfer", "source.amount", () -> source.amount)), source),
                executor(new QuantumMutatingService("credit", "target", target, 25,
                        new QuantumConservationParticipant("target")
                                .conserve("transfer", "target.amount", () -> target.amount)), target)
        )));

        assertEquals("quantum group transfer is not conserved: -5", error.getMessage());
        assertEquals(100, source.amount);
        assertEquals(20, target.amount);
        assertEquals(QuantumEntanglementBoundary.Status.ROLLED_BACK,
                DefaultTransactionManager.getLastContext().getQuantumEntanglementBoundary().getStatus());
        assertEquals(0, manager.getQuantumJournalStore()
                .findByTransactionId(DefaultTransactionManager.getLastContext().getId()).size());
    }

    @Test
    public void quantumConservationParticipantRequiresAtLeastTwoAttributes() {
        DefaultTransactionManager manager = new DefaultTransactionManager();
        MutableBalance source = new MutableBalance(100);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> manager.execute(List.of(
                executor(new QuantumMutatingService("debit", "source", source, -30,
                        new QuantumConservationParticipant("source")
                                .conserve("transfer", "source.amount", () -> source.amount)), source)
        )));

        assertEquals("quantum group transfer must contain at least two quantum attributes", error.getMessage());
        assertEquals(100, source.amount);
    }

    private static IExecutor executor(AtomicService service) {
        return new IExecutor() {
            @Override
            public AtomicService getService() {
                return service;
            }

            @Override
            public void execute() {
                service.execute();
            }
        };
    }

    private static IExecutor executor(AtomicService service, Object... arguments) {
        return new IExecutor() {
            @Override
            public AtomicService getService() {
                return service;
            }

            @Override
            public Object[] getArguments() {
                return arguments;
            }

            @Override
            public void execute() {
                service.execute(arguments);
            }
        };
    }

    private static JdbcDataSource h2DataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:quantum-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }

    private record TestService(String name, String resourceId, TransactionMode mode, boolean fail,
                               TransactionParticipant participant) implements AtomicService {
        @Override
        public Object execute(Object... role) {
            if (fail) {
                throw new IllegalStateException(name + " failed");
            }
            return null;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getTransactionResourceId() {
            return resourceId;
        }

        @Override
        public TransactionMode getTransactionMode() {
            return mode;
        }

        @Override
        public TransactionParticipant getTransactionParticipant() {
            return participant;
        }
    }

    private static class ReserveCreditForTestService implements AtomicService, CompensationRuleProvider {
        private final List<String> events;

        ReserveCreditForTestService(List<String> events) {
            this.events = events;
        }

        @Override
        public Object execute(Object... role) {
            reserveCreditForTest((MutableBalance) role[0]);
            return null;
        }

        public void reserveCreditForTest(MutableBalance balance) {
            balance.amount -= 30;
            events.add("reserve");
        }

        @Override
        public String getName() {
            return "reserveCreditForTest";
        }

        @Override
        public String getTransactionResourceId() {
            return "credit";
        }

        @Override
        public List<String> getCompensationServiceNames() {
            return List.of("releaseCreditForTest");
        }
    }

    private static class ReleaseCreditForTestService implements AtomicService {
        protected final List<String> events;

        ReleaseCreditForTestService(List<String> events) {
            this.events = events;
        }

        @Override
        public Object execute(Object... role) {
            releaseCreditForTest((MutableBalance) role[0]);
            return null;
        }

        public void releaseCreditForTest(MutableBalance balance) {
            balance.amount += 30;
            events.add("release");
        }

        @Override
        public String getName() {
            return "releaseCreditForTest";
        }
    }

    private static class FailingReleaseCreditForTestService extends ReleaseCreditForTestService {
        FailingReleaseCreditForTestService(List<String> events) {
            super(events);
        }

        @Override
        public void releaseCreditForTest(MutableBalance balance) {
            events.add("release-failed");
            throw new IllegalStateException("release failed");
        }
    }

    private static class MultiCompensatedReserveCreditForTestService extends ReserveCreditForTestService {
        MultiCompensatedReserveCreditForTestService(List<String> events) {
            super(events);
        }

        @Override
        public List<String> getCompensationServiceNames() {
            return List.of("releaseCreditForTest", "auditCompensationForTest");
        }
    }

    private static class AuditCompensationForTestService implements AtomicService {
        private final List<String> events;

        AuditCompensationForTestService(List<String> events) {
            this.events = events;
        }

        @Override
        public Object execute(Object... role) {
            events.add("audit-compensation");
            return null;
        }

        @Override
        public String getName() {
            return "auditCompensationForTest";
        }
    }

    private static class RecordingParticipant implements TransactionParticipant {
        private final String resourceId;
        private final List<String> events = new ArrayList<>();

        RecordingParticipant(String resourceId) {
            this.resourceId = resourceId;
        }

        @Override
        public String getResourceId() {
            return resourceId;
        }

        @Override
        public void begin(TransactionContext context) {
            events.add("begin");
        }

        @Override
        public void beforeExecute(TransactionContext context) {
            events.add("before");
        }

        @Override
        public void afterExecute(TransactionContext context) {
            events.add("after");
        }

        @Override
        public void commit(TransactionContext context) {
            events.add("commit");
        }

        @Override
        public void rollback(TransactionContext context, Throwable cause) {
            events.add("rollback");
        }
    }

    private interface BalanceService extends AtomicService {
        String describe(MutableBalance balance);
    }

    private static class SimpleBalanceService implements BalanceService {
        private final List<String> events;

        private SimpleBalanceService(List<String> events) {
            this.events = events;
        }

        @Override
        public Object execute(Object... role) {
            ((MutableBalance) role[0]).amount -= 10;
            events.add("execute");
            return null;
        }

        @Override
        public String getName() {
            return "balanceService";
        }

        @Override
        public TransactionParticipant getTransactionParticipant() {
            return new AfterExecuteTransactionParticipant(getTransactionResourceId(),
                    args -> events.add("after"));
        }

        @Override
        public String describe(MutableBalance balance) {
            return "manual:" + balance.amount;
        }
    }

    private static class RecordingTccParticipant implements TccTransactionParticipant {
        private final String resourceId;
        private final List<String> events = new ArrayList<>();

        RecordingTccParticipant(String resourceId) {
            this.resourceId = resourceId;
        }

        @Override
        public String getResourceId() {
            return resourceId;
        }

        @Override
        public void begin(TransactionContext context) {
            events.add("begin");
        }

        @Override
        public void beforeTry(TransactionContext context) {
            events.add("beforeTry");
        }

        @Override
        public void afterTry(TransactionContext context) {
            events.add("afterTry");
        }

        @Override
        public void confirm(TransactionContext context) {
            events.add("confirm");
        }

        @Override
        public void cancel(TransactionContext context, Throwable cause) {
            events.add("cancel");
        }
    }

    private record SnapshotMutatingService(String name, boolean fail, int amount) implements AtomicService {
        @Override
        public Object execute(Object... role) {
            MutableBalance balance = (MutableBalance) role[0];
            balance.amount -= amount;
            if (fail) {
                throw new IllegalStateException(name + " failed");
            }
            return null;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private record TccSnapshotMutatingService(String name, String resourceId, int amount,
                                              TransactionParticipant participant) implements AtomicService {
        @Override
        public Object execute(Object... role) {
            MutableBalance balance = (MutableBalance) role[0];
            balance.amount -= amount;
            return null;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getTransactionResourceId() {
            return resourceId;
        }

        @Override
        public TransactionMode getTransactionMode() {
            return TransactionMode.TCC;
        }

        @Override
        public TransactionParticipant getTransactionParticipant() {
            return participant;
        }
    }

    private record TccInventoryTryService(TransactionParticipant participant) implements AtomicService {
        @Override
        public Object execute(Object... role) {
            MutableInventory inventory = (MutableInventory) role[0];
            int quantity = (Integer) role[1];
            inventory.availableQty -= quantity;
            inventory.lockedQty += quantity;
            return null;
        }

        @Override
        public String getName() {
            return "tryLockInventory";
        }

        @Override
        public String getTransactionResourceId() {
            return "inventoryTcc";
        }

        @Override
        public TransactionMode getTransactionMode() {
            return TransactionMode.TCC;
        }

        @Override
        public TransactionParticipant getTransactionParticipant() {
            return participant;
        }
    }

    private static class ConfirmInventoryService implements AtomicService {
        @Override
        public Object execute(Object... role) {
            return confirmDeductInventory((MutableInventory) role[0], (Integer) role[1]);
        }

        public Object confirmDeductInventory(MutableInventory inventory, Integer quantity) {
            inventory.lockedQty -= quantity;
            return null;
        }

        @Override
        public String getName() {
            return "confirmDeductInventory";
        }
    }

    private static class CancelInventoryService implements AtomicService {
        @Override
        public Object execute(Object... role) {
            return cancelLockInventory((MutableInventory) role[0], (Integer) role[1]);
        }

        public Object cancelLockInventory(MutableInventory inventory, Integer quantity) {
            inventory.availableQty += quantity;
            inventory.lockedQty -= quantity;
            return null;
        }

        @Override
        public String getName() {
            return "cancelLockInventory";
        }
    }

    private record QuantumMutatingService(String name, String resourceId, MutableBalance balance, int amount,
                                          TransactionParticipant participant) implements AtomicService {
        @Override
        public Object execute(Object... role) {
            balance.amount += amount;
            return null;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getTransactionResourceId() {
            return resourceId;
        }

        @Override
        public TransactionMode getTransactionMode() {
            return TransactionMode.QUANTUM;
        }

        @Override
        public TransactionParticipant getTransactionParticipant() {
            return participant;
        }
    }

    private record QuantumInventoryMutatingService(String name, String resourceId, MutableInventory inventory,
                                                   int amount, TransactionParticipant participant)
            implements AtomicService {
        @Override
        public Object execute(Object... role) {
            inventory.availableQty += amount;
            return null;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getTransactionResourceId() {
            return resourceId;
        }

        @Override
        public TransactionMode getTransactionMode() {
            return TransactionMode.QUANTUM;
        }

        @Override
        public TransactionParticipant getTransactionParticipant() {
            return participant;
        }
    }

    private record ScopedQuantumMutatingService(String name, String resourceId, MutableBalance balance, int amount,
                                                JdbcDataSource dataSource,
                                                TransactionParticipant participant) implements AtomicService {
        @Override
        public Object execute(Object... role) {
            assertTrue(JdbcTransactionScope.currentConnection(dataSource).isPresent());
            balance.amount += amount;
            return null;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getTransactionResourceId() {
            return resourceId;
        }

        @Override
        public TransactionMode getTransactionMode() {
            return TransactionMode.QUANTUM;
        }

        @Override
        public TransactionParticipant getTransactionParticipant() {
            return participant;
        }
    }

    public static class MutableBalance {
        public int amount;

        public MutableBalance() {
        }

        MutableBalance(int amount) {
            this.amount = amount;
        }
    }

    public static class MutableInventory {
        public int availableQty;
        public int lockedQty;

        public MutableInventory() {
        }

        MutableInventory(int availableQty, int lockedQty) {
            this.availableQty = availableQty;
            this.lockedQty = lockedQty;
        }
    }
}
