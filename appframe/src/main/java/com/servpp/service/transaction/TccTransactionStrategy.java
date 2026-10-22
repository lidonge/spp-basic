package com.servpp.service.transaction;

/**
 * Reference TCC strategy. Service execution is the Try body; commit is Confirm
 * and rollback is Cancel.
 */
public class TccTransactionStrategy extends AbstractTransactionStrategy {
    @Override
    public TransactionMode getMode() {
        return TransactionMode.TCC;
    }

    @Override
    public void beforeExecute(TransactionContext context, TransactionParticipant participant) {
        if (participant instanceof TccTransactionParticipant tcc) {
            tcc.beforeTry(context);
            return;
        }
        super.beforeExecute(context, participant);
    }

    @Override
    public void afterExecute(TransactionContext context, TransactionParticipant participant) {
        if (participant instanceof TccTransactionParticipant tcc) {
            tcc.afterTry(context);
            return;
        }
        super.afterExecute(context, participant);
    }

    @Override
    public void commit(TransactionContext context, TransactionParticipant participant) {
        if (participant instanceof TccTransactionParticipant tcc) {
            tcc.confirm(context);
            return;
        }
        super.commit(context, participant);
    }

    @Override
    public void rollback(TransactionContext context, TransactionParticipant participant, Throwable cause) {
        if (participant instanceof TccTransactionParticipant tcc) {
            tcc.cancel(context, cause);
            return;
        }
        super.rollback(context, participant, cause);
    }
}
