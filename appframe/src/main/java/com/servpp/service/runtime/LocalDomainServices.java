package com.servpp.service.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.Service;
import com.servpp.service.scenario.IExecutor;
import com.servpp.service.server.DefaultServiceContainer;
import com.servpp.service.transaction.DefaultTransactionManager;
import java.beans.Introspector;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** APP-bound local delegation to an actual generated provider, with explicit fact projection. */
public final class LocalDomainServices {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final ThreadLocal<java.util.Map<Object,java.util.Map<String,Object>>> PROJECTIONS = ThreadLocal.withInitial(LocalDomainServices::newScope);
    private static java.util.Map<Object,java.util.Map<String,Object>> newScope() {
        return java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
    }
    private LocalDomainServices() { }
    public static void executeScope(Runnable action) {
        var previous = PROJECTIONS.get(); PROJECTIONS.set(newScope());
        try { DomainServiceRouter.executeScope(action); } finally { PROJECTIONS.set(previous); }
    }
    public static Runnable bindScope(Runnable action) {
        var shared = PROJECTIONS.get();
        return () -> { var previous = PROJECTIONS.get(); PROJECTIONS.set(shared); try { DomainServiceRouter.executeScope(action); } finally { PROJECTIONS.set(previous); } };
    }
    static Object roleView(Object participant, Class<?> type) {
        var views = PROJECTIONS.get().computeIfAbsent(participant, ignored -> new java.util.concurrent.ConcurrentHashMap<>());
        return views.computeIfAbsent("roleView:" + type.getName(), ignored -> {
            try { return type.getConstructor().newInstance(); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("ROLE_VIEW_CONSTRUCTION_FAILED", failure); }
        });
    }
    static Object roleProjection(Object participant, Class<?> type) {
        if (type.isInstance(participant)) return participant;
        Object view = roleView(participant, type);
        try {
            String[] facts = java.util.Arrays.stream(Introspector.getBeanInfo(type, Object.class).getPropertyDescriptors())
                    .filter(property -> property.getReadMethod() != null && property.getWriteMethod() != null)
                    .map(java.beans.PropertyDescriptor::getName).toArray(String[]::new);
            copyFacts(view, participant, type, facts, new String[0], false, false);
            RoleIdentityContext.performers(participant, type.getSimpleName()).ifPresent(actors ->
                    RoleIdentityContext.attachPerformers(view, type.getSimpleName(), actors.toArray()));
            return view;
        } catch (java.beans.IntrospectionException failure) {
            throw new IllegalStateException("ROLE_PROJECTION_FAILED", failure);
        }
    }
    static void mergeRoleProjection(Object target, Object source, boolean query) {
        if (target == source) return;
        try {
            String[] facts = java.util.Arrays.stream(Introspector.getBeanInfo(target.getClass(), Object.class).getPropertyDescriptors())
                    .filter(property -> property.getReadMethod() != null && property.getWriteMethod() != null)
                    .map(java.beans.PropertyDescriptor::getName).toArray(String[]::new);
            copyFacts(target, source, target.getClass(), facts, new String[0], false, query);
        } catch (java.beans.IntrospectionException failure) {
            throw new IllegalStateException("ROLE_PROJECTION_FAILED", failure);
        }
    }
    private static Method providerMethod(Class<?> type, String name, int count) {
        for (Class<?> contract : type.getInterfaces()) {
            Method declared = providerMethod(contract, name, count);
            if (declared != null) return declared;
        }
        return java.util.Arrays.stream(type.getDeclaredMethods()).filter(candidate -> candidate.getName().equals(name) && candidate.getParameterCount() == count).findFirst().orElse(null);
    }
    public static void invoke(String qualifiedService, Object[] participants, Class<?>[] participantTypes, String[][] facts, String[][] identities, boolean[] existing, boolean query) {
        if (participants.length != facts.length) throw new IllegalArgumentException("LOCAL_SERVICE_PARTICIPANT_MAPPING_REQUIRED");
        boolean remote = DomainServiceRouter.isRemote(qualifiedService);
        Service service = remote ? DomainServiceRouter.prototype(qualifiedService) : DefaultServiceContainer.getInstance().getService(qualifiedService);
        if (service == null) throw new IllegalStateException("LOCAL_PROVIDER_NOT_DEPLOYED: " + qualifiedService);
        String name = qualifiedService.substring(qualifiedService.lastIndexOf('.') + 1);
        Method method = providerMethod(service.getClass(), name, participants.length);
        if (method == null) throw new IllegalStateException("LOCAL_PROVIDER_METHOD_MISSING: " + qualifiedService);
        Type[] types = method.getGenericParameterTypes();
        Object[] projected = new Object[participants.length];
        for (int index = 0; index < participants.length; index++) {
            if (participants[index] == null) throw new IllegalArgumentException("LOCAL_SERVICE_PARTICIPANT_REQUIRED");
            var type = MAPPER.getTypeFactory().constructType(types[index]);
            Object fresh = MAPPER.convertValue(select(participants[index], facts[index]), type);
            projectMetadata(participants[index], fresh);
            var views = PROJECTIONS.get().computeIfAbsent(participants[index], ignored -> new java.util.concurrent.ConcurrentHashMap<>());
            Object previous = views.get(types[index].getTypeName());
            if (previous != null) {
                copyFacts(previous, fresh, type.isCollectionLikeType() ? type.getContentType().getRawClass() : type.getRawClass(), facts[index], identities[index], false, false);
                fresh = previous;
            }
            views.put(types[index].getTypeName(), fresh);
            projectMetadata(participants[index], fresh);
            ParticipantIdentity.confirm(fresh,ParticipantIdentity.isIdentified(participants[index]));
            projected[index] = fresh;
        }
        Runnable action = () -> {
            try { method.invoke(service, projected); }
            catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw new IllegalStateException("LOCAL_PROVIDER_FAILED", cause);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("LOCAL_PROVIDER_INVOCATION_FAILED", failure); }
        };
        var bounds = com.servpp.service.transaction.QuantumInvocationBounds.project(participants, projected);
        var manager = DefaultTransactionManager.getInstance();
        if (remote) {
            Object[] returned = DomainServiceRouter.invoke(qualifiedService,service,projected,types);
            for (int index = 0; index < projected.length; index++) {
                projected[index] = returned[index];
                PROJECTIONS.get().get(participants[index]).put(types[index].getTypeName(),returned[index]);
            }
        } else if (manager.isExecutingTransaction()) {
            Runnable invocation = () -> manager.execute(List.of(new IExecutor() {
                public Service getService() { return service; }
                public Object[] getArguments() { return projected; }
                public List<com.servpp.service.transaction.QuantumInvocationBounds.Bound> getQuantumBounds() { return bounds; }
                public void execute() { com.servpp.service.repository.TransactionQueryReads.project(participants, projected, action); }
            }));
            if (com.servpp.service.transaction.DeclaredTransactionContext.currentMode() == com.servpp.service.transaction.TransactionMode.SAGA
                    && service instanceof com.servpp.service.repository.RepositoryWriteRuleProvider)
                com.servpp.service.repository.RepositoryWriteRuleProvider.executeInRepositoryScope(service, invocation);
            else invocation.run();
        } else com.servpp.service.repository.TransactionQueryReads.project(participants, projected, action);
        for (int index = 0; index < participants.length; index++) copyFacts(participants[index], projected[index], participantTypes[index], facts[index], identities[index], existing[index], query);
    }
    /** Complete an identified role through its owning domain, without replacing the role instance. */
    public static void hydrateRole(String provider, Object participant, Class<?> roleType, String[] facts) {
        if (participant instanceof Iterable<?> collection) {
            for (Object role : collection) hydrateRole(provider, role, roleType, facts);
            return;
        }
        if (participant == null || !ParticipantIdentity.isIdentified(participant)) return;
        invoke(provider, new Object[]{participant}, new Class<?>[]{roleType},
                new String[][]{facts}, new String[][]{new String[0]}, new boolean[]{false}, true);
    }
    private static Object select(Object participant, String[] facts) {
        if (participant instanceof Iterable<?> collection) {
            List<Object> selected = new ArrayList<>();
            for (Object item : collection) selected.add(select(item, facts));
            return selected;
        }
        var selected = new LinkedHashMap<String,Object>();
        for (String fact : facts) selected.put(fact, read(participant, fact));
        return selected;
    }
    @SuppressWarnings("unchecked")
    private static void copyFacts(Object target, Object source, Class<?> element, String[] facts, String[] identity, boolean existingOnly, boolean query) {
        if (target instanceof List<?> list && source instanceof List<?> values) {
            List<Object> output = (List<Object>) list;
            List<Object> hydrated = new ArrayList<>();
            var byIdentity = new java.util.LinkedHashMap<List<Object>,Object>();
            for (Object item : output) {
                var key = java.util.Arrays.stream(identity).map(fact -> com.servpp.service.repository.ObjectPropertyAccess.read(item, fact)).toList();
                if (byIdentity.putIfAbsent(key, item) != null) throw new IllegalStateException("LOCAL_COLLECTION_IDENTITY_DUPLICATE");
            }
            var returned = new java.util.HashSet<List<Object>>();
            for (Object item : values) {
                var key = java.util.Arrays.stream(identity).map(fact -> com.servpp.service.repository.ObjectPropertyAccess.read(item, fact)).toList();
                if (!returned.add(key)) throw new IllegalStateException("LOCAL_PROVIDER_COLLECTION_IDENTITY_DUPLICATE");
                Object targetItem = byIdentity.get(key);
                if (targetItem == null) {
                    if (existingOnly) continue;
                    targetItem = MAPPER.convertValue(select(item, facts), element);
                }
                copyFacts(targetItem, item, element, facts, identity, false, query);
                hydrated.add(targetItem);
            }
            if (existingOnly) {
                for (var entry : byIdentity.entrySet()) if (!returned.contains(entry.getKey()) && query)
                    ParticipantIdentity.confirm(entry.getValue(), false);
            } else { output.clear(); output.addAll(hydrated); }
            if (query) ParticipantIdentity.confirm(target, ParticipantIdentity.isIdentified(source));
            return;
        }
        try {
            for (var property : Introspector.getBeanInfo(target.getClass(), Object.class).getPropertyDescriptors()) {
                if (!java.util.Arrays.asList(facts).contains(property.getName()) || property.getWriteMethod() == null) continue;
                Object incoming = read(source, property.getName());
                var propertyType = MAPPER.getTypeFactory().constructType(
                        property.getWriteMethod().getGenericParameterTypes()[0]);
                Object converted = MAPPER.convertValue(incoming, propertyType);
                projectMetadata(incoming, converted);
                Object current = property.getReadMethod() == null ? null : property.getReadMethod().invoke(target);
                if (current != null && converted != null && current.getClass().equals(converted.getClass())
                        && !current.getClass().getName().startsWith("java.") && !(current instanceof Enum<?>)) {
                    ParticipantFacts.hydrate(current, converted);
                    projectMetadata(incoming, current);
                    converted = current;
                }
                Object projectedValue = converted;
                PropertyChanges.mapped(source,property.getName(),target,property.getName(), () -> {
                    try { property.getWriteMethod().invoke(target, projectedValue); }
                    catch (ReflectiveOperationException failure) { throw new IllegalStateException("LOCAL_PARTICIPANT_FACT_HYDRATION_FAILED", failure); }
                });
                com.servpp.service.transaction.QuantumParticipantResults.link(source,target,property.getName(), value -> {
                    try { property.getWriteMethod().invoke(target,MAPPER.convertValue(value,propertyType)); }
                    catch (ReflectiveOperationException failure) { throw new IllegalStateException("QUANTUM_REFILL_FAILED",failure); }
                });
            }
            if (query) {
                ParticipantIdentity.confirm(target, ParticipantIdentity.isIdentified(source));
            }
            com.servpp.service.repository.RepositoryParticipantSnapshots.transfer(source, target);
            transferPerformers(source, target);
        } catch (ReflectiveOperationException | java.beans.IntrospectionException failure) {
            throw new IllegalStateException("LOCAL_PARTICIPANT_FACT_HYDRATION_FAILED", failure);
        }
    }
    /** JSON projects declared facts; private storage identity and confirmation travel out of band. */
    private static void projectMetadata(Object source, Object target) {
        projectMetadata(source, target, new java.util.IdentityHashMap<>());
    }
    private static void projectMetadata(Object source, Object target, java.util.IdentityHashMap<Object,java.util.Set<Object>> visited) {
        if (source == null || target == null) return;
        var targets = visited.computeIfAbsent(source, ignored -> java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
        if (!targets.add(target)) return;
        if (source instanceof List<?> inputs && target instanceof List<?> outputs) {
            for (int index = 0; index < Math.min(inputs.size(), outputs.size()); index++)
                projectMetadata(inputs.get(index), outputs.get(index), visited);
        } else if (!source.getClass().getName().startsWith("java.") && !(source instanceof Enum<?>)) {
            try {
                var inputs = new java.util.HashMap<String,Method>();
                for (var property : Introspector.getBeanInfo(source.getClass(), Object.class).getPropertyDescriptors())
                    if (property.getReadMethod() != null) inputs.put(property.getName(), property.getReadMethod());
                for (var property : Introspector.getBeanInfo(target.getClass(), Object.class).getPropertyDescriptors()) {
                    Method input = inputs.get(property.getName());
                    if (input != null && property.getReadMethod() != null)
                        projectMetadata(input.invoke(source), property.getReadMethod().invoke(target), visited);
                }
            } catch (ReflectiveOperationException | java.beans.IntrospectionException failure) {
                throw new IllegalStateException("LOCAL_PARTICIPANT_METADATA_PROJECTION_FAILED", failure);
            }
        } else return;
        PropertyChanges.project(source,target);
        if (source.getClass().getSimpleName().equals(target.getClass().getSimpleName()))
            com.servpp.service.repository.RepositoryParticipantSnapshots.replaceMetadata(source, target);
        else com.servpp.service.repository.RepositoryParticipantSnapshots.transfer(source, target);
        ParticipantIdentity.restore(target, ParticipantIdentity.snapshot(source));
        transferPerformers(source, target);
    }
    private static void transferPerformers(Object source, Object target) {
        if (!source.getClass().getSimpleName().equals(target.getClass().getSimpleName())) return;
        String role = source.getClass().getSimpleName();
        RoleIdentityContext.performers(source, role).ifPresent(actors ->
                RoleIdentityContext.attachPerformers(target, role, actors.toArray()));
    }
    private static Object read(Object bean, String propertyName) {
        try {
            for (var property : Introspector.getBeanInfo(bean.getClass(), Object.class).getPropertyDescriptors())
                if (property.getName().equals(propertyName) && property.getReadMethod() != null)
                    return property.getReadMethod().invoke(bean);
        } catch (ReflectiveOperationException | java.beans.IntrospectionException failure) {
            throw new IllegalStateException("LOCAL_PARTICIPANT_FACT_READ_FAILED", failure);
        }
        throw new IllegalArgumentException("LOCAL_PARTICIPANT_FACT_MISSING: " + propertyName);
    }
}
