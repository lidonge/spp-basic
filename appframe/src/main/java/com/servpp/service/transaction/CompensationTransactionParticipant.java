package com.servpp.service.transaction;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import com.servpp.service.Service;
import com.servpp.service.server.DefaultServiceContainer;

public class CompensationTransactionParticipant implements ArgumentAwareTransactionParticipant {
    private final String resourceId;
    private final List<String> compensationServiceNames;
    private Object[] arguments = new Object[0];

    public CompensationTransactionParticipant(String resourceId, List<String> compensationServiceNames) {
        this.resourceId = resourceId;
        this.compensationServiceNames = compensationServiceNames == null ? List.of() : compensationServiceNames;
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
    public void rollback(TransactionContext context, Throwable cause) {
        Throwable firstFailure = null;
        for (String compensationServiceName : compensationServiceNames) {
            try {
                Service compensationService = DefaultServiceContainer.getInstance().getService(compensationServiceName);
                if (compensationService == null) {
                    throw new IllegalStateException("Compensation service not found: " + compensationServiceName);
                }
                invokeCompensation(compensationServiceName, compensationService);
                SagaLogManager.appendCurrentTrace(compensationServiceName, "COMPENSATION_DONE",
                        "compensated " + resourceId);
            } catch (RuntimeException | Error e) {
                SagaLogManager.appendCurrentTrace(compensationServiceName, "COMPENSATION_FAILED",
                        "compensation failed for " + resourceId + ": " + failureMessage(e));
                firstFailure = appendFailure(firstFailure, e);
            }
        }
        rethrow(firstFailure);
    }

    private String failureMessage(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private void invokeCompensation(String compensationServiceName, Service compensationService) {
        Method method = findCompensationMethod(compensationServiceName, compensationService);
        if (method == null) {
            compensationService.execute(arguments);
            return;
        }
        try {
            method.invoke(compensationService, arguments);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access compensation service: " + compensationServiceName, e);
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            if (target instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (target instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Compensation service failed: " + compensationServiceName, target);
        }
    }

    private Method findCompensationMethod(String compensationServiceName, Service compensationService) {
        for (Method method : compensationService.getClass().getMethods()) {
            if (!method.getName().equals(compensationServiceName)
                    || method.getParameterCount() != arguments.length) {
                continue;
            }
            return method;
        }
        return null;
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
