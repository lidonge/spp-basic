package com.servpp.lang.models;

import java.util.List;

/**
 * @author lidong@date 2023-12-01@version 1.0
 */
public class SppEnumItem {
    public enum Fallback { OTHER, NULL }

    String name;
    private List<String> aliases = List.of();
    private Fallback fallback;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = List.copyOf(aliases);
    }

    public String getWireValue() {
        return aliases.isEmpty() ? name : aliases.get(0);
    }

    public List<String> getWireValues() {
        return aliases.isEmpty() ? List.of(name) : aliases;
    }

    public boolean acceptsWireValue(String value) {
        return getWireValues().contains(value);
    }

    public Fallback getFallback() {
        return fallback;
    }

    public void setFallback(Fallback fallback) {
        this.fallback = fallback;
    }
}
