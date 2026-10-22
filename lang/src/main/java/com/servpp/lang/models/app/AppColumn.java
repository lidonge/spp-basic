package com.servpp.lang.models.app;

import com.servpp.lang.models.SppClass;

/**
 * @author lidong@date 2023-12-05@version 1.0
 */
public class AppColumn {
    private boolean isPrimaryKey;
    private boolean nullable =true;
    private int primaryKeyModifierCount;
    private int nullableModifierCount;
    private int notNullModifierCount;

    private SppClass type;
    private int precision;
    private int scale;
    private String name;
    private boolean objectBacked = true;
    private boolean businessFieldShadow;

    public boolean isPrimaryKey() {
        return isPrimaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        isPrimaryKey = primaryKey;
    }

    public void addPrimaryKeyModifier() {
        primaryKeyModifierCount++;
        setPrimaryKey(true);
    }

    public int getPrimaryKeyModifierCount() {
        return primaryKeyModifierCount;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public void addNullableModifier() {
        nullableModifierCount++;
        setNullable(true);
    }

    public int getNullableModifierCount() {
        return nullableModifierCount;
    }

    public void addNotNullModifier() {
        notNullModifierCount++;
        setNullable(false);
    }

    public int getNotNullModifierCount() {
        return notNullModifierCount;
    }

    public boolean hasConflictingNullabilityModifiers() {
        return nullableModifierCount > 0 && notNullModifierCount > 0;
    }

    public SppClass getType() {
        return type;
    }

    public void setType(SppClass type) {
        this.type = type;
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        this.precision = precision;
    }

    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isObjectBacked() {
        return objectBacked;
    }

    public void setObjectBacked(boolean objectBacked) {
        this.objectBacked = objectBacked;
    }

    public boolean isBusinessFieldShadow() {
        return businessFieldShadow;
    }

    public void setBusinessFieldShadow(boolean businessFieldShadow) {
        this.businessFieldShadow = businessFieldShadow;
    }
}
