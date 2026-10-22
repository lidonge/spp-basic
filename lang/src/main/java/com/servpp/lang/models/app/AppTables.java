package com.servpp.lang.models.app;

import com.servpp.lang.models.Annotation;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lidong@date 2023-12-05@version 1.0
 */
public class AppTables extends Annotation implements INamedObject{
    public enum Category {
        entities, roles, contracts, other;

        public static Category fromName(String name) {
            for (Category category : values()) {
                if (category.name().equals(name)) {
                    return category;
                }
            }
            return other;
        }
    }

    String name;
    Category category;
    List<AppTable> appTableList = new ArrayList<>();

    public AppTables(String name) {
        this.name = name;
        this.category = Category.fromName(name);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.category = Category.fromName(name);
    }

    public Category getCategory() {
        return category;
    }

    public boolean isKnownCategory() {
        return category != Category.other;
    }

    public List<AppTable> getAppTableList() {
        return appTableList;
    }
}
