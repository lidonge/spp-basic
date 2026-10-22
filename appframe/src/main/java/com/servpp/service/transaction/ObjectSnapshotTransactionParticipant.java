package com.servpp.service.transaction;

import java.lang.reflect.Field;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class ObjectSnapshotTransactionParticipant implements ArgumentAwareTransactionParticipant {
    private final String resourceId;
    private Object[] arguments = new Object[0];
    private Object[] snapshots = new Object[0];

    public ObjectSnapshotTransactionParticipant(String resourceId) {
        this.resourceId = resourceId;
    }

    @Override
    public String getResourceId() {
        return resourceId;
    }

    @Override
    public void setArguments(Object[] arguments) {
        this.arguments = arguments == null ? new Object[0] : arguments;
    }

    protected Object[] getArguments() {
        return arguments;
    }

    @Override
    public void beforeExecute(TransactionContext context) {
        snapshots = new Object[arguments.length];
        Map<Object, Object> visited = new IdentityHashMap<>();
        for (int i = 0; i < arguments.length; i++) {
            snapshots[i] = copy(arguments[i], visited);
        }
    }

    @Override
    public void rollback(TransactionContext context, Throwable cause) {
        Map<Object, Object> visited = new IdentityHashMap<>();
        for (int i = 0; i < arguments.length; i++) {
            restore(arguments[i], snapshots[i], visited);
        }
    }

    private Object copy(Object value, Map<Object, Object> visited) {
        if (value == null || isImmutable(value.getClass())) {
            return value;
        }
        Object existing = visited.get(value);
        if (existing != null) {
            return existing;
        }
        if (value.getClass().isArray()) {
            int size = Array.getLength(value);
            Object result = Array.newInstance(value.getClass().getComponentType(), size);
            visited.put(value, result);
            for (int index = 0; index < size; index++) Array.set(result, index, copy(Array.get(value, index), visited));
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            visited.put(value, copy);
            for (Object item : list) {
                copy.add(copy(item, visited));
            }
            return copy;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> result = new java.util.LinkedHashMap<>();
            visited.put(value, result);
            map.forEach((key, item) -> result.put(copy(key, visited), copy(item, visited)));
            return result;
        }
        try {
            Object copy = value.getClass().getDeclaredConstructor().newInstance();
            visited.put(value, copy);
            for (Field field : fieldsOf(value.getClass())) {
                field.setAccessible(true);
                field.set(copy, copy(field.get(value), visited));
            }
            return copy;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot snapshot " + value.getClass().getName(), e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void restore(Object target, Object snapshot, Map<Object, Object> visited) {
        if (target == null || snapshot == null || isImmutable(target.getClass())) {
            return;
        }
        if (visited.put(target, snapshot) != null) {
            return;
        }
        if (target.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(target); index++) {
                Object current = Array.get(target, index), previous = Array.get(snapshot, index);
                if (canRestoreInPlace(current, previous)) restore(current, previous, visited);
                else Array.set(target, index, copy(previous, new IdentityHashMap<>()));
            }
            return;
        }
        if (target instanceof List targetList && snapshot instanceof List snapshotList) {
            if (targetList.size() == snapshotList.size()) {
                for (int i = 0; i < targetList.size(); i++) {
                    Object targetItem = targetList.get(i);
                    Object snapshotItem = snapshotList.get(i);
                    if (canRestoreInPlace(targetItem, snapshotItem)) {
                        restore(targetItem, snapshotItem, visited);
                    } else if (targetList instanceof java.util.RandomAccess) {
                        targetList.set(i, copy(snapshotItem, new IdentityHashMap<>()));
                    }
                }
            } else {
                targetList.clear();
                targetList.addAll(snapshotList);
            }
            return;
        }
        if (target instanceof Map targetMap && snapshot instanceof Map snapshotMap) {
            targetMap.keySet().retainAll(snapshotMap.keySet());
            for (Object key : snapshotMap.keySet()) {
                Object current = targetMap.get(key), previous = snapshotMap.get(key);
                if (canRestoreInPlace(current, previous)) restore(current, previous, visited);
                else targetMap.put(key, copy(previous, new IdentityHashMap<>()));
            }
            return;
        }
        try {
            for (Field field : fieldsOf(target.getClass())) {
                field.setAccessible(true);
                Object targetValue = field.get(target);
                Object snapshotValue = field.get(snapshot);
                if (canRestoreInPlace(targetValue, snapshotValue)) {
                    restore(targetValue, snapshotValue, visited);
                } else {
                    field.set(target, copy(snapshotValue, new IdentityHashMap<>()));
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot restore " + target.getClass().getName(), e);
        }
    }

    private boolean canRestoreInPlace(Object target, Object snapshot) {
        return target != null
                && snapshot != null
                && target.getClass() == snapshot.getClass()
                && !isImmutable(target.getClass())
                && (!target.getClass().isArray() || Array.getLength(target) == Array.getLength(snapshot));
    }

    private List<Field> fieldsOf(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
                    fields.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private boolean isImmutable(Class<?> type) {
        return type.isPrimitive()
                || type.isEnum()
                || String.class == type
                || Number.class.isAssignableFrom(type)
                || Boolean.class == type
                || Character.class == type
                || BigDecimal.class == type
                || BigInteger.class == type
                || Temporal.class.isAssignableFrom(type);
    }
}
