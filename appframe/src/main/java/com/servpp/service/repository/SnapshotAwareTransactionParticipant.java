package com.servpp.service.repository;

import com.servpp.service.transaction.ArgumentAwareTransactionParticipant;
import com.servpp.service.transaction.TccTransactionParticipant;
import com.servpp.service.transaction.TransactionContext;
import com.servpp.service.transaction.TransactionParticipant;

public class SnapshotAwareTransactionParticipant implements ArgumentAwareTransactionParticipant, TccTransactionParticipant {
    private final TransactionParticipant primary;
    private final ObjectSnapshotParticipant snapshot;

    public SnapshotAwareTransactionParticipant(TransactionParticipant primary, ObjectSnapshotParticipant snapshot) {
        this.primary = primary;
        this.snapshot = snapshot;
    }

    @Override
    public String getResourceId() {
        return primary.getResourceId();
    }

    @Override
    public void setArguments(Object[] arguments) {
        if (primary instanceof ArgumentAwareTransactionParticipant argumentAware) {
            argumentAware.setArguments(arguments);
        }
        snapshot.setArguments(arguments);
    }

    @Override
    public void begin(TransactionContext context) {
        primary.begin(context);
        snapshot.begin(context);
    }

    @Override
    public void beforeExecute(TransactionContext context) {
        primary.beforeExecute(context);
        snapshot.beforeExecute(context);
    }

    @Override
    public void afterExecute(TransactionContext context) {
        primary.afterExecute(context);
        snapshot.afterExecute(context);
    }

    @Override
    public void commit(TransactionContext context) {
        primary.commit(context);
        snapshot.commit(context);
    }

    @Override
    public void rollback(TransactionContext context, Throwable cause) {
        primary.rollback(context, cause);
        snapshot.afterExecute(context);
    }

    @Override
    public void beforeTry(TransactionContext context) {
        if (primary instanceof TccTransactionParticipant tcc) {
            tcc.beforeTry(context);
        } else {
            primary.beforeExecute(context);
        }
        snapshot.beforeExecute(context);
    }

    @Override
    public void afterTry(TransactionContext context) {
        if (primary instanceof TccTransactionParticipant tcc) {
            tcc.afterTry(context);
        } else {
            primary.afterExecute(context);
        }
        snapshot.afterExecute(context);
    }

    @Override
    public void confirm(TransactionContext context) {
        if (primary instanceof TccTransactionParticipant tcc) {
            tcc.confirm(context);
        } else {
            primary.commit(context);
        }
        snapshot.commit(context);
    }

    @Override
    public void cancel(TransactionContext context, Throwable cause) {
        if (primary instanceof TccTransactionParticipant tcc) {
            tcc.cancel(context, cause);
        } else {
            primary.rollback(context, cause);
        }
        snapshot.afterExecute(context);
    }
}
