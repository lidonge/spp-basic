package com.servpp.lang.models.app;

import com.servpp.lang.models.Annotation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author lidong@date 2023-11-21@version 1.0
 */
public class AppHeader extends Annotation implements INamedObject{
    private String name;
    private boolean isTemp;
    private List<AppHeader> extendsHeaders = new ArrayList<>();
    private List<SppExtendField> sppExtendFields = new ArrayList<>();
    private Set<String> duplicateExtendsHeaderNames = new LinkedHashSet<>();
    private Set<String> duplicateFieldNames = new LinkedHashSet<>();

    public boolean isTemp() {
        return isTemp;
    }

    public void setTemp(boolean temp) {
        isTemp = temp;
    }

    public String addExtendsHeader(AppHeader appHeader){
        String error = appHeader.addToList(extendsHeaders, appHeader);
        if (error != null && appHeader.getName() != null) {
            duplicateExtendsHeaderNames.add(appHeader.getName());
        }
        return error;
    }

    public void _addExtendField(SppExtendField field){
        sppExtendFields.add(field);
    }
    public String addExtendField(SppExtendField field){
        String error = field.addToList(sppExtendFields, field);
        if (error != null && field.getName() != null) {
            duplicateFieldNames.add(field.getName());
        }
        return error;
    }
    public List<AppHeader> getExtendsHeaders() {
        return extendsHeaders;
    }

    public Set<String> getDuplicateExtendsHeaderNames() {
        return duplicateExtendsHeaderNames;
    }

    public List<SppExtendField> getSppExtendFields() {
        return sppExtendFields;
    }

    public Set<String> getDuplicateFieldNames() {
        return duplicateFieldNames;
    }

    public SppExtendField findField(String fieldName) {
        return findField(fieldName, new LinkedHashSet<>());
    }

    private SppExtendField findField(String fieldName, Set<AppHeader> visited) {
        if (!visited.add(this)) {
            return null;
        }
        for (SppExtendField field : sppExtendFields) {
            if (fieldName.equals(field.getName())) {
                return field;
            }
        }
        for (AppHeader header : extendsHeaders) {
            SppExtendField field = header.findField(fieldName, visited);
            if (field != null) {
                return field;
            }
        }
        return null;
    }

    public boolean hasUniqueIdempotencyField() {
        return isUniqueField("globalSerialNumber") || isUniqueField("messageId");
    }

    public String getPreferredUniqueIdempotencyFieldName() {
        if (isUniqueField("globalSerialNumber")) {
            return "globalSerialNumber";
        }
        if (isUniqueField("messageId")) {
            return "messageId";
        }
        return null;
    }

    private boolean isUniqueField(String fieldName) {
        SppExtendField declaredField = findDeclaredField(fieldName);
        if (declaredField != null) {
            return declaredField.isUnique() && isStringField(declaredField);
        }
        List<SppExtendField> inheritedFields = findInheritedFields(fieldName, new LinkedHashSet<>());
        if (inheritedFields.size() != 1) {
            return false;
        }
        SppExtendField field = inheritedFields.get(0);
        return field != null && field.isUnique() && isStringField(field);
    }

    private SppExtendField findDeclaredField(String fieldName) {
        for (SppExtendField field : sppExtendFields) {
            if (fieldName.equals(field.getName())) {
                return field;
            }
        }
        return null;
    }

    private List<SppExtendField> findInheritedFields(String fieldName, Set<AppHeader> visited) {
        List<SppExtendField> fields = new ArrayList<>();
        for (AppHeader header : extendsHeaders) {
            collectInheritedFields(header, fieldName, visited, fields);
        }
        return fields;
    }

    private void collectInheritedFields(AppHeader header, String fieldName, Set<AppHeader> visited,
                                        List<SppExtendField> fields) {
        if (header == null || header.isTemp() || !visited.add(header)) {
            return;
        }
        SppExtendField declaredField = header.findDeclaredField(fieldName);
        if (declaredField != null) {
            fields.add(declaredField);
        }
        for (AppHeader extendsHeader : header.getExtendsHeaders()) {
            collectInheritedFields(extendsHeader, fieldName, visited, fields);
        }
    }

    public List<SppExtendField> getDeclaredUniqueIdempotencyFields() {
        List<SppExtendField> fields = new ArrayList<>();
        for (SppExtendField field : sppExtendFields) {
            if (field.isUnique()
                    && ("globalSerialNumber".equals(field.getName()) || "messageId".equals(field.getName()))) {
                fields.add(field);
            }
        }
        return fields;
    }

    public boolean isStringField(SppExtendField field) {
        return field != null && field.getType() != null && "String".equals(field.getType().getName());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "AppHeader{" +
                "name='" + name + '\'' +
                ", isTemp=" + isTemp +
                ", extendsHeaders=" + extToString() +
                ", sppExtendFields=" + sppExtendFields +
                '}';
    }
    private String extToString(){
        String ret = "";
        for(AppHeader header:extendsHeaders){
            ret += header.name+",";
        }
        return ret;
    }
}
