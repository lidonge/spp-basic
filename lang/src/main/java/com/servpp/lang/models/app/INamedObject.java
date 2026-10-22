package com.servpp.lang.models.app;

import java.util.List;

/**
 * @author lidong@date 2023-11-22@version 1.0
 */
public interface INamedObject<T extends INamedObject> {
    String getName();
    default String addToList(List<T> namedObjects, T namedObject){
        for (int i = 0; i < namedObjects.size(); i++) {
            T object = namedObjects.get(i);
            if (object.getName().equals(namedObject.getName())) {
                return "Duplicate name "+ namedObject.getName() +" at position: " + i;
            }
        }
        namedObjects.add(namedObject);
        return null;
    }

    default T searchAppHeader(List<T> namedObjects, T namedObject){
        for (T object : namedObjects) {
            if (object.getName().equals(namedObject.getName())) {
                return object;
            }
        }
        return null;
    }

}
