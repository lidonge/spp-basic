package com.servpp.lang.models.app;

import java.util.HashMap;
import java.util.Map;

/**
 * @author lidong@date 2023-11-29@version 1.0
 */
public class AnnotationDefine {
    private String name;
    private Map<String,String> parameters = new HashMap<>();

    public AnnotationDefine(String name) {
        this.name = name;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void addParameter(String name, String value) {
        parameters.put(name,value);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
