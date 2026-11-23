package com.servpp.lang.models;

import java.util.List;

public class QuantumServiceAttribute {
    private final int argumentIndex;
    private final String groupId;
    private final String attributeId;
    private final String propertyName;
    private final List<String> playKeyPaths;

    public QuantumServiceAttribute(int argumentIndex, String groupId, String attributeId, String propertyName) {
        this(argumentIndex, groupId, attributeId, propertyName, List.of());
    }

    QuantumServiceAttribute(int argumentIndex, String groupId, String attributeId, String propertyName,
                            List<String> playKeyPaths) {
        this.argumentIndex = argumentIndex;
        this.groupId = groupId;
        this.attributeId = attributeId;
        this.propertyName = propertyName;
        this.playKeyPaths = List.copyOf(playKeyPaths);
    }

    public int getArgumentIndex() {
        return argumentIndex;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getAttributeId() {
        return attributeId;
    }

    List<String> getPlayKeyPaths() { return playKeyPaths; }

    public String getPropertyName() {
        return propertyName;
    }
}
