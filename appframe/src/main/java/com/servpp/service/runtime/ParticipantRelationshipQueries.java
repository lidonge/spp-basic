package com.servpp.service.runtime;

import com.servpp.service.repository.ObjectPropertyAccess;
import java.beans.Introspector;
import java.util.*;

/** Stored participant associations are identities, completed through declared query services. */
public final class ParticipantRelationshipQueries {
    private record QueryScope(Set<List<Object>> active, Map<List<Object>,Object> completed) { }
    private static final ThreadLocal<QueryScope> ACTIVE = new ThreadLocal<>();
    private ParticipantRelationshipQueries() { }
    public static void complete(Object root, AppDdlCatalog catalog) {
        if (catalog == null || root == null) return;
        boolean owner = ACTIVE.get() == null;
        if (owner) ACTIVE.set(new QueryScope(new HashSet<>(),new HashMap<>()));
        try { visit(root,catalog,true); }
        finally { if (owner) ACTIVE.remove(); }
    }
    private static void visit(Object value, AppDdlCatalog catalog, boolean root) {
        if (value == null || value instanceof Number || value instanceof String || value instanceof Boolean
                || value instanceof Enum<?> || value.getClass().getName().startsWith("java.time.")) return;
        if (value instanceof Iterable<?> members) { for (Object item : members) visit(item,catalog,false); return; }
        List<Object> completedKey = null;
        var table = catalog.tableForBusinessType(value.getClass().getSimpleName());
        if (table.isPresent() && !table.get().queryService().isBlank()) {
            var mapping = table.get(); List<Object> key = new ArrayList<>(); key.add(mapping.queryService());
            if (!mapping.platformPrimaryKeys().isEmpty()) key.add(com.servpp.service.repository.RepositoryParticipantSnapshots.objectIdentity(value,mapping.businessType()).orElse(null));
            for (String path : mapping.objectBackedPrimaryKeys()) key.add(ObjectPropertyAccess.read(value,com.servpp.service.repository.ParticipantStorageKeys.path(value,path,catalog)));
            List<Object> viewKey = new ArrayList<>(key); viewKey.add(value.getClass());
            Object cached = ACTIVE.get().completed().get(viewKey);
            if (cached != null) { if (cached != value) ParticipantFacts.hydrate(value,cached); return; }
            if (!ACTIVE.get().active().add(key)) return;
            completedKey = viewKey;
            if (!root) {
                String[] facts = facts(value);
                LocalDomainServices.invoke(mapping.queryService(),new Object[]{value},new Class<?>[]{value.getClass()},
                        new String[][]{facts},new String[][]{new String[0]},new boolean[]{false},true);
                if (!ParticipantIdentity.isIdentified(value)) throw new IllegalStateException("PARTICIPANT_ASSOCIATION_NOT_FOUND: " + mapping.queryService());
            }
        }
        try {
            for (var property : Introspector.getBeanInfo(value.getClass(),Object.class).getPropertyDescriptors())
                if (property.getReadMethod() != null
                        && (table.isEmpty() || !table.get().snapshotFacts().contains(property.getName())))
                    visit(property.getReadMethod().invoke(value),catalog,false);
            if (completedKey != null) {
                ACTIVE.get().completed().put(completedKey,value);
                ACTIVE.get().active().remove(completedKey.subList(0,completedKey.size()-1));
            }
        } catch (ReflectiveOperationException | java.beans.IntrospectionException failure) {
            throw new IllegalStateException("PARTICIPANT_ASSOCIATION_QUERY_FAILED",failure);
        }
    }
    private static String[] facts(Object value) {
        try { return Arrays.stream(Introspector.getBeanInfo(value.getClass(),Object.class).getPropertyDescriptors())
                .filter(property -> property.getReadMethod() != null && property.getWriteMethod() != null)
                .map(java.beans.PropertyDescriptor::getName).toArray(String[]::new); }
        catch (java.beans.IntrospectionException failure) { throw new IllegalStateException("PARTICIPANT_ASSOCIATION_MODEL_INVALID",failure); }
    }
}
