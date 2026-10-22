package com.servpp.lang.models.app;

import java.util.ArrayList;
import java.util.List;

public class AppIndex {
    private String name;
    private boolean unique;
    public boolean isUnique() { return unique; }
    public void setUnique(boolean value) { unique = value; }
    private List<String> paths = new ArrayList<>();

    public AppIndex(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<String> getPaths() {
        return paths;
    }
}
