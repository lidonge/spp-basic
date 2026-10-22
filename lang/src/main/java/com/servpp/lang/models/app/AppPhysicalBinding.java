package com.servpp.lang.models.app;

public class AppPhysicalBinding {
    public static final String ALL_TARGETS = "*";

    public enum Kind {
        at,
        xa,
        tcc,
        quantum,
        saga,
        repository
    }

    private final Kind kind;
    private final String targetName;

    public AppPhysicalBinding(Kind kind, String targetName) {
        this.kind = kind;
        this.targetName = targetName;
    }

    public Kind getKind() {
        return kind;
    }

    public String getTargetName() {
        return targetName;
    }

    public boolean isAllTargets() {
        return ALL_TARGETS.equals(targetName);
    }

    public String key() {
        return kind.name() + ":" + targetName;
    }
}
