package com.servpp.service.runtime;

import com.servpp.service.transaction.ArgumentAwareTransactionParticipant;
import com.servpp.service.transaction.TccBranchStatus;
import com.servpp.service.transaction.TccTransactionParticipant;
import com.servpp.service.transaction.TransactionContext;
import com.servpp.service.transaction.TransactionParticipant;

public class PhysicalCoordinatedTccTransactionParticipant
        implements ArgumentAwareTransactionParticipant, TccTransactionParticipant {
    private final String resourceId;
    private final TransactionParticipant businessParticipant;
    private final PhysicalBackendTransactionParticipant physicalParticipant;

    public PhysicalCoordinatedTccTransactionParticipant(
            String resourceId,
            TransactionParticipant businessParticipant,
            PhysicalBackendTransactionParticipant physicalParticipant) {
        this.resourceId = resourceId;
        this.businessParticipant = businessParticipant;
        this.physicalParticipant = physicalParticipant;
    }

    @Override
    public String getResourceId() {
        return resourceId;
    }

    @Override
    public void setArguments(Object[] arguments) {
        if (businessParticipant instanceof ArgumentAwareTransactionParticipant argumentAware) {
            argumentAware.setArguments(arguments);
        }
        physicalParticipant.setArguments(arguments);
    }

    @Override
    public void begin(TransactionContext context) {
        businessParticipant.begin(context);
        physicalParticipant.begin(context);
    }

    @Override
    public void beforeTry(TransactionContext context) {
        invokeBeforeTry(businessParticipant, context);
        if (!physicalParticipant.ownsTccPhaseTwo()) {
            physicalParticipant.beforeTry(context);
            return;
        }
        physicalParticipant.registerTccPhaseTwoCallback(context,
                (branchId, targetStatus) -> {
                    if (targetStatus == TccBranchStatus.CONFIRMED) {
                        invokeConfirm(businessParticipant, context);
                        return;
                    }
                    if (targetStatus == TccBranchStatus.CANCELLED) {
                        invokeCancel(businessParticipant, context,
                                new IllegalStateException("Physical TCC branch " + branchId
                                        + " was cancelled."));
                        return;
                    }
                    throw new IllegalArgumentException("Unsupported physical TCC phase-two status "
                            + targetStatus + ".");
                });
        try {
            physicalParticipant.beforeTry(context);
        } catch (RuntimeException | Error failure) {
            physicalParticipant.unregisterTccPhaseTwoCallback(context);
            invokeCancel(businessParticipant, context, failure);
            throw failure;
        }
    }

    @Override
    public void afterTry(TransactionContext context) {
        invokeAfterTry(businessParticipant, context);
        physicalParticipant.afterTry(context);
    }

    @Override
    public void confirm(TransactionContext context) {
        if (physicalParticipant.ownsTccPhaseTwo()) {
            physicalParticipant.confirm(context);
            return;
        }
        invokeConfirm(businessParticipant, context);
        physicalParticipant.confirm(context);
    }

    @Override
    public void cancel(TransactionContext context, Throwable cause) {
        if (physicalParticipant.ownsTccPhaseTwo()) {
            physicalParticipant.cancel(context, cause);
            return;
        }
        physicalParticipant.cancel(context, cause);
        invokeCancel(businessParticipant, context, cause);
    }

    private void invokeBeforeTry(TransactionParticipant participant, TransactionContext context) {
        if (participant instanceof TccTransactionParticipant tcc) {
            tcc.beforeTry(context);
        } else {
            participant.beforeExecute(context);
        }
    }

    private void invokeAfterTry(TransactionParticipant participant, TransactionContext context) {
        if (participant instanceof TccTransactionParticipant tcc) {
            tcc.afterTry(context);
        } else {
            participant.afterExecute(context);
        }
    }

    private void invokeConfirm(TransactionParticipant participant, TransactionContext context) {
        if (participant instanceof TccTransactionParticipant tcc) {
            tcc.confirm(context);
        } else {
            participant.commit(context);
        }
    }

    private void invokeCancel(TransactionParticipant participant, TransactionContext context, Throwable cause) {
        if (participant instanceof TccTransactionParticipant tcc) {
            tcc.cancel(context, cause);
        } else {
            participant.rollback(context, cause);
        }
    }
}
