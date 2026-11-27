package com.servpp.service.repository;

import java.util.List;
import java.util.Map;

/** Metadata emitted from APP persistence declarations. */
public record RepositoryWriteRule(Operation operation, int argumentIndex, String objectType,
                                   List<String> keyPaths, VersionCheck versionCheck, ArgumentProjection projection) {
    public RepositoryWriteRule(Operation operation, int argumentIndex, String objectType,
                               List<String> keyPaths, VersionCheck versionCheck) {
        this(operation, argumentIndex, objectType, keyPaths, versionCheck, null);
    }

    public record ArgumentProjection(Class<?> rowType, Map<String, String> fields) {
        public ArgumentProjection {
            fields = Map.copyOf(fields);
        }
    }
    public enum Operation { INSERT, INSERT_IF_ABSENT, UPDATE, UPSERT, DELETE }
    public record VersionCheck(int expectedArgumentIndex, String expectedPath, String proposedPath, boolean advanceVersion) {
        public VersionCheck(int expectedArgumentIndex, String expectedPath, String proposedPath) {
            this(expectedArgumentIndex, expectedPath, proposedPath, false);
        }
    }
    public RepositoryWriteRule { keyPaths = List.copyOf(keyPaths); }
}
