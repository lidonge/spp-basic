package com.servpp.service.transaction;

import java.util.function.Consumer;

public class AfterExecuteTransactionParticipant implements ArgumentAwareTransactionParticipant {
    private final String resourceId;
    private final Consumer<Object[]> action;
    private Object[] arguments = new Object[0];

    public AfterExecuteTransactionParticipant(String resourceId, Consumer<Object[]> action) {
        this.resourceId = resourceId;
        this.action = action;
    }

    @Override
    public String getResourceId() {
        return resourceId;
    }

    @Override
    public void setArguments(Object[] arguments) {
        this.arguments = arguments == null ? new Object[0] : arguments;
    }

    @Override
    public void afterExecute(TransactionContext context) {
        action.accept(arguments);
    }
}
