package com.servpp.service.transaction;

import java.time.Instant;

public class DefaultTccTransactionParticipant extends ObjectSnapshotTransactionParticipant
        implements TccTransactionParticipant {
    private static TccBranchStore branchStore = new InMemoryTccBranchStore();

    private final String resourceId;
    private final String actionName;
    private Runnable confirmAction = () -> {
    };
    private Runnable cancelAction = () -> {
    };
    private String confirmServiceName;
    private String cancelServiceName;
    private String branchId;
    private String recoveryPayload;

    public DefaultTccTransactionParticipant(String resourceId, String actionName) {
        super(resourceId);
        this.resourceId = resourceId;
        this.actionName = actionName;
    }

    public static TccBranchStore getBranchStore() {
        return branchStore;
    }

    public static void setBranchStore(TccBranchStore branchStore) {
        DefaultTccTransactionParticipant.branchStore = branchStore;
    }

    public DefaultTccTransactionParticipant onConfirm(Runnable confirmAction) {
        this.confirmAction = confirmAction == null ? () -> {
        } : confirmAction;
        return this;
    }

    public DefaultTccTransactionParticipant onCancel(Runnable cancelAction) {
        this.cancelAction = cancelAction == null ? () -> {
        } : cancelAction;
        return this;
    }

    public DefaultTccTransactionParticipant confirmWithService(String confirmServiceName) {
        this.confirmServiceName = blankToNull(confirmServiceName);
        return this;
    }

    public DefaultTccTransactionParticipant cancelWithService(String cancelServiceName) {
        this.cancelServiceName = blankToNull(cancelServiceName);
        return this;
    }

    @Override
    public String getResourceId() {
        return resourceId;
    }

    @Override
    public void beforeTry(TransactionContext context) {
        branchId = context.getTraceId() + ":" + resourceId + ":" + actionName;
        recoveryPayload = TccRecoveryPayloadCodec.encode(getArguments());
        rejectTryAfterTerminalBranch(context);
        super.beforeExecute(context);
        branchStore.save(record(context, TccBranchStatus.TRYING, "try started"));
    }

    @Override
    public void afterTry(TransactionContext context) {
        branchStore.save(record(context, TccBranchStatus.TRYING, "try completed"));
    }

    @Override
    public void confirm(TransactionContext context) {
        if (terminalBranchAlreadyHandled(context, TccBranchStatus.CONFIRMED)) {
            return;
        }
        confirmAction.run();
        invokeLifecycleService(confirmServiceName);
        branchStore.save(record(context, TccBranchStatus.CONFIRMED, "confirm completed"));
    }

    @Override
    public void cancel(TransactionContext context, Throwable cause) {
        if (terminalBranchAlreadyHandled(context, TccBranchStatus.CANCELLED)) {
            return;
        }
        if (!hasTryingBranch(context)) {
            branchStore.save(record(context, TccBranchStatus.CANCELLED, "empty rollback"));
            return;
        }
        cancelAction.run();
        invokeLifecycleService(cancelServiceName);
        super.rollback(context, cause);
        branchStore.save(record(context, TccBranchStatus.CANCELLED,
                cause == null || cause.getMessage() == null ? "cancel completed" : cause.getMessage()));
    }

    private void rejectTryAfterTerminalBranch(TransactionContext context) {
        TccBranchRecord existing = branchStore.find(branchId).orElse(null);
        if (existing == null || existing.status() == TccBranchStatus.TRYING) {
            return;
        }
        throw new IllegalStateException("TCC branch " + branchId + " is already "
                + existing.status() + " and cannot start TRY.");
    }

    private boolean terminalBranchAlreadyHandled(TransactionContext context, TccBranchStatus targetStatus) {
        String currentBranchId = branchId == null
                ? context.getTraceId() + ":" + resourceId + ":" + actionName
                : branchId;
        TccBranchRecord existing = branchStore.find(currentBranchId).orElse(null);
        if (existing == null || existing.status() == TccBranchStatus.TRYING) {
            return false;
        }
        if (existing.status() == targetStatus) {
            return true;
        }
        throw new IllegalStateException("TCC branch " + currentBranchId + " is already "
                + existing.status() + " and cannot be marked " + targetStatus + ".");
    }

    private boolean hasTryingBranch(TransactionContext context) {
        String currentBranchId = branchId == null
                ? context.getTraceId() + ":" + resourceId + ":" + actionName
                : branchId;
        return branchStore.find(currentBranchId)
                .map(record -> record.status() == TccBranchStatus.TRYING)
                .orElse(false);
    }

    private void invokeLifecycleService(String serviceName) {
        if (serviceName != null) {
            TccLifecycleServiceInvoker.invoke(serviceName, getArguments());
        }
    }

    private TccBranchRecord record(TransactionContext context, TccBranchStatus status, String message) {
        if (branchId == null) {
            branchId = context.getTraceId() + ":" + resourceId + ":" + actionName;
        }
        if (recoveryPayload == null) {
            recoveryPayload = TccRecoveryPayloadCodec.encode(getArguments());
        }
        return new TccBranchRecord(branchId, context.getTraceId(), resourceId, actionName,
                status, message, Instant.now().toString(), recoveryPayload);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
