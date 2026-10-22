package com.servpp.generator.util;

import com.servpp.lang.models.app.INamedObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author lidong@date 2023-12-08@version 1.0
 */
public class NamedArray<T extends INamedObject>{
    private ArrayList<T> arrayList = new ArrayList<T>();
    private final Map<String,T> map = new HashMap<String, T>();

    public ArrayList<T> getArrayList() {
        return arrayList;
    }

    public Map<String, T> getMap() {
        return map;
    }

    public boolean add(T object){
        if(get(object.getName()) != null)
            return false;
        map.put(object.getName(),object);
        return arrayList.add(object);
    }

    public void reindex(String oldName, T object) {
        map.remove(oldName);
        map.put(object.getName(), object);
    }

    public T remove(int index) {
        T ret = arrayList.remove(index);
        if(ret != null)
            map.remove(ret.getName());
        return ret;
    }

    public boolean remove(T o) {
        map.remove(o.getName());
        return arrayList.remove(o);
    }

    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    public boolean containsValue(T value) {
        return map.containsKey(value.getName());
    }

    public T get(String key) {
        return map.get(key);
    }

    @Override
    public String toString() {
        return "NamedArray{" +
                "arrayList=" + arrayList +
                '}';
    }
}
