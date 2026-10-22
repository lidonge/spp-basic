package com.servpp.service.transaction;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import com.servpp.service.Service;
import com.servpp.service.runtime.RemoteCompensationPolicyProvider;
import com.servpp.service.server.DefaultServiceContainer;

public class RemoteCompensationTransactionParticipant implements ArgumentAwareTransactionParticipant {
    private final String resourceId;
    private final List<String> remoteCompensationServiceNames;
    private Object[] arguments = new Object[0];

    public RemoteCompensationTransactionParticipant(String resourceId, List<String> remoteCompensationServiceNames) {
        this.resourceId = resourceId;
        this.remoteCompensationServiceNames = remoteCompensationServiceNames == null
                ? List.of()
                : List.copyOf(remoteCompensationServiceNames);
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
        String recoveryPayload = TccRecoveryPayloadCodec.encode(arguments);
        for (String compensationServiceName : remoteCompensationServiceNames) {
            try {
                Service compensationService = DefaultServiceContainer.getInstance().getService(compensationServiceName);
                if (compensationService == null) {
                    throw new IllegalStateException("Remote compensation service not found: " + compensationServiceName);
                }
                RemoteCompensationPolicy policy = RemoteCompensationPolicy.from(compensationService);
                invokeRemoteCompensationWithRetry(compensationServiceName, compensationService, policy, recoveryPayload);
                SagaLogManager.appendCurrentTrace(compensationServiceName, "REMOTE_COMPENSATION_DONE",
                        "remote compensated " + resourceId, recoveryPayload);
            } catch (RuntimeException | Error e) {
                SagaLogManager.appendCurrentTrace(compensationServiceName, "REMOTE_COMPENSATION_FAILED",
                        "remote compensation failed for " + resourceId + ": " + failureMessage(e),
                        recoveryPayload);
                Service compensationService = DefaultServiceContainer.getInstance().getService(compensationServiceName);
                if (RemoteCompensationPolicy.from(compensationService).manualInterventionRequired()) {
                    SagaLogManager.appendCurrentTrace(compensationServiceName, "REMOTE_COMPENSATION_MANUAL_REQUIRED",
                            "remote compensation for " + resourceId + " requires manual intervention: "
                                    + failureMessage(e), recoveryPayload);
                }
                firstFailure = appendFailure(firstFailure, e);
            }
        }
        rethrow(firstFailure);
    }

    private String failureMessage(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private void invokeRemoteCompensationWithRetry(String compensationServiceName, Service compensationService,
                                                   RemoteCompensationPolicy policy, String recoveryPayload) {
        RuntimeException lastRuntimeFailure = null;
        Error lastError = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                invokeRemoteCompensation(compensationServiceName, compensationService);
                return;
            } catch (RuntimeException e) {
                lastRuntimeFailure = e;
                if (attempt >= policy.maxAttempts()) {
                    break;
                }
                SagaLogManager.appendCurrentTrace(compensationServiceName, "REMOTE_COMPENSATION_RETRY",
                        "remote compensation retry " + (attempt + 1) + "/" + policy.maxAttempts()
                                + " for " + resourceId + ": " + failureMessage(e), recoveryPayload);
                waitBeforeRetry(policy);
            } catch (Error e) {
                lastError = e;
                if (attempt >= policy.maxAttempts()) {
                    break;
                }
                SagaLogManager.appendCurrentTrace(compensationServiceName, "REMOTE_COMPENSATION_RETRY",
                        "remote compensation retry " + (attempt + 1) + "/" + policy.maxAttempts()
                                + " for " + resourceId + ": " + failureMessage(e), recoveryPayload);
                waitBeforeRetry(policy);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw lastRuntimeFailure;
    }

    private void waitBeforeRetry(RemoteCompensationPolicy policy) {
        if (policy.backoffMillis() <= 0) {
            return;
        }
        try {
            Thread.sleep(policy.backoffMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted before remote compensation retry", e);
        }
    }

    private void invokeRemoteCompensation(String compensationServiceName, Service compensationService) {
        Method method = findCompensationMethod(compensationServiceName, compensationService);
        if (method == null) {
            compensationService.execute(arguments);
            return;
        }
        try {
            method.invoke(compensationService, arguments);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access remote compensation service: " + compensationServiceName, e);
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            if (target instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (target instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Remote compensation service failed: " + compensationServiceName, target);
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

    private record RemoteCompensationPolicy(int maxAttempts, long backoffMillis,
                                            boolean manualInterventionRequired) {
        static RemoteCompensationPolicy from(Service service) {
            if (service instanceof RemoteCompensationPolicyProvider provider) {
                return new RemoteCompensationPolicy(
                        Math.max(1, provider.getRemoteCompensationMaxAttempts()),
                        Math.max(0L, provider.getRemoteCompensationBackoffMillis()),
                        provider.isRemoteCompensationManualInterventionRequired());
            }
            return new RemoteCompensationPolicy(1, 0L, false);
        }
    }
}
