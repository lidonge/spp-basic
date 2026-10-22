package com.servpp.service.runtime;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Bridges source-language collection parameters and generated model properties. */
public final class ParticipantCollections {
    private ParticipantCollections() { }
    public static int length(Object values) {
        if (values instanceof java.util.Collection<?> collection) return collection.size();
        if (values != null && values.getClass().isArray()) return Array.getLength(values);
        throw new IllegalArgumentException("PARTICIPANT_COLLECTION_REQUIRED");
    }
    public static <T> List<T> list(T[] values) { return values == null ? null : new ArrayList<>(Arrays.asList(values)); }
    @SuppressWarnings("unchecked")
    public static <T> List<T> list(Object values) {
        if (values == null) { return null; }
        if (values instanceof List<?> list) return (List<T>) list;
        if (!values.getClass().isArray()) { throw new IllegalArgumentException("PARTICIPANT_COLLECTION_REQUIRES_ARRAY"); }
        List<T> result = new ArrayList<>(Array.getLength(values));
        for (int i=0; i<Array.getLength(values); i++) { result.add((T)Array.get(values,i)); }
        return result;
    }
    /** Returns a new sequence; neither the input sequence nor its existing elements are mutated. */
    @SuppressWarnings("unchecked")
    public static <T> List<T> append(Object values, T element) {
        if (values == null || element == null) { throw new IllegalArgumentException("PARTICIPANT_APPEND_REQUIRES_VALUES"); }
        List<T> result = values instanceof List<?> list ? new ArrayList<>((List<T>) list) : new ArrayList<>(ParticipantCollections.<T>list(values));
        result.add(element); return result;
    }
    public static Object array(List<?> values, Class<?> component) {
        if (values == null) { return null; }
        Object result = Array.newInstance(component, values.size());
        for (int i=0; i<values.size(); i++) { Array.set(result,i,values.get(i)); }
        return result;
    }
}
