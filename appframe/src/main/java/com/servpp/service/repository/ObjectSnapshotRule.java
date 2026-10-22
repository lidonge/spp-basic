package com.servpp.service.repository;

import java.util.List;

public record ObjectSnapshotRule(Source source, int argumentIndex, String objectType, String objectIdPath,
                                 List<String> objectIdPaths, Timing timing, String repositoryName,
                                 boolean appDdlInferred) {
    public enum Source {
        ARGUMENT,
        CURRENT,
        TRANSACTION
    }

    public enum Timing {
        BEFORE,
        AFTER,
        COMMIT,
        AFTER_COMMIT
    }

    public ObjectSnapshotRule(Source source, int argumentIndex, String objectType, String objectIdPath) {
        this(source, argumentIndex, objectType, objectIdPath, List.of(objectIdPath), Timing.AFTER_COMMIT);
    }

    public ObjectSnapshotRule(Source source, int argumentIndex, String objectType, String objectIdPath,
                              Timing timing) {
        this(source, argumentIndex, objectType, objectIdPath, List.of(objectIdPath), timing, null);
    }

    public ObjectSnapshotRule(Source source, int argumentIndex, String objectType, String objectIdPath,
                              Timing timing, String repositoryName) {
        this(source, argumentIndex, objectType, objectIdPath, List.of(objectIdPath), timing, repositoryName);
    }

    public ObjectSnapshotRule(Source source, int argumentIndex, String objectType, String objectIdPath,
                              List<String> objectIdPaths, Timing timing) {
        this(source, argumentIndex, objectType, objectIdPath, objectIdPaths, timing, null);
    }

    public ObjectSnapshotRule(Source source, int argumentIndex, String objectType, String objectIdPath,
                              List<String> objectIdPaths, Timing timing, String repositoryName) {
        this(source, argumentIndex, objectType, objectIdPath, objectIdPaths, timing, repositoryName, false);
    }

    public ObjectSnapshotRule {
        objectIdPaths = objectIdPaths == null ? List.of(objectIdPath) : List.copyOf(objectIdPaths);
    }

    public static ObjectSnapshotRule argument(int argumentIndex, String objectType, String objectIdPath) {
        return new ObjectSnapshotRule(Source.ARGUMENT, argumentIndex, objectType, objectIdPath);
    }

    public static ObjectSnapshotRule argument(int argumentIndex, String objectType, String objectIdPath,
                                              Timing timing) {
        return new ObjectSnapshotRule(Source.ARGUMENT, argumentIndex, objectType, objectIdPath, timing);
    }

    public static ObjectSnapshotRule argument(int argumentIndex, String objectType, String objectIdPath,
                                              Timing timing, String repositoryName) {
        return new ObjectSnapshotRule(Source.ARGUMENT, argumentIndex, objectType, objectIdPath,
                timing, repositoryName);
    }

    public static ObjectSnapshotRule argument(int argumentIndex, String objectType, List<String> objectIdPaths) {
        return argument(argumentIndex, objectType, objectIdPaths, Timing.AFTER_COMMIT);
    }

    public static ObjectSnapshotRule argument(int argumentIndex, String objectType, List<String> objectIdPaths,
                                              Timing timing) {
        return argument(argumentIndex, objectType, objectIdPaths, timing, null);
    }

    public static ObjectSnapshotRule argument(int argumentIndex, String objectType, List<String> objectIdPaths,
                                              Timing timing, String repositoryName) {
        if (objectIdPaths == null || objectIdPaths.isEmpty()) {
            throw new IllegalArgumentException("Object snapshot rule requires at least one object id path.");
        }
        return new ObjectSnapshotRule(Source.ARGUMENT, argumentIndex, objectType, objectIdPaths.get(0),
                objectIdPaths, timing, repositoryName);
    }

    public static ObjectSnapshotRule appDdlArgument(int argumentIndex, String objectType, String objectIdPath) {
        return appDdlArgument(argumentIndex, objectType, objectIdPath, Timing.AFTER_COMMIT);
    }

    public static ObjectSnapshotRule appDdlArgument(int argumentIndex, String objectType, String objectIdPath,
                                                    Timing timing) {
        return appDdlArgument(argumentIndex, objectType, objectIdPath, timing, null);
    }

    public static ObjectSnapshotRule appDdlArgument(int argumentIndex, String objectType, String objectIdPath,
                                                    Timing timing, String repositoryName) {
        return new ObjectSnapshotRule(Source.ARGUMENT, argumentIndex, objectType, objectIdPath,
                List.of(objectIdPath), timing, repositoryName, true);
    }

    public static ObjectSnapshotRule appDdlArgument(int argumentIndex, String objectType,
                                                    List<String> objectIdPaths) {
        return appDdlArgument(argumentIndex, objectType, objectIdPaths, Timing.AFTER_COMMIT);
    }

    public static ObjectSnapshotRule appDdlArgument(int argumentIndex, String objectType,
                                                    List<String> objectIdPaths, Timing timing) {
        return appDdlArgument(argumentIndex, objectType, objectIdPaths, timing, null);
    }

    public static ObjectSnapshotRule appDdlArgument(int argumentIndex, String objectType,
                                                    List<String> objectIdPaths, Timing timing,
                                                    String repositoryName) {
        if (objectIdPaths == null || objectIdPaths.isEmpty()) {
            throw new IllegalArgumentException("Object snapshot rule requires at least one object id path.");
        }
        return new ObjectSnapshotRule(Source.ARGUMENT, argumentIndex, objectType, objectIdPaths.get(0),
                objectIdPaths, timing, repositoryName, true);
    }

    public static ObjectSnapshotRule current(String objectType, String objectIdPath) {
        return new ObjectSnapshotRule(Source.CURRENT, -1, objectType, objectIdPath);
    }

    public static ObjectSnapshotRule current(String objectType, String objectIdPath, Timing timing) {
        return new ObjectSnapshotRule(Source.CURRENT, -1, objectType, objectIdPath, timing);
    }

    public static ObjectSnapshotRule current(String objectType, String objectIdPath, Timing timing,
                                             String repositoryName) {
        return new ObjectSnapshotRule(Source.CURRENT, -1, objectType, objectIdPath, timing, repositoryName);
    }

    public static ObjectSnapshotRule current(String objectType, List<String> objectIdPaths) {
        return current(objectType, objectIdPaths, Timing.AFTER_COMMIT);
    }

    public static ObjectSnapshotRule current(String objectType, List<String> objectIdPaths, Timing timing) {
        return current(objectType, objectIdPaths, timing, null);
    }

    public static ObjectSnapshotRule current(String objectType, List<String> objectIdPaths, Timing timing,
                                             String repositoryName) {
        if (objectIdPaths == null || objectIdPaths.isEmpty()) {
            throw new IllegalArgumentException("Object snapshot rule requires at least one object id path.");
        }
        return new ObjectSnapshotRule(Source.CURRENT, -1, objectType, objectIdPaths.get(0),
                objectIdPaths, timing, repositoryName);
    }

    public static ObjectSnapshotRule transaction() {
        return transaction(Timing.AFTER_COMMIT);
    }

    public static ObjectSnapshotRule transaction(Timing timing) {
        return new ObjectSnapshotRule(Source.TRANSACTION, -1, "TransactionSnapshot", "globalSerialNumber",
                List.of("globalSerialNumber"), timing);
    }

    public static ObjectSnapshotRule transaction(Timing timing, String repositoryName) {
        return new ObjectSnapshotRule(Source.TRANSACTION, -1, "TransactionSnapshot", "globalSerialNumber",
                List.of("globalSerialNumber"), timing, repositoryName);
    }
}
