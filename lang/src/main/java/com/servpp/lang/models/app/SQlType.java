package com.servpp.lang.models.app;

/**
 * @author lidong@date 2023-11-22@version 1.0
 */
public class SQlType {
    String type;
    Boolean notnull;
    Integer precision;
    Integer scale;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean isNotnull() {
        return notnull;
    }

    public void setNotnull(boolean notnull) {
        this.notnull = notnull;
    }

    public Integer getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        this.precision = precision;
    }

    public Integer getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    @Override
    public String toString() {
        return "SQlType{" +
                "type='" + type + '\'' +
                ", notnull=" + notnull +
                ", precision=" + precision +
                ", scale=" + scale +
                '}';
    }
}
