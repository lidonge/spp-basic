package com.servpp.service.transaction;

public final class SagaLogManager {
    private static SagaLogStore store = new InMemorySagaLogStore();

    private SagaLogManager() {
    }

    public static SagaLogEntry appendCurrentTrace(String serviceName, String status, String message) {
        return appendCurrentTrace(serviceName, status, message, null);
    }

    public static SagaLogEntry appendCurrentTrace(String serviceName, String status, String message,
                                                  String recoveryPayload) {
        return new SagaLogRecorder(store)
                .append(BusinessTraceContext.currentGlobalSerialNumber().orElse(""), serviceName, status, message,
                        recoveryPayload);
    }

    public static void setStore(SagaLogStore nextStore) {
        store = nextStore == null ? new InMemorySagaLogStore() : nextStore;
    }

    public static SagaLogStore getStore() {
        return store;
    }

    public static void clear() {
        store.clear();
    }
}
