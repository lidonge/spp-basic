package com.servpp.service.transaction;

import com.servpp.service.Service;
import com.servpp.service.repository.RepositoryWriteRuleProvider;
import com.servpp.service.runtime.GeneratedServiceKind;
import com.servpp.service.runtime.GeneratedServiceKindProvider;
import com.servpp.service.runtime.RemoteCompensationRuleProvider;
import com.servpp.service.scenario.IExecutor;
import com.servpp.service.server.DefaultServiceContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Recovery contract shared by source execution and persistent coordinator adapters. */
public final class SourceSagaCompensation {
    public record Call(String service, List<String> local, List<String> remote, Object[] arguments) {
        public Call {
            local = local == null ? List.of() : List.copyOf(local);
            remote = remote == null ? List.of() : List.copyOf(remote);
            arguments = arguments == null ? new Object[0] : arguments.clone();
        }
    }
    private SourceSagaCompensation() { }

    public static List<Call> calls(List<IExecutor> executors) {
        var result = new ArrayList<Call>();
        for (var executor : executors) {
            Service service = executor.getService();
            if (service == null || service instanceof GeneratedServiceKindProvider kind
                    && kind.getGeneratedServiceKind() != GeneratedServiceKind.ATOMIC_UPDATE) continue;
            result.add(new Call(service.getName(),
                    service instanceof CompensationRuleProvider local ? local.getCompensationServiceNames() : List.of(),
                    service instanceof RemoteCompensationRuleProvider remote ? remote.getRemoteCompensationServiceNames() : List.of(),
                    executor.getArguments()));
        }
        return List.copyOf(result);
    }

    public static boolean localResourceStage(TransactionMode mode, List<IExecutor> calls) {
        return mode == TransactionMode.SAGA && !calls.isEmpty()
                && RepositoryWriteRuleProvider.transactionResource(calls.get(0).getService()) != null;
    }

    public static void compensate(boolean atomicLocal, List<Call> calls) {
        // Resolve every rule before performing any recovery work. A grouped forward commit
        // must never be recovered through individually committed local compensators.
        Service owner = null;
        Object resource = null;
        for (var call : calls) {
            if (call.local().isEmpty() && call.remote().isEmpty())
                throw new IllegalStateException("COMMITTED_STAGE_COMPENSATION_REQUIRED: " + call.service());
            if (atomicLocal && !call.remote().isEmpty())
                throw new IllegalStateException("SOURCE_SAGA_COMPENSATION_RESOURCE_MISMATCH: " + call.service());
            for (String name : call.local()) {
                Service service = DefaultServiceContainer.getInstance().getService(name);
                if (service == null) throw new IllegalStateException("Compensation service not found: " + name);
                if (atomicLocal) {
                    Object next = RepositoryWriteRuleProvider.transactionResource(service);
                    if (next == null || resource != null && resource != next)
                        throw new IllegalStateException("SOURCE_SAGA_COMPENSATION_RESOURCE_MISMATCH: " + name);
                    if (owner == null) { owner = service; resource = next; }
                }
            }
        }
        if (calls.isEmpty()) return;
        if (atomicLocal) {
            Service transactionOwner = owner;
            SourceTransactionBoundary.executeStage(TransactionMode.AT, () ->
                    RepositoryWriteRuleProvider.executeInRepositoryScope(transactionOwner, () -> runReverse(calls)));
        } else {
            for (int index = calls.size() - 1; index >= 0; index--) {
                Call call = calls.get(index);
                SourceTransactionBoundary.executeStage(TransactionMode.AT, () ->
                        DefaultTransactionManager.getInstance().getQuantumAccessAdapter()
                                .executeInAccessScope(() -> run(call)));
            }
        }
    }

    private static void runReverse(List<Call> calls) {
        for (int index = calls.size() - 1; index >= 0; index--) run(calls.get(index));
    }
    private static void run(Call call) {
        var cause = new IllegalStateException("SOURCE_SAGA_RECOVERY");
        if (!call.local().isEmpty()) {
            var participant = new CompensationTransactionParticipant(call.service(), call.local());
            participant.setArguments(call.arguments());
            participant.rollback(new TransactionContext(Map.of()), cause);
        }
        if (!call.remote().isEmpty()) {
            var participant = new RemoteCompensationTransactionParticipant(call.service(), call.remote());
            participant.setArguments(call.arguments());
            participant.rollback(new TransactionContext(Map.of()), cause);
        }
    }
}
