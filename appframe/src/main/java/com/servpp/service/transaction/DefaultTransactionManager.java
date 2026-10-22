package com.servpp.service.transaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.lang.reflect.Method;

import com.servpp.service.Service;
import com.servpp.service.runtime.GeneratedRoleIdentityContextBinder;
import com.servpp.service.runtime.RemoteCompensationRuleProvider;
import com.servpp.service.runtime.RemoteGeneratedService;
import com.servpp.service.runtime.ScopeMetadataProvider;
import com.servpp.service.scenario.IExecutor;

public class DefaultTransactionManager {
    private static final DefaultTransactionManager INSTANCE = new DefaultTransactionManager();
    private static final ThreadLocal<TransactionContext> LAST_CONTEXT = new ThreadLocal<>();

    private final ThreadLocal<List<java.util.function.Consumer<Throwable>>> nestedRollback = new ThreadLocal<>();
    private record ActiveExecution(TransactionContext context, List<TransactionEntry> executed, TransactionMode mode) { }
    private final ThreadLocal<ActiveExecution> activeExecution = new ThreadLocal<>();
    private final ThreadLocal<TransactionMode> nestedMode = new ThreadLocal<>();

    private final Map<TransactionMode, TransactionStrategy> strategies = new EnumMap<>(TransactionMode.class);
    private QuantumJournalStore quantumJournalStore = new InMemoryQuantumJournalStore();
    private QuantumAccessAdapter quantumAccessAdapter = new JournalQuantumAccessAdapter(quantumJournalStore);

    public DefaultTransactionManager() {
        registerStrategy(new AtTransactionStrategy());
        registerStrategy(new AbstractTransactionStrategy() { public TransactionMode getMode() { return TransactionMode.LOCAL; } });
        registerStrategy(new AbstractTransactionStrategy() { public TransactionMode getMode() { return TransactionMode.XA; } });
        registerStrategy(new SagaTransactionStrategy());
        registerStrategy(new TccTransactionStrategy());
        registerStrategy(new QuantumTransactionStrategy());
    }

    public static DefaultTransactionManager getInstance() {
        return INSTANCE;
    }

    public static TransactionContext getLastContext() {
        return LAST_CONTEXT.get();
    }

    public static void clearLastContext() {
        LAST_CONTEXT.remove();
    }

    public void registerStrategy(TransactionStrategy strategy) {
        strategies.put(strategy.getMode(), strategy);
    }

    public QuantumJournalStore getQuantumJournalStore() {
        return quantumJournalStore;
    }

    public void setQuantumJournalStore(QuantumJournalStore quantumJournalStore) {
        this.quantumJournalStore = Objects.requireNonNull(quantumJournalStore, "quantumJournalStore");
        this.quantumAccessAdapter = new JournalQuantumAccessAdapter(this.quantumJournalStore);
    }

    public QuantumAccessAdapter getQuantumAccessAdapter() {
        return quantumAccessAdapter;
    }

    public void setQuantumAccessAdapter(QuantumAccessAdapter quantumAccessAdapter) {
        this.quantumAccessAdapter = Objects.requireNonNull(quantumAccessAdapter, "quantumAccessAdapter");
    }

    public Optional<String> committedQuantumBoundaryPayload() {
        return QuantumBoundaryPayloadCodec.encode(LAST_CONTEXT.get());
    }

    public void reverseCommittedQuantumBoundary(String payload) { reverseCommittedQuantumBoundary(payload,null); }

    public void reverseCommittedQuantumBoundary(String payload, TransactionContext original) {
        QuantumBoundaryPayloadCodec.DecodedBoundary decoded =
                QuantumBoundaryPayloadCodec.decode(payload, quantumJournalStore);
        quantumAccessAdapter.executeInAccessScope(
                () -> quantumAccessAdapter.reverse(decoded.context(), decoded.boundary()));
        if (original != null) QuantumParticipantResults.refill(original,
                quantumJournalStore.findByTransactionId(decoded.context().getId()));
    }

    public void execute(Collection<IExecutor> executors) { execute(executors, false); }

