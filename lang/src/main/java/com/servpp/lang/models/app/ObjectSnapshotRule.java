package com.servpp.lang.models.app;

import java.util.List;

public record ObjectSnapshotRule(Source source, int argumentIndex, String objectPath,
                                 List<String> objectIdPaths, Timing timing, String repositoryName,
                                 boolean appDdlInferred) {
    public enum Source {
        argument, current, transaction
    }

    public enum Timing {
        before, after, commit, afterCommit
    }

    public ObjectSnapshotRule(Source source, int argumentIndex, String objectPath) {
        this(source, argumentIndex, objectPath, Timing.afterCommit);
    }

    public ObjectSnapshotRule(Source source, int argumentIndex, String objectPath, Timing timing) {
        this(source, argumentIndex, objectPath, List.of(objectIdPath(objectPath)), timing, null);
    }

    public ObjectSnapshotRule(Source source, int argumentIndex, String objectPath, Timing timing,
                              String repositoryName) {
        this(source, argumentIndex, objectPath, List.of(objectIdPath(objectPath)), timing, repositoryName);
    }

    public ObjectSnapshotRule(Source source, int argumentIndex, String objectPath, Timing timing,
                              String repositoryName, boolean appDdlInferred) {
        this(source, argumentIndex, objectPath, List.of(objectIdPath(objectPath)), timing, repositoryName,
                appDdlInferred);
    }

    public ObjectSnapshotRule(Source source, int argumentIndex, String objectPath, List<String> objectIdPaths,
                              Timing timing) {
        this(source, argumentIndex, objectPath, objectIdPaths, timing, null);
    }

    public ObjectSnapshotRule(Source source, int argumentIndex, String objectPath, List<String> objectIdPaths,
                              Timing timing, String repositoryName) {
        this(source, argumentIndex, objectPath, objectIdPaths, timing, repositoryName, false);
    }

    public ObjectSnapshotRule {
        objectIdPaths = objectIdPaths == null ? List.of(objectIdPath(objectPath)) : List.copyOf(objectIdPaths);
        if (source != Source.transaction && objectIdPaths.isEmpty()) {
            throw new IllegalArgumentException("Object snapshot rule requires at least one object id path.");
        }
    }

    public String getObjectType() {
        int index = objectPath.indexOf('.');
        return index < 0 ? objectPath : objectPath.substring(0, index);
    }

    public String getObjectIdPath() {
        return objectIdPaths.isEmpty() ? objectIdPath(objectPath) : objectIdPaths.get(0);
    }

    private static String objectIdPath(String objectPath) {
        int index = objectPath.indexOf('.');
        return index < 0 ? "" : objectPath.substring(index + 1);
    }
}
