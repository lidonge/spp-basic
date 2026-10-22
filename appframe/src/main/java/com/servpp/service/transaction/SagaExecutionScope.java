package com.servpp.service.transaction;

public record SagaExecutionScope(String sagaServiceName, String businessKey) {
    public SagaExecutionScope {
        sagaServiceName = normalize(sagaServiceName, "unknownSaga");
        businessKey = normalize(businessKey, "");
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
