package com.servpp.service.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deep value copies used by generated source-language participant operations. */
public final class ParticipantCopies {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private ParticipantCopies() { }

    @SuppressWarnings("unchecked")
    public static <T> T copy(T value) {
        if (value == null) { return null; }
        // A plain Object is a stateless identity token used by custom executors, not a bean.
        if (value.getClass() == Object.class) { return value; }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) { result.add(copy(item)); }
            return (T) result;
        }
        if (value instanceof Set<?> set) {
            Set<Object> result = new LinkedHashSet<>();
            for (Object item : set) { result.add(copy(item)); }
            return (T) result;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(copy(key), copy(item)));
            return (T) result;
        }
        if (value.getClass().isArray()) {
            int size = Array.getLength(value);
            Object result = Array.newInstance(value.getClass().getComponentType(), size);
            for (int i = 0; i < size; i++) { Array.set(result, i, copy(Array.get(value, i))); }
            return (T) result;
        }
        try {
            T result = (T) MAPPER.convertValue(value, value.getClass());
            ParticipantIdentity.restore(result, ParticipantIdentity.snapshot(value));
            copyMetadata(value,result,new java.util.IdentityHashMap<>());
            return result;
        }
        catch (IllegalArgumentException failure) {
            throw new IllegalStateException("PARTICIPANT_COPY_FAILED", failure);
        }
    }
    private static void copyMetadata(Object source,Object target,Map<Object,Object> visited) {
        if (source == null || target == null || visited.containsKey(source)) return;
        visited.put(source,target);
        com.servpp.service.repository.RepositoryParticipantSnapshots.copyMetadata(source,target);
        PropertyChanges.copy(source,target);
        if (source instanceof List<?> inputs && target instanceof List<?> outputs) {
            for (int i=0;i<Math.min(inputs.size(),outputs.size());i++) copyMetadata(inputs.get(i),outputs.get(i),visited);
            return;
        }
        if (source.getClass().getName().startsWith("java.") || source instanceof Enum<?>) return;
        try {
            for (var property : java.beans.Introspector.getBeanInfo(source.getClass(),Object.class).getPropertyDescriptors())
                if (property.getReadMethod() != null) copyMetadata(property.getReadMethod().invoke(source),property.getReadMethod().invoke(target),visited);
            String role = source.getClass().getSimpleName();
            var actors = RoleIdentityContext.performers(source,role);
            if (actors.isPresent()) {
                List<Object> copied = new ArrayList<>();
                for (Object actor : actors.get()) {
                    Object clone = visited.get(actor);
                    if (clone == null) { clone = MAPPER.convertValue(actor,actor.getClass()); copyMetadata(actor,clone,visited); }
                    copied.add(clone);
                }
                RoleIdentityContext.attachPerformers(target,role,copied.toArray());
            }
        } catch (ReflectiveOperationException | java.beans.IntrospectionException failure) {
            throw new IllegalStateException("PARTICIPANT_IDENTITY_COPY_FAILED",failure);
        }
    }

}
