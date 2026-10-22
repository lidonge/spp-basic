package com.servpp.service.runtime;

public interface RemoteCompensationPolicyProvider {
    default int getRemoteCompensationMaxAttempts() {
        return 1;
    }

    default long getRemoteCompensationBackoffMillis() {
        return 0L;
    }

    default boolean isRemoteCompensationManualInterventionRequired() {
        return false;
    }
}
