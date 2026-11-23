package com.servpp.lang.models;

public class QuantumServiceAttribute {
    private final int argumentIndex;
    private final String groupId;
    private final String attributeId;
    private final String propertyName;

    public QuantumServiceAttribute(int argumentIndex, String groupId, String attributeId, String propertyName) {
        this.argumentIndex = argumentIndex;
        this.groupId = groupId;
        this.attributeId = attributeId;
        this.propertyName = propertyName;
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

    public String getPropertyName() {
        return propertyName;
    }
}
