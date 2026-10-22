package com.servpp.service.transaction;

import com.servpp.service.scenario.*;
import com.servpp.service.runtime.*;
import java.util.*;

/** Source boundaries compile to quantum-first stages. Committed stages recover by reversal/compensation. */
public final class SourceTransactionPlan {
    private record Stage(TransactionMode mode, List<IExecutor> calls) { }
    private record Committed(Stage stage, String quantumPayload, TransactionContext context,
                             List<SourceSagaCompensation.Call> compensation) { }
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    static boolean isActive() { return DEPTH.get() > 0; }
    private SourceTransactionPlan() { }
    static Runnable bind(Runnable action) {
        int depth = DEPTH.get();
        boolean initialization = DatabaseInitializationContext.isActive();
        return () -> {
            int previous = DEPTH.get(); DEPTH.set(depth);
            try { if (initialization) DatabaseInitializationContext.execute(action); else action.run(); }
            finally { if (previous == 0) DEPTH.remove(); else DEPTH.set(previous); }
        };
    }
    private static boolean quantum(IExecutor call) {
        return call.isQuantumUpdate() || call.getService() instanceof QuantumServiceMetadataProvider metadata
                && !metadata.getQuantumAttributeIds().isEmpty();
    }

    public static void execute(ITransactionGroup boundary) {
        int previous = DEPTH.get(); DEPTH.set(previous + 1);
        try { executePlan(boundary); }
        finally { if (previous == 0) DEPTH.remove(); else DEPTH.set(previous); }
    }
    private static void executePlan(ITransactionGroup boundary) {
        if (boundary.getPropagation() == TransactionPropagation.NESTED)
            throw new IllegalStateException("TRANSACTION_PLAN_NESTED_UNSUPPORTED");
        List<IExecutor> calls = new ArrayList<>(); flatten(boundary.getServices(),calls);
        boolean parallelUpdates = boundary.parallelOrdinaryUpdates();
        if (parallelUpdates) calls = UpdateScheduling.order(calls);
        final List<IExecutor> planned = calls;
        // Capture all caller intervals before any quantum call can change a participant.
        for (int i = 0; i < calls.size(); i++) {
            IExecutor original = calls.get(i);
            if (original instanceof ITransactionGroup || mode(original) != TransactionMode.QUANTUM) continue;
            var bounds = List.copyOf(original.getQuantumBounds());
            calls.set(i,new IExecutor() {
                public com.servpp.service.Service getService() { return original.getService(); }
                public List<com.servpp.service.Service> getRepositoryScopeServices() { return original.getRepositoryScopeServices(); }
                public Object[] getArguments() { return original.getArguments(); }
                public List<QuantumInvocationBounds.Bound> getQuantumBounds() { return bounds; }
                public TransactionMode getTransactionModeHint() { return original.getTransactionModeHint(); }
                public boolean isQuantumUpdate() { return original.isQuantumUpdate(); }
                public boolean isPreparedOrdinaryUpdate() { return original.isPreparedOrdinaryUpdate(); }
                public void prepareForParallelExecution() { original.prepareForParallelExecution(); }
                public void execute() { original.execute(); }
            });
        }
        Set<TransactionMode> modes = new LinkedHashSet<>();
        for (IExecutor call : calls) if (!(call instanceof ITransactionGroup) && isUpdate(call)) modes.add(mode(call));
        boolean independent = calls.stream().anyMatch(call -> call instanceof ITransactionGroup);
        if (modes.size() <= 1 && !modes.contains(TransactionMode.SAGA)
                && (!independent || modes.isEmpty() || (modes.contains(TransactionMode.AT) || modes.contains(TransactionMode.LOCAL)))) {
            TransactionMode mode = modes.isEmpty() ? TransactionMode.AT : modes.iterator().next();
            if (parallelUpdates && calls.stream().allMatch(call -> call.getService() == null)
                    && boundary.getDeclaredTransactionMode() != null) mode = boundary.getDeclaredTransactionMode();
            SourceTransactionBoundary.execute(boundary.getPropagation(),mode,
                    () -> DefaultTransactionManager.getInstance().execute(planned,parallelUpdates));
            return;
        }
        SourceTransactionBoundary.execute(boundary.getPropagation(),TransactionMode.SAGA, () -> {
            // Startup preparation has missing-only retry semantics, not business compensation.
            if (DatabaseInitializationContext.isActive()) executeSaga(planned, parallelUpdates);
            else SagaTransactionGroupManager.executeSourceStages(boundary.getSourceTransactionId(), () -> sourceStages(planned),
                    () -> executeSaga(planned, parallelUpdates));
        });
    }

