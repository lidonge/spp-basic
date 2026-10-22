package com.servpp.service.repository;

import java.beans.Introspector;
import java.util.*;

/** Resolve APP flattened column identities without exposing them in SPP. */
public final class ParticipantStorageKeys {
    private ParticipantStorageKeys() { }
    public static String path(Object value, String key) { return path(value,key,null); }
    public static String path(Object value, String key,com.servpp.service.runtime.AppDdlCatalog catalog) {
        String prefix = value.getClass().getSimpleName() + ".";
        if (key.startsWith(prefix)) key = key.substring(prefix.length());
        if (key.contains(".")) return key;
        String nativeKey = key;
        List<String> path = new ArrayList<>(); Object current = value;
        try {
            var direct = Arrays.stream(Introspector.getBeanInfo(value.getClass(),Object.class).getPropertyDescriptors())
                    .filter(property -> property.getName().equalsIgnoreCase(nativeKey)).findFirst();
            if (direct.isPresent()) return direct.get().getName();
            String[] segments=key.split("_");
            for (int index=0;index<segments.length;index++) {
                String segment=segments[index];
                if (current == null) throw new IllegalStateException("PARTICIPANT_IDENTITY_OWNER_REQUIRED");
                var role=catalog==null?null:catalog.roleStorage().get(current.getClass().getSimpleName());
                String remaining=String.join("_",Arrays.copyOfRange(segments,index,segments.length));
                if(role!=null && role.independent() && role.primaryKey().equalsIgnoreCase(remaining)) {
                    path.add("@storageIdentity");return String.join(".",path);
                }
                var properties = Introspector.getBeanInfo(current.getClass(),Object.class).getPropertyDescriptors();
                var property = Arrays.stream(properties).filter(candidate -> candidate.getName().equalsIgnoreCase(segment))
                        .findFirst().orElseThrow(() -> new IllegalStateException("PARTICIPANT_IDENTITY_COLUMN_UNMAPPED: " + segment));
                path.add(property.getName()); current = ObjectPropertyAccess.read(current,property.getName());
            }
            return String.join(".",path);
        } catch (java.beans.IntrospectionException failure) { throw new IllegalStateException("PARTICIPANT_IDENTITY_MODEL_INVALID",failure); }
    }
}
