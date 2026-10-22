package com.servpp.service.repository;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import com.servpp.service.runtime.ParticipantCopies;

/** Physical query baselines, keyed by the stable participant instance. */
public final class RepositoryParticipantSnapshots {
    private static final ThreadLocal<Map<Object, Object>> CURRENT = ThreadLocal.withInitial(
            () -> Collections.synchronizedMap(new IdentityHashMap<>()));
    private RepositoryParticipantSnapshots() { }

    private record Baseline(Object facts,Map<String,Object> platform, Map<String,Revision> revisions) { }
    public record Revision(ObjectRepository repository, String objectType, String objectId, String token, String origin) {
        public Revision(ObjectRepository repository, String objectType, String objectId, String token) {
            this(repository, objectType, objectId, token, null);
        }
    }
    public record WireRevision(String origin, String objectType, String objectId, String token) { }
    public record PrivateIdentity(String type, String id) { }
    /** Only identities confirmed by local APP independent storage; never copy email/phone business keys. */
    public static java.util.List<PrivateIdentity> privateIdentities(Object participant) {
        Object baseline=CURRENT.get().get(participant);
        if(!(baseline instanceof Baseline data))return java.util.List.of();
        return data.revisions().values().stream()
                .filter(revision->revision.repository() instanceof JdbcObjectRepository jdbc && jdbc.hasIndependentIdentity(revision.objectType()))
                .map(revision->new PrivateIdentity(revision.objectType(),revision.objectId())).distinct().toList();
    }
    public record WireBaseline(@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = ExactBaselineFactsDeserializer.class) Object facts, Map<String,Object> platform, java.util.List<WireRevision> revisions) { }
    public static WireBaseline exportBaseline(Object participant, String origin) {
        Object value = CURRENT.get().get(participant);
        if (!(value instanceof Baseline data)) return null;
        return new WireBaseline(data.facts(), data.platform(), data.revisions().values().stream()
                .map(revision -> new WireRevision(revision.origin() == null ? origin : revision.origin(),
                        revision.objectType(), revision.objectId(), revision.token())).toList());
    }
    public static void importBaseline(Object participant, WireBaseline wire, Object facts) {
        if (wire == null) return;
        Map<String,Revision> revisions = new java.util.HashMap<>();
        for (WireRevision revision : wire.revisions()) revisions.put(revision.objectType() + ":" + revision.objectId(),
                new Revision(null, revision.objectType(), revision.objectId(), revision.token(), revision.origin()));
        CURRENT.get().put(participant, new Baseline(facts, Map.copyOf(wire.platform()), Map.copyOf(revisions)));
    }
    public static void loaded(Object participant, ObjectRepository repository, ObjectSnapshot snapshot) {
        CURRENT.get().put(participant, new Baseline(ParticipantCopies.copy(participant), Map.of(),
                mergeRevisions(participant, new Revision(repository, snapshot.objectType(), snapshot.objectId(), snapshot.revision()))));
    }
    public static void transfer(Object source, Object target) {
        Object baseline = CURRENT.get().get(source);
        if (baseline instanceof Baseline data) {
            Map<String,Revision> revisions = new java.util.HashMap<>();
            Object current = CURRENT.get().get(target);
            Map<String,Object> platform = data.platform();
            if (current instanceof Baseline previous) {
                revisions.putAll(previous.revisions());
                for(var incoming:data.revisions().values())if(incoming.token()!=null)
                    revisions.entrySet().removeIf(entry->entry.getValue().objectType().equals(incoming.objectType())
                            && entry.getValue().repository()==null && entry.getValue().token()==null);
                // Root entity facts do not replace the role table's technical identity.
                if (!source.getClass().getSimpleName().equals(target.getClass().getSimpleName())) platform = previous.platform();
            }
            revisions.putAll(data.revisions());
            CURRENT.get().put(target, new Baseline(ParticipantCopies.copy(target), platform, Map.copyOf(revisions)));
        }
    }
    public static String requireRevision(Object participant, ObjectRepository repository, String type, String id) {
        Object baseline = CURRENT.get().get(participant);
        if (!(baseline instanceof Baseline data)) throw new IllegalStateException("PERSISTENCE_READ_REVISION_REQUIRED");
        Revision revision = data.revisions().get(type + ":" + id);
        if (revision == null || revision.token() == null) throw new IllegalStateException("PERSISTENCE_READ_REVISION_REQUIRED");
        boolean importedHere = revision.repository() == null && com.servpp.service.runtime.DomainRpcContext.currentDomain() != null
                && java.util.Objects.equals(revision.origin(), com.servpp.service.runtime.DomainRpcContext.currentDomain());
        if ((!importedHere && revision.repository() != repository) || !revision.objectType().equals(type) || !revision.objectId().equals(id))
            throw new IllegalStateException("PERSISTENCE_QUERY_IDENTITY_CHANGED");
        return revision.token();
    }
    public static void advanced(Object participant, ObjectRepository repository, String type, String id, String token) {
        CURRENT.get().put(participant, new Baseline(ParticipantCopies.copy(participant), Map.of(),
                mergeRevisions(participant, new Revision(repository, type, id, token))));
    }
    private static Map<String,Revision> mergeRevisions(Object participant, Revision revision) {
        Map<String,Revision> result = new java.util.HashMap<>();
        Object previous = CURRENT.get().get(participant);
        if (previous instanceof Baseline data) result.putAll(data.revisions());
        result.put(revision.objectType() + ":" + revision.objectId(), revision);
        return Map.copyOf(result);
    }
    public static void remember(Object participant,ObjectRepository repository,String objectType,String objectId) {
        if(participant!=null) CURRENT.get().put(participant,new Baseline(ParticipantCopies.copy(participant),repository.platformFacts(objectType,objectId),
                CURRENT.get().get(participant) instanceof Baseline data ? data.revisions() : Map.of()));
    }
    public static Map<String, Object> platformFacts(Object participant) {
        Object baseline = CURRENT.get().get(participant);
        return baseline instanceof Baseline data ? data.platform() : Map.of();
    }
    /** A loaded participant keeps the storage identity established by its owning repository. */
    public static java.util.Optional<String> objectIdentity(Object participant, String objectType) {
        Object baseline = CURRENT.get().get(participant);
        if (!(baseline instanceof Baseline data)) return java.util.Optional.empty();
        var identities = data.revisions().values().stream()
                .filter(revision -> revision.objectType().equals(objectType))
                .map(Revision::objectId).distinct().toList();
        if (identities.size() > 1) throw new IllegalStateException("PERSISTENCE_QUERY_IDENTITY_AMBIGUOUS");
        return identities.stream().findFirst();
    }
    /** A fresh independent participant has one provisional ID before facts are copied into related participants. */
    public static void prepareTransientIdentity(Object participant,String type) {
        synchronized(CURRENT.get()) {
            Object previous=CURRENT.get().get(participant);
            if(previous instanceof Baseline data) {
                var own=data.revisions().values().stream().filter(revision->revision.objectType().equals(type)).toList();
                if(own.size()==1 && own.get(0).repository()==null && own.get(0).token()==null)return;
                var retained=new java.util.HashMap<>(data.revisions());retained.entrySet().removeIf(entry->entry.getValue().objectType().equals(type));
                CURRENT.get().put(participant,new Baseline(ParticipantCopies.copy(participant),Map.of(),Map.copyOf(retained)));
            }
            attachIdentity(participant,type,java.util.UUID.randomUUID().toString());
        }
    }

    public static String identityOrCreate(Object participant, String type) {
        synchronized (CURRENT.get()) {
            var existing = objectIdentity(participant,type);
            if (existing.isPresent()) return existing.get();
            String identity = java.util.UUID.randomUUID().toString();
            attachIdentity(participant,type,identity);
            return identity;
        }
    }
    public static void attachIdentity(Object participant, String type, String identity) {
        Object previous = CURRENT.get().get(participant);
        Baseline baseline = previous instanceof Baseline data ? data : new Baseline(null,Map.of(),Map.of());
        CURRENT.get().put(participant,new Baseline(baseline.facts(),baseline.platform(),
                mergeRevisions(participant,new Revision(null,type,identity,null))));
    }
    /** Explicit source-language assignment replaces the old value, including its private identity. */
    public static void replaceMetadata(Object source, Object target) {
        Object baseline = CURRENT.get().get(source);
        if (baseline == null) CURRENT.get().remove(target);
        else CURRENT.get().put(target, baseline);
    }
    public static void copyMetadata(Object source, Object target) {
        Object baseline = CURRENT.get().get(source);
        if (baseline != null) CURRENT.get().put(target,baseline);
    }
    public static Object platform(Object participant,String column) {
        Object baseline=CURRENT.get().get(participant);
        if (!(baseline instanceof Baseline data) || !data.platform().containsKey(column))
            throw new IllegalStateException("PERSISTENCE_PLATFORM_QUERY_SNAPSHOT_REQUIRED");
        return data.platform().get(column);
    }
    public static void remember(Object participant) {
        if (participant != null) {
            Object existing = CURRENT.get().get(participant);
            CURRENT.get().put(participant, existing instanceof Baseline data
                    ? new Baseline(ParticipantCopies.copy(participant), data.platform(), data.revisions())
                    : ParticipantCopies.copy(participant));
        }
    }

    public static Object require(Object participant) {
        Object baseline = CURRENT.get().get(participant);
        if (baseline == null) throw new IllegalStateException("PERSISTENCE_QUERY_SNAPSHOT_REQUIRED");
        return baseline instanceof Baseline data ? data.facts() : baseline;
    }

    public static Map<Object, Object> checkpoint() {
        var current = CURRENT.get();
        synchronized (current) { return new IdentityHashMap<>(current); }
    }

    public static void restore(Map<Object, Object> checkpoint) {
        var current = CURRENT.get();
        synchronized (current) { current.clear(); current.putAll(checkpoint); }
    }

    public static void execute(Runnable action) {
        var previous = CURRENT.get();
        CURRENT.set(Collections.synchronizedMap(new IdentityHashMap<>()));
        try { action.run(); } finally { CURRENT.set(previous); }
    }

    public static Runnable bind(Runnable action) {
        var shared = CURRENT.get();
        return () -> {
            var previous = CURRENT.get(); CURRENT.set(shared);
            try { action.run(); } finally { CURRENT.set(previous); }
        };
    }
}
