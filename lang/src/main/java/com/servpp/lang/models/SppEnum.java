package com.servpp.lang.models;

import com.servpp.lang.general.IConstance;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lidong@date 2023-11-28@version 1.0
 */
public class SppEnum extends SppCompilationUnit{
    List<SppEnumItem> items = new ArrayList<>();

    public SppEnum(String name) {
        super(name);
        setType(IConstance.CompilationUnitType.Enum);
    }

    public List<SppEnumItem> getItems() {
        return items;
    }

    public SppEnumItem getWireItem(String value) {
        return items.stream().filter(item -> item.acceptsWireValue(value)).findFirst().orElse(null);
    }

    static boolean bindsProviderString(SppField consumer, SppField provider) {
        return consumer.getArrayDimension() == 0 && provider.getArrayDimension() == 0
                && consumer.getType() instanceof SppEnum enumeration
                && "String".equals(provider.getType().getName())
                && enumeration.items.stream().anyMatch(item -> !item.getAliases().isEmpty());
    }

    public String codeFactory() {
        return preservesUnknownWireValues() ? "fromValue" : "fromCode";
    }

    public boolean preservesUnknownWireValues() {
        return getFallbackItem(SppEnumItem.Fallback.OTHER) != null;
    }

    public SppEnumItem getOtherItem() {
        SppEnumItem explicit = getFallbackItem(SppEnumItem.Fallback.OTHER);
        return explicit != null ? explicit : items.stream()
                .filter(item -> item.getFallback() == null && "OTHER".equals(item.getName()))
                .findFirst().orElse(null);
    }

    public SppEnumItem getFallbackItem(SppEnumItem.Fallback fallback) {
        return items.stream().filter(item -> item.getFallback() == fallback).findFirst().orElse(null);
    }

    public SppEnumItem getMissingItem() {
        SppEnumItem explicit = getFallbackItem(SppEnumItem.Fallback.NULL);
        return explicit != null ? explicit : getOtherItem();
    }

}
