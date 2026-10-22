package com.servpp.service.runtime;

import java.util.List;

public interface ScopeMetadataProvider {
    boolean isLocalScope();

    default boolean isUpdateEntry() { return false; }

    default boolean isSystemIdempotencyEnabled() { return true; }

    default boolean isSystemDuplicateGuardEnabled() { return true; }

    default boolean isSystemPartialUpdateEnabled() { return true; }

    default List<String> getDeclaredIdempotencyKeyPaths() { return List.of(); }
    default String getDuplicateWindow() { return null; }
    default String getDeclaredIdempotencyFactsPath() { return null; }
    default List<String> getDeclaredIdempotencyFactsPaths() {
        String path = getDeclaredIdempotencyFactsPath();
        return path == null ? List.of() : List.of(path);
    }

    String getInputHeaderName();

    String getOutputHeaderName();

    default boolean isAsyncScope() {
        return false;
    }

    default boolean requiresPhysicalBackendBinding() {
        return false;
    }

    default boolean requiresIdempotencyKey() {
        return false;
    }

    default String getIdempotencyKeyFieldName() {
        return null;
    }

    default boolean hasIdempotencyKeyField() {
        return getIdempotencyKeyFieldName() != null && !getIdempotencyKeyFieldName().isBlank();
    }

    default List<String> getServiceParameterNames() {
        return List.of();
    }

    default List<String> getServiceContextParameterNames() {
        return List.of();
    }
}
