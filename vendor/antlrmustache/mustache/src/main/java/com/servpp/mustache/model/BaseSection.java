package com.servpp.mustache.model;

/**
 * @author lidong@date 2024-07-27@version 1.0
 */
public class BaseSection implements IStatement{
    public enum SectionType{
        Normal, First,Last, FirstAndLast
    }
    private String sectionName;
    private boolean exprSection = false;
    private Template subTemplate;
    private SectionType sectionType = SectionType.Normal;

    public boolean isExprSection() {
        return exprSection;
    }

    public void setExprSection(boolean exprSection) {
        this.exprSection = exprSection;
    }

    public String getSectionName() {
        return sectionName;
    }

    public BaseSection setSectionName(String sectionName) {
        this.sectionName = sectionName;
        if("-first".equals(sectionName))
            this.sectionType = SectionType.First;
        else if("-last".equals(sectionName))
            this.sectionType = SectionType.Last;
        return this;
    }

    public Template getSubTemplate() {
        return subTemplate;
    }

    public BaseSection setSubTemplate(Template subTemplate) {
        this.subTemplate = subTemplate;
        return this;
    }

    public SectionType getSectionType() {
        return sectionType;
    }
}
