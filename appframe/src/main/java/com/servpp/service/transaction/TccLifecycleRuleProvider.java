package com.servpp.service.transaction;

public interface TccLifecycleRuleProvider {
    String getTccConfirmServiceName();

    String getTccCancelServiceName();
}