    public void execute(Collection<IExecutor> executors, boolean parallelUpdates) {
        if (DeclaredTransactionContext.currentMode() == TransactionMode.SAGA) {
            // Saga nodes own their explicitly declared local transactions.
            executeInCurrentScope(executors, parallelUpdates);
        } else {
            // Ordinary-only applications need their declared repositories enlisted too.
            // The quantum access scope alone may use a different bootstrap datasource.
            withRepositoryScopes(executors.stream().flatMap(call -> call.getRepositoryScopeServices().stream()).toList(), 0,
                    () -> quantumAccessAdapter.executeInAccessScope(() -> executeInCurrentScope(executors, parallelUpdates)));
        }
    }

    private static void withRepositoryScopes(java.util.List<com.servpp.service.Service> calls, int index, Runnable action) {
        if (index == calls.size()) { action.run(); return; }
        var service = calls.get(index);
        com.servpp.service.repository.RepositoryWriteRuleProvider.executeInRepositoryScope(
                service, () -> withRepositoryScopes(calls, index + 1, action));
    }

    /** Suspend undo enlistment: independently committed work must not join outer undo. */
    public void executeIndependently(Runnable action) {
        var rollback = nestedRollback.get();
        var mode = nestedMode.get();
        var context = LAST_CONTEXT.get();
        var active = activeExecution.get(); activeExecution.remove();
        nestedRollback.remove(); nestedMode.remove(); LAST_CONTEXT.remove();
        try { action.run(); }
        finally {
            if (active == null) activeExecution.remove(); else activeExecution.set(active);
            if (rollback == null) nestedRollback.remove(); else nestedRollback.set(rollback);
            if (mode == null) nestedMode.remove(); else nestedMode.set(mode);
            if (context == null) LAST_CONTEXT.remove(); else LAST_CONTEXT.set(context);
        }
    }

    public boolean isExecutingTransaction() {
        return nestedRollback.get() != null;
    }

    private void executeInCurrentScope(Collection<IExecutor> executors, boolean parallelUpdates) {
        ActiveExecution parentExecution = activeExecution.get();
        TransactionMode executionMode = DeclaredTransactionContext.currentMode();
        if (parentExecution != null && executionMode == parentExecution.mode()
                && (executionMode == TransactionMode.LOCAL || executionMode == TransactionMode.XA || executionMode == TransactionMode.AT || executionMode == TransactionMode.QUANTUM)) {
            List<TransactionEntry> entries = collectEntries(executors);
            for (TransactionEntry entry : entries) strategy(entry).begin(parentExecution.context(),entry.participant());
            executeEntries(entries,parentExecution.context(),parentExecution.executed(),parallelUpdates);
            return;
        }
        var queryBaselines = com.servpp.service.repository.RepositoryParticipantSnapshots.checkpoint();
        var modifiedProperties = com.servpp.service.runtime.PropertyChanges.checkpoint(queryBaselines.keySet());
        List<TransactionEntry> entries = collectEntries(executors);
        TransactionContext context = new TransactionContext(collectResourceModes(entries), quantumJournalStore);
        LAST_CONTEXT.set(context);
        List<TransactionEntry> executed = java.util.Collections.synchronizedList(new ArrayList<>());
        List<java.util.function.Consumer<Throwable>> parentRollback = nestedRollback.get();
        TransactionMode parentMode = nestedMode.get();
        TransactionMode currentMode = DeclaredTransactionContext.currentMode();
        if (currentMode == null) nestedMode.remove(); else nestedMode.set(currentMode);
        List<java.util.function.Consumer<Throwable>> childRollback = java.util.Collections.synchronizedList(new ArrayList<>());
        nestedRollback.set(childRollback);
        activeExecution.set(new ActiveExecution(context,executed,currentMode));
        try {
            for (TransactionEntry entry : entries) {
                strategy(entry).begin(context, entry.participant());
            }
            executeEntries(entries,context,executed,parallelUpdates);
            SourceTransactionBoundary.assertCommittable();
            validateQuantumBoundary(context);
            for (TransactionEntry entry : executed) {
                strategy(entry).commit(context, entry.participant());
            }
            commitQuantumBoundary(context, executed);
            if (parentRollback != null && parentMode != null && parentMode == currentMode && (currentMode == TransactionMode.LOCAL || currentMode == TransactionMode.XA || currentMode == TransactionMode.AT || currentMode == TransactionMode.QUANTUM)) {
                parentRollback.add(cause -> {
                    rollbackNested(childRollback, cause);
                    rollback(context, executed, cause);
                });
            }
        } catch (RuntimeException | Error e) {
            rollbackNested(childRollback, e);
            try { rollback(context, executed, e); }
            finally {
                com.servpp.service.repository.RepositoryParticipantSnapshots.restore(queryBaselines);
                com.servpp.service.runtime.PropertyChanges.restoreCheckpoint(modifiedProperties);
            }
            throw e;
        } finally {
            if (parentExecution == null) activeExecution.remove(); else activeExecution.set(parentExecution);
            if (parentRollback == null) nestedRollback.remove(); else nestedRollback.set(parentRollback);
            if (parentMode == null) nestedMode.remove(); else nestedMode.set(parentMode);
        }
    }

