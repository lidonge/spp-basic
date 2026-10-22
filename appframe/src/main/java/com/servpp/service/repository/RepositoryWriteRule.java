package com.servpp.service.repository;

import java.util.List;

/** Metadata emitted from APP persistence declarations. */
public record RepositoryWriteRule(Operation operation, int argumentIndex, String objectType,
                                   List<String> keyPaths, VersionCheck versionCheck) {
    public enum Operation { INSERT, INSERT_IF_ABSENT, UPDATE, UPSERT, DELETE }
    public record VersionCheck(int expectedArgumentIndex, String expectedPath, String proposedPath, boolean advanceVersion) {
        public VersionCheck(int expectedArgumentIndex, String expectedPath, String proposedPath) {
            this(expectedArgumentIndex, expectedPath, proposedPath, false);
        }
    }
    public RepositoryWriteRule { keyPaths = List.copyOf(keyPaths); }
}
