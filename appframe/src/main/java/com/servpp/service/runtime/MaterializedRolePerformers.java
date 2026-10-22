package com.servpp.service.runtime;

import java.beans.Introspector;
import java.lang.reflect.Array;
import java.util.*;

/** Generated take mappings construct/backfill entity facts inside atomic service execution. */
public final class MaterializedRolePerformers {
    public record Definition(Class<?> role, List<Class<?>> entities, Object mapper, List<String> snapshotFacts,
                             List<List<Class<?>>> alternatives) {
        public Definition(Class<?> role, List<Class<?>> entities, Object mapper) { this(role, entities, mapper, List.of()); }
        public Definition(Class<?> role, List<Class<?>> entities, Object mapper, List<String> snapshotFacts) {
            this(role, entities, mapper, snapshotFacts, List.of());
        }
        public Definition {
            entities = List.copyOf(entities); snapshotFacts = List.copyOf(snapshotFacts);
            alternatives = alternatives.stream().map(List::copyOf).toList();
        }
    }
    /** Only the selected take branch is materialized; common role members do not select a branch. */
    public static boolean isActive(Definition definition, Object role) {
        return isActive(definition,role,false);
    }
    public static boolean isQueryActive(Definition definition, Object role) { return isActive(definition,role,true); }
    private static boolean isActive(Definition definition, Object role, boolean requireSelection) {
        if (definition.alternatives().size() < 2) return true;
        Set<Class<?>> common = new HashSet<>(definition.alternatives().get(0));
        Set<Class<?>> possible = new HashSet<>();
        for (var branch : definition.alternatives()) { common.retainAll(branch); possible.addAll(branch); }
        Set<Class<?>> observed = new HashSet<>();
        var actors = actors(definition,role);
        for (Class<?> type : possible) if (!common.contains(type) && actors.stream().anyMatch(type::isInstance)) observed.add(type);
        var matching = definition.alternatives().stream().filter(branch -> {
            Set<Class<?>> distinct = new HashSet<>(branch); distinct.removeAll(common); return distinct.equals(observed);
        }).toList();
        if (matching.size() > 1) throw new IllegalStateException("ROLE_PERFORMER_VARIANT_AMBIGUOUS: " + definition.role().getSimpleName());
        if (matching.isEmpty()) {
            if (observed.isEmpty() && !requireSelection) return false; // A primary-key query can load stored links before choosing a branch.
            throw new IllegalStateException("ROLE_PERFORMER_VARIANT_INVALID: " + definition.role().getSimpleName());
        }
        return new HashSet<>(matching.get(0)).equals(new HashSet<>(definition.entities()));
    }
    private static List<Object> actors(Definition definition,Object role) {
        List<Object> result = new ArrayList<>(RoleIdentityContext.performers(role,definition.role().getSimpleName()).orElse(List.of()));
        try {
            for (var property : Introspector.getBeanInfo(role.getClass(),Object.class).getPropertyDescriptors()) {
                var getter = property.getReadMethod(); if (getter == null) continue;
                Object value = getter.invoke(role); if (value != null) result.add(value);
            }
        } catch (ReflectiveOperationException | java.beans.IntrospectionException failure) {
            throw new IllegalStateException("ROLE_ENTITY_MAPPING_FAILED",failure);
        }
        return result;
    }
    private MaterializedRolePerformers() { }
    public static Map<String,Object> materialize(Definition definition, Object role) {
        if (!isActive(definition,role)) throw new IllegalStateException("ROLE_PERFORMER_VARIANT_REQUIRED: " + definition.role().getSimpleName());
        var previous = actors(definition,role);
        Map<String,Object> entities = new LinkedHashMap<>();
        try {
            for (Class<?> type : definition.entities()) {
                Object entity = previous.stream().filter(type::isInstance).findFirst().orElse(null);
                if (entity == null) entity = type.getConstructor().newInstance();
                entities.put(type.getSimpleName(),entity);
            }
            definition.mapper().getClass().getMethod("roleToEntity",definition.role(),Map.class).invoke(definition.mapper(),role,entities);
            RoleIdentityContext.attachPerformers(role,definition.role().getSimpleName(),entities.values().toArray());
            return entities;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("ROLE_ENTITY_MAPPING_FAILED",failure); }
    }
    public static void bind(List<Definition> definitions,Object[] roots) {
        Set<Object> visited=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Object root:roots) walk(definitions,root,visited);
    }
    private static void walk(List<Definition> definitions,Object value,Set<Object> visited) {
        if(value==null || !visited.add(value)) return;
        if(value instanceof Iterable<?> items) { for(Object item:items) walk(definitions,item,visited); return; }
        if(value.getClass().isArray()) { for(int i=0;i<Array.getLength(value);i++) walk(definitions,Array.get(value,i),visited); return; }
        if(value.getClass().getName().startsWith("java.") || value instanceof Number || value instanceof Enum<?>) return;
        try {
            for(Definition definition:definitions) {
                if(!definition.role().isInstance(value)) continue;
                if (isActive(definition,value)) materialize(definition,value);
            }
            for(var property:Introspector.getBeanInfo(value.getClass(),Object.class).getPropertyDescriptors()) {
                var getter=property.getReadMethod(); if(getter!=null) walk(definitions,getter.invoke(value),visited);
            }
        } catch(ReflectiveOperationException | java.beans.IntrospectionException failure) {
            throw new IllegalStateException("ROLE_ENTITY_MAPPING_FAILED",failure);
        }
    }
}
