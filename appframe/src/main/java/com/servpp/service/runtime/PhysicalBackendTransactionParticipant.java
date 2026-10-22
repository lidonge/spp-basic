package com.servpp.service.runtime;

import java.util.Optional;
import java.util.function.Supplier;

import com.servpp.service.transaction.ArgumentAwareTransactionParticipant;
import com.servpp.service.transaction.TccTransactionParticipant;
import com.servpp.service.transaction.TransactionContext;

public class PhysicalBackendTransactionParticipant
        implements ArgumentAwareTransactionParticipant, TccTransactionParticipant {
    private final String resourceId;
    private final Supplier<Optional<ResolvedBackend>> resolvedBackend;
    private Object[] arguments = new Object[0];

    public PhysicalBackendTransactionParticipant(String resourceId,
                                                 Supplier<Optional<ResolvedBackend>> resolvedBackend) {
        this.resourceId = resourceId;
        this.resolvedBackend = resolvedBackend;
    }

    @Override
    public String getResourceId() {
        return resourceId;
    }

    @Override
    public void setArguments(Object[] arguments) {
        this.arguments = arguments == null ? new Object[0] : arguments.clone();
    }

    @Override
    public void begin(TransactionContext context) {
        resolvedBackend.get().ifPresent(resolved -> resolved.adapter()
                .begin(resolved.assignment(), context, arguments.clone()));
    }

    @Override
    public void beforeExecute(TransactionContext context) {
        resolvedBackend.get().ifPresent(resolved -> resolved.adapter()
                .beforeExecute(resolved.assignment(), context, arguments.clone()));
    }

    @Override
    public void afterExecute(TransactionContext context) {
        resolvedBackend.get().ifPresent(resolved -> resolved.adapter()
                .afterExecute(resolved.assignment(), context, arguments.clone()));
    }

    @Override
    public void commit(TransactionContext context) {
        resolvedBackend.get().ifPresent(resolved -> resolved.adapter()
                .commit(resolved.assignment(), context, arguments.clone()));
    }

    @Override
    public void rollback(TransactionContext context, Throwable cause) {
        resolvedBackend.get().ifPresent(resolved -> resolved.adapter()
                .rollback(resolved.assignment(), context, arguments.clone(), cause));
    }

    @Override
    public void beforeTry(TransactionContext context) {
        resolvedBackend.get().ifPresent(resolved -> resolved.adapter()
                .beforeTry(resolved.assignment(), context, arguments.clone()));
    }

    @Override
    public void afterTry(TransactionContext context) {
        resolvedBackend.get().ifPresent(resolved -> resolved.adapter()
                .afterTry(resolved.assignment(), context, arguments.clone()));
    }

    @Override
    public void confirm(TransactionContext context) {
        resolvedBackend.get().ifPresent(resolved -> resolved.adapter()
                .confirm(resolved.assignment(), context, arguments.clone()));
    }

    @Override
    public void cancel(TransactionContext context, Throwable cause) {
        resolvedBackend.get().ifPresent(resolved -> resolved.adapter()
                .cancel(resolved.assignment(), context, arguments.clone(), cause));
    }

    public boolean ownsTccPhaseTwo() {
        return resolvedBackend.get()
                .map(resolved -> resolved.adapter().ownsTccPhaseTwo(resolved.assignment()))
                .orElse(false);
    }

    public void registerTccPhaseTwoCallback(TransactionContext context,
                                            TccPhaseTwoRecoveryHandler callback) {
        resolvedBackend.get().ifPresent(resolved -> resolved.adapter()
                .registerTccPhaseTwoCallback(resolved.assignment(), context, callback));
    }

    public void unregisterTccPhaseTwoCallback(TransactionContext context) {
        resolvedBackend.get().ifPresent(resolved -> resolved.adapter()
                .unregisterTccPhaseTwoCallback(resolved.assignment(), context));
    }

    public record ResolvedBackend(PhysicalBackendAssignment assignment, PhysicalBackendAdapter adapter) {
    }
}
