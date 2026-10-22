package com.servpp.lang.models.app;

import java.util.ArrayList;
import java.util.List;

import com.servpp.lang.models.Annotation;

public class AppQuantumStore extends Annotation {
    private String name;
    private final List<String> attributeIds = new ArrayList<>();

    public AppQuantumStore(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAttributeIds() {
        return attributeIds;
    }

    public void addAttributeId(String attributeId) {
        attributeIds.add(attributeId);
    }
}