    private void executeEntries(List<TransactionEntry> entries, TransactionContext context,
                                List<TransactionEntry> executed, boolean parallelUpdates) {
        for (int index = 0; index < entries.size();) {
            var entry = entries.get(index);
            if (!parallelUpdates || !UpdateScheduling.isAvailable() || !entry.executor().isPreparedOrdinaryUpdate()) {
                executeEntry(entry, context, executed); index++; continue;
            }
            var batch = new ArrayList<UpdateSchedulingProvider.Work>();
            while (index < entries.size() && entries.get(index).executor().isPreparedOrdinaryUpdate()) {
                var next = entries.get(index);
                batch.add(new UpdateSchedulingProvider.Work(next.executor().getArguments(),
                        next.executor()::prepareForParallelExecution, () -> bindWorker(() -> executeEntry(next, context, executed)))); index++;
            }
            UpdateScheduling.executePrepared(batch);
        }
    }

    private void executeEntry(TransactionEntry entry, TransactionContext context, List<TransactionEntry> executed) {
        try {
            executeEntryBody(entry, context, executed);
        } catch (RuntimeException | Error failure) {
            TransactionFailureHandling.recordOrigin(failure,
                    entry.executor().getService() == null ? "" : entry.executor().getService().getName(),
                    entry.resourceId(), "EXECUTION");
            throw failure;
        }
    }

    private void executeEntryBody(TransactionEntry entry, TransactionContext context, List<TransactionEntry> executed) {
        var bounds = entry.executor().getQuantumBounds();
        if (QuantumInvocationBounds.hasLimits(bounds)) {
            if (!quantumAccessAdapter.supportsInvocationBounds()) throw new IllegalStateException("QUANTUM_INVOCATION_BOUNDS_UNSUPPORTED");
            if (entry.executor().getService() instanceof RemoteGeneratedService)
                throw new IllegalStateException("REMOTE_SERVICE_QUANTUM_BOUNDS_TRANSPORT_UNSUPPORTED");
        }
        QuantumInvocationBounds.execute(bounds, () -> {
            strategy(entry).beforeExecute(context, entry.participant());
            executed.add(entry);
            entry.executor().execute();
            strategy(entry).afterExecute(context, entry.participant());
            if (entry.executor().getService() instanceof QuantumConstraintProvider constraints)
                constraints.validateQuantumConstraints(transactionArguments(entry.executor().getService(), entry.executor()));
        });
    }

    private Runnable bindWorker(Runnable action) {
        var active = activeExecution.get(); var undo = nestedRollback.get(); var mode = nestedMode.get();
        var context = LAST_CONTEXT.get(); var declared = DeclaredTransactionContext.currentMode();
        var header = com.servpp.service.runtime.InvocationContext.current();
        Runnable inherited = () -> {
            var previousActive = activeExecution.get(); var previousUndo = nestedRollback.get();
            var previousMode = nestedMode.get(); var previousContext = LAST_CONTEXT.get();
            activeExecution.set(active); nestedRollback.set(undo); nestedMode.set(mode); LAST_CONTEXT.set(context);
            try { DeclaredTransactionContext.execute(declared, () ->
                    com.servpp.service.runtime.InvocationContext.with(header, () -> { action.run(); return null; })); }
            finally {
                if (previousActive == null) activeExecution.remove(); else activeExecution.set(previousActive);
                if (previousUndo == null) nestedRollback.remove(); else nestedRollback.set(previousUndo);
                if (previousMode == null) nestedMode.remove(); else nestedMode.set(previousMode);
                if (previousContext == null) LAST_CONTEXT.remove(); else LAST_CONTEXT.set(previousContext);
            }
        };
        return com.servpp.service.jdbc.JdbcTransactionScope.bindWorker(SourceTransactionPlan.bind(
                SourceTransactionBoundary.bind(com.servpp.service.repository.CurrentObjectContext.bind(
                com.servpp.service.runtime.ParticipantIdentity.bind(
                com.servpp.service.runtime.ExampleServiceFaults.bind(com.servpp.service.runtime.ExampleJdbcFaults.bind(inherited)))))));
    }

