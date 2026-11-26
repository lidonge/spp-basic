package com.servpp.service.quantum.settlement;

/** {@code (namespace, requestKey)} identifies one command. */
public record SettlementKey(String namespace, String requestKey) {
    public SettlementKey {
        namespace = Values.text(namespace, "namespace");
        requestKey = Values.text(requestKey, "requestKey");
    }
}
