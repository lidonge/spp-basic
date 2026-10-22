package com.servpp.lang.models.app;

import com.servpp.lang.models.SppField;

/**
 * @author lidong@date 2023-11-22@version 1.0
 */
public class SppFieldReference {
//    private SppClass sppClass;
    private SppField sppField;
    private String ownerTypeName;
    private String objectPath;
    private String qualifiedName;

    public SppFieldReference(SppField sppField) {
//        this.sppClass = sppClass;
        this.sppField = sppField;
        if (sppField != null) {
            this.ownerTypeName = sppField.getParent().getName();
            this.objectPath = sppField.getName();
            this.qualifiedName = ownerTypeName + "." + objectPath;
        }
    }

    public SppFieldReference(SppField sppField, String ownerTypeName, String objectPath) {
        this(sppField);
        if (ownerTypeName != null && !ownerTypeName.isBlank()) {
            this.ownerTypeName = ownerTypeName;
        }
        if (objectPath != null && !objectPath.isBlank()) {
            this.objectPath = objectPath;
        }
        if (this.ownerTypeName != null && this.objectPath != null) {
            this.qualifiedName = this.ownerTypeName + "." + this.objectPath;
        }
    }

//    public SppClass getSppClass() {
//        return sppClass;
//    }
//
//    public void setSppClass(SppClass sppClass) {
//        this.sppClass = sppClass;
//    }

    public SppField getSppField() {
        return sppField;
    }

    public void setSppField(SppField sppField) {
        this.sppField = sppField;
    }

    public String getOwnerTypeName() {
        return ownerTypeName;
    }

    public String getObjectPath() {
        return objectPath == null && sppField != null ? sppField.getName() : objectPath;
    }

    public String getQualifiedName() {
        return qualifiedName == null && sppField != null
                ? sppField.getParent().getName() + "." + getObjectPath()
                : qualifiedName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SppFieldReference that = (SppFieldReference) o;
        return sppField.getParent().equals(that.getSppField().getParent())
                && sppField.equals(that.sppField)
                && getQualifiedName().equals(that.getQualifiedName());
    }

    @Override
    public String toString() {
        return "SppFieldDefine{" +
                "sppClass=" + sppField.getParent().getName() +
                ", sppField=" + sppField +
                '}';
    }
}
