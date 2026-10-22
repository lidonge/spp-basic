package com.servpp.service.runtime;

import com.servpp.service.repository.ObjectPropertyAccess;
import com.servpp.service.repository.RepositoryParticipantSnapshots;
import com.servpp.service.repository.TransactionQueryReads;
import com.servpp.service.transaction.QuantumInvocationBounds;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/** Generated take adapters bridge domain parameter roles while retaining entry instances. */
public final class RoleBoundArguments {
    public record Adapter(Class<?> viewType, Object mapper, String performer, String[] facts, String[] identities, boolean existing, boolean performerView) {
        public Adapter(Class<?> viewType, Object mapper, String performer, String[] facts, String[] identities, boolean existing) {
            this(viewType, mapper, performer, facts, identities, existing, false);
        }
    }
    private RoleBoundArguments() { }
    public static void invoke(String service, Object[] participants, Class<?>[] participantTypes, Adapter[] adapters, boolean query) {
        Object[] views = new Object[participants.length];
        for (int i = 0; i < views.length; i++) views[i] = views(participants[i], adapters[i]);
        String[][] facts = Arrays.stream(adapters).map(Adapter::facts).toArray(String[][]::new);
        String[][] identities = Arrays.stream(adapters).map(Adapter::identities).toArray(String[][]::new);
        boolean[] existing = new boolean[adapters.length];
        Class<?>[] types = Arrays.stream(adapters).map(Adapter::viewType).toArray(Class<?>[]::new);
        for (int i = 0; i < existing.length; i++) existing[i] = adapters[i].existing();
        var bounds = QuantumInvocationBounds.project(participants, views);
        TransactionQueryReads.project(participants, views, () -> QuantumInvocationBounds.execute(bounds,
                () -> LocalDomainServices.invoke(service, views, types, facts, identities, existing, query)));
        for (int i = 0; i < views.length; i++) if (adapters[i].mapper() != null)
            merge(participants[i], views[i], participantTypes[i], adapters[i], query);
    }
    private static Object views(Object participant, Adapter adapter) {
        if (adapter.mapper() == null) return participant;
        if (participant instanceof List<?> collection) {
            List<Object> result = new ArrayList<>();
            for (Object item : collection) result.add(view(item, adapter));
            return result;
        }
        return view(participant, adapter);
    }
    private static Object identityView(Object participant, Adapter adapter) {
        try {
            Object value = adapter.viewType().getConstructor().newInstance();
            mapping(adapter, true, value, participant);
            return value;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("ROLE_IDENTITY_MAPPING_FAILED",failure); }
    }
    private static Object view(Object participant, Adapter adapter) {
        if (participant == null) throw new IllegalStateException("ROLE_PARTICIPANT_REQUIRED");
        if (adapter.performerView()) return RolePerformerArguments.performer(participant, adapter);
        Object view = LocalDomainServices.roleView(participant, adapter.viewType());
        mapping(adapter, true, view, participant);
        RepositoryParticipantSnapshots.transfer(participant, view);
        return view;
    }
    @SuppressWarnings("unchecked")
    private static void merge(Object target, Object source, Class<?> participantType, Adapter adapter, boolean query) {
        if (adapter.performerView()) {
            if (target instanceof List<?> originals && source instanceof List<?> values) {
                if (originals.size() != values.size()) throw new IllegalStateException("PERFORMER_COLLECTION_CARDINALITY_CHANGED");
                for (int i = 0; i < originals.size(); i++) RolePerformerArguments.merge(originals.get(i), values.get(i), adapter, query);
            } else RolePerformerArguments.merge(target, source, adapter, query);
            return;
        }
        if (target instanceof List<?> originals && source instanceof List<?> values) {
            var byKey = new LinkedHashMap<List<Object>,Object>();
            for (Object original : originals) {
                if (byKey.putIfAbsent(key(identityView(original,adapter),adapter.identities()), original) != null)
                    throw new IllegalStateException("ROLE_COLLECTION_IDENTITY_DUPLICATE");
            }
            List<Object> hydrated = new ArrayList<>();
            for (Object value : values) {
                Object original = byKey.get(key(value, adapter.identities()));
                if (original == null) {
                    if (adapter.existing()) continue;
                    try { original = participantType.getConstructor().newInstance(); }
                    catch (ReflectiveOperationException failure) { throw new IllegalStateException("ROLE_PARTICIPANT_CONSTRUCTION_FAILED",failure); }
                }
                merge(original,value,participantType,adapter,query);
                hydrated.add(original);
            }
            if (!adapter.existing()) { var mutable = (List<Object>) originals; mutable.clear(); mutable.addAll(hydrated); }
            if (query) ParticipantIdentity.confirm(target, ParticipantIdentity.isIdentified(source));
            return;
        }
        mapping(adapter, false, source, target);
        RepositoryParticipantSnapshots.transfer(source, target);
        if (query) ParticipantIdentity.confirm(target, ParticipantIdentity.isIdentified(source));
    }
    private static List<Object> key(Object view, String[] paths) { return Arrays.stream(paths).map(path -> ObjectPropertyAccess.read(view,path)).toList(); }
    private static void mapping(Adapter adapter, boolean toRole, Object role, Object participant) {
        Map<String,Object> performers = new LinkedHashMap<>(); performers.put(adapter.performer(),participant);
        try {
            if (toRole) adapter.mapper().getClass().getMethod("entityToRole",Map.class,adapter.viewType()).invoke(adapter.mapper(),performers,role);
            else adapter.mapper().getClass().getMethod("roleToEntity",adapter.viewType(),Map.class).invoke(adapter.mapper(),role,performers);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException cause) throw cause;
            throw new IllegalStateException("ROLE_PARAMETER_MAPPING_FAILED",failure.getCause());
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("ROLE_PARAMETER_MAPPING_FAILED",failure); }
    }
}
