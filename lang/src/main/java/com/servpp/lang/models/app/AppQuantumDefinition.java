package com.servpp.lang.models.app;

import java.util.LinkedHashSet;
import java.util.Set;

public class AppQuantumDefinition {
    private final String attributeId;
    private String numberType;
    private String minValue;
    private String maxValue;
    private String scale;
    private final Set<String> declaredProperties = new LinkedHashSet<>();
    private final Set<String> duplicateProperties = new LinkedHashSet<>();

    public AppQuantumDefinition(String attributeId) {
        this.attributeId = attributeId;
    }

    public String getAttributeId() {
        return attributeId;
    }

    public String getNumberType() {
        return numberType;
    }

    public void setNumberType(String numberType) {
        declare("numberType");
        this.numberType = numberType;
    }

    public String getMinValue() {
        return minValue;
    }

    public void setMinValue(String minValue) {
        declare("minValue");
        this.minValue = minValue;
    }

    public String getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(String maxValue) {
        declare("maxValue");
        this.maxValue = maxValue;
    }

    public String getScale() {
        return scale;
    }

    public void setScale(String scale) {
        declare("scale");
        this.scale = scale;
    }

    public Set<String> getDeclaredProperties() {
        return Set.copyOf(declaredProperties);
    }

    public Set<String> getDuplicateProperties() {
        return Set.copyOf(duplicateProperties);
    }

    private void declare(String property) {
        if (!declaredProperties.add(property)) {
            duplicateProperties.add(property);
        }
    }
}
