package com.servpp.service.runtime;

import java.beans.Introspector;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** Confirmation is execution metadata; it cannot be supplied as a participant JSON property. */
public final class ParticipantIdentity {
    private static final ThreadLocal<Map<Object, Boolean>> CURRENT = ThreadLocal.withInitial(ParticipantIdentity::newScope);
    private static Map<Object,Boolean> newScope() { return Collections.synchronizedMap(new IdentityHashMap<>()); }
    private ParticipantIdentity() { }
    public static boolean isIdentified(Object participant) {
        return participant != null && Boolean.TRUE.equals(CURRENT.get().get(participant));
    }
    public static void confirm(Object participant, boolean identified) {
        if (participant == null) throw new IllegalStateException("PARTICIPANT_INSTANCE_REQUIRED");
        CURRENT.get().put(participant, identified);
    }
    public static void confirmFacts(Object participant) {
        walk(participant, "", (path,value) -> confirm(value,true), Collections.newSetFromMap(new IdentityHashMap<>()));
    }
    public static Map<String,Boolean> snapshot(Object root) {
        var result = new LinkedHashMap<String,Boolean>();
        walk(root,"",(path,value) -> { if(CURRENT.get().containsKey(value)) result.put(path,CURRENT.get().get(value)); },
                Collections.newSetFromMap(new IdentityHashMap<>()));
        return Map.copyOf(result);
    }
    public static void restore(Object root, Map<String,Boolean> snapshot) {
        if(snapshot==null) return;
        walk(root,"",(path,value) -> { if(snapshot.containsKey(path)) confirm(value,snapshot.get(path)); },
                Collections.newSetFromMap(new IdentityHashMap<>()));
    }
    /** Parallel tasks share confirmation facts and query baselines, not a physical transaction context. */
    public static Runnable bind(Runnable action) {
        var shared = CURRENT.get();
        return com.servpp.service.transaction.BusinessTraceContext.bind(com.servpp.service.transaction.ScenarioCommitEvidence.bind(LocalDomainServices.bindScope(com.servpp.service.repository.RepositoryParticipantSnapshots.bind(() -> { var previous=CURRENT.get(); CURRENT.set(shared); try { action.run(); } finally { CURRENT.set(previous); } }))));
    }
    public static void execute(Runnable action) {
        var previous = CURRENT.get(); CURRENT.set(newScope());
        try { LocalDomainServices.executeScope(() -> com.servpp.service.repository.RepositoryParticipantSnapshots.execute(action)); } finally { CURRENT.set(previous); }
    }
    private static void walk(Object value,String path,BiConsumer<String,Object> visitor,Set<Object> visited) {
        if(value==null || !visited.add(value)) return;
        if(value instanceof Iterable<?> values) {
            visitor.accept(path,value);
            int index=0; for(Object item:values) walk(item,path+"["+(index++)+"]",visitor,visited); return;
        }
        if(value.getClass().isArray()) {
            visitor.accept(path,value);
            for(int index=0;index<Array.getLength(value);index++) walk(Array.get(value,index),path+"["+index+"]",visitor,visited); return;
        }
        if(value.getClass().getName().startsWith("java.") || value instanceof Number || value instanceof Enum<?>) return;
        visitor.accept(path,value);
        try {
            for(var property:Introspector.getBeanInfo(value.getClass(),Object.class).getPropertyDescriptors()) {
                var getter=property.getReadMethod(); if(getter==null) continue; getter.setAccessible(true);
                walk(getter.invoke(value),path.isEmpty()?property.getName():path+"."+property.getName(),visitor,visited);
            }
        } catch(ReflectiveOperationException | java.beans.IntrospectionException failure) {
            throw new IllegalStateException("PARTICIPANT_CONFIRMATION_FACTS_FAILED",failure);
        }
    }
}
