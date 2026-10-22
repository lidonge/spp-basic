package com.servpp.lang.models.app;

import com.servpp.lang.models.SppCompilationUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lidong@date 2023-12-05@version 1.0
 */
public class AppForeign {
    List<SppFieldReference> keys = new ArrayList<>();
    private String declaredForeignReferenceName;
    private SppCompilationUnit foreignReference;

    public List<SppFieldReference> getKeys() {
        return keys;
    }

    public SppCompilationUnit getForeignReference() {
        return foreignReference;
    }

    public String getDeclaredForeignReferenceName() {
        return declaredForeignReferenceName;
    }

    public void setDeclaredForeignReferenceName(String declaredForeignReferenceName) {
        this.declaredForeignReferenceName = declaredForeignReferenceName;
    }

    public void setForeignReference(SppCompilationUnit foreignReference) {
        this.foreignReference = foreignReference;
    }
}
