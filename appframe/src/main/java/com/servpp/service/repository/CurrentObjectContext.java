package com.servpp.service.repository;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class CurrentObjectContext {
    private static final ThreadLocal<Deque<Object[]>> CONTEXT =
            ThreadLocal.withInitial(ArrayDeque::new);

    public static Runnable bind(Runnable action) {
        var captured = new ArrayDeque<>(CONTEXT.get());
        return () -> {
            var previous = CONTEXT.get(); CONTEXT.set(new ArrayDeque<>(captured));
            try { action.run(); } finally { CONTEXT.set(previous); }
        };
    }

    private CurrentObjectContext() {
    }

    public static <T> T withObjects(Object[] objects, Supplier<T> supplier) {
        Deque<Object[]> stack = CONTEXT.get();
        stack.push(objects == null ? new Object[0] : objects);
        try {
            return supplier.get();
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                CONTEXT.remove();
            }
        }
    }

    public static Optional<Object> find(String objectType) {
        if (objectType == null || objectType.isBlank()) {
            return Optional.empty();
        }
        return findCandidate(objectType, null);
    }

    public static Optional<Object> find(String objectType, String objectIdPath) {
        if (objectType == null || objectType.isBlank()) {
            return Optional.empty();
        }
        return findCandidate(objectType, objectIdPath);
    }

    public static List<Object> findAll(String objectType) {
        if (objectType == null || objectType.isBlank()) {
            return List.of();
        }
        List<Object> matches = new ArrayList<>();
        for (Object[] objects : CONTEXT.get()) {
            Map<Object, Boolean> visited = new IdentityHashMap<>();
            for (Object object : objects) {
                find(object, objectType, null, visited, matches);
            }
        }
        return List.copyOf(matches);
    }

    private static Optional<Object> findCandidate(String objectType, String objectIdPath) {
        for (Object[] objects : CONTEXT.get()) {
            Map<Object, Boolean> visited = new IdentityHashMap<>();
            List<Object> matches = new ArrayList<>();
            for (Object object : objects) {
                find(object, objectType, objectIdPath, visited, matches);
            }
            if (matches.size() > 1) {
                throw new IllegalStateException("Current object context for " + objectType
                        + (objectIdPath == null || objectIdPath.isBlank() ? "" : "." + objectIdPath)
                        + " is ambiguous; found " + matches.size() + " candidates.");
            }
            if (matches.size() == 1) {
                return Optional.of(matches.get(0));
            }
        }
        return Optional.empty();
    }

    private static void find(Object value, String objectType, String objectIdPath, Map<Object, Boolean> visited,
                             List<Object> matches) {
        if (value == null || isLeafType(value.getClass()) || visited.put(value, Boolean.TRUE) != null) {
            return;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                find(Array.get(value, i), objectType, objectIdPath, visited, matches);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                find(item, objectType, objectIdPath, visited, matches);
            }
            return;
        }
        if (type.getSimpleName().equals(objectType) && hasUsableObjectId(value, objectIdPath)) {
            matches.add(value);
        }
        findFromAccessors(value, objectType, objectIdPath, visited, matches);
        findFromFields(value, objectType, objectIdPath, visited, matches);
    }

    private static void findFromAccessors(Object value, String objectType, String objectIdPath,
                                          Map<Object, Boolean> visited, List<Object> matches) {
        for (Method method : value.getClass().getMethods()) {
            if (method.getParameterCount() != 0
                    || method.getReturnType() == Void.TYPE
                    || method.getDeclaringClass() == Object.class
                    || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            try {
                find(method.invoke(value), objectType, objectIdPath, visited, matches);
            } catch (ReflectiveOperationException ignored) {
                // Try the remaining bean accessors, then fields.
            }
        }
    }

    private static void findFromFields(Object value, String objectType, String objectIdPath,
                                       Map<Object, Boolean> visited, List<Object> matches) {
        Class<?> current = value.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    find(field.get(value), objectType, objectIdPath, visited, matches);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Continue scanning other fields.
                }
            }
            current = current.getSuperclass();
        }
    }

    private static boolean hasUsableObjectId(Object value, String objectIdPath) {
        if (objectIdPath == null || objectIdPath.isBlank()) {
            return true;
        }
        Object objectId = readPath(value, objectIdPath);
        return objectId != null && !objectId.toString().isBlank();
    }

    private static Object readPath(Object value, String path) {
        Object current = value;
        for (String property : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            current = readProperty(current, property);
        }
        return current;
    }

    private static Object readProperty(Object value, String property) {
        Class<?> type = value.getClass();
        String accessorSuffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (String methodName : java.util.List.of("get" + accessorSuffix, "is" + accessorSuffix, property)) {
            try {
                Method method = type.getMethod(methodName);
                if (method.getParameterCount() == 0) {
                    return method.invoke(value);
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next bean style before falling back to field access.
            }
        }
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(property);
                field.setAccessible(true);
                return field.get(value);
            } catch (ReflectiveOperationException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static boolean isLeafType(Class<?> type) {
        return type.isPrimitive()
                || type.isEnum()
                || CharSequence.class.isAssignableFrom(type)
                || Number.class.isAssignableFrom(type)
                || Boolean.class == type
                || Character.class == type
                || type.getName().startsWith("java.time.");
    }
}