    private static List<SourceSagaStage> sourceStages(List<IExecutor> calls) {
        return stages(calls).stream().map(stage -> (SourceSagaStage)new SourceSagaStage() {
            private String quantumBoundary = "";
            public com.servpp.service.Service getService() { return null; }
            public TransactionMode getStageMode() { return stage.mode(); }
            public List<IExecutor> getStageCalls() { return List.copyOf(stage.calls()); }
            public boolean isIndependentBoundary() { return stage.calls().get(0) instanceof ITransactionGroup; }
            public String getCommittedQuantumBoundary() { return quantumBoundary; }
            public void execute() {
                if (isIndependentBoundary()) SourceTransactionBoundary.executeIndependent(stage.calls().get(0)::execute);
                else {
                    var completed = new ArrayList<Committed>();
                    executeStage(stage, completed, false);
                    quantumBoundary = Objects.toString(completed.get(0).quantumPayload(), "");
                }
            }
        }).toList();
    }
    private static void flatten(IExecutor[] calls, List<IExecutor> result) {
        for (IExecutor call : calls) {
            if (call instanceof ITransactionGroup nested && nested.getPropagation() == TransactionPropagation.REQUIRED)
                flatten(nested.getServices(),result);
            else result.add(call);
        }
    }
    private static boolean isUpdate(IExecutor call) {
        return !(call.getService() instanceof GeneratedServiceKindProvider kind)
                || kind.getGeneratedServiceKind() == GeneratedServiceKind.ATOMIC_UPDATE;
    }
    private static TransactionMode mode(IExecutor call) {
        if (call.getTransactionModeHint() != null) return call.getTransactionModeHint();
        if (call.getService() instanceof GeneratedServiceKindProvider kind
                && kind.getGeneratedServiceKind() != GeneratedServiceKind.ATOMIC_UPDATE) return TransactionMode.AT;
        return call.getService() instanceof TransactionalService service ? service.getTransactionMode() : TransactionMode.AT;
    }
    private static List<Stage> stages(List<IExecutor> calls) {
        List<Stage> result = new ArrayList<>();
        List<IExecutor> ready = new ArrayList<>();
        for (IExecutor call : calls) {
            if (call.isPreparedOrdinaryUpdate() || quantum(call)) ready.add(call);
            else {
                result.addAll(readyStages(ready)); ready.clear();
                result.add(new Stage(mode(call), List.of(call)));
            }
        }
        result.addAll(readyStages(ready));
        return result;
    }

