package com.servpp.service.transaction;

import java.util.Arrays;
import java.util.List;

public class ChainedTransactionParticipant implements ArgumentAwareTransactionParticipant, TccTransactionParticipant {
    private final String resourceId;
    private final List<TransactionParticipant> participants;

    public ChainedTransactionParticipant(String resourceId, TransactionParticipant... participants) {
        this.resourceId = resourceId;
        this.participants = Arrays.stream(participants)
                .filter(participant -> participant != null)
                .toList();
    }

    @Override
    public String getResourceId() {
        return resourceId;
    }

    @Override
    public void setArguments(Object[] arguments) {
        for (TransactionParticipant participant : participants) {
            if (participant instanceof ArgumentAwareTransactionParticipant argumentAware) {
                argumentAware.setArguments(arguments);
            }
        }
    }

    @Override
    public void begin(TransactionContext context) {
        for (TransactionParticipant participant : participants) {
            participant.begin(context);
        }
    }

    @Override
    public void beforeExecute(TransactionContext context) {
        for (TransactionParticipant participant : participants) {
            participant.beforeExecute(context);
        }
    }

    @Override
    public void afterExecute(TransactionContext context) {
        for (TransactionParticipant participant : participants) {
            participant.afterExecute(context);
        }
    }

    @Override
    public void commit(TransactionContext context) {
        for (TransactionParticipant participant : participants) {
            participant.commit(context);
        }
    }

    @Override
    public void rollback(TransactionContext context, Throwable cause) {
        Throwable firstFailure = null;
        for (int i = participants.size() - 1; i >= 0; i--) {
            try {
                participants.get(i).rollback(context, cause);
            } catch (RuntimeException | Error failure) {
                firstFailure = appendFailure(firstFailure, failure);
            }
        }
        rethrow(firstFailure);
    }

    @Override
    public void beforeTry(TransactionContext context) {
        for (TransactionParticipant participant : participants) {
            if (participant instanceof TccTransactionParticipant tcc) {
                tcc.beforeTry(context);
            } else {
                participant.beforeExecute(context);
            }
        }
    }

    @Override
    public void afterTry(TransactionContext context) {
        for (TransactionParticipant participant : participants) {
            if (participant instanceof TccTransactionParticipant tcc) {
                tcc.afterTry(context);
            } else {
                participant.afterExecute(context);
            }
        }
    }

    @Override
    public void confirm(TransactionContext context) {
        for (TransactionParticipant participant : participants) {
            if (participant instanceof TccTransactionParticipant tcc) {
                tcc.confirm(context);
            } else {
                participant.commit(context);
            }
        }
    }

    @Override
    public void cancel(TransactionContext context, Throwable cause) {
        Throwable firstFailure = null;
        for (int i = participants.size() - 1; i >= 0; i--) {
            TransactionParticipant participant = participants.get(i);
            try {
                if (participant instanceof TccTransactionParticipant tcc) {
                    tcc.cancel(context, cause);
                } else {
                    participant.rollback(context, cause);
                }
            } catch (RuntimeException | Error failure) {
                firstFailure = appendFailure(firstFailure, failure);
            }
        }
        rethrow(firstFailure);
    }

    private Throwable appendFailure(Throwable firstFailure, Throwable failure) {
        if (firstFailure == null) {
            return failure;
        }
        firstFailure.addSuppressed(failure);
        return firstFailure;
    }

    private void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }
}
