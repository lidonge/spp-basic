package com.servpp.service.repository;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Objects;

public interface ObjectRepository {
    /** Identity of the resource whose local transaction is shared by repository calls.
     * Implementations return the same object only when executeInTransaction actually joins
     * that resource's current transaction. Unknown resources must not be coalesced. */
    default Object transactionResourceIdentity() { return null; }

    /** Execute a local Saga stage against this repository's own resource. */
    default void executeInTransaction(Runnable action) { action.run(); }

    void save(String objectType, String objectId, Object value);

    default void verifyWriteTransaction() {
        throw new UnsupportedOperationException("Repository has no declared-transaction write support");
    }

    default void insert(String objectType, String objectId, Object value) {
        throw new UnsupportedOperationException("Repository does not support insert-only writes");
    }

    default void insertIfAbsent(String objectType, String objectId, Object value) {
        throw new UnsupportedOperationException("Repository does not support insert-if-absent writes");
    }

    default void update(String objectType, String objectId, Object value) {
        throw new UnsupportedOperationException("Repository does not support update-only writes");
    }

    /** Default APP updates require a reliable query revision and an atomic conditional write. */
    default void updateFromRead(String objectType, String objectId, Object value) {
        throw new UnsupportedOperationException("PERSISTENCE_OPTIMISTIC_UPDATE_UNSUPPORTED");
    }

    default void delete(String objectType, String objectId) {
        throw new UnsupportedOperationException("Repository does not support delete-only writes");
    }

    default void compareAndSet(String objectType, String objectId, Object value,
                               String versionPath, Object expectedVersion) {
        throw new UnsupportedOperationException("Repository does not support atomic version-checked writes");
    }

    /** Prepare only transient metadata after a predicate miss; this does not identify or persist the participant. */
    default void prepareUnidentifiedParticipant(String objectType,Object participant) { }

    default java.util.Map<String,Object> platformFacts(String objectType,String objectId) { return java.util.Map.of(); }
    default void compareAndSetPlatform(String objectType,String objectId,Object value,String column,Object expectedVersion) {
        throw new UnsupportedOperationException("Repository does not support APP platform version checks");
    }

    Optional<?> find(String objectType, String objectId);

    /** Lock an exact identity in the caller's existing declared transaction. */
    default <T> Optional<T> findForUpdate(String objectType, String objectId, Class<T> type) {
        throw new UnsupportedOperationException("Repository does not support declared-transaction locked reads");
    }

    default <T> Optional<T> find(String objectType, String objectId, Class<T> type) {
        return find(objectType, objectId).map(value -> {
            if (!type.isInstance(value)) {
                throw new IllegalStateException(objectType + " " + objectId
                        + " is not a " + type.getName() + ": " + value.getClass().getName());
            }
            return type.cast(value);
        });
    }

    default <T> T require(String objectType, String objectId, Class<T> type) {
        return find(objectType, objectId, type)
                .orElseThrow(() -> new IllegalStateException(objectType + " not found: " + objectId));
    }

    default <T> List<T> findAll(String objectType, Class<T> type) {
        throw new UnsupportedOperationException("Object repository does not support collection query.");
    }

    default <T> List<T> findBy(String objectType, String keyPath, Object keyValue, Class<T> type) {
        throw new UnsupportedOperationException("Object repository does not support filtered collection query.");
    }

    default <T> List<T> findBy(String objectType, List<String> keyPaths, List<?> keyValues, Class<T> type) {
        if (keyPaths == null || keyValues == null || keyPaths.size() != keyValues.size()) {
            throw new IllegalArgumentException("Object repository composite query keys and values must match.");
        }
        if (keyPaths.isEmpty()) {
            return findAll(objectType, type);
        }
        List<T> values = findBy(objectType, keyPaths.get(0), keyValues.get(0), type);
        if (keyPaths.size() == 1 || values.isEmpty()) {
            return values;
        }
        List<T> filtered = new ArrayList<>();
        for (T value : values) {
            boolean matches = true;
            for (int i = 1; i < keyPaths.size(); i++) {
                if (!Objects.equals(readPath(value, keyPaths.get(i)), keyValues.get(i))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                filtered.add(value);
            }
        }
        return List.copyOf(filtered);
    }

    default <T> List<T> findByForUpdate(String objectType, List<String> keyPaths, List<?> keyValues, Class<T> type) {
        throw new UnsupportedOperationException("Repository does not support transaction-protected filtered reads");
    }

    /** Missing data returns false without modifying the lookup target; storage failures still throw. */
    default <T> boolean hydrateIfPresent(String objectType, String objectId, T target) {
        throw new UnsupportedOperationException("Repository does not support optional hydration");
    }

    <T> T hydrate(String objectType, String objectId, T target);

    private static Object readPath(Object value, String keyPath) {
        Object current = value;
        for (String segment : keyPath.split("\\.")) {
            current = readProperty(current, segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static Object readProperty(Object value, String propertyName) {
        if (value == null || propertyName == null || propertyName.isBlank()) {
            return null;
        }
        Class<?> type = value.getClass();
        for (Method method : type.getMethods()) {
            if (method.getParameterCount() == 0 && methodNameMatches(method.getName(), propertyName)) {
                try {
                    return method.invoke(value);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot read " + method.getName()
                            + " from " + type.getName(), e);
                }
            }
        }
        Field field = findField(type, propertyName);
        if (field == null) {
            return null;
        }
        try {
            return field.get(value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read " + field.getName()
                    + " from " + type.getName(), e);
        }
    }

    private static Field findField(Class<?> type, String propertyName) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (propertyMatches(field.getName(), propertyName)) {
                    field.setAccessible(true);
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean methodNameMatches(String methodName, String propertyName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return propertyMatches(methodName.substring(3), propertyName);
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return propertyMatches(methodName.substring(2), propertyName);
        }
        return propertyMatches(methodName, propertyName);
    }

    private static boolean propertyMatches(String actual, String expected) {
        return normalized(actual).equals(normalized(expected));
    }

    private static String normalized(String value) {
        return value == null ? "" : value.replace("_", "").replace(".", "").toLowerCase(Locale.ROOT);
    }
}
