package com.servpp.service.repository;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Property access for validated declarations, including deserialized snapshot facts. */
public final class ObjectPropertyAccess {
    private ObjectPropertyAccess() { }

    public static Object read(Object object, String path) {
        if ("@storageIdentity".equals(path)) return object == null ? null
                : RepositoryParticipantSnapshots.objectIdentity(object,object.getClass().getSimpleName())
                    .orElseThrow(() -> new IllegalStateException("PERSISTENCE_READ_IDENTITY_REQUIRED"));
        Object current = object;
        for (String name : path.split("\\.")) {
            if (current == null) { return null; }
            current = property(current, name);
        }
        return current;
    }

    private static Object property(Object object, String name) {
        if ("@storageIdentity".equals(name)) return RepositoryParticipantSnapshots.objectIdentity(object,object.getClass().getSimpleName())
                .orElseThrow(() -> new IllegalStateException("PERSISTENCE_READ_IDENTITY_REQUIRED"));
        // JSON snapshots use maps; an omitted nullable field has the same meaning as null.
        if (object instanceof java.util.Map<?, ?> facts) return facts.get(name);
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String getter : new String[]{"get" + suffix, "is" + suffix, name}) {
            try {
                Method method = object.getClass().getMethod(getter);
                if (method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    return method.invoke(object);
                }
            } catch (NoSuchMethodException ignored) {
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read repository property " + name, e);
            }
        }
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read repository property " + name, e);
            }
        }
        throw new IllegalStateException("Undefined repository property " + name + " on " + object.getClass().getName());
    }
}
