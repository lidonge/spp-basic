package com.servpp.service.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.runtime.AppDdlCatalog;
import java.util.*;

/** A stored role carries its own facts and performer identity, never inherited entity facts. */
final class ParticipantStorageFacts {
    private ParticipantStorageFacts() { }
    static Object project(Object facts, Object original, AppDdlCatalog catalog, ObjectMapper mapper) {
        if (catalog == null || catalog.roleStorage().isEmpty() && catalog.tables().stream().allMatch(table -> table.excludedFacts().isEmpty() && table.queryService().isBlank())) return facts;
        if (facts == null || original == null) return facts;
        if (original instanceof Iterable<?> values) {
            List<Object> originals = new ArrayList<>(); values.forEach(originals::add);
            List<Object> stored = new ArrayList<>(); ((Iterable<?>)facts).forEach(stored::add);
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < stored.size(); i++) result.add(project(stored.get(i),originals.get(i),catalog,mapper));
            return result;
        }
        if (original instanceof String || original instanceof Number || original instanceof Boolean || original instanceof Enum<?>
                || original.getClass().getName().startsWith("java.time.")) return facts;
        Map<String,Object> result = facts instanceof Map<?,?> map ? new LinkedHashMap<>((Map<String,Object>)map)
                : mapper.convertValue(facts,Map.class);
        var table = catalog.tableForBusinessType(original.getClass().getSimpleName());
        table.ifPresent(mapping -> mapping.excludedFacts().forEach(result::remove));
        for (var entry : new ArrayList<>(result.entrySet())) {
            if (table.isPresent() && table.get().snapshotFacts().contains(entry.getKey())) continue;
            Object child = ObjectPropertyAccess.read(original,entry.getKey());
            result.put(entry.getKey(), reference(entry.getValue(),child,catalog,mapper));
        }
        return result;
    }
    private static Object reference(Object facts, Object original, AppDdlCatalog catalog, ObjectMapper mapper) {
        if (original == null) return facts;
        if (original instanceof Iterable<?> members && facts instanceof Iterable<?> values) {
            var inputs = members.iterator(); var stored = values.iterator(); List<Object> result = new ArrayList<>();
            while (inputs.hasNext() && stored.hasNext()) result.add(reference(stored.next(),inputs.next(),catalog,mapper));
            return result;
        }
        var table = catalog.tableForBusinessType(original.getClass().getSimpleName());
        var roleIdentity = catalog.roleStorage().get(original.getClass().getSimpleName());
        if (table.isPresent() && roleIdentity != null && roleIdentity.independent()) {
            String id = RepositoryParticipantSnapshots.identityOrCreate(original,roleIdentity.type());
            Map<String,Object> retained = table.get().queryService().isBlank()
                    ? new LinkedHashMap<>((Map<String,Object>)project(facts,original,catalog,mapper)) : new LinkedHashMap<>();
            retained.put(ParticipantStoredIdentities.MARKER,Map.of("type",roleIdentity.type(),"id",id));
            return retained;
        }
        if (table.isPresent() && !table.get().queryService().isBlank()) {
            if (!table.get().platformPrimaryKeys().isEmpty()) {
                var role = catalog.roleStorage().get(original.getClass().getSimpleName());
                if (role == null || !role.independent()) throw new IllegalStateException("PERSISTENCE_NESTED_PLATFORM_IDENTITY_UNSUPPORTED");
                String id = RepositoryParticipantSnapshots.identityOrCreate(original,role.type());
                return Map.of(ParticipantStoredIdentities.MARKER,Map.of("type",role.type(),"id",id));
            }
            Map<String,Object> keys = new LinkedHashMap<>();
            for (String column : table.get().objectBackedPrimaryKeys()) {
                String path = ParticipantStorageKeys.path(original,column,catalog);
                String[] parts = path.split("\\."); Map<String,Object> cursor = keys;
                for (int i=0;i<parts.length-1;i++) cursor = (Map<String,Object>)cursor.computeIfAbsent(parts[i],ignored -> new LinkedHashMap<>());
                Object identity=ObjectPropertyAccess.read(original,path);
                if("@storageIdentity".equals(parts[parts.length-1])) {
                    Object owner=parts.length==1?original:ObjectPropertyAccess.read(original,String.join(".",Arrays.copyOf(parts,parts.length-1)));
                    cursor.put(ParticipantStoredIdentities.MARKER,Map.of("type",owner.getClass().getSimpleName(),"id",identity));
                } else cursor.put(parts[parts.length-1],identity);
            }
            return keys;
        }
        return project(facts,original,catalog,mapper);
    }

}
