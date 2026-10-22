package com.servpp.generator.openapi;

import com.servpp.lang.models.SppCompilationUnit;

import java.io.File;

/**
 * @author lidong@date 2023-11-15@version 1.0
 */
public class ClassWriterConfig {
    protected File domainPath;
    protected SppCompilationUnit sppCompilationUnit;
    protected String domainName;
    private String basePackage;
    public ClassWriterConfig(File domainPath, SppCompilationUnit sppCompilationUnit, String basePackage, String domainName) {
        this.domainPath = domainPath;
        this.sppCompilationUnit = sppCompilationUnit;
        this.basePackage =basePackage;
        this.domainName = domainName;
    }
    public File getDomainPath() {
        return domainPath;
    }

    public String getDomainName() {
        return domainName;
    }

    public String getBasePackage() {
        return basePackage;
    }
}