    private void rollbackNested(List<java.util.function.Consumer<Throwable>> actions, Throwable cause) {
        for (int i = actions.size() - 1; i >= 0; i--) {
            try { actions.get(i).accept(cause); }
            catch (RuntimeException | Error failure) { if (failure != cause) cause.addSuppressed(failure); }
        }
    }

    private List<TransactionEntry> collectEntries(Collection<IExecutor> executors) {
        List<TransactionEntry> entries = new ArrayList<>();
        for (IExecutor executor : executors) {
            Service service = executor.getService();
            if (service instanceof TransactionalService transactional) {
                TransactionParticipant participant = service instanceof RemoteGeneratedService
                        ? TransactionParticipant.noop(transactional.getTransactionResourceId())
                        : transactional.getTransactionParticipant();
                if (!SourceTransactionPlan.isActive() && service instanceof CompensationRuleProvider compensationRuleProvider) {
                    participant = new ChainedTransactionParticipant(transactional.getTransactionResourceId(),
                            participant,
                            new CompensationTransactionParticipant(transactional.getTransactionResourceId(),
                                    compensationRuleProvider.getCompensationServiceNames()));
                }
                if (!SourceTransactionPlan.isActive() && service instanceof RemoteCompensationRuleProvider remoteCompensationRuleProvider) {
                    participant = new ChainedTransactionParticipant(transactional.getTransactionResourceId(),
                            participant,
                            new RemoteCompensationTransactionParticipant(transactional.getTransactionResourceId(),
                                    remoteCompensationRuleProvider.getRemoteCompensationServiceNames()));
                }
                if (participant instanceof ArgumentAwareTransactionParticipant argumentAware) {
                    argumentAware.setArguments(transactionArguments(service, executor));
                }
                entries.add(new TransactionEntry(executor, transactional.getTransactionResourceId(),
                        transactional.getTransactionMode(), participant));
            } else {
                entries.add(new TransactionEntry(executor, service == null ? "unknown" : service.getName(),
                        TransactionMode.AT, TransactionParticipant.noop(service == null ? "unknown" : service.getName())));
            }
        }
        return entries;
    }

    private Object[] transactionArguments(Service service, IExecutor executor) {
        Object[] arguments = executor == null ? new Object[0] : executor.getArguments();
        Object[] safeArguments = arguments == null ? new Object[0] : arguments;
        for (Object argument : safeArguments) {
            GeneratedRoleIdentityContextBinder.attach(argument);
        }
        return requestEnvelopeArguments(service, safeArguments).orElse(safeArguments);
    }

    private Optional<Object[]> requestEnvelopeArguments(Service service, Object[] arguments) {
        if (!(service instanceof ScopeMetadataProvider metadata)
                || arguments == null
                || arguments.length != 1
                || arguments[0] == null
                || !isGeneratedRequestEnvelope(service, arguments[0])) {
            return Optional.empty();
        }
        List<String> parameterNames = metadata.getServiceParameterNames();
        if (parameterNames == null || parameterNames.isEmpty()) {
            return Optional.empty();
        }
        Object request = arguments[0];
        Object[] extracted = new Object[parameterNames.size()];
        for (int i = 0; i < parameterNames.size(); i++) {
            Optional<Method> getter = getter(request.getClass(), parameterNames.get(i));
            if (getter.isEmpty()) {
                return Optional.empty();
            }
            extracted[i] = invoke(getter.orElseThrow(), request);
        }
        return Optional.of(extracted);
    }

    private boolean isGeneratedRequestEnvelope(Service service, Object argument) {
        String serviceName = service == null ? "" : service.getName();
        if (serviceName == null || serviceName.isBlank()) {
            return false;
        }
        String expectedName = Character.toUpperCase(serviceName.charAt(0))
                + serviceName.substring(1) + "Request";
        return argument.getClass().getSimpleName().equals(expectedName);
    }

