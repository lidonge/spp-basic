package com.servpp.service.transaction;

import java.util.Optional;
import java.util.function.Supplier;

public final class SagaExecutionContext {
    private static final ThreadLocal<String> CURRENT_SAGA_SERVICE = new ThreadLocal<>();
    private static final ThreadLocal<java.util.Map<String,Integer>> SOURCE_OCCURRENCES = new ThreadLocal<>();

    private SagaExecutionContext() {
    }

    public static Optional<String> currentSagaServiceName() {
        String value = CURRENT_SAGA_SERVICE.get();
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    public static SagaExecutionScope currentScope() {
        return new SagaExecutionScope(
                currentSagaServiceName().orElse("unknownSaga"),
                BusinessTraceContext.currentGlobalSerialNumber().orElse(""));
    }

    public static <T> T execute(String sagaServiceName, Supplier<T> action) {
        String previous = CURRENT_SAGA_SERVICE.get();
        boolean root = SOURCE_OCCURRENCES.get() == null;
        if (root) SOURCE_OCCURRENCES.set(new java.util.HashMap<>());
        if (sagaServiceName == null || sagaServiceName.isBlank()) {
            CURRENT_SAGA_SERVICE.remove();
        } else {
            CURRENT_SAGA_SERVICE.set(sagaServiceName);
        }
        try {
            return action.get();
        } finally {
            if (root) SOURCE_OCCURRENCES.remove();
            if (previous == null) {
                CURRENT_SAGA_SERVICE.remove();
            } else {
                CURRENT_SAGA_SERVICE.set(previous);
            }
        }
    }

    static SagaExecutionScope nextSourceScope(String boundaryId) {
        var occurrences = SOURCE_OCCURRENCES.get();
        if (occurrences == null) throw new IllegalStateException("SOURCE_SAGA_INVOCATION_SCOPE_REQUIRED");
        var scope = currentScope();
        String identity = scope.sagaServiceName() + ":" + boundaryId;
        int occurrence = occurrences.merge(identity, 1, Integer::sum);
        return new SagaExecutionScope(scope.sagaServiceName(), sourceBusinessKey(scope, boundaryId, occurrence));
    }

    /** Persistent key for a particular source boundary occurrence; fits Seata's 48-character column. */
    public static String sourceBusinessKey(SagaExecutionScope scope, String boundaryId, int occurrence) {
        if (scope.businessKey().isBlank() || boundaryId == null || boundaryId.isBlank() || occurrence < 1)
            throw new IllegalArgumentException("SOURCE_SAGA_IDENTITY_REQUIRED");
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            for (String value : new String[]{scope.sagaServiceName(), scope.businessKey(), boundaryId, Integer.toString(occurrence)}) {
                byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
            }
            return "src_" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
