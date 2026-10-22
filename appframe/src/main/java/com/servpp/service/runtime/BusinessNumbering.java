package com.servpp.service.runtime;

import com.servpp.service.repository.ObjectPropertyAccess;

/** APP supplies a business credential; it is unrelated to request or database identifiers. */
public final class BusinessNumbering {
    private BusinessNumbering() { }
    public static void assign(Object target, String path, String prefix) {
        if (target instanceof Iterable<?> values) { for (Object item : values) assign(item,path,prefix); return; }
        Object current = ObjectPropertyAccess.read(target, path);
        if (current != null && !current.toString().isBlank()) return;
        String[] parts = path.split("\\.");
        for (int i=0;i<parts.length-1;i++) target=ObjectPropertyAccess.read(target,parts[i]);
        if (target == null) throw new IllegalStateException("BUSINESS_NUMBER_OWNER_REQUIRED");
        String property=parts[parts.length-1];
        String setter="set"+Character.toUpperCase(property.charAt(0))+property.substring(1);
        try { target.getClass().getMethod(setter,String.class).invoke(target,prefix+"-"+java.util.UUID.randomUUID()); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("BUSINESS_NUMBER_ASSIGNMENT_FAILED",failure); }
    }
}