    private Optional<Method> getter(Class<?> type, String propertyName) {
        if (type == null || propertyName == null || propertyName.isBlank()) {
            return Optional.empty();
        }
        String methodName = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        try {
            return Optional.of(type.getMethod(methodName));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    private Object invoke(Method method, Object target) {
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read generated request transaction argument "
                    + method.getName(), e);
        }
    }

    private Map<String, TransactionMode> collectResourceModes(List<TransactionEntry> entries) {
        Map<String, TransactionMode> resourceModes = new LinkedHashMap<>();
        for (TransactionEntry entry : entries) {
            TransactionMode previous = resourceModes.putIfAbsent(entry.resourceId(), entry.mode());
            if (previous != null && previous != entry.mode()) {
                throw new IllegalStateException("Resource " + entry.resourceId()
                        + " uses multiple transaction modes: " + previous + " and " + entry.mode());
            }
        }
        return resourceModes;
    }

    private void rollback(TransactionContext context, List<TransactionEntry> executed, Throwable cause) {
        for (int i = executed.size() - 1; i >= 0; i--) {
            TransactionEntry entry = executed.get(i);
            try {
                strategy(entry).rollback(context, entry.participant(), cause);
            } catch (RuntimeException | Error rollbackFailure) {
                TransactionOutcomes.record(cause, TransactionOutcomes.Outcome.UNKNOWN);
                if (rollbackFailure != cause) cause.addSuppressed(rollbackFailure);
            }
        }
        try {
            rollbackQuantumBoundary(context);
        } catch (RuntimeException | Error rollbackFailure) {
            TransactionOutcomes.record(cause, TransactionOutcomes.Outcome.UNKNOWN);
            if (rollbackFailure != cause) cause.addSuppressed(rollbackFailure);
        }
    }

    private void validateQuantumBoundary(TransactionContext context) {
        QuantumEntanglementBoundary boundary = context.getQuantumEntanglementBoundary();
        if (boundary != null) {
            boundary.validateConservation();
        }
    }

    private void commitQuantumBoundary(TransactionContext context, List<TransactionEntry> executed) {
        QuantumEntanglementBoundary boundary = context.getQuantumEntanglementBoundary();
        if (boundary != null) {
            try {
                quantumAccessAdapter.commit(context, boundary);
            } catch (RuntimeException | Error failure) {
                String rejectedAttribute = failure instanceof FailureReasonProvider details
                        ? details.getFailureAttribute() : null;
                var candidates = executed.stream().filter(entry -> entry.executor().isQuantumUpdate()
                                || entry.executor().getService() instanceof com.servpp.service.runtime.QuantumServiceMetadataProvider metadata
                                    && !metadata.getQuantumAttributeIds().isEmpty()
                                || QuantumInvocationBounds.hasLimits(entry.executor().getQuantumBounds()))
                        .map(entry -> entry.executor().getService()).filter(java.util.Objects::nonNull)
                        .filter(service -> rejectedAttribute == null
                                || service instanceof com.servpp.service.runtime.QuantumServiceMetadataProvider metadata
                                    && metadata.getQuantumAttributeIds().contains(rejectedAttribute))
                        .map(com.servpp.service.Service::getName).distinct().toList();
                TransactionFailureHandling.recordOrigin(failure, candidates.size() == 1 ? candidates.get(0) : "",
                        String.join(",", boundary.getResourceIds()), "QUANTUM_SETTLEMENT");
                throw failure;
            }
        }
    }

    private void rollbackQuantumBoundary(TransactionContext context) {
        QuantumEntanglementBoundary boundary = context.getQuantumEntanglementBoundary();
        if (boundary != null) {
            quantumAccessAdapter.rollback(context, boundary);
        }
    }

    private TransactionStrategy strategy(TransactionEntry entry) {
        TransactionStrategy strategy = strategies.get(entry.mode());
        if (strategy == null) {
            throw new IllegalStateException("No transaction strategy registered for " + entry.mode());
        }
        return strategy;
    }

    private record TransactionEntry(IExecutor executor, String resourceId, TransactionMode mode,
                                    TransactionParticipant participant) {
    }
}
