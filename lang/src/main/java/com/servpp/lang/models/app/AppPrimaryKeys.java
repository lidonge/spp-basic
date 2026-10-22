package com.servpp.lang.models.app;

import com.servpp.lang.models.Annotation;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lidong@date 2023-11-21@version 1.0
 */
public class AppPrimaryKeys extends Annotation {
    List<SppFieldReference> keys = new ArrayList<>();

    public List<SppFieldReference> getKeys() {
        return keys;
    }

    public String addKey(String qualifiedName, SppFieldReference sppFieldReference){
        if(keys.indexOf(sppFieldReference) != -1){
            return "Duplicate primary key " + qualifiedName;
        }
        keys.add(sppFieldReference);
        return null;
    }

    @Override
    public String toString() {
        return "PrimaryKeys{" +
                "keys=" + keys +
                '}';
    }
}
