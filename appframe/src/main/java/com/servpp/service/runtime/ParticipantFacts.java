package com.servpp.service.runtime;

import java.beans.Introspector;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.lang.reflect.InvocationTargetException;
import com.servpp.service.numeric.ExactNumbers;
import com.servpp.service.numeric.NumericAdapter;

/** Copies facts into existing instances. Query hydration and replay never exchange entry participants. */
public final class ParticipantFacts {
    /** APP identity determines logical participant equality; business fields remain ordinary facts. */
    public static boolean sameIdentity(Object left,Object right,java.util.List<String> paths) {
        if(left==null || right==null) return false;
        if(!left.getClass().equals(right.getClass()) || paths.isEmpty()) return false;
        for(String path:paths) {
            Object a=com.servpp.service.repository.ObjectPropertyAccess.read(left,path);
            Object b=com.servpp.service.repository.ObjectPropertyAccess.read(right,path);
            if(a==null || b==null || !java.util.Objects.equals(a,b)) return false;
        }
        return true;
    }

    private ParticipantFacts() { }
    public static <T> T assign(T target, T source) {
        if (source == null) {
            if (target != null) ParticipantIdentity.confirm(target, false);
            return target;
        }
        if (target == null) return ParticipantCopies.copy(source);
        merge(target, source, new IdentityHashMap<>(), true); return target;
    }
    public static void hydrate(Object target, Object source) {
        if (target == null || source == null || target.getClass() != source.getClass())
            throw new IllegalStateException("PARTICIPANT_HYDRATION_TYPE_MISMATCH");
        merge(target, source, new IdentityHashMap<>(), false);
    }
    private static void merge(Object target, Object source, IdentityHashMap<Object, Object> visited, boolean assignment) {
        if (visited.put(source, target) != null) return;
        try {
            java.util.Set<String> handled = new java.util.HashSet<>();
            for (var property : Introspector.getBeanInfo(target.getClass(), Object.class).getPropertyDescriptors()) {
                var getter = property.getReadMethod(); var setter = property.getWriteMethod();
                if (getter == null || setter == null) continue;
                getter.setAccessible(true); setter.setAccessible(true);
                Object incoming = getter.invoke(source), existing = getter.invoke(target);
                Object value = mergeValue(existing, incoming, visited, assignment);
                if (assignment) setter.invoke(target,value);
                else PropertyChanges.mapped(source,property.getName(),target,property.getName(), () -> {
                    try { setter.invoke(target,value); }
                    catch (ReflectiveOperationException failure) { throw new IllegalStateException("PARTICIPANT_HYDRATION_FAILED",failure); }
                });
                handled.add(property.getName());
            }
            for (var field : target.getClass().getFields()) {
                if (handled.contains(field.getName()) || java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        || java.lang.reflect.Modifier.isFinal(field.getModifiers())) continue;
                field.setAccessible(true);
                field.set(target, mergeValue(field.get(target), field.get(source), visited, assignment));
            }
            if (assignment) com.servpp.service.repository.RepositoryParticipantSnapshots.replaceMetadata(source, target);
            else { PropertyChanges.project(source,target); com.servpp.service.repository.RepositoryParticipantSnapshots.transfer(source, target); }
            String role = source.getClass().getSimpleName();
            var actors = RoleIdentityContext.performers(source, role);
            if (actors.isPresent()) {
                var attached = new ArrayList<Object>();
                for (Object actor : actors.get()) {
                    Object existing = visited.get(actor);
                    if (existing == null) {
                        existing = assignment ? ParticipantCopies.copy(actor) : actor;
                        visited.put(actor, existing);
                    }
                    attached.add(existing);
                }
                RoleIdentityContext.attachPerformers(target, role, attached.toArray());
            } else RoleIdentityContext.clear(target);
        } catch (ReflectiveOperationException | java.beans.IntrospectionException failure) {
            throw new IllegalStateException("PARTICIPANT_HYDRATION_FAILED", failure);
        }
    }
    private static Object mergeValue(Object existing, Object incoming, IdentityHashMap<Object, Object> visited, boolean assignment) {
        Object value = incoming;
        if (existing != null && incoming != null && existing.getClass() == incoming.getClass() && bean(existing.getClass())) {
            merge(existing, incoming, visited, assignment); value = existing;
        } else if (existing instanceof List<?> list && incoming instanceof List<?> values) {
            mergeList(list, values, visited, assignment); value = existing;
        } else if (existing != null && incoming == null && bean(existing.getClass())) {
            // An existing participant cannot be replaced by absence; its confirmation changes instead.
            ParticipantIdentity.confirm(existing, false); value = existing;
        } else if (incoming != null && incoming.getClass().isArray()) {
            int size = Array.getLength(incoming); value = Array.newInstance(incoming.getClass().getComponentType(), size);
            for (int i=0;i<size;i++) Array.set(value,i,ParticipantCopies.copy(Array.get(incoming,i)));
        } else if (incoming != null && bean(incoming.getClass())) {
            value = ParticipantCopies.copy(incoming);
        }
        return value;
    }
    @SuppressWarnings("unchecked")
    public static void hydrateCollection(List<?> target, List<?> source) {
        mergeList(target, source, new IdentityHashMap<>(), false);
    }
    @SuppressWarnings("unchecked")
    private static void mergeList(List<?> target, List<?> source, IdentityHashMap<Object, Object> visited, boolean assignment) {
        List<Object> output = new ArrayList<>(source.size());
        for (int index=0;index<source.size();index++) {
            Object incoming=source.get(index), existing=index<target.size()?target.get(index):null;
            if (incoming!=null && existing!=null && existing.getClass()==incoming.getClass() && bean(existing.getClass())) {
                merge(existing,incoming,visited,assignment); output.add(existing);
            } else {
                Object created=ParticipantCopies.copy(incoming);
                com.servpp.service.repository.RepositoryParticipantSnapshots.transfer(incoming, created); output.add(created);
            }
        }
        var mutable=(List<Object>)target; mutable.clear(); mutable.addAll(output);
    }
    private static boolean bean(Class<?> type) {
        if(type.isPrimitive() || type.isEnum() || type.isArray() || type.getName().startsWith("java.")
                || Number.class.isAssignableFrom(type) || java.util.Collection.class.isAssignableFrom(type)) return false;
        try {
            for(var property:Introspector.getBeanInfo(type,Object.class).getPropertyDescriptors())
                if(property.getReadMethod()!=null && property.getWriteMethod()!=null) return true;
            for (var field : type.getFields())
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        && !java.lang.reflect.Modifier.isFinal(field.getModifiers())) return true;
            return false;
        } catch(java.beans.IntrospectionException failure) { throw new IllegalStateException("PARTICIPANT_FACTS_TYPE_INVALID",failure); }
    }
    public record Field(String getter, NumericAdapter<?> numericAdapter) {
        public Field(String getter) { this(getter, null); }
    }
    public record Type(String name, List<Field> fields) {
        public Type { fields = List.copyOf(fields); }
    }

    public static boolean same(Object left, Object right, Type... types) {
        Map<String, Type> schema = new LinkedHashMap<>();
        for (Type type : types) {
            if (schema.putIfAbsent(type.name(), type) != null) {
                throw new IllegalArgumentException("PARTICIPANT_FACTS_DUPLICATE_TYPE: " + type.name());
            }
        }
        return new Comparison(schema).same(left, right, null);
    }

    private static final class Comparison {
        private final Map<String, Type> schema;
        private final IdentityHashMap<Object, IdentityHashMap<Object, Boolean>> visited = new IdentityHashMap<>();
        private Comparison(Map<String, Type> schema) { this.schema = schema; }

        private boolean same(Object left, Object right, NumericAdapter<?> adapter) {
            if (left == null || right == null) { return left == right; }
            boolean leftSequence = sequence(left), rightSequence = sequence(right);
            if (leftSequence || rightSequence) {
                if (!leftSequence || !rightSequence) { return false; }
                List<?> a = elements(left), b = elements(right);
                if (a.size() != b.size()) { return false; }
                if (seen(left, right)) { return true; }
                for (int index = 0; index < a.size(); index++) {
                    if (!same(a.get(index), b.get(index), adapter)) { return false; }
                }
                return true;
            }
            if (adapter != null) { return adaptedEqual(adapter, left, right); }
            if (left instanceof Number a && right instanceof Number b) {
                return ExactNumbers.decimal(a).compareTo(ExactNumbers.decimal(b)) == 0;
            }
            if (left instanceof String || left instanceof Character || left instanceof Boolean || left instanceof Enum<?>) {
                return left.equals(right);
            }
            if (left.getClass() != right.getClass()) { return false; }
            Type type = schema.get(left.getClass().getSimpleName());
            if (type == null) {
                throw new IllegalStateException("PARTICIPANT_FACTS_SCHEMA_MISSING: " + left.getClass().getName());
            }
            if (seen(left, right)) { return true; }
            for (Field field : type.fields()) {
                if (!same(read(left, field.getter()), read(right, field.getter()), field.numericAdapter())) { return false; }
            }
            return true;
        }

        private boolean seen(Object left, Object right) {
            return visited.computeIfAbsent(left, ignored -> new IdentityHashMap<>()).put(right, Boolean.TRUE) != null;
        }
        @SuppressWarnings({"rawtypes", "unchecked"})
        private boolean adaptedEqual(NumericAdapter adapter, Object left, Object right) {
            return adapter.compare(left, right) == 0;
        }
        private static boolean sequence(Object value) {
            return value.getClass().isArray() || value instanceof Collection<?>;
        }
        private static List<?> elements(Object value) {
            if (value instanceof Collection<?> collection) { return new java.util.ArrayList<>(collection); }
            var values = new java.util.ArrayList<Object>();
            for (int index = 0; index < Array.getLength(value); index++) { values.add(Array.get(value, index)); }
            return values;
        }
        private static Object read(Object target, String getter) {
            try { return target.getClass().getMethod(getter).invoke(target); }
            catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException failure) {
                throw new IllegalStateException("PARTICIPANT_FACTS_READ_FAILED: " + getter, failure);
            }
        }
    }
}
