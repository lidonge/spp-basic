package com.servpp.lang.models;

import com.servpp.lang.general.IConstance;

/**
 * @author lidong@date 2023-11-28@version 1.0
 */
public class SppCompilationUnit extends Annotation {
    private String basePackage;
    private String domainName;
    private String additionalPackage;
    private String name;

    private boolean isReal = true;
    protected IConstance.CompilationUnitType type;


    public SppCompilationUnit(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public IConstance.CompilationUnitType getType() {
        return type;
    }

    public void setType(IConstance.CompilationUnitType type) {
        this.type = type;
    }

    public boolean isReal() {
        return isReal;
    }

    public void setReal(boolean real) {
        isReal = real;
    }

    public String getBasePackage() {
        return basePackage;
    }

    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public String getAdditionalPackage() {
        return additionalPackage;
    }

    public void setAdditionalPackage(String additionalPackage) {
        this.additionalPackage = additionalPackage;
    }

    public String getPackageName() {
        if(additionalPackage != null)
            return basePackage +"." + domainName +"."+additionalPackage;
        else
            return basePackage +"." + domainName;
    }

    public void copyFrom(SppCompilationUnit cls) {
        name = cls.name;
        type = cls.type;
        isReal = cls.isReal;
        annotations = cls.annotations;
    }

}
