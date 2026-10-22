package com.servpp.mustache.model;

/**
 * @author lidong@date 2024-07-27@version 1.0
 */
public class Section extends BaseSection {
    private boolean mapAsList = false;
    private boolean recursive = false;

    public boolean isMapAsList() {
        return mapAsList;
    }

    public void setMapAsList(boolean mapAsList) {
        this.mapAsList = mapAsList;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }
}