    private static List<Stage> readyStages(List<IExecutor> calls) {
        Map<TransactionMode,List<IExecutor>> groups = new LinkedHashMap<>();
        TransactionMode calculationMode = calls.stream().filter(SourceTransactionPlan::isUpdate).map(SourceTransactionPlan::mode)
                .filter(mode -> mode != TransactionMode.QUANTUM).findFirst().orElse(TransactionMode.QUANTUM);
        for (IExecutor call : calls) groups.computeIfAbsent(isUpdate(call) ? mode(call) : calculationMode,ignored -> new ArrayList<>()).add(call);
        List<Stage> stages = new ArrayList<>();
        if (groups.containsKey(TransactionMode.QUANTUM)) stages.add(new Stage(TransactionMode.QUANTUM,groups.remove(TransactionMode.QUANTUM)));
        for (var group : groups.entrySet()) {
            if (group.getKey() == TransactionMode.SAGA) {
                // A logical resource name or remote peer does not prove a shared local transaction.
                var local = new IdentityHashMap<Object, List<IExecutor>>();
                for (IExecutor node : group.getValue()) {
                    Object resource = com.servpp.service.repository.RepositoryWriteRuleProvider.transactionResource(node.getService());
                    List<IExecutor> members = resource == null ? null : local.get(resource);
                    if (members == null) {
                        members = new ArrayList<>();
                        if (resource != null) local.put(resource, members);
                        stages.add(new Stage(TransactionMode.SAGA, members));
                    }
                    members.add(node);
                }
            }
            else stages.add(new Stage(group.getKey(),group.getValue()));
        }
        return stages;
    }
    private static void executeSaga(List<IExecutor> calls, boolean parallelUpdates) {
        List<Committed> completed = Collections.synchronizedList(new ArrayList<>());
        List<IExecutor> segment = new ArrayList<>();
        try {
            for (IExecutor call : calls) {
                if (call instanceof ITransactionGroup nested) {
                    executeStages(segment,completed,parallelUpdates); segment.clear();
                    // An independent boundary is not enlisted in outer recovery.
                    SourceTransactionBoundary.executeIndependent(nested::execute);
                } else segment.add(call);
            }
            executeStages(segment,completed,parallelUpdates);
        } catch (RuntimeException | Error failure) {
            // Startup seeds are technical preparation, not completed business trades.
            // Preserve completed seed stages for a missing-only retry before readiness.
            if (DatabaseInitializationContext.isActive()) throw failure;
            if (TransactionOutcomes.outcome(failure) == TransactionOutcomes.Outcome.UNKNOWN) throw failure;
            for (int index = completed.size()-1; index >= 0; index--) {
                Committed committed = completed.get(index);
                try {
                    TransactionOutcomes.observeIndependent(() -> {
                        if (committed.stage().mode() == TransactionMode.QUANTUM) {
                            if (committed.quantumPayload() == null) throw new IllegalStateException("QUANTUM_REVERSAL_EVIDENCE_REQUIRED");
                            DefaultTransactionManager.getInstance().reverseCommittedQuantumBoundary(committed.quantumPayload(),committed.context());
                        } else SourceSagaCompensation.compensate(
                                SourceSagaCompensation.localResourceStage(committed.stage().mode(), committed.stage().calls()),
                                committed.compensation());
                    });
                    SagaLogManager.appendCurrentTrace("sourceTransactionPlan","REVERSED",committed.stage().mode().name());
                } catch (RuntimeException | Error recovery) {
                    if (recovery != failure) failure.addSuppressed(recovery);
                    TransactionOutcomes.record(failure,TransactionOutcomes.Outcome.UNKNOWN);
                    SagaLogManager.appendCurrentTrace("sourceTransactionPlan","RECOVERY_REQUIRED",recovery.getMessage(),committed.quantumPayload());
                }
            }
            if (!completed.isEmpty() && TransactionOutcomes.outcome(failure) != TransactionOutcomes.Outcome.UNKNOWN)
                TransactionFailureHandling.recordReversed(failure);
            throw failure;
        }
    }
    private static void executeStages(List<IExecutor> calls, List<Committed> completed, boolean parallelUpdates) {
        var plan = stages(calls);
        for (int index = 0; index < plan.size();) {
            Stage stage = plan.get(index);
            if (!parallelUpdates || !UpdateScheduling.isAvailable() || stage.mode() != TransactionMode.SAGA || !stage.calls().stream().allMatch(IExecutor::isPreparedOrdinaryUpdate)) {
                executeStage(stage, completed, parallelUpdates); index++; continue;
            }
            var batch = new ArrayList<Stage>();
            while (index < plan.size() && plan.get(index).mode() == TransactionMode.SAGA
                    && plan.get(index).calls().stream().allMatch(IExecutor::isPreparedOrdinaryUpdate)) {
                Stage next = plan.get(index);
                batch.add(next); index++;
            }
            var header = InvocationContext.current();
            var tasks = new ArrayList<UpdateSchedulingProvider.Work>();
            for (var next : batch) tasks.add(new UpdateSchedulingProvider.Work(
                    next.calls().stream().map(IExecutor::getArguments).filter(Objects::nonNull).flatMap(Arrays::stream).toArray(),
                    () -> next.calls().forEach(IExecutor::prepareForParallelExecution),
                    () -> bind(ParticipantIdentity.bind(ExampleServiceFaults.bind(ExampleJdbcFaults.bind(
                    com.servpp.service.repository.CurrentObjectContext.bind(() -> InvocationContext.with(header, () -> {
                        executeStage(next, completed, false); return null;
                    }))))))));
            UpdateScheduling.executePrepared(tasks);

        }
    }
    private static void executeStage(Stage stage, List<Committed> completed, boolean parallelUpdates) {
        String[] evidence = {null}; TransactionContext[] context = {null};
        SourceTransactionBoundary.executeStage(stage.mode(), () -> {
            // Local members share one owner transaction; independent stages may run in parallel.
            Runnable action = () -> DefaultTransactionManager.getInstance().execute(stage.calls(),
                    stage.mode() == TransactionMode.SAGA ? false : parallelUpdates);
            if (stage.mode() == TransactionMode.SAGA)
                com.servpp.service.repository.RepositoryWriteRuleProvider.executeInRepositoryScope(stage.calls().get(0).getService(), action);
            else action.run();
            context[0] = DefaultTransactionManager.getLastContext();
            evidence[0] = DefaultTransactionManager.getInstance().committedQuantumBoundaryPayload().orElse(null);
        });
        final List<SourceSagaCompensation.Call> recovery;
        try {
            recovery = stage.mode() == TransactionMode.QUANTUM ? List.of()
                    : SourceSagaCompensation.calls(stage.calls()).stream().map(call ->
                            new SourceSagaCompensation.Call(call.service(), call.local(), call.remote(),
                                    ParticipantCopies.copy(call.arguments()))).toList();
        } catch (RuntimeException failure) {
            TransactionOutcomes.record(failure, TransactionOutcomes.Outcome.UNKNOWN);
            throw failure;
        }
        completed.add(new Committed(stage,evidence[0],context[0], recovery));
        SagaLogManager.appendCurrentTrace("sourceTransactionPlan","STAGE_COMMITTED",stage.mode().name(),evidence[0]);
    }
}
