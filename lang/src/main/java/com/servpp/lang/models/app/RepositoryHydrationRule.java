package com.servpp.lang.models.app;

import java.util.List;

public record RepositoryHydrationRule(String objectType,
                                      List<KeyTarget> keyTargets,
                                      String responseMethodName, String repositoryName,
                                      boolean appDdlInferred,
                                      String sourceHint) {
    public RepositoryHydrationRule {
        if ((objectType == null || objectType.isBlank()) && keyTargets != null && !keyTargets.isEmpty()) {
            objectType = keyTargets.get(0).getObjectType();
        }
        keyTargets = keyTargets == null ? List.of() : List.copyOf(keyTargets);
        sourceHint = sourceHint == null || sourceHint.isBlank() ? null : sourceHint;
    }

    public RepositoryHydrationRule(List<KeyTarget> keyTargets,
                                   String responseMethodName, String repositoryName) {
        this(null, keyTargets, responseMethodName, repositoryName);
    }

    public RepositoryHydrationRule(String objectType,
                                   List<KeyTarget> keyTargets,
                                   String responseMethodName, String repositoryName) {
        this(objectType, keyTargets, responseMethodName, repositoryName, false);
    }

    public RepositoryHydrationRule(String objectType,
                                   List<KeyTarget> keyTargets,
                                   String responseMethodName, String repositoryName,
                                   boolean appDdlInferred) {
        this(objectType, keyTargets, responseMethodName, repositoryName, appDdlInferred, null);
    }

    public RepositoryHydrationRule(int argumentIndex, String objectPath,
                                   String responseMethodName, String repositoryName) {
        this(null, List.of(new KeyTarget(argumentIndex, objectPath)), responseMethodName, repositoryName);
    }

    public RepositoryHydrationRule(int argumentIndex, String objectPath, String responseMethodName) {
        this(argumentIndex, objectPath, responseMethodName, null);
    }

    public int argumentIndex() {
        return firstKey().argumentIndex();
    }

    public String objectPath() {
        return firstKey().objectPath();
    }

    public String getObjectType() {
        return objectType;
    }

    public String getObjectIdPath() {
        return objectIdPath(objectPath());
    }

    public KeyTarget firstKey() {
        if (keyTargets.isEmpty()) {
            throw new IllegalStateException("Repository hydration rule must declare at least one key target.");
        }
        return keyTargets.get(0);
    }

    public static String objectType(String objectPath) {
        int index = objectPath.indexOf('.');
        return index < 0 ? objectPath : objectPath.substring(0, index);
    }

    public static String objectIdPath(String objectPath) {
        int index = objectPath.indexOf('.');
        return index < 0 ? "" : objectPath.substring(index + 1);
    }

    public record KeyTarget(int argumentIndex, String objectPath, String repositoryObjectPath) {
        public KeyTarget(int argumentIndex, String objectPath) {
            this(argumentIndex, objectPath, objectPath);
        }

        public boolean isPlatform() { return getObjectIdPath().startsWith("@platform:"); }
        public boolean isSingleton() { return "@singleton".equals(getObjectIdPath()); }
        public String getObjectType() {
            return objectType(objectPath);
        }

        public String getObjectIdPath() {
            return objectIdPath(objectPath);
        }

        public String getRepositoryObjectIdPath() {
            return objectIdPath(repositoryObjectPath);
        }
    }
}
