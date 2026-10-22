package com.servpp.lang.models.app;

import com.servpp.lang.general.app.SemanticException;
import com.servpp.lang.models.SppClass;
import com.servpp.lang.models.SppDomain;
import com.servpp.lang.models.SppField;
import com.servpp.lang.models.SppLocalVar;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

/**
 * @author lidong@date 2023-11-23@version 1.0
 */
public interface IQualifiedNameUtil {
    default SppLocalVar getQualifieField(String qualifiedName, Map<String, ? extends SppLocalVar> sppLocalVarHashMap){
        String[] parts = qualifiedName.split("\\.");
        SppLocalVar inst = sppLocalVarHashMap.get(parts[0]);
        if(inst == null)
            return null;
        if (parts.length == 1) { return inst; }
        if (!(inst.getType() instanceof SppClass cls)) { return null; }
        for(int i = 1;i<parts.length;i++){
            inst = cls.getField(parts[i]);

            if(inst == null){
                return null;
            }
            if (i < parts.length - 1) {
                if (!(inst.getType() instanceof SppClass)) {
                    return null;
                }
                cls = (SppClass) inst.getType();
            }
        }
        return inst;

    }

    default SppFieldReference getQualifiedField(SppDomain sppDomain, SppClass mapEntity, String qualifiedName) throws SemanticException {
        SppField sppField = null;
        SppFieldReference fieldDefine = null;
        String[] path = qualifiedName.split("\\.");
        if(path.length == 1){
            if(mapEntity == null){
                throw new SemanticException(qualifiedName + " is not a qualified name.");
            }else {
                sppField = mapEntity.getField(path[0]);
                fieldDefine = new SppFieldReference(sppField, mapEntity.getName(), path[0]);
            }
        }else {
            Iterator<String> iterator = Arrays.stream(path).iterator();
            String ownerTypeName = iterator.next();
            SppClass sppClass = (SppClass) sppDomain.getSppClass(ownerTypeName);
            if (sppClass == null) {
                throw new SemanticException("Entity " + path[0] + " not defined!");
            } else {
                StringBuilder objectPath = new StringBuilder();
                while (iterator.hasNext()) {
                    String fieldName = iterator.next();
                    sppField = sppClass.getField(fieldName);
                    if (sppField == null) {
                        fieldDefine = null;
                        break;
                    }
                    if (objectPath.length() > 0) {
                        objectPath.append('.');
                    }
                    objectPath.append(fieldName);
                    fieldDefine = new SppFieldReference(sppField, ownerTypeName, objectPath.toString());
                    if (iterator.hasNext()) {
                        if (!(sppField.getType() instanceof SppClass nextClass)) {
                            fieldDefine = null;
                            break;
                        }
                        sppClass = nextClass;
                    }
                }
            }
        }
        if (fieldDefine == null || fieldDefine.getSppField() == null) {
            return null;
        }
        if (!fieldDefine.getObjectPath().contains(".")) {
            fieldDefine = sppDomain.addSppFieldReference(fieldDefine.getSppField());
        }
        return fieldDefine;
    }

}
